package au.crewcomp.evidence

import au.crewcomp.engine.HoldingStatus
import au.crewcomp.people.Person
import au.crewcomp.people.PersonRepository
import au.crewcomp.platform.adapters.DocumentPart
import au.crewcomp.platform.adapters.ExtractedField
import au.crewcomp.platform.adapters.ExtractionRequest
import au.crewcomp.platform.adapters.LlmClient
import au.crewcomp.platform.adapters.ObjectStorage
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.Requirement
import au.crewcomp.reference.RequirementRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.hibernate.Hibernate
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The office-side certificate intake — the Coolibah portal's "upload a certificate" as a §8 flow.
 *
 * Two steps, and the gap between them is the whole design:
 *
 *  1. **Read.** The bytes are held in object storage under an intake id, the model reads them
 *     (stage 2, with the same fixed schema and constant instruction the mobile pipeline uses), and
 *     the reading comes back to the screen *with suggestions*: which crew member the holder's name
 *     looks like, which catalogue entry the title looks like. Nothing is written to the system of
 *     record. Not a holding, not a document.
 *  2. **File.** A person **confirms** the person, the code and the dates, and only then does the
 *     document exist — created, extracted-as-recorded, and accepted in one transaction through the
 *     same [EvidenceReviewService.accept] a Data Steward uses on ADM-9. The audit trail therefore
 *     carries both what the model read and what the human filed, which is the LLM-2 measurement.
 *
 * Why the person is a *suggestion* and never an inference: `ExtractionStages.matchRequirement`
 * says it — a name printed on a certificate is exactly the class of guess that produced the source
 * data's reused Sam numbers. The screen's "no such crew member — add them?" pop-up is the human
 * step that rule requires, made cheap.
 */
@ApplicationScoped
class OfficeEvidenceService(
    private val stages: OfficeIntakeStages,
    private val storage: ObjectStorage,
    private val llm: LlmClient,
    private val policy: AccessPolicy,
    private val objectMapper: ObjectMapper,
) {

    /** Reads one uploaded file and returns the model's reading with suggestions. Writes nothing. */
    fun read(bytes: ByteArray, contentType: String, fileName: String?): IntakeReading {
        policy.require(Role.DATA_STEWARD, Role.CREW_COORDINATOR, Role.SYSTEM_ADMINISTRATOR)
        require(bytes.isNotEmpty()) { "The upload carried no bytes" }
        require(bytes.size <= EvidenceService.MAX_SUBMISSION_BYTES) {
            "The file exceeds the ${EvidenceService.MAX_SUBMISSION_BYTES} byte limit"
        }
        require(contentType in EvidenceService.ACCEPTED_CONTENT_TYPES) {
            "Unsupported content type '$contentType' — accepted: ${EvidenceService.ACCEPTED_CONTENT_TYPES.joinToString()}"
        }

        val intakeId = UUID.randomUUID()
        storage.put(originalKey(intakeId), bytes, contentType)

        // Outside any transaction, deliberately — the model call is the slow part (LLM-5).
        val result = llm.extract(
            ExtractionRequest(
                documents = listOf(DocumentPart(contentType, bytes)),
                outputSchema = ExtractionSchema.OUTPUT_SCHEMA,
                instruction = ExtractionSchema.INSTRUCTION,
            ),
        )

        val meta = IntakeMeta(
            intakeId = intakeId.toString(),
            fileName = fileName?.trim()?.ifEmpty { null },
            contentType = contentType,
            byteSize = bytes.size.toLong(),
            readAt = Instant.now().toString(),
            model = result.model.model,
            configVersion = result.model.configVersion,
            rawResponse = result.rawResponse,
            fields = ExtractionSchema.toStoredForm(result.fields),
        )
        storage.put(metaKey(intakeId), objectMapper.writeValueAsBytes(meta), "application/json")

        return stages.suggest(meta, result.fields)
    }

    /**
     * Files a read document to a confirmed person and code. One transaction: the document is
     * created complete, the stored reading is recorded on it, and it is accepted with the values
     * the human confirmed — see [OfficeIntakeStages.file].
     */
    fun file(
        intakeId: UUID,
        personId: Long,
        requirementId: Long,
        status: HoldingStatus,
        expiry: LocalDate?,
        issueDate: LocalDate?,
        note: String?,
    ): EvidenceDocument {
        policy.require(Role.DATA_STEWARD, Role.SYSTEM_ADMINISTRATOR)
        val bytes = storage.get(originalKey(intakeId))
            ?: throw EntityNotFoundException("No intake $intakeId — read the file again")
        val meta = storage.get(metaKey(intakeId))?.let { objectMapper.readValue(it, IntakeMeta::class.java) }
            ?: throw EntityNotFoundException("No reading for intake $intakeId — read the file again")
        return stages.file(meta, bytes, personId, requirementId, status, expiry, issueDate, note)
    }

    private fun originalKey(intakeId: UUID) = "intake/$intakeId/original"
    private fun metaKey(intakeId: UUID) = "intake/$intakeId/reading.json"
}

/** What the read step stored beside the bytes: the reading, verbatim, and how it was made. */
data class IntakeMeta(
    val intakeId: String,
    val fileName: String?,
    val contentType: String,
    val byteSize: Long,
    val readAt: String,
    val model: String,
    val configVersion: String,
    val rawResponse: String,
    val fields: Map<String, Any?>,
)

data class PersonSuggestion(val person: Person, val score: Int, val why: String)

data class IntakeReading(
    val meta: IntakeMeta,
    val fields: Map<String, ExtractedField>,
    val people: List<PersonSuggestion>,
    val requirements: List<Requirement>,
)

/**
 * The transactional halves of the intake, a separate bean for the reason [ExtractionStages] is:
 * a `@Transactional` method called from its own class runs with no transaction at all.
 */
@ApplicationScoped
class OfficeIntakeStages(
    private val documents: EvidenceDocumentRepository,
    private val people: PersonRepository,
    private val requirements: RequirementRepository,
    private val review: EvidenceReviewService,
    private val storage: ObjectStorage,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
) {

    /** Suggestions for the screen: crew whose name the holder's name looks like, codes the title looks like. */
    @Transactional
    fun suggest(meta: IntakeMeta, fields: Map<String, ExtractedField>): IntakeReading {
        val holder = fields[ExtractionSchema.HOLDER_NAME]?.value
        val title = fields[ExtractionSchema.QUALIFICATION_TITLE]?.value
        val candidates = people.allActiveUnscoped()

        val byHolder = holder?.let { suggestPeople(it, candidates, "the name on the certificate") }.orEmpty()
        // The filename is a second, weaker witness — the portal names files "SURNAME_ First - CODE …".
        val byFile = meta.fileName?.substringBefore(" - ")?.replace('_', ',')
            ?.let { suggestPeople(it, candidates, "the filename") }.orEmpty()
        val peopleSuggested = (byHolder + byFile)
            .groupBy { it.person.requiredId }
            .map { (_, hits) -> hits.maxBy { it.score } }
            .sortedByDescending { it.score }
        // The DTO is mapped after this transaction closes and reads the position and partnership,
        // both lazy — the same trap the review fetch set exists for. Initialised here, for the few
        // rows suggested, rather than fetch-joined for the whole roster.
        peopleSuggested.forEach {
            Hibernate.initialize(it.person.position)
            Hibernate.initialize(it.person.partnership)
        }

        val codeInText = listOfNotNull(meta.fileName, title, fields[ExtractionSchema.DOCUMENT_TYPE]?.value)
            .firstNotNullOfOrNull { CODE.find(it)?.value }
            ?.let { requirements.byCode(it) }
        val byTitle = title?.takeIf { it.isNotBlank() }?.let { requirements.matching(it) }.orEmpty()
        val requirementsSuggested = (listOfNotNull(codeInText) + byTitle).distinctBy { it.requiredId }

        audit.record(
            entityType = "EvidenceIntake",
            event = "evidence.office_read",
            businessKey = meta.intakeId,
            after = mapOf(
                "fileName" to meta.fileName,
                "model" to "${meta.model}/${meta.configVersion}",
                "holderName" to holder,
                "title" to title,
                "peopleSuggested" to peopleSuggested.map { it.person.sam },
                "requirementsSuggested" to requirementsSuggested.map { it.code },
            ),
        )
        return IntakeReading(meta, fields, peopleSuggested, requirementsSuggested)
    }

    /**
     * Surname match is the gate, given name the tie-break. "Brenton Evans", "EVANS, Brenton" and
     * "Mr B. Evans" all reach the same row; two Evanses stay two suggestions for a human to pick from.
     */
    private fun suggestPeople(text: String, candidates: List<Person>, why: String): List<PersonSuggestion> {
        val tokens = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length > 1 && it !in HONORIFICS }
        if (tokens.isEmpty()) return emptyList()
        return candidates.mapNotNull { person ->
            val surname = person.name.substringBefore(',').trim().lowercase()
            val given = person.name.substringAfter(',', "").trim().substringBefore(' ').lowercase()
            if (surname.isEmpty() || surname !in tokens) return@mapNotNull null
            val givenHit = given.isNotEmpty() && tokens.any { it == given || (it.length >= 3 && given.startsWith(it)) }
            PersonSuggestion(person, if (givenHit) 2 else 1, "Surname${if (givenHit) " and given name" else ""} match $why")
        }
    }

    @Transactional
    fun file(
        meta: IntakeMeta,
        bytes: ByteArray,
        personId: Long,
        requirementId: Long,
        status: HoldingStatus,
        expiry: LocalDate?,
        issueDate: LocalDate?,
        note: String?,
    ): EvidenceDocument {
        val person = people.findById(personId) ?: throw EntityNotFoundException("No person $personId")
        policy.assertCanSeePerson(personId, person.partnership.requiredId)
        val requirement = requirements.findById(requirementId)
            ?: throw EntityNotFoundException("No requirement $requirementId")

        val publicId = UUID.randomUUID()
        val key = "evidence/$publicId/original"
        storage.put(key, bytes, meta.contentType)

        // Filed under the office's naming rule — "SURNAME, Given - Certificate - issue date.pdf" —
        // whatever the scan was called when it arrived; the arrival name goes in the audit event.
        val storedName = storedFileName(person.name, requirement.title, issueDate, meta.contentType)

        val actor = policy.actor()
        val document = EvidenceDocument().apply {
            this.publicId = publicId
            this.person = person
            source = EvidenceSource.ADMIN_UPLOAD
            contentType = meta.contentType
            byteSize = bytes.size.toLong()
            fileName = storedName
            objectKey = key
            uploadOffset = bytes.size.toLong()
            uploadComplete = true
            declaredSize = bytes.size.toLong()
            submittedBy = actor.label
            requirementHint = requirement
            // The reading as the model made it, recorded before the human's values land, so the
            // accept step below can tell a correction from a confirmation.
            extraction = meta.fields.toMutableMap()
            extractionRaw = meta.rawResponse
            extractionModel = "${meta.model}/${meta.configVersion}"
            matchedRequirement = requirement
            verificationStatus = VerificationStatus.PENDING_REVIEW
            reviewReason = "Office upload awaiting the uploader's confirmation"
            stampCreated(actor.label)
        }
        documents.persist(document)
        documents.flush()

        audit.record(
            entityType = "EvidenceDocument",
            event = "evidence.submitted",
            entityId = document.id,
            businessKey = publicId.toString(),
            after = mapOf(
                "personSam" to person.sam,
                "source" to EvidenceSource.ADMIN_UPLOAD.wire,
                "contentType" to meta.contentType,
                "fileName" to storedName,
                "uploadedAs" to meta.fileName,
                "intakeId" to meta.intakeId,
            ),
        )

        return review.accept(
            publicId = publicId,
            requirementId = requirementId,
            status = status,
            expiry = expiry,
            issueDate = issueDate,
            note = note ?: "Filed from an office upload, confirmed by ${actor.label}",
        )
    }

    private companion object {
        val CODE = Regex("\\b(QL|VS|PS|MS|CS|HR|PT|VI|PI)-\\d{2}\\b")
        private val UNSAFE = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
        private val SPACES = Regex("\\s+")

        /** `EVANS, Brenton - GMDSS General Operator - 2026-05-27.pdf`; anything a file system refuses becomes a space. */
        fun storedFileName(personName: String, certificate: String, issueDate: LocalDate?, contentType: String): String {
            val clean = { s: String -> s.replace(UNSAFE, " ").replace(SPACES, " ").trim() }
            val extension = when (contentType) {
                "application/pdf" -> "pdf"
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                else -> "bin"
            }
            return "${clean(personName)} - ${clean(certificate)} - ${issueDate?.toString() ?: "undated"}.$extension"
        }
        val HONORIFICS = setOf("mr", "mrs", "ms", "miss", "dr", "capt", "captain", "master", "chief", "officer", "engineer")
    }
}
