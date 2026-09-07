package au.crewcomp.platform.dev

import au.crewcomp.engine.HoldingStatus
import au.crewcomp.engine.PersonStatus
import au.crewcomp.engine.QuotaScope
import au.crewcomp.engine.RuleLevel
import au.crewcomp.engine.Shift
import au.crewcomp.evidence.EvidenceDocument
import au.crewcomp.evidence.EvidenceSource
import au.crewcomp.evidence.VerificationStatus
import au.crewcomp.people.Assignment
import au.crewcomp.people.Person
import au.crewcomp.people.QualificationHolding
import au.crewcomp.people.UserAccount
import au.crewcomp.people.UserAccountKind
import au.crewcomp.platform.adapters.ObjectStorage
import au.crewcomp.platform.security.Role
import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.reference.CrewChange
import au.crewcomp.reference.CrewPosition
import au.crewcomp.reference.Customer
import au.crewcomp.reference.Partnership
import au.crewcomp.reference.PositionSlot
import au.crewcomp.reference.PositionTier
import au.crewcomp.reference.Requirement
import au.crewcomp.reference.ShipDocument
import au.crewcomp.reference.Vessel
import au.crewcomp.rules.ConditionalKind
import au.crewcomp.rules.ConditionalMemberRole
import au.crewcomp.rules.ConditionalRule
import au.crewcomp.rules.ConditionalRuleMember
import au.crewcomp.rules.MatrixStatus
import au.crewcomp.rules.MatrixVersion
import au.crewcomp.rules.QuotaRule
import au.crewcomp.rules.RequirementRule
import au.crewcomp.workflow.ExceptionItem
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import org.jboss.logging.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Loads a snapshot of the Coolibah crew portal — the collaborator-maintained proof-of-concept
 * whose `portal-state.json` holds a live operation's roster, qualification matrix, swing dates
 * and certificate linkage — as the development stack's third dataset, `portal`.
 *
 * **The snapshot is real, current crew data** (names, employee ids, medical and certificate
 * expiries). It lives outside this repository — `scripts/portal-snapshot.sh` maintains it under
 * `~/coolibah-portal/` — and is read from `crewcomp.dev-seed.portal-root`, which should point at
 * the snapshot directory (conventionally the `latest` symlink). Nothing here may copy it into the
 * source tree, and the loader runs only inside [DevDataSeeder]'s build-time-gated, prod-refusing
 * start-up path. `docs/handoff/coolibah-portal-dataset.md` is the map of the source and records
 * the mapping decisions this loader implements (26 Aug 2026).
 *
 * The reading is deliberately conservative and every anomaly lands on ADM-7's worklist:
 *
 *  - The catalogue comes from the portal's matrix columns; validity periods have no schema column
 *    yet, so they inform the notes and the `Y`-on-an-expiring-code cross-check only.
 *  - A dated cell is a `held_expiry` holding; `Y` is held-perpetual (flagged per code when the
 *    validity list says the code expires); `N` is not held; `?` is unknown and flagged; a blank
 *    is no row at all. Issue dates join in from the portal's certificate linkage; a certificate
 *    whose expiry disagrees with the grid is counted and flagged, never preferred.
 *  - The portal state carries **no position×requirement mandatory matrix** (that lives in the
 *    un-schema'd skills-matrix spreadsheet), so the published matrix is provisional: quota and
 *    one-of rules parsed from the portal's own machine-readable shift-allocation check, with
 *    footnote cells for the positions those rules name, and nothing else.
 *  - Only the explicitly dated swing is imported (the swing report's); the portal's other swing
 *    structures are internally inconsistent and are flagged rather than guessed at.
 *
 * Not a CDI bean, deliberately — constructed by [DevDataSeeder] inside its transaction, so the
 * build-time gate and the prod refusal live in exactly one place (same shape as
 * [ExtractedSeedLoader]).
 */
class PortalSeedLoader(
    private val em: EntityManager,
    private val clock: BusinessClock,
    private val root: Path,
    /** Where certificate bytes go. Null (the fixture ITs) means the files are not imported. */
    private val storage: ObjectStorage? = null,
    /** The mirrored `documents/` tree the index's paths are relative to. Null: not imported. */
    private val documentsRoot: Path? = null,
) {
    private val log = Logger.getLogger(PortalSeedLoader::class.java)

    private val now: Instant = Instant.now()
    private val actor = "portal-import"

    private data class Flag(val severity: String, val area: String, val entity: String, val issue: String)

    private val flags = mutableListOf<Flag>()

    private fun flag(severity: String, area: String, entity: String, issue: String) {
        flags.add(Flag(severity, area, entity, issue))
    }

    companion object {
        /**
         * The published matrix's label prefix — [DevDataSeeder] recognises the dataset by it.
         * A prefix rather than a constant label because the label carries the snapshot revision,
         * so a running stack can say which portal revision it is showing.
         */
        const val PUBLISHED_MATRIX_LABEL_PREFIX = "Coolibah portal rev "

        private val CODE = Regex("\\b(QL|VS|PS|MS|CS|HR|PT|VI|PI)-\\d{2}\\b")
        private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

        /** Portal category names → the Appendix A prefix codes the schema stores. */
        private val CATEGORY_BY_NAME = mapOf(
            "Qualification" to "QL", "Vessel Specific" to "VS", "Project Specific" to "PS",
            "Master Specific" to "MS", "Cargo System" to "CS",
            "High Risk Work Licence (HRWL)" to "HR", "High Risk Work" to "HR",
            "Permit to Work" to "PT", "Vessel Induction" to "VI", "Project Induction" to "PI",
        )

        /**
         * The portal's three overlapping rank vocabularies (matrix ranks, crew-list ranks, the
         * shift-allocation sheet's position phrases) → one canonical position each, plus a tier
         * where the rank carries a grade. "Chief Officer" is the codebase's canonical name for
         * the rank the crew lists call "Chief Mate" — the spec and both other datasets use it.
         */
        private val RANKS: Map<String, Pair<String, String?>> = mapOf(
            "Master" to ("Master" to null),
            "Chief Officer" to ("Chief Officer" to null),
            "Chief Mate" to ("Chief Officer" to null),
            "Chief Officer - Unlimited" to ("Chief Officer" to "Unlimited"),
            "Chief Officer - 100m" to ("Chief Officer" to "100m"),
            "Second Mate" to ("Second Mate" to null),
            "2nd Mate" to ("Second Mate" to null),
            "Chief Engineer" to ("Chief Engineer" to null),
            "First Engineer" to ("First Engineer" to null),
            "1st Engineer" to ("First Engineer" to null),
            "Assistant Engineer" to ("Assistant Engineer" to null),
            "Junior Engineer" to ("Assistant Engineer" to null),
            "GPH" to ("GPH" to null),
            "Cook" to ("Cook" to null),
        )
    }

    // Lookups built as loading proceeds.
    private lateinit var partnership: Partnership
    private val positionsByName = mutableMapOf<String, CrewPosition>()
    private val tiersSeen = mutableSetOf<Pair<String, String>>()
    private val requirementsByCode = mutableMapOf<String, Requirement>()
    private val personsByNormName = mutableMapOf<String, Person>()
    /** `normName::CODE` → the holding, so an imported certificate can link to what it evidences. */
    private val holdingsByKey = mutableMapOf<String, QualificationHolding>()
    private val counts = mutableMapOf<String, Int>()

    fun load() {
        val stateFile = root.resolve("portal-state.json")
        check(Files.isRegularFile(stateFile)) {
            "crewcomp.dev-seed.portal-root=$root does not hold a portal snapshot — missing " +
                "portal-state.json. Run scripts/portal-snapshot.sh and point portal-root at the " +
                "snapshot directory (conventionally ~/coolibah-portal/latest)."
        }
        val state = ObjectMapper().readTree(Files.readString(stateFile))
        val rev = state["rev"]?.asInt() ?: error("portal-state.json has no rev — not a portal snapshot.")
        val savedAt = state["savedAt"]?.asText() ?: "(unrecorded)"
        val data = state["data"] ?: error("portal-state.json has no data node.")

        loadPartnership()
        loadRequirements(data)
        val certDates = certDateIndex(data)
        loadPersons(data, certDates)
        val swing = loadSwing(data)
        loadMatrix(data, rev, savedAt)
        loadDocuments(certDates)
        seedAccounts()
        writeWorklist()

        em.flush()
        log.infof(
            "Portal dataset loaded from %s (rev %d, saved %s): %d requirements, %d people, " +
                "%d holdings, swing %s, %d quota rules, %d certificates on file, %d ship sheets, %d worklist items",
            root, rev, savedAt, requirementsByCode.size, personsByNormName.size,
            counts["holdings"] ?: 0, swing?.ccId ?: "(none)", counts["quotas"] ?: 0,
            counts["documents"] ?: 0, counts["sheets"] ?: 0, counts["worklist"] ?: 0,
        )
    }

    // ------------------------------------------------------------------ reference data

    private fun loadPartnership() {
        // The office's customers (COM-1), as names to start from — added at the client's request,
        // 7 Sep 2026. The portal's own operation — the Coolibah job for MinRes — is United Marine's.
        val customers = listOf("United Marine", "Sindry", "NORSE", "SHIP MATES").associateWith { name ->
            Customer().apply {
                this.name = name
                notes = "Added as a starting entry; contacts to follow."
                stampCreated(actor, now)
            }.also { em.persist(it) }
        }
        partnership = Partnership().apply {
            abbrev = "COO"
            name = "TSV Coolibah — MinRes Onslow"
            vesselClass = null // "engineer class 3" per the shift sheet; O-3 semantics stay open
            customer = customers.getValue("United Marine")
            // The portal's four-week pattern (ROSTER_DEFAULTS): swing 0 flies out 12 Aug 2026 with
            // crew B, crew A on the next, Wednesday to Wednesday.
            rosterAnchor = LocalDate.of(2026, 8, 12)
            rosterCycleDays = 28
            rosterAnchorCrew = "B"
            stampCreated(actor, now)
        }
        em.persist(partnership)
        em.persist(
            Vessel().apply {
                name = "TSV Coolibah"
                this.partnership = this@PortalSeedLoader.partnership
                kind = "tug"
                stampCreated(actor, now)
            },
        )
        flag(
            "INFO", "partnership", "TSV Coolibah",
            "The barge is not named anywhere in the portal — vessel pair incomplete, tug seeded alone.",
        )
    }

    private fun position(rank: String, context: String): CrewPosition {
        val (name, tier) = RANKS[rank.trim()] ?: run {
            flag(
                "DATAERR", "slot", rank.trim(),
                "Rank named by $context is not in the portal rank map — position created as-is; confirm.",
            )
            rank.trim() to null
        }
        val position = positionsByName.getOrPut(name) {
            CrewPosition().apply {
                this.name = name
                stampCreated(actor, now)
            }.also { em.persist(it) }
        }
        if (tier != null && tiersSeen.add(name to tier)) {
            em.persist(
                PositionTier().apply {
                    this.position = position
                    this.name = tier
                    stampCreated(actor, now)
                },
            )
        }
        return position
    }

    /** The rank's tier, where it carries one ("Chief Officer - 100m"). */
    private fun tierOf(rank: String): String? = RANKS[rank.trim()]?.second

    private fun loadRequirements(data: JsonNode) {
        val validity = data.path("validityPeriods").path("periods").associate {
            it.path("code").asText() to it
        }
        data.path("quals").path("cols").forEach { col ->
            val code = col[0].asText().trim()
            val period = validity[code]
            val months = period?.path("months")?.takeUnless { it.isNull }?.asInt()
            val neverExpires = period?.path("neverExpires")?.asBoolean() ?: false
            val requirement = Requirement().apply {
                this.code = code
                category = CODE.find(code)?.value?.substringBefore('-')
                    ?: CATEGORY_BY_NAME[col[2].asText().trim()]
                    ?: "QL"
                title = col[1].asText().trim()
                status = "active"
                // The validity period (V12): the months where the portal gives a number, its own
                // words where it does not ("1 or 2 years — as printed on the certificate").
                validityMonths = months
                validityText = period?.path("validFor")?.takeUnless { it.isNull || it.isMissingNode }
                    ?.asText()?.trim()?.ifEmpty { null }
                    ?: if (neverExpires) "Never expires" else null
                notes = when {
                    neverExpires -> "Never expires (portal validity list)."
                    months != null -> "Valid ${period.path("validFor").asText(months.toString() + " months")} (portal validity list)."
                    else -> null
                }
                stampCreated(actor, now)
            }
            em.persist(requirement)
            requirementsByCode[code] = requirement
        }
        val unassessed = data.path("quals").path("cols")
            .map { it[0].asText().trim() }
            .filter { it !in validity.keys }
        if (unassessed.isNotEmpty()) {
            flag(
                "REVIEW", "requirement", "${unassessed.size} codes",
                "No validity period in the portal for: ${unassessed.joinToString()} — treated as " +
                    "unassessed rather than never-expiring; dated holdings keep their dates.",
            )
        }
    }

    /** Portal validity months per code, for the Y-cell cross-check. Null = no period / never expires. */
    private fun expiryMonths(data: JsonNode): Map<String, Int> =
        data.path("validityPeriods").path("periods")
            .filter { !it.path("months").isNull && !it.path("neverExpires").asBoolean() }
            .associate { it.path("code").asText() to it.path("months").asInt() }

    // ------------------------------------------------------------------ people and holdings

    /** Join key across the portal's name spellings: case- and punctuation-insensitive. */
    private fun normName(name: String) = name.lowercase().replace(Regex("[\\s.]"), "")

    /** [fileId] is the portal's file id from the linkage's `/api/files/<id>` url — the join to the documents index. */
    private data class CertDate(val issued: LocalDate?, val expires: LocalDate?, val fileId: String?)

    private fun certDateIndex(data: JsonNode): Map<String, CertDate> {
        val index = mutableMapOf<String, CertDate>()
        data.path("certDates").path("map").fields().forEach { (key, node) ->
            val (person, code) = key.split("::").takeIf { it.size == 2 } ?: return@forEach
            index["${normName(person)}::${code.trim()}"] = CertDate(
                issued = node.path("issued").asText("").takeIf { ISO_DATE.matches(it) }?.let(LocalDate::parse),
                expires = node.path("expires").asText("").takeIf { ISO_DATE.matches(it) }?.let(LocalDate::parse),
                fileId = node.path("url").asText("").substringAfterLast('/').ifEmpty { null },
            )
        }
        return index
    }

    private fun loadPersons(data: JsonNode, certDates: Map<String, CertDate>) {
        val cols = data.path("quals").path("cols").map { it[0].asText().trim() }
        val expiring = expiryMonths(data)
        val idsSeen = mutableMapOf<String, String>()
        val perpetualButExpiring = mutableMapOf<String, Int>()
        val certificateDisagrees = mutableMapOf<String, Int>()
        var holdings = 0

        data.path("quals").path("rows").forEach { row ->
            val name = row[0].asText().trim()
            val rank = row[1].asText().trim()
            val employeeId = row[2].asText().trim()

            idsSeen.put(employeeId, name)?.let { previous ->
                // Person.sam is deliberately non-unique (§4.3): both rows import, flagged.
                flag(
                    "DATAERR", "person", employeeId,
                    "Two crew share employee id $employeeId ($previous and $name) — both imported; " +
                        "correct at source.",
                )
            }

            val person = Person().apply {
                sam = employeeId
                this.name = name
                position = position(rank, "person $employeeId")
                tier = tierOf(rank)
                partnership = this@PortalSeedLoader.partnership
                status = PersonStatus.ACTIVE
                stampCreated(actor, now)
            }
            em.persist(person)
            personsByNormName[normName(name)] = person

            row[3].forEachIndexed { i, cellNode ->
                val cell = cellNode.asText().trim()
                if (cell.isEmpty()) return@forEachIndexed
                val code = cols[i]
                val requirement = requirementsByCode.getValue(code)
                val cert = certDates["${normName(name)}::$code"]
                val holding = when {
                    ISO_DATE.matches(cell) -> {
                        val expiry = LocalDate.parse(cell)
                        if (cert?.expires != null && cert.expires != expiry) {
                            certificateDisagrees.merge(code, 1, Int::plus)
                        }
                        QualificationHolding().apply {
                            status = HoldingStatus.HELD_EXPIRY
                            expiryDate = expiry
                        }
                    }
                    cell == "Y" -> {
                        if (code in expiring) perpetualButExpiring.merge(code, 1, Int::plus)
                        QualificationHolding().apply { status = HoldingStatus.HELD_PERPETUAL }
                    }
                    cell == "N" -> QualificationHolding().apply { status = HoldingStatus.NOT_HELD }
                    cell == "?" -> {
                        flag(
                            "CHASE", "holding", "$name / $code",
                            "The portal matrix carries '?' — never established; chase the certificate.",
                        )
                        QualificationHolding().apply {
                            status = HoldingStatus.UNKNOWN
                            note = "Imported as unknown: the portal matrix carries '?'."
                        }
                    }
                    else -> {
                        flag(
                            "DATAERR", "holding", "$name / $code",
                            "Unreadable matrix cell \"$cell\" — not imported.",
                        )
                        null
                    }
                }
                holding?.apply {
                    this.person = person
                    this.requirement = requirement
                    if (status.isHeld) issueDate = cert?.issued
                    stampCreated(actor, now)
                }?.also {
                    em.persist(it)
                    holdings++
                    holdingsByKey["${normName(name)}::$code"] = it
                }
            }
        }
        counts["holdings"] = holdings

        perpetualButExpiring.toSortedMap().forEach { (code, n) ->
            flag(
                "REVIEW", "holding", code,
                "$n crew carry 'Y' for $code though the portal's validity list says it expires " +
                    "every ${expiring.getValue(code)} months — imported as held-perpetual; confirm.",
            )
        }
        certificateDisagrees.toSortedMap().forEach { (code, n) ->
            flag(
                "REVIEW", "holding", code,
                "$n crew have a certificate on the portal whose expiry disagrees with the matrix " +
                    "cell for $code — the matrix date was kept; reconcile at source.",
            )
        }
    }

    /**
     * A crew-list name ("Alice Tester") against the matrix's "SURNAME, First Middle" rows:
     * surname first, then the given name, accepting a prefix ("Chris" for "Christopher") only
     * while it stays unambiguous.
     */
    private fun matchPerson(listName: String): Person? {
        val tokens = listName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size == 1) {
            // A given name alone: the person if exactly one crew member carries it.
            val given = tokens.single().lowercase()
            return personsByNormName.values.singleOrNull {
                it.name.substringAfter(',').trim().substringBefore(' ').lowercase() == given
            }
        }
        if (tokens.size < 2) return null
        val surname = tokens.last().lowercase()
        val given = tokens.first().lowercase()
        val sameSurname = personsByNormName.values.filter {
            it.name.substringBefore(',').trim().lowercase() == surname
        }
        val byGiven = sameSurname.filter {
            val first = it.name.substringAfter(',').trim().substringBefore(' ').lowercase()
            first.startsWith(given) || given.startsWith(first)
        }
        return byGiven.singleOrNull() ?: sameSurname.singleOrNull()
    }

    // ------------------------------------------------------------------ swing and roster

    /**
     * The one explicitly dated swing — the swing report's. The portal's other swing structures
     * (`swingDates` keyed 0/-1, the on/off boards) are internally inconsistent and are flagged
     * rather than interpreted (decision: trust only what is dated).
     */
    private fun loadSwing(data: JsonNode): CrewChange? {
        val swing = data.path("swingReport").path("swing")
        val start = swing.path("start").asText("").takeIf { ISO_DATE.matches(it) }?.let(LocalDate::parse)
        val end = swing.path("end").asText("").takeIf { ISO_DATE.matches(it) }?.let(LocalDate::parse)
        if (start == null || end == null || end < start) {
            flag(
                "DATAERR", "roster", "swing report",
                "The swing report carries no coherent dated window — no crew change seeded.",
            )
            return null
        }
        val crewKey = swing.path("crew").asText("A")
        val cc = CrewChange().apply {
            ccId = "CC%02d".format(swing.path("k").asInt(1))
            partnership = this@PortalSeedLoader.partnership
            fromDate = start
            toDate = end
            // Not stated anywhere in the portal. Assumed, and flagged, rather than left to crash
            // the schema's not-null: Q17's cutoff semantics need a value to be testable at all.
            cutoffDate = start.minusDays(7)
            // The swing's place in the pattern, so the swing page can read it as one of its own.
            rotation = crewKey.takeIf { it == "A" || it == "B" }
            patternK = swing.path("k").takeUnless { it.isMissingNode || it.isNull }?.asInt()
            stampCreated(actor, now)
        }
        // Who sails with which crew, from the portal's roster list. A first name alone ("Brenton")
        // is matched when exactly one crew member carries it; anything less certain is left off.
        var rotations = 0
        data.path("people").forEach { entry ->
            val crew = entry.path("crew").asText("").takeIf { it == "A" || it == "B" } ?: return@forEach
            val person = matchPerson(entry.path("name").asText("")) ?: return@forEach
            if (person.rotation == null) {
                person.rotation = crew
                rotations++
            }
        }
        counts["rotations"] = rotations
        em.persist(cc)
        flag(
            "WARN", "roster", cc.ccId,
            "The portal states no register cutoff for the swing — assumed 7 days before " +
                "fly-out ($start); confirm (Q17).",
        )

        // Per-person watch from the swing report; day/night → Shift 1/Shift 2 is an assumption.
        val watch = mutableMapOf<String, String>()
        listOf("blocked", "clear", "watch").forEach { group ->
            data.path("swingReport").path(group).forEach {
                watch[normName(it.path("name").asText())] = it.path("watch").asText("")
            }
        }

        val entries = data.path("swingLists").path(crewKey).path("entries")
        if (entries.isEmpty) {
            flag("DATAERR", "roster", "crew $crewKey", "No crew list for the rostered crew — no assignments seeded.")
            return cc
        }
        var assignments = 0
        entries.forEachIndexed { i, entry ->
            val listName = entry.path("name").asText().trim()
            val rank = entry.path("rank").asText().trim()
            val slotRef = i + 1
            val person = matchPerson(listName)
            em.persist(
                PositionSlot().apply {
                    ref = slotRef
                    shift = when (person?.let { watch[normName(it.name)] }) {
                        "day" -> Shift.SHIFT_1
                        "night" -> Shift.SHIFT_2
                        else -> Shift.NOT_APPLICABLE
                    }
                    allowedPositions = mutableSetOf(position(rank, "crew list slot $slotRef"))
                    stampCreated(actor, now)
                },
            )
            if (person == null) {
                flag(
                    "DATAERR", "roster", listName,
                    "Crew list names someone the qualification matrix does not carry — slot " +
                        "$slotRef left open.",
                )
                return@forEachIndexed
            }
            em.persist(
                Assignment().apply {
                    this.person = person
                    crewChange = cc
                    this.partnership = this@PortalSeedLoader.partnership
                    this.slotRef = slotRef
                    fromDate = cc.fromDate
                    toDate = cc.toDate
                    stampCreated(actor, now)
                },
            )
            assignments++
        }
        counts["assignments"] = assignments

        flag(
            "INFO", "roster", "crew ${if (crewKey == "A") "B" else "A"}",
            "The other crew has no dated swing window in the portal (swingDates is keyed 0/-1 and " +
                "internally inconsistent) — its roster is not seeded.",
        )
        flag(
            "INFO", "roster", cc.ccId,
            "Day/night watches from the swing report are mapped to Shift 1/Shift 2 — confirm the " +
                "correspondence.",
        )
        return cc
    }

    // ------------------------------------------------------------------ matrix

    /**
     * The provisional matrix: quota and one-of rules parsed from the shift-allocation check the
     * portal itself derived from the allocation spreadsheet, with footnote cells (`S1`, `S2`, …)
     * for the positions each rule names. Per-position *mandatory* cells do not exist in the
     * portal state at all — that absence is the matrix's biggest caveat and is flagged loudly.
     */
    private fun loadMatrix(data: JsonNode, rev: Int, savedAt: String) {
        val matrix = MatrixVersion().apply {
            label = "$PUBLISHED_MATRIX_LABEL_PREFIX$rev"
            status = MatrixStatus.PUBLISHED
            effectiveFrom = data.path("matrixUpdated").asText("").takeIf { ISO_DATE.matches(it) }
                ?.let(LocalDate::parse) ?: clock.today()
            publishedAt = now
            publishedBy = actor
            notes = "Provisional, from the portal's shift-allocation check (snapshot rev $rev, " +
                "saved $savedAt). Coverage minimums only — the per-position mandatory matrix " +
                "lives in the skills-matrix spreadsheet, which is not yet imported."
            stampCreated(actor, now)
        }
        em.persist(matrix)
        flag(
            "REVIEW", "requirement", matrix.label,
            "The matrix is provisional: the portal state carries no position×requirement " +
                "mandatory cells (they live in the un-schema'd ATB Skills Matrix spreadsheet), so " +
                "per-person cell evaluation understates what the operation actually requires.",
        )

        val cellsSeen = mutableSetOf<Pair<String, String>>()
        fun cell(position: CrewPosition, code: String, level: String) {
            if (!cellsSeen.add(position.name to code)) return
            em.persist(
                RequirementRule().apply {
                    matrixVersion = matrix
                    this.position = position
                    requirement = requirementsByCode.getValue(code)
                    this.level = RuleLevel(level)
                    stampCreated(actor, now)
                },
            )
        }

        /** "GPH", "Chief Officer or Second Mate or …", "Engineer Class 3 (Assistant Engineer)", "Any Position". */
        fun rulePositions(text: String): List<CrewPosition>? {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.equals("Any Position", ignoreCase = true)) return null
            Regex("\\(([^)]+)\\)").find(trimmed)?.let {
                return listOf(position(it.groupValues[1], "shift-allocation rule"))
            }
            return trimmed.split(Regex("\\s+or\\s+")).map { position(it, "shift-allocation rule") }
        }

        // Day/night pairs of the same rule collapse to one shift-scoped quota; the group key is
        // the rule's codes+positions so two different rules on one code stay distinct.
        data class RuleKey(val codes: List<String>, val positions: String, val scope: QuotaScope)

        var labelSeq = 0
        val quotaMins = LinkedHashMap<RuleKey, MutableList<Int>>()
        val oneOfs = LinkedHashMap<RuleKey, JsonNode>()

        data.path("shiftAnalysis").path("check").path("requirements").forEach { entry ->
            val item = entry.path("item").asText()
            val codes = CODE.findAll(item).map { it.value }.distinct().toList()
            val unknown = codes.filter { it !in requirementsByCode }
            if (codes.isEmpty() || unknown.isNotEmpty()) {
                flag(
                    "DATAERR", "requirement", item.take(60),
                    "Shift-allocation rule names no known catalogue code" +
                        (if (unknown.isEmpty()) "" else " (unknown: ${unknown.joinToString()})") +
                        " — not imported.",
                )
                return@forEach
            }
            val min = Regex("^(\\d+)").find(entry.path("required").asText().trim())?.groupValues?.get(1)?.toInt()
            val scope = if (entry.path("shift").asText() == "swing") QuotaScope.SWING else QuotaScope.SHIFT
            val key = RuleKey(codes, entry.path("positions").asText().trim(), scope)
            when {
                min != null && codes.size == 1 -> quotaMins.getOrPut(key) { mutableListOf() }.add(min)
                min == null && codes.size > 1 -> oneOfs.putIfAbsent(key, entry)
                else -> flag(
                    "REVIEW", "requirement", codes.joinToString(),
                    "Shift-allocation rule is not machine-readable as a quota or a one-of set " +
                        "(\"${entry.path("required").asText().take(120)}\") — not imported; encode by hand.",
                )
            }
        }

        quotaMins.forEach { (key, mins) ->
            if (mins.toSet().size > 1) {
                flag(
                    "REVIEW", "requirement", key.codes.single(),
                    "The shift-allocation minimum for ${key.codes.single()} differs between " +
                        "shifts (${mins.joinToString()}) — the larger imported; the schema holds " +
                        "one minimum per shift-scoped rule.",
                )
            }
            val footnote = "S${++labelSeq}"
            val positions = rulePositions(key.positions)
            em.persist(
                QuotaRule().apply {
                    this.footnote = footnote
                    matrixVersion = matrix
                    requirement = requirementsByCode.getValue(key.codes.single())
                    minCount = mins.max()
                    scope = key.scope
                    this.positions = positions.orEmpty().toMutableSet()
                    stampCreated(actor, now)
                },
            )
            positions?.forEach { cell(it, key.codes.single(), footnote) }
        }
        counts["quotas"] = quotaMins.size

        oneOfs.forEach { (key, entry) ->
            val label = "S${++labelSeq}"
            val positions = rulePositions(key.positions)
            if (positions == null || positions.size != 1) {
                flag(
                    "REVIEW", "requirement", key.codes.joinToString(),
                    "A one-of rule needs exactly one position and the shift-allocation entry names " +
                        "\"${key.positions}\" — not imported; encode by hand.",
                )
                return@forEach
            }
            val rule = ConditionalRule().apply {
                matrixVersion = matrix
                kind = ConditionalKind.ONE_OF
                position = positions.single()
                this.label = label
                stampCreated(actor, now)
            }
            em.persist(rule)
            key.codes.forEachIndexed { index, code ->
                em.persist(
                    ConditionalRuleMember().apply {
                        conditionalRule = rule
                        requirement = requirementsByCode.getValue(code)
                        role = ConditionalMemberRole.MEMBER
                        ordinal = index
                    },
                )
                cell(positions.single(), code, label)
            }
        }
    }

    // ------------------------------------------------------------------ certificates on file

    /**
     * The portal's certificate files, attached to the people and codes the portal itself linked
     * them to — the handoff's "deferred half", no longer deferred.
     *
     * Who a file belongs to is read in this order, and the order is the point: the portal's own
     * certificate linkage first (it is the matrix's join, made by the people who know the data),
     * then the filename's "SURNAME_ First - CODE …" convention, and only then the folder the file
     * sits in — because the folder is exactly where the misfiled ones are wrong. A file with a code
     * lands **verified** and linked to the holding it evidences, since the portal's matrix *is* the
     * accepted record; one with no code lands in the review queue and says why. Nothing is
     * re-extracted: the model has no say over a record a human already made.
     *
     * The portal's file id becomes the document's public id, so a re-import is idempotent and a
     * document can be traced back to the portal row it came from.
     */
    private fun loadDocuments(certDates: Map<String, CertDate>) {
        val indexFile = root.resolve("documents-index.json")
        val docsRoot = documentsRoot
        if (storage == null || docsRoot == null || !Files.isRegularFile(indexFile) || !Files.isDirectory(docsRoot)) {
            log.infof(
                "Portal certificates not imported: needs documents-index.json beside portal-state.json " +
                    "and a documents directory (looked at %s)",
                docsRoot ?: "(none)",
            )
            return
        }
        // portal file id → "normName::CODE", from the linkage's url.
        val linkByFileId = certDates.entries.mapNotNull { (key, cert) -> cert.fileId?.let { it to key } }.toMap()

        var imported = 0
        var unfiled = 0
        var missing = 0
        var misfiled = 0
        var unknownPerson = 0
        ObjectMapper().readTree(Files.readString(indexFile)).forEach { entry ->
            if (entry.path("category").asText() != "certificate") return@forEach
            if (entry.path("removedAt").asText("").isNotEmpty()) return@forEach
            val fileId = entry.path("id").asText()
            val filename = entry.path("filename").asText()

            val link = linkByFileId[fileId]?.split("::")
            val fromFilename = normName(filename.substringBefore(" - ").replace('_', ','))
            val folderPerson = normName(entry.path("person").asText(""))
            val personKey = link?.get(0)
                ?: fromFilename.takeIf { it in personsByNormName }
                ?: folderPerson
            val person = personsByNormName[personKey]
            if (person == null) {
                unknownPerson++
                return@forEach
            }
            if (personKey != folderPerson && folderPerson in personsByNormName) misfiled++

            val code = link?.get(1) ?: CODE.find(filename)?.value
            val requirement = code?.let { requirementsByCode[it] }
            val path = docsRoot.resolve(entry.path("path").asText())
            if (!Files.isRegularFile(path)) {
                missing++
                return@forEach
            }
            val bytes = Files.readAllBytes(path)
            val contentType = entry.path("contentType").asText("application/pdf")
            val publicId = try {
                UUID.fromString(fileId)
            } catch (_: IllegalArgumentException) {
                UUID.randomUUID()
            }
            val key = "evidence/$publicId/original"
            storage.put(key, bytes, contentType)

            em.persist(
                EvidenceDocument().apply {
                    this.publicId = publicId
                    this.person = person
                    source = EvidenceSource.ADMIN_UPLOAD
                    this.contentType = contentType
                    byteSize = bytes.size.toLong()
                    fileName = filename
                    objectKey = key
                    uploadOffset = bytes.size.toLong()
                    uploadComplete = true
                    declaredSize = bytes.size.toLong()
                    declaredSha256 = entry.path("checksum").asText("").lowercase().ifEmpty { null }
                    submittedBy = entry.path("uploadedBy").asText("").ifEmpty { actor }
                    submittedAt = runCatching { Instant.parse(entry.path("createdAt").asText("")) }.getOrNull() ?: now
                    matchedRequirement = requirement
                    linkedHolding = requirement?.let { holdingsByKey["$personKey::${it.code}"] }
                    if (requirement != null) {
                        verificationStatus = VerificationStatus.VERIFIED
                    } else {
                        verificationStatus = VerificationStatus.PENDING_REVIEW
                        reviewReason = "Imported from the portal without a matrix code — file it by hand."
                    }
                    stampCreated(actor, now)
                },
            )
            if (requirement != null) imported++ else unfiled++
        }
        counts["documents"] = imported + unfiled
        counts["sheets"] = loadShipDocuments(indexFile, docsRoot, storage)

        if (misfiled > 0) {
            flag(
                "REVIEW", "evidence", "$misfiled certificates",
                "Filed in another crew member's folder on the portal — attached here to the person " +
                    "the file names; refile at source.",
            )
        }
        if (unfiled > 0) {
            flag(
                "INFO", "evidence", "$unfiled certificates",
                "Carry no matrix code in the portal's linkage or their filename — in the evidence " +
                    "queue to file by hand.",
            )
        }
        if (missing > 0) {
            flag("DATAERR", "evidence", "$missing certificates", "Listed in the portal's index but absent from the documents directory — not imported.")
        }
        if (unknownPerson > 0) {
            flag("DATAERR", "evidence", "$unknownPerson certificates", "Name someone the qualification matrix does not carry — not imported.")
        }
    }

    /**
     * The ship's own sheets — every index entry that is not a certificate: the training, skills
     * and validity matrices, the shift-allocation guideline, the OPMS export, the crew
     * certificates sheet, the one document. The newest per category is current; the rest are
     * history, as the portal kept them.
     */
    private fun loadShipDocuments(indexFile: Path, docsRoot: Path, storage: ObjectStorage): Int {
        val entries = ObjectMapper().readTree(Files.readString(indexFile))
            .filter { it.path("category").asText() != "certificate" }
            .filter { it.path("removedAt").asText("").isEmpty() }
            .sortedByDescending { it.path("createdAt").asText("") }
        val currentSeen = mutableSetOf<String>()
        var loaded = 0
        entries.forEach { entry ->
            val path = docsRoot.resolve(entry.path("path").asText())
            if (!Files.isRegularFile(path)) return@forEach
            val category = entry.path("category").asText("document")
            val bytes = Files.readAllBytes(path)
            val key = "ship-documents/${partnership.abbrev}/${UUID.randomUUID()}/original"
            storage.put(key, bytes, entry.path("contentType").asText("application/octet-stream"))
            em.persist(
                ShipDocument().apply {
                    this.partnership = this@PortalSeedLoader.partnership
                    this.category = category
                    fileName = entry.path("filename").asText()
                    contentType = entry.path("contentType").asText("application/octet-stream")
                    byteSize = bytes.size.toLong()
                    objectKey = key
                    sha256 = entry.path("checksum").asText("").lowercase().ifEmpty { null }
                    current = currentSeen.add(category)
                    filedBy = entry.path("uploadedBy").asText("").ifEmpty { actor }
                    filedAt = runCatching { Instant.parse(entry.path("createdAt").asText("")) }.getOrNull() ?: now
                    if (!current) supersededAt = now
                    stampCreated(actor, now)
                },
            )
            loaded++
        }
        return loaded
    }

    // ------------------------------------------------------------------ worklist and accounts

    private fun writeWorklist() {
        var written = 0
        flags.forEach { f ->
            em.persist(
                ExceptionItem().apply {
                    area = f.area
                    description = "[${f.severity}] ${f.entity} — ${f.issue}"
                    stampCreated(actor, now)
                },
            )
            written++
        }
        counts["worklist"] = written
    }

    /**
     * Accounts are the one thing the portal cannot supply — same posture as the extracted
     * dataset: five invented SEC-1b back-office rows (§9's fan-out needs one per role), a crew
     * account per person, and Masters supervising their partnership (MOB-11).
     */
    private fun seedAccounts() {
        fun backOffice(name: String, email: String, role: Role) {
            em.persist(
                UserAccount().apply {
                    kind = UserAccountKind.LOCAL_TEST
                    displayName = name
                    this.email = email
                    grantRole(role, actor, now)
                    stampCreated(actor, now)
                },
            )
        }
        backOffice("Dana Whitlock", "dana.whitlock@example.test", Role.CREW_COORDINATOR)
        backOffice("Marcus Reid", "marcus.reid@example.test", Role.WORKFLOW_MANAGER)
        backOffice("Priya Anand", "priya.anand@example.test", Role.DATA_STEWARD)
        backOffice("Ellen Kovač", "ellen.kovac@example.test", Role.COMPLIANCE_LEAD)
        backOffice("Ops Admin", "ops.admin@example.test", Role.SYSTEM_ADMINISTRATOR)

        personsByNormName.values.forEach { person ->
            em.persist(
                UserAccount().apply {
                    this.person = person
                    kind = UserAccountKind.LOCAL_TEST
                    displayName = person.name
                    grantRole(Role.CREW_MEMBER, actor, now)
                    if (person.position.name == "Master") {
                        grantRole(Role.VESSEL_MASTER, actor, now)
                        scopedPartnershipIds.add(person.partnership.requiredId)
                    }
                    stampCreated(actor, now)
                },
            )
        }
    }
}
