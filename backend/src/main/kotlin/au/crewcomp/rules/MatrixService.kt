package au.crewcomp.rules

import au.crewcomp.notify.NotificationKind
import au.crewcomp.notify.NotificationService
import au.crewcomp.platform.audit.AuditWriter
import au.crewcomp.platform.persistence.EntityNotFoundException
import au.crewcomp.platform.security.AccessPolicy
import au.crewcomp.platform.security.Role
import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.reference.CrewPositionRepository
import au.crewcomp.reference.PartnershipRepository
import au.crewcomp.reference.RequirementRepository
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.time.LocalDate

/**
 * ADM-3 / §5.5 — the matrix versioning write path: draft → edit → diff → publish.
 *
 * Everything about a matrix version's lifecycle is here, and the shape of it follows one rule:
 * **a published version is immutable.** That is not a convenience, it is what makes an evaluation
 * reproducible — a swing evaluated last March must still evaluate the same way today, because the
 * version it was evaluated against cannot have changed underneath it (§5.5, AUD-1). So:
 *
 *  * Editing is refused on anything but a draft; there is no "publish a correction in place".
 *  * A draft is created as a **deep copy** of a source version rather than as a reference to it.
 *    Copying a few thousand rows costs nothing at this scale and buys the property that editing a
 *    draft cannot reach into the version it came from.
 *  * Publication supersedes the current published version under an advisory lock, so two
 *    coordinators publishing at the same instant cannot leave two versions published.
 *
 * Publication is restricted to the Compliance Lead (§5.5, pending Q3/O-1); drafting and editing
 * are open to the Compliance Lead too rather than to coordinators, because a draft cell edit is a
 * change to the vocabulary of every compliance answer the system gives.
 *
 * The diff itself is not here: it is [au.crewcomp.engine.MatrixDiff] over two snapshots, reached
 * through `ComplianceService.matrixDiff`. §5.5 is engine semantics, and the engine's copy of them
 * is the one with the test suite.
 */
@ApplicationScoped
class MatrixService(
    private val versions: MatrixVersionRepository,
    private val requirementRules: RequirementRuleRepository,
    private val conditionalRules: ConditionalRuleRepository,
    private val quotaRules: QuotaRuleRepository,
    private val partnerships: PartnershipRepository,
    private val positions: CrewPositionRepository,
    private val requirements: RequirementRepository,
    private val policy: AccessPolicy,
    private val audit: AuditWriter,
    private val notifications: NotificationService,
    private val clock: BusinessClock,
    private val em: EntityManager,
) {

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    /**
     * Every version with its rule counts — ADM-3's version list.
     *
     * Readable by every back-office role, not only the Compliance Lead: a coordinator looking at a
     * gap needs to know which matrix version produced it, and a Vessel Master's swing view is
     * evaluated against one. Editing is what is restricted.
     */
    @Transactional
    fun list(): List<MatrixVersionSummary> {
        policy.require(
            Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER, Role.COMPLIANCE_LEAD,
            Role.DATA_STEWARD, Role.VESSEL_MASTER, Role.SYSTEM_ADMINISTRATOR,
        )
        val ruleCounts = requirementRules.countByVersion()
        val conditionalCounts = conditionalRules.countByVersion()
        val quotaCounts = quotaRules.countByVersion()

        return versions.allOrdered().map { version ->
            val id = version.requiredId
            MatrixVersionSummary(
                version = version,
                requirementRuleCount = ruleCounts[id] ?: 0,
                conditionalRuleCount = conditionalCounts[id] ?: 0,
                quotaRuleCount = quotaCounts[id] ?: 0,
            )
        }
    }

    /**
     * One version's rules, fetch-joined for the cell editor.
     *
     * The grid the SPA renders is positions × requirements, which it already has from the
     * catalogue and position endpoints; what it cannot get elsewhere is the level in each cell and
     * which partnership overrides exist. That is exactly what this returns.
     */
    @Transactional
    fun detail(matrixVersionId: Long): MatrixVersionDetail {
        policy.require(
            Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER, Role.COMPLIANCE_LEAD,
            Role.DATA_STEWARD, Role.VESSEL_MASTER, Role.SYSTEM_ADMINISTRATOR,
        )
        val version = versionById(matrixVersionId)
        return MatrixVersionDetail(
            version = version,
            rules = requirementRules.forVersion(matrixVersionId),
            conditionals = conditionalRules.forVersion(matrixVersionId),
            quotas = quotaRules.forVersion(matrixVersionId),
        )
    }

    // -----------------------------------------------------------------------
    // Drafting
    // -----------------------------------------------------------------------

    /**
     * Creates a draft, optionally as a deep copy of [copyFromVersionId].
     *
     * "Draft creation from any version" (§6 ADM-3) means *any*, superseded ones included: reviving
     * a previous matrix as the basis for the next is a normal thing to want, and the version it
     * was copied from is recorded in the audit event so the lineage survives.
     *
     * An empty draft — no source version — is permitted and is how the very first matrix is built.
     */
    @Transactional
    fun createDraft(label: String, copyFromVersionId: Long? = null, notes: String? = null): MatrixVersion {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)

        val cleanLabel = label.trim()
        require(cleanLabel.isNotEmpty()) { "A matrix version needs a label" }
        require(versions.byLabel(cleanLabel) == null) {
            "There is already a matrix version labelled '$cleanLabel'"
        }

        val source = copyFromVersionId?.let { versionById(it) }
        val actor = policy.actor()

        val draft = MatrixVersion().apply {
            this.label = cleanLabel
            status = MatrixStatus.DRAFT
            this.notes = notes?.ifBlank { null }
            tierFootnote = source?.tierFootnote
            stampCreated(actor.label)
        }
        versions.persist(draft)

        if (source != null) {
            source.tierPolicies.forEach { policyRow ->
                draft.tierPolicies.add(
                    MatrixTierPolicy().apply {
                        this.matrixVersion = draft
                        this.vesselClass = policyRow.vesselClass
                        this.acceptedTier = policyRow.acceptedTier
                    },
                )
            }
            copyRules(source.requiredId, draft, actor.label)
        }

        audit.record(
            entityType = "MatrixVersion",
            event = "matrix_version.drafted",
            entityId = draft.id,
            businessKey = cleanLabel,
            after = mapOf(
                "label" to cleanLabel,
                "copiedFromVersionId" to copyFromVersionId,
                "copiedFromLabel" to source?.label,
                "notes" to draft.notes,
            ),
        )
        return draft
    }

    /** Renames a draft or edits its notes and tier footnote. Drafts only. */
    @Transactional
    fun updateDraft(
        matrixVersionId: Long,
        label: String,
        notes: String?,
        tierFootnote: String?,
    ): MatrixVersion {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)

        val version = requireDraft(matrixVersionId)
        val cleanLabel = label.trim()
        require(cleanLabel.isNotEmpty()) { "A matrix version needs a label" }
        val clash = versions.byLabel(cleanLabel)
        require(clash == null || clash.requiredId == matrixVersionId) {
            "There is already a matrix version labelled '$cleanLabel'"
        }

        val before = snapshot(version)
        version.label = cleanLabel
        version.notes = notes?.ifBlank { null }
        version.tierFootnote = tierFootnote?.ifBlank { null }
        version.stampUpdated(policy.actor().label)

        audit.record(
            entityType = "MatrixVersion",
            event = "matrix_version.updated",
            entityId = version.id,
            businessKey = version.label,
            before = before,
            after = snapshot(version),
        )
        return version
    }

    /**
     * Discards a draft and everything in it.
     *
     * Only a draft, and that is the whole safety argument: a draft has never produced a compliance
     * answer, so nothing references it and deleting it destroys no evidence. Children are deleted
     * explicitly because a JPQL bulk delete does not fire the database's cascade.
     */
    @Transactional
    fun discardDraft(matrixVersionId: Long) {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)

        val version = requireDraft(matrixVersionId)
        val label = version.label

        conditionalRules.deleteForVersion(matrixVersionId)
        quotaRules.deleteForVersion(matrixVersionId)
        requirementRules.deleteForVersion(matrixVersionId)
        versions.delete(version)

        audit.record(
            entityType = "MatrixVersion",
            event = "matrix_version.discarded",
            entityId = matrixVersionId,
            businessKey = label,
            before = mapOf("label" to label),
        )
    }

    // -----------------------------------------------------------------------
    // Cell editing
    // -----------------------------------------------------------------------

    /**
     * Sets one cell of a draft: the level for (partnership, position, requirement).
     *
     * [partnershipId] `null` addresses the base (`'*'`) rule that applies to every partnership;
     * a partnership id addresses that partnership's override. The two are genuinely different
     * edits and the distinction is the whole of "partnership overrides" in §6:
     *
     *  * setting a base level changes the requirement for everyone who has no override;
     *  * setting a partnership override to `''` says "this partnership does *not* require it",
     *    which is a positive statement and not the same as having no override at all.
     *
     * A blank level on the **base** rule is refused: an absent base rule already means "not
     * required", so a blank base row is a row that says nothing, and one that would then show up
     * in a §5.5 diff as a change when nothing changed. Use [clearCell] instead.
     */
    @Transactional
    fun setCell(
        matrixVersionId: Long,
        partnershipId: Long?,
        positionId: Long,
        requirementId: Long,
        level: String,
    ): RequirementRule {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)

        val version = requireDraft(matrixVersionId)
        val cleanLevel = level.trim()
        require(partnershipId != null || cleanLevel.isNotEmpty()) {
            "A blank level on the base rule says nothing — clear the cell instead of blanking it"
        }
        require(cleanLevel.length <= MAX_LEVEL_LENGTH) {
            "'$cleanLevel' is too long for a level; a level is 'M', 'R', a footnote label, or empty"
        }

        val position = positions.findById(positionId)
            ?: throw EntityNotFoundException("No crew position $positionId")
        val requirement = requirements.findById(requirementId)
            ?: throw EntityNotFoundException("No requirement $requirementId")
        val partnership = partnershipId?.let {
            partnerships.findById(it) ?: throw EntityNotFoundException("No partnership $it")
        }

        val actor = policy.actor()
        val existing = requirementRules.find(matrixVersionId, partnershipId, positionId, requirementId)
        val before = existing?.let { mapOf("level" to it.levelValue) }

        val rule = existing ?: RequirementRule().apply {
            this.matrixVersion = version
            this.partnership = partnership
            this.position = position
            this.requirement = requirement
            stampCreated(actor.label)
        }
        rule.levelValue = cleanLevel
        rule.stampUpdated(actor.label)
        if (existing == null) requirementRules.persist(rule)

        audit.record(
            entityType = "RequirementRule",
            event = if (existing == null) "matrix_cell.set" else "matrix_cell.changed",
            entityId = rule.id,
            businessKey = cellKey(version.label, partnership?.abbrev, position.name, requirement.code),
            before = before,
            after = mapOf("level" to cleanLevel),
        )
        return rule
    }

    /**
     * Removes a cell's rule from a draft.
     *
     * On a base cell that means "no longer required of this position". On a partnership override it
     * means "this partnership follows the base rule again", which is why clearing an override is
     * not the same as setting it blank.
     */
    @Transactional
    fun clearCell(matrixVersionId: Long, partnershipId: Long?, positionId: Long, requirementId: Long) {
        policy.require(Role.COMPLIANCE_LEAD, Role.SYSTEM_ADMINISTRATOR)

        val version = requireDraft(matrixVersionId)
        val rule = requirementRules.find(matrixVersionId, partnershipId, positionId, requirementId)
            ?: throw EntityNotFoundException(
                "No rule for position $positionId / requirement $requirementId in version $matrixVersionId",
            )

        val key = cellKey(
            version.label,
            rule.partnership?.abbrev,
            rule.position.name,
            rule.requirement.code,
        )
        val before = mapOf("level" to rule.levelValue)
        requirementRules.delete(rule)

        audit.record(
            entityType = "RequirementRule",
            event = "matrix_cell.cleared",
            entityId = null,
            businessKey = key,
            before = before,
        )
    }

    // -----------------------------------------------------------------------
    // Publication
    // -----------------------------------------------------------------------

    /**
     * Publishes a draft, superseding the currently published version.
     *
     * Two things guard this. The advisory lock serialises publication so that two simultaneous
     * publishes cannot both supersede the same predecessor and leave two rows published — the
     * schema's index is ordered defensively, but "exactly one current published version" is a
     * service-layer invariant and this is where it is kept. And [effectiveFrom] defaults to today
     * rather than to the draft's creation date, because a matrix takes effect when someone decides
     * it does.
     *
     * An empty draft is refused. Publishing nothing would silently make every cell `na` across
     * every swing, which is the single most destructive thing this endpoint could do.
     */
    @Transactional
    fun publish(matrixVersionId: Long, effectiveFrom: LocalDate? = null): PublicationResult {
        policy.require(Role.COMPLIANCE_LEAD)

        // A third advisory-lock use gets its own namespace: the locks are a flat global keyspace,
        // so borrowing the audit writer's or the register's constant would serialise this against
        // unrelated work for no reason.
        em.createNativeQuery("select pg_advisory_xact_lock(:key)")
            .setParameter("key", PUBLISH_ADVISORY_LOCK_KEY)
            .singleResult

        val draft = requireDraft(matrixVersionId)
        val ruleCount = requirementRules.count("matrixVersion.id", matrixVersionId)
        require(ruleCount > 0) {
            "Draft '${draft.label}' has no requirement rules — publishing it would make every " +
                "cell in every swing 'na'"
        }

        val date = effectiveFrom ?: clock.today()
        val superseded = versions.currentPublished()
        require(superseded?.id != draft.id) { "'${draft.label}' is already the published version" }
        if (superseded?.effectiveFrom != null) {
            require(!date.isBefore(superseded.effectiveFrom)) {
                "Effective date $date precedes the currently published '${superseded.label}' " +
                    "(effective ${superseded.effectiveFrom}); the published version is whichever " +
                    "has the latest effective date, so this would publish into the past"
            }
        }

        val actor = policy.actor()
        val now = clock.realToday().atStartOfDay(clock.zone).toInstant()

        superseded?.let {
            it.status = MatrixStatus.SUPERSEDED
            it.stampUpdated(actor.label)
            audit.record(
                entityType = "MatrixVersion",
                event = "matrix_version.superseded",
                entityId = it.id,
                businessKey = it.label,
                before = mapOf("status" to MatrixStatus.PUBLISHED.wire),
                after = mapOf("status" to MatrixStatus.SUPERSEDED.wire, "supersededBy" to draft.label),
            )
        }

        val before = snapshot(draft)
        draft.status = MatrixStatus.PUBLISHED
        draft.effectiveFrom = date
        draft.publishedBy = actor.label
        draft.publishedAt = now
        draft.stampUpdated(actor.label)

        audit.record(
            entityType = "MatrixVersion",
            event = "matrix_version.published",
            entityId = draft.id,
            businessKey = draft.label,
            before = before,
            after = snapshot(draft) + mapOf(
                "requirementRuleCount" to ruleCount,
                "supersededVersionId" to superseded?.id,
                "supersededLabel" to superseded?.label,
            ),
        )

        // §9 lists matrix publication as a notifying domain event, and it is the most consequential
        // one in the system: it changes the answer to every compliance question at once, so everyone
        // who plans against those answers is told. Empty until back-office accounts exist (ADR 0003).
        notifications.raiseForRoles(
            roles = listOf(
                Role.CREW_COORDINATOR, Role.WORKFLOW_MANAGER, Role.DATA_STEWARD, Role.COMPLIANCE_LEAD,
            ),
            kind = NotificationKind.MATRIX_PUBLISHED,
            title = "A new requirements matrix has been published",
            body = "'${draft.label}' is effective from $date" +
                (superseded?.let { ", superseding '${it.label}'." } ?: ".") +
                " Compliance answers for every swing now come from it.",
            deepLink = "/matrix",
        )

        return PublicationResult(published = draft, superseded = superseded)
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    /**
     * Deep-copies one version's rules onto [draft].
     *
     * Entity-by-entity rather than as an `insert ... select`: the row counts are in the low
     * thousands, the copy runs a handful of times a year, and a JPQL copy would have to name every
     * column — which is exactly the kind of statement that silently stops copying a column added
     * later.
     */
    private fun copyRules(sourceVersionId: Long, draft: MatrixVersion, actorLabel: String) {
        requirementRules.forVersion(sourceVersionId).forEach { source ->
            requirementRules.persist(
                RequirementRule().apply {
                    this.matrixVersion = draft
                    this.partnership = source.partnership
                    this.position = source.position
                    this.requirement = source.requirement
                    this.levelValue = source.levelValue
                    stampCreated(actorLabel)
                },
            )
        }

        conditionalRules.forVersion(sourceVersionId).forEach { source ->
            val copy = ConditionalRule().apply {
                this.matrixVersion = draft
                this.kindValue = source.kindValue
                this.position = source.position
                this.requirement = source.requirement
                this.label = source.label
                stampCreated(actorLabel)
            }
            source.members.forEach { member ->
                copy.members.add(
                    ConditionalRuleMember().apply {
                        this.conditionalRule = copy
                        this.requirement = member.requirement
                        this.roleValue = member.roleValue
                        this.ordinal = member.ordinal
                    },
                )
            }
            conditionalRules.persist(copy)
        }

        quotaRules.forVersion(sourceVersionId).forEach { source ->
            quotaRules.persist(
                QuotaRule().apply {
                    this.matrixVersion = draft
                    this.footnote = source.footnote
                    this.requirement = source.requirement
                    this.minCount = source.minCount
                    this.scopeValue = source.scopeValue
                    // `this.` is load-bearing: MatrixService has its own `positions` (the position
                    // repository), and a bare name inside `apply` is one resolution rule away from
                    // meaning the wrong thing.
                    this.positions = source.positions.toMutableSet()
                    stampCreated(actorLabel)
                },
            )
        }
    }

    /**
     * Named `versionById` rather than `require`: a private member `require(Long)` sits beside
     * `kotlin.require(Boolean)` and reads as a coin toss at every call site — a trap this codebase
     * has already paid for once.
     */
    private fun versionById(matrixVersionId: Long): MatrixVersion =
        versions.findById(matrixVersionId)
            ?: throw EntityNotFoundException("No matrix version $matrixVersionId")

    /**
     * Loads a version and refuses anything but a draft.
     *
     * `IllegalStateException` rather than a validation failure, and the API maps it to 409: the
     * request is well formed and the caller is permitted to make it, the *version* is in the wrong
     * state. "You cannot edit a published matrix" is a fact about the world, not a bad request.
     */
    private fun requireDraft(matrixVersionId: Long): MatrixVersion {
        val version = versionById(matrixVersionId)
        if (!version.isEditable) {
            throw MatrixNotEditableException(version.label, version.status)
        }
        return version
    }

    private fun cellKey(versionLabel: String, partnershipAbbrev: String?, position: String, code: String): String =
        "$versionLabel/${partnershipAbbrev ?: "*"}/$position/$code"

    private fun snapshot(version: MatrixVersion): Map<String, Any?> = mapOf(
        "label" to version.label,
        "status" to version.statusValue,
        "effectiveFrom" to version.effectiveFrom?.toString(),
        "tierFootnote" to version.tierFootnote,
        "notes" to version.notes,
        "publishedBy" to version.publishedBy,
    )

    companion object {
        /**
         * The publication lock's own namespace. Postgres advisory locks are a flat `bigint`
         * keyspace, so this must stay distinct from [AuditWriter.ADVISORY_LOCK_KEY] and from the
         * register's per-prefix keys — two unrelated uses of one number serialise against each
         * other for no reason.
         */
        const val PUBLISH_ADVISORY_LOCK_KEY: Long = 0x4352_4557_4d54_5258L

        /** Footnote labels are short by construction (`M`, `R`, `M9`, `Mˣ`); this catches paste accidents. */
        private const val MAX_LEVEL_LENGTH = 8
    }
}

/** A version plus the counts ADM-3's list column shows. */
data class MatrixVersionSummary(
    val version: MatrixVersion,
    val requirementRuleCount: Long,
    val conditionalRuleCount: Long,
    val quotaRuleCount: Long,
)

/** A version with its rules, fetch-joined for the cell editor. */
data class MatrixVersionDetail(
    val version: MatrixVersion,
    val rules: List<RequirementRule>,
    val conditionals: List<ConditionalRule>,
    val quotas: List<QuotaRule>,
)

/** What publication changed: the new current version, and the one it displaced (if any). */
data class PublicationResult(val published: MatrixVersion, val superseded: MatrixVersion?)

/**
 * An edit was attempted on a version that is not a draft.
 *
 * 409, not 400: published versions are immutable by design (§5.5) and the caller is asking for
 * something the state of the world forbids rather than something malformed. The remedy is
 * "draft from this version and edit that", which the message says.
 */
class MatrixNotEditableException(val label: String, val status: MatrixStatus) :
    RuntimeException(
        "Matrix version '$label' is ${status.wire} and cannot be edited — a published version is " +
            "immutable so that past evaluations stay reproducible (§5.5). Create a draft from it instead.",
    )
