package au.crewcomp.evidence

import au.crewcomp.engine.HoldingStatus
import au.crewcomp.notify.NotificationKind
import au.crewcomp.notify.NotificationService
import au.crewcomp.people.HoldingService
import au.crewcomp.people.QualificationHoldingRepository
import au.crewcomp.people.UserAccountRepository
import au.crewcomp.platform.adapters.DocumentPart
import au.crewcomp.platform.adapters.ExtractedField
import au.crewcomp.platform.adapters.ExtractionRequest
import au.crewcomp.platform.adapters.LlmClient
import au.crewcomp.platform.adapters.ObjectStorage
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.config.ConfigService
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.Actor
import au.crewcomp.platform.security.ActorContext
import au.crewcomp.platform.security.ActorKind
import au.crewcomp.platform.security.Role
import au.crewcomp.reference.Requirement
import au.crewcomp.reference.RequirementRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * §8 stages 2, 3 and 4 — extract, match, decide.
 *
 * ### The rule the whole module is arranged around
 *
 * **LLM-1: the model never mutates the system of record.** Extraction writes to
 * `evidence_document` and nowhere else. The one path from here to a holding is [HoldingService],
 * reached only when every gate in [ExtractionStages.decide] is open, and it is the same service the
 * admin API and a human reviewer call. There is no second door.
 *
 * ### Why the transactional work lives in [ExtractionStages] and not here
 *
 * The model call is the slow part (LLM-5 budgets seconds to minutes) and must not hold a database
 * connection for its duration. So this class reads what it needs, calls the model with no
 * transaction open, and hands the result back to be recorded — three units of work rather than one.
 *
 * They are a **separate bean** rather than `@Transactional` methods on this class because a
 * `@Transactional` method invoked from inside the same class is self-invoked: the CDI interceptor
 * never runs and the method executes with no transaction at all. This codebase has paid for that
 * once already, in test helpers.
 *
 * ### Nothing here decides on its own authority
 *
 * The auto-accept gate is off by default (LLM-2) and cannot be opened by accident: the threshold
 * has no default value, refuses to be set to zero, and the unconfigured extractor reports zero
 * confidence on every field. Three independent facts have to change before a document is accepted
 * without a human — and the audit event for one records the model, its config version, and
 * `ai_automatic` as the actor kind (AIA-3).
 */
@ApplicationScoped
class EvidencePipeline(
    private val stages: ExtractionStages,
    private val storage: ObjectStorage,
    private val llm: LlmClient,
) {
    private val log = Logger.getLogger(EvidencePipeline::class.java)

    /**
     * Runs the pipeline over the documents waiting for extraction and returns a one-line summary
     * for the job log and ADM-10's health view.
     *
     * Bounded by [limit] so a backlog is worked through over several runs rather than in one long
     * stretch a restart would abandon halfway.
     */
    fun sweep(limit: Int = DEFAULT_SWEEP_LIMIT): String {
        val pending = stages.pendingIds(limit)
        if (pending.isEmpty()) return "No documents awaiting extraction"

        var accepted = 0
        var review = 0
        var failed = 0
        pending.forEach { publicId ->
            try {
                if (process(publicId) == VerificationStatus.AUTO_ACCEPTED) accepted++ else review++
            } catch (e: Exception) {
                // One bad document must not stop the sweep. It stays in `pending_extraction`, so
                // the next run retries it and ADM-9 can still see it in the meantime.
                failed++
                log.errorf(e, "Extraction failed for evidence document %s", publicId)
            }
        }
        return "Extracted ${pending.size}: $accepted auto-accepted, $review to review, $failed failed"
    }

    /**
     * Runs stages 2–4 for one document and returns the status it ended in.
     *
     * Idempotent by status: a document already extracted, reviewed or rejected is left exactly as
     * it is. Re-running the pipeline is therefore always safe, which is what lets the sweep be a
     * plain periodic scan with no queue behind it.
     */
    fun process(publicId: UUID): VerificationStatus {
        val claim = stages.claim(publicId) ?: return stages.statusOf(publicId)

        // Outside a transaction, deliberately — see the class comment.
        val bytes = storage.get(claim.objectKey)
            ?: throw IllegalStateException("Evidence object ${claim.objectKey} is missing from storage")
        val result = llm.extract(
            ExtractionRequest(
                documents = listOf(DocumentPart(claim.contentType, bytes)),
                outputSchema = ExtractionSchema.OUTPUT_SCHEMA,
                // A constant. Nothing read out of a document reaches the instruction (LLM-3).
                instruction = ExtractionSchema.INSTRUCTION,
            ),
        )

        return stages.record(
            publicId = publicId,
            fields = result.fields,
            rawResponse = result.rawResponse,
            model = result.model.model,
            configVersion = result.model.configVersion,
        )
    }

    companion object {
        /**
         * How many documents one sweep works through. Small on purpose: the sweep runs often, and a
         * bounded run is one a restart cannot leave half-done in a way that matters.
         */
        const val DEFAULT_SWEEP_LIMIT = 20
    }
}

/**
 * The transactional halves of the pipeline: read the document, then record what the model said.
 *
 * Split out of [EvidencePipeline] so the `@Transactional` boundaries are real ones — see that
 * class's comment for why calling these as private methods would silently run them without a
 * transaction.
 */
@ApplicationScoped
class ExtractionStages(
    private val documents: EvidenceDocumentRepository,
    private val requirements: RequirementRepository,
    private val holdings: QualificationHoldingRepository,
    private val holdingService: HoldingService,
    private val accounts: UserAccountRepository,
    private val notifications: NotificationService,
    private val config: ConfigService,
    private val audit: AuditWriter,
    private val actorContext: ActorContext,
) {

    @Transactional
    fun pendingIds(limit: Int): List<UUID> =
        documents.awaitingExtractionUnscoped(limit).map { it.publicId }

    @Transactional
    fun statusOf(publicId: UUID): VerificationStatus = document(publicId).verificationStatus

    /**
     * Reads what the model call needs, or returns null when this document is not ours to process.
     *
     * A document with no bytes yet is skipped rather than failed: MOB-5a uploads arrive over
     * however many connectivity windows a vessel gets, and "not finished uploading" is a normal
     * state that resolves itself.
     */
    @Transactional
    fun claim(publicId: UUID): ExtractionClaim? {
        val document = document(publicId)
        if (document.verificationStatus != VerificationStatus.PENDING_EXTRACTION) return null
        if (!document.uploadComplete) return null
        val key = document.objectKey ?: return null
        return ExtractionClaim(key, document.contentType ?: "application/octet-stream")
    }

    /**
     * Stores stage 2's output, then runs stages 3 and 4, in one transaction.
     *
     * Runs as an AI-automatic actor ([aiActor]) so every audit event it produces is attributable to
     * the model and its config version rather than to whoever triggered the sweep (AIA-3).
     */
    @Transactional
    fun record(
        publicId: UUID,
        fields: Map<String, ExtractedField>,
        rawResponse: String,
        model: String,
        configVersion: String,
    ): VerificationStatus {
        val actor = aiActor(model, configVersion)
        return actorContext.runAs(actor) {
            val document = document(publicId)
            if (document.verificationStatus != VerificationStatus.PENDING_EXTRACTION) {
                return@runAs document.verificationStatus
            }

            document.extraction = ExtractionSchema.toStoredForm(fields)
            document.extractionRaw = rawResponse
            document.extractionModel = "$model/$configVersion"

            audit.record(
                entityType = "EvidenceDocument",
                event = "evidence.extracted",
                entityId = document.id,
                businessKey = publicId.toString(),
                after = mapOf(
                    "model" to model,
                    "configVersion" to configVersion,
                    "fields" to ExtractionSchema.FIELDS.associateWith { name ->
                        fields[name]?.let { mapOf("value" to it.value, "confidence" to it.confidence) }
                    },
                ),
            )

            val match = matchRequirement(document, fields)
            document.matchedRequirement = match.requirement

            val outcome = decide(document, fields, match)
            document.verificationStatus = outcome.status
            document.reviewReason = outcome.reason
            document.stampUpdated(actor.label)

            audit.record(
                entityType = "EvidenceDocument",
                event = if (outcome.status == VerificationStatus.AUTO_ACCEPTED) {
                    "evidence.auto_accepted"
                } else {
                    "evidence.sent_to_review"
                },
                entityId = document.id,
                businessKey = publicId.toString(),
                after = mapOf(
                    "matchedRequirement" to match.requirement?.code,
                    "matchNote" to match.note,
                    "status" to outcome.status.wire,
                    "reason" to outcome.reason,
                ),
            )

            notify(document, outcome.status)
            outcome.status
        }
    }

    // -----------------------------------------------------------------------
    // Stage 3 — match
    // -----------------------------------------------------------------------

    /**
     * Resolves the document to a catalogue requirement (§8 stage 3).
     *
     * The **person** is never resolved here: it is the submitter for a mobile upload and an explicit
     * selection for an admin one, and both were settled at ingest. Inferring a person from a name
     * printed on a certificate is precisely the class of guess that produced the source data's
     * reused Sam numbers.
     *
     * Precedence, and the reasoning for each step:
     *
     *  1. An extracted title matching **exactly one** catalogue entry is the strongest signal, and
     *     it may correct the crew member's hint — but a disagreement between the two is *reported*,
     *     never silently resolved, because one of them is wrong and a human should see which.
     *  2. Several matches is ambiguity, and §8 sends ambiguity to review rather than guessing. The
     *     candidates travel in the note so the reviewer picks from them.
     *  3. No match at all falls back to the hint: a person's stated intent, not an inference.
     */
    private fun matchRequirement(
        document: EvidenceDocument,
        fields: Map<String, ExtractedField>,
    ): RequirementMatch {
        val hint = document.requirementHint
        val title = fields[ExtractionSchema.QUALIFICATION_TITLE]?.value?.trim()

        if (title.isNullOrEmpty()) {
            return if (hint == null) {
                RequirementMatch(null, "Nothing was extracted to match, and the submission carried no hint")
            } else {
                RequirementMatch(hint, "Matched from the submitter's hint; nothing was extracted to check it against")
            }
        }

        val candidates = requirements.matching(title)
        return when {
            candidates.isEmpty() && hint != null ->
                RequirementMatch(hint, "'$title' matches no catalogue entry; fell back to the submitter's hint")

            candidates.isEmpty() ->
                RequirementMatch(null, "'$title' matches no catalogue entry by code, title or alias")

            candidates.size > 1 -> RequirementMatch(
                null,
                "'$title' matches ${candidates.size} catalogue entries " +
                    "(${candidates.joinToString { it.code }}) and was not guessed",
            )

            hint != null && candidates.single().requiredId != hint.requiredId -> RequirementMatch(
                null,
                "The document reads as ${candidates.single().code} but the submitter tagged it " +
                    "${hint.code}; one of the two is wrong",
            )

            else -> RequirementMatch(candidates.single(), null)
        }
    }

    // -----------------------------------------------------------------------
    // Stage 4 — decide
    // -----------------------------------------------------------------------

    /**
     * Decides between auto-acceptance and the review queue (§8 stage 4).
     *
     * Every gate produces a *reason* when it closes, and the reason is shown in ADM-9's queue. That
     * is not politeness: a queue of documents with no explanation for why each is in it cannot be
     * prioritised, and measuring where the pipeline gives up is exactly what LLM-2 needs before
     * auto-acceptance can responsibly be turned on.
     */
    private fun decide(
        document: EvidenceDocument,
        fields: Map<String, ExtractedField>,
        match: RequirementMatch,
    ): DecideOutcome {
        val threshold = config.autoAcceptThreshold()
            ?: return review(match.note ?: "Auto-acceptance is off; every extraction is reviewed (LLM-2)")

        val requirement = match.requirement
            ?: return review(match.note ?: "No requirement could be resolved")

        val belowThreshold = ExtractionSchema.CRITICAL_FIELDS.filter { name ->
            (fields[name]?.confidence ?: 0.0) < threshold
        }
        if (belowThreshold.isNotEmpty()) {
            return review("Confidence below $threshold for ${belowThreshold.joinToString()}")
        }

        if (fields[ExtractionSchema.QUALIFICATION_TITLE]?.value.isNullOrBlank()) {
            return review("No qualification title was extracted")
        }
        val expiryDate = fields[ExtractionSchema.EXPIRY_DATE]?.value?.let(::parseDate)
            ?: return review("No usable expiry date was extracted")

        // "Low-risk (e.g. a renewal extending an existing holding of the same requirement)" — §8
        // stage 4, read strictly. A first-ever grant, a different requirement, or a date that moves
        // *backwards* are all changes a human should see; the last most of all, because a
        // superseded or mis-scanned certificate is exactly what it looks like.
        val existing = holdings.find(document.person.requiredId, requirement.requiredId)
            ?: return review("No existing ${requirement.code} holding — a first grant is not a renewal")
        if (existing.status != HoldingStatus.HELD_EXPIRY) {
            return review("The existing ${requirement.code} holding is ${existing.status.wire}, not an expiring one")
        }
        val currentExpiry = existing.expiryDate
        if (currentExpiry == null || !expiryDate.isAfter(currentExpiry)) {
            return review("Extracted expiry $expiryDate does not extend the current ${currentExpiry ?: "unknown"}")
        }

        // Corroborating rather than critical: a holding is valid without an issue date but never
        // without an expiry, so a low-confidence issue date is dropped instead of blocking.
        val issueDate = fields[ExtractionSchema.ISSUE_DATE]
            ?.takeIf { it.confidence >= threshold }
            ?.value
            ?.let(::parseDate)

        document.linkedHolding = holdingService.setHolding(
            personId = document.person.requiredId,
            requirementId = requirement.requiredId,
            status = HoldingStatus.HELD_EXPIRY,
            expiry = expiryDate,
            issueDate = issueDate,
            note = "Auto-accepted from evidence ${document.publicId} (§8 stage 4)",
            evidenceDocumentId = document.id,
        )

        return DecideOutcome(
            VerificationStatus.AUTO_ACCEPTED,
            "Renewal of ${requirement.code} extending $currentExpiry to $expiryDate",
        )
    }

    private fun review(reason: String) = DecideOutcome(VerificationStatus.PENDING_REVIEW, reason)

    // -----------------------------------------------------------------------
    // Notification and helpers
    // -----------------------------------------------------------------------

    /**
     * Tells the submitter what happened (§8 stage 4; LLM-5's "user is notified on completion").
     *
     * A crew member with no [au.crewcomp.people.UserAccount] gets no notification, and that is not
     * an error: Person records exist before onboarding (§4.3), and an admin-uploaded document is
     * often for someone who has never signed in. Nothing is lost — the holding change is audited
     * either way.
     */
    private fun notify(document: EvidenceDocument, status: VerificationStatus) {
        if (status == VerificationStatus.PENDING_REVIEW) {
            // The other side of §9's routing: work has arrived in ADM-9's queue. Keyed on the
            // document so a re-extraction does not raise it twice.
            notifications.raiseForRoles(
                roles = listOf(Role.DATA_STEWARD),
                kind = NotificationKind.EVIDENCE_AWAITING_REVIEW,
                title = "A document is waiting for verification",
                body = "${document.person.name} (${document.person.sam}) submitted a document" +
                    (document.matchedRequirement?.let { " read as ${it.code}" } ?: "") +
                    ". ${document.reviewReason ?: ""}".trimEnd(),
                deepLink = "/evidence/${document.publicId}",
                dedupeKey = "evidence-review:${document.publicId}",
            )
            return
        }
        if (status != VerificationStatus.AUTO_ACCEPTED) return

        val account = accounts.forPerson(document.person.requiredId) ?: return
        notifications.raise(
            recipientUserAccountId = account.requiredId,
            kind = NotificationKind.EVIDENCE_VERIFIED,
            // SEC-13: the title is the only field a push payload carries, so it names nothing.
            title = "A document you submitted has been accepted",
            body = "Your ${document.matchedRequirement?.code ?: "qualification"} record has been " +
                "updated from the document you submitted.",
            deepLink = "crewcomp://certifications",
        )
    }

    private fun document(publicId: UUID): EvidenceDocument =
        documents.byPublicId(publicId) ?: throw EntityNotFoundException("No evidence document $publicId")

    private fun parseDate(raw: String): LocalDate? = try {
        LocalDate.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * AIA-3's `ai_automatic` actor.
     *
     * It holds [Role.DATA_STEWARD] rather than being an [ActorKind.SYSTEM] actor, and the difference
     * matters: `AccessPolicy.require` waves a system actor through every role check, so a system
     * actor here would mean the pipeline's writes were never authorised at all. This way the write
     * passes exactly the check a human Data Steward's write passes, while the audit event still says
     * plainly that no human was involved.
     */
    private fun aiActor(model: String, configVersion: String) = Actor(
        userAccountId = null,
        personId = null,
        roles = setOf(Role.DATA_STEWARD),
        kind = ActorKind.AI_AUTOMATIC,
        label = "evidence-pipeline ($model)",
        aiModel = model,
        aiConfigVersion = configVersion,
    )

    data class ExtractionClaim(val objectKey: String, val contentType: String)

    private data class RequirementMatch(val requirement: Requirement?, val note: String?)

    private data class DecideOutcome(val status: VerificationStatus, val reason: String?)
}
