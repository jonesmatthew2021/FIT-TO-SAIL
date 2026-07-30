package au.crewcomp.platform.dev

import au.crewcomp.engine.HoldingStatus
import au.crewcomp.engine.PersonStatus
import au.crewcomp.engine.QuotaScope
import au.crewcomp.engine.RegisterOutcome
import au.crewcomp.engine.RuleLevel
import au.crewcomp.engine.Shift
import au.crewcomp.people.Assignment
import au.crewcomp.people.Person
import au.crewcomp.people.QualificationHolding
import au.crewcomp.people.UserAccount
import au.crewcomp.people.UserAccountKind
import au.crewcomp.platform.dev.ExtractTransforms.MatrixRuleRow
import au.crewcomp.platform.security.Role
import au.crewcomp.platform.time.BusinessClock
import au.crewcomp.reference.CrewChange
import au.crewcomp.reference.CrewPosition
import au.crewcomp.reference.Partnership
import au.crewcomp.reference.PositionSlot
import au.crewcomp.reference.PositionTier
import au.crewcomp.reference.Requirement
import au.crewcomp.reference.RequirementAlias
import au.crewcomp.reference.Vessel
import au.crewcomp.rules.ConditionalKind
import au.crewcomp.rules.ConditionalMemberRole
import au.crewcomp.rules.ConditionalRule
import au.crewcomp.rules.ConditionalRuleMember
import au.crewcomp.rules.MatrixStatus
import au.crewcomp.rules.MatrixVersion
import au.crewcomp.rules.QuotaRule
import au.crewcomp.rules.RequirementRule
import au.crewcomp.workflow.ApprovalCondition
import au.crewcomp.workflow.ExceptionItem
import au.crewcomp.workflow.RegisterAuditEntry
import au.crewcomp.workflow.RegisterNote
import au.crewcomp.workflow.RegisterRecord
import jakarta.persistence.EntityManager
import org.jboss.logging.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

/**
 * Loads the POC's validated workbook extracts (the seed CSVs + `exceptions.csv`) — the first cut
 * of the §11 migration load, usable today as the development stack's "extracted" dataset.
 *
 * **The extracts are real crew data** (names, Sam numbers, certification expiries, register
 * correspondence). They live outside this repository and are read from
 * `crewcomp.dev-seed.extract-root`; nothing here may copy them into the source tree, and the
 * loader runs only inside [DevDataSeeder]'s build-time-gated, prod-refusing start-up path.
 *
 * The fix-up rules are the POC's (`build_real_seed.py`), ported in [ExtractTransforms]: slot →
 * position rule collapse with the M8 CoC one-of set, legacy-title aliasing, `UNREC-xx` register
 * placeholders, derived statuses/types, and person stubs for roster/register-only Sam numbers.
 * Every fix-up lands in the ADM-7 exceptions worklist. What this is **not** yet (§11): it is not
 * re-runnable against a populated database, and the CC24/CC25 acceptance diff against the POC's
 * rendering has not been executed.
 *
 * Not a CDI bean, deliberately: constructed by [DevDataSeeder] inside its transaction, so the
 * build-time gate and the prod refusal live in exactly one place.
 */
class ExtractedSeedLoader(
    private val em: EntityManager,
    private val clock: BusinessClock,
    private val root: Path,
) {
    private val log = Logger.getLogger(ExtractedSeedLoader::class.java)

    private val now: Instant = Instant.now()
    private val actor = "extract-import"

    private data class Flag(val severity: String, val area: String, val entity: String, val issue: String)

    private val flags = mutableListOf<Flag>()

    private fun flag(severity: String, area: String, entity: String, issue: String) {
        flags.add(Flag(severity, area, entity, issue))
    }

    companion object {
        /** The published matrix's label — [DevDataSeeder] uses it to recognise this dataset. */
        const val PUBLISHED_MATRIX_LABEL = "Matrix 29.06.2026"
        const val ARCHIVE_MATRIX_LABEL = "Template – Matrix (archive)"

        private val SEED_FILES = listOf(
            "partnerships.csv", "positions.csv", "position_slots.csv", "requirements.csv",
            "crew_change_calendar.csv", "persons.csv", "qualification_holdings.csv",
            "assignments.csv", "matrix_rules.csv", "exemption_register.csv",
        )

        /** Workbook category names → the Appendix A prefix codes the schema stores. */
        private val CATEGORY_BY_NAME = mapOf(
            "Qualification" to "QL", "Vessel Specific" to "VS", "Project Specific" to "PS",
            "Master Specific" to "MS", "Cargo System" to "CS", "High Risk Work" to "HR",
            "Permit to Work" to "PT", "Vessel Induction" to "VI", "Project Induction" to "PI",
        )
        private val CODE_PREFIX = Regex("^(QL|VS|PS|MS|CS|HR|PT|VI|PI)-\\d+$")
    }

    // Lookups built as loading proceeds.
    private val partnershipsByName = mutableMapOf<String, Partnership>()
    private val partnershipsByAbbrev = mutableMapOf<String, Partnership>()
    private val positionsByName = mutableMapOf<String, CrewPosition>()
    private val requirementsByCode = mutableMapOf<String, Requirement>()
    private val titleToCode = mutableMapOf<String, String>()
    private val crewChangesByKey = mutableMapOf<String, CrewChange>()
    private val personsBySam = mutableMapOf<String, Person>()
    private val slotFirstPosition = mutableMapOf<Int, String>()

    fun load() {
        val seedDir = root.resolve("seed")
        val missing = SEED_FILES.filterNot { Files.isRegularFile(seedDir.resolve(it)) } +
            listOf("exceptions.csv").filterNot { Files.isRegularFile(root.resolve(it)) }
        check(missing.isEmpty()) {
            "crewcomp.dev-seed.extract-root=$root does not hold the POC extracts — missing: " +
                missing.joinToString() + ". Expected the layout of the POC repository " +
                "(seed/*.csv beside exceptions.csv); run its extract_seed.py first."
        }

        val assignmentRows = table(seedDir, "assignments.csv")
        val registerRows = raw(seedDir, "exemption_register.csv").drop(1)

        loadPartnerships(table(seedDir, "partnerships.csv"))
        loadPositions(table(seedDir, "positions.csv"))
        loadSlots(table(seedDir, "position_slots.csv"))
        loadRequirements(table(seedDir, "requirements.csv"))
        loadCalendar(table(seedDir, "crew_change_calendar.csv"))
        loadPersons(table(seedDir, "persons.csv"))
        createStubs(assignmentRows, registerRows)
        loadHoldings(table(seedDir, "qualification_holdings.csv"))
        loadMatrix(table(seedDir, "matrix_rules.csv"))
        loadAssignments(assignmentRows)
        loadRegister(registerRows)
        loadWorklist(table(root, "exceptions.csv"))
        seedAccounts()

        em.flush()
        log.infof(
            "Extracted dataset loaded from %s: %d partnerships, %d requirements, %d people " +
                "(%d lookup-only), %d crew changes, %d assignments, %d register records, " +
                "%d worklist items",
            root, partnershipsByAbbrev.size, requirementsByCode.size, personsBySam.size,
            personsBySam.values.count { it.status == PersonStatus.LOOKUP_ONLY },
            crewChangesByKey.size, counts["assignments"] ?: 0, counts["register"] ?: 0,
            counts["worklist"] ?: 0,
        )
    }

    private val counts = mutableMapOf<String, Int>()

    private fun table(dir: Path, name: String): List<Map<String, String>> =
        SeedCsv.parseWithHeader(Files.readString(dir.resolve(name)).removePrefix("\uFEFF"))

    private fun raw(dir: Path, name: String): List<List<String>> =
        SeedCsv.parse(Files.readString(dir.resolve(name)).removePrefix("\uFEFF"))

    // ------------------------------------------------------------------ reference data

    private fun loadPartnerships(rows: List<Map<String, String>>) {
        rows.forEach { r ->
            val partnership = Partnership().apply {
                abbrev = r.getValue("abbrev")
                name = r.getValue("name")
                vesselClass = null // absent from the extracts — flagged on the worklist
                stampCreated(actor, now)
            }
            em.persist(partnership)
            partnershipsByName[partnership.name] = partnership
            partnershipsByAbbrev[partnership.abbrev] = partnership

            val pair = r.getValue("vessel_pair").split('/')
            listOf("tug", "barge").zip(pair).forEach { (kindName, vesselName) ->
                em.persist(
                    Vessel().apply {
                        name = vesselName.trim()
                        this.partnership = partnership
                        kind = kindName
                        stampCreated(actor, now)
                    },
                )
            }
        }
    }

    private fun loadPositions(rows: List<Map<String, String>>) {
        rows.forEach { r ->
            val position = CrewPosition().apply {
                name = r.getValue("position").trim()
                stampCreated(actor, now)
            }
            em.persist(position)
            positionsByName[position.name] = position
        }
    }

    /** Resolves a position by name, creating and flagging one the extract never declared. */
    private fun position(name: String, context: String): CrewPosition {
        val trimmed = name.trim()
        positionsByName[trimmed]?.let { return it }
        positionsByName.entries.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }
            ?.let { return it.value }
        val created = CrewPosition().apply {
            this.name = trimmed
            stampCreated(actor, now)
        }
        em.persist(created)
        positionsByName[trimmed] = created
        flag(
            "DATAERR", "slot", trimmed,
            "Position named by $context is not in the extract's position list — created on import; confirm.",
        )
        return created
    }

    private fun loadSlots(rows: List<Map<String, String>>) {
        rows.groupBy { it.getValue("slot_ref").trim().toInt() }.toSortedMap().forEach { (ref, group) ->
            slotFirstPosition[ref] = group.first().getValue("position").trim()
            em.persist(
                PositionSlot().apply {
                    this.ref = ref
                    shift = Shift.fromWire(group.first().getValue("shift").trim())
                    allowedPositions = group
                        .map { position(it.getValue("position"), "slot $ref") }
                        .toMutableSet()
                    notes = group.firstNotNullOfOrNull { it.getValue("note").trim().ifEmpty { null } }
                    stampCreated(actor, now)
                },
            )
        }
    }

    private fun loadRequirements(rows: List<Map<String, String>>) {
        rows.forEach { r ->
            val code = r.getValue("code").trim()
            val requirement = Requirement().apply {
                this.code = code
                category = CODE_PREFIX.find(code)?.groupValues?.get(1)
                    ?: CATEGORY_BY_NAME[r.getValue("category").trim()]
                    ?: "QL" // the uncoded Advanced Fire Fighting row; its worklist item is in exceptions.csv
                title = r.getValue("title").trim()
                // The schema's status vocabulary is active|retired; the extract's `needs_code`
                // marker moves to the notes, and its worklist item is already in exceptions.csv.
                status = "active"
                notes = if (r.getValue("status").trim() == "needs_code") {
                    "Uncoded in the source workbook — needs a catalogue code (see exceptions worklist)."
                } else {
                    null
                }
                stampCreated(actor, now)
            }
            em.persist(requirement)
            requirementsByCode[code] = requirement
            titleToCode.putIfAbsent(ExtractTransforms.normTitle(requirement.title), code)
        }

        // The confirmed legacy-title mappings become catalogue aliases (§4.1), so the register
        // import below and the evidence pipeline's title matching share one vocabulary.
        ExtractTransforms.TITLE_ALIASES.forEach { (alias, code) ->
            val requirement = requirementsByCode[code] ?: run {
                flag("DATAERR", "requirement", code, "Legacy-title alias targets a code absent from the catalogue.")
                return@forEach
            }
            em.persist(
                RequirementAlias().apply {
                    this.requirement = requirement
                    this.alias = alias
                    stampCreated(actor, now)
                },
            )
        }
    }

    private fun loadCalendar(rows: List<Map<String, String>>) {
        rows.forEach { r ->
            val partnership = partnershipsByName[r.getValue("partnership").trim()] ?: run {
                flag(
                    "DATAERR", "partnership", r.getValue("partnership"),
                    "Crew-change calendar row names an unknown partnership — row not imported.",
                )
                return@forEach
            }
            val key = "${r.getValue("cc_id").trim()}|${partnership.abbrev}"
            if (crewChangesByKey.containsKey(key)) {
                flag("DATAERR", "partnership", key, "Duplicate crew-change calendar row — first kept.")
                return@forEach
            }
            val from = LocalDate.parse(r.getValue("from_date").trim())
            val to = LocalDate.parse(r.getValue("to_date").trim())
            if (to < from) {
                // Real occurrence: four CC30 rows whose to-date is years before their from-date
                // (workbook typos). The schema refuses an inverted window and a guessed
                // correction would be fabrication — row not imported, correct at source (§11).
                flag(
                    "DATAERR", "partnership", key,
                    "Crew-change window inverted at source ($from to $to) — row not imported, correct at source.",
                )
                return@forEach
            }
            val cc = CrewChange().apply {
                ccId = r.getValue("cc_id").trim()
                this.partnership = partnership
                fromDate = from
                toDate = to
                cutoffDate = LocalDate.parse(r.getValue("cutoff_date").trim())
                stampCreated(actor, now)
            }
            em.persist(cc)
            crewChangesByKey[key] = cc
        }
    }

    // ------------------------------------------------------------------ people

    private fun loadPersons(rows: List<Map<String, String>>) {
        val uni = partnershipsByAbbrev.getValue("UNI")
        val tiersSeen = mutableSetOf<Pair<String, String>>()
        rows.forEach { r ->
            val sam = r.getValue("sam_no").trim()
            // Extraction artifacts (header/legend rows) — already flagged in exceptions.csv.
            if (!ExtractTransforms.SAM_FORMAT.matches(sam)) return@forEach
            val (positionName, tierName) = ExtractTransforms.splitTier(r.getValue("position_raw"))
            val person = Person().apply {
                this.sam = sam
                name = r.getValue("name").trim()
                position = position(positionName, "person $sam")
                tier = tierName
                partnership = uni // the holdings workbook is United's
                status = PersonStatus.ACTIVE
                stampCreated(actor, now)
            }
            em.persist(person)
            personsBySam[sam] = person
            if (tierName != null && tiersSeen.add(positionName to tierName)) {
                em.persist(
                    PositionTier().apply {
                        position = position(positionName, "tier $tierName")
                        name = tierName
                        stampCreated(actor, now)
                    },
                )
            }
        }
    }

    /**
     * Crew who appear on rosters or in the register but have no holdings row become lookup-only
     * stubs (POC rule): name and position recovered from the roster (the slot's first allowed
     * position) or from the register row.
     */
    private fun createStubs(assignmentRows: List<Map<String, String>>, registerRows: List<List<String>>) {
        val uni = partnershipsByAbbrev.getValue("UNI")
        data class Stub(val name: String, val positionName: String, val context: String)

        val stubs = LinkedHashMap<String, Stub>()
        assignmentRows.forEach { a ->
            val sam = a.getValue("sam_no").trim()
            if (sam.isEmpty() || personsBySam.containsKey(sam)) return@forEach
            stubs.putIfAbsent(
                sam,
                Stub(
                    a.getValue("name").trim(),
                    slotFirstPosition[a.getValue("slot_ref").trim().toInt()] ?: "GPH",
                    "roster",
                ),
            )
        }
        registerRows.forEach { r ->
            val sam = r.getOrNull(10)?.trim().orEmpty()
            if (sam.isEmpty() || personsBySam.containsKey(sam)) return@forEach
            stubs.putIfAbsent(sam, Stub(r.getOrNull(9)?.trim().orEmpty(), r.getOrNull(11)?.trim().orEmpty(), "register"))
        }

        stubs.forEach { (sam, stub) ->
            if (!ExtractTransforms.SAM_FORMAT.matches(sam)) {
                flag(
                    "WARN", "person", sam,
                    "Sam # on the ${stub.context} does not match the Sam format — stub person created as-is.",
                )
            }
            val (positionName, tierName) = ExtractTransforms.splitTier(stub.positionName.ifEmpty { "GPH" })
            val person = Person().apply {
                this.sam = sam
                name = stub.name.ifEmpty { "(name not recorded)" }
                position = position(positionName, "${stub.context} stub $sam")
                tier = tierName
                partnership = uni
                status = PersonStatus.LOOKUP_ONLY
                stampCreated(actor, now)
            }
            em.persist(person)
            personsBySam[sam] = person
        }
    }

    private fun loadHoldings(rows: List<Map<String, String>>) {
        val seen = mutableSetOf<Pair<String, String>>()
        rows.forEach { r ->
            val sam = r.getValue("sam_no").trim()
            var code = r.getValue("requirement_code").trim()
            if (code == "(UNCODED:Advanced Fire Fighting)") code = "(UNASSIGNED)"
            val person = personsBySam[sam] ?: run {
                flag("DATAERR", "holding", sam, "Holding row for a Sam # with no person row — not imported.")
                return@forEach
            }
            val requirement = requirementsByCode[code]
                ?: error("Holding references unknown requirement $code — the extract is inconsistent.")
            if (!seen.add(sam to code)) {
                flag("DATAERR", "holding", "$sam / $code", "Duplicate holding row — first kept.")
                return@forEach
            }
            var status = HoldingStatus.fromWire(r.getValue("status").trim())
            val expiry = r.getValue("expiry_date").trim().ifEmpty { null }?.let { LocalDate.parse(it) }
            var note: String? = null
            if (status == HoldingStatus.HELD_EXPIRY && expiry == null) {
                // Never in the current extracts, but the schema requires the pairing (§4.3).
                status = HoldingStatus.UNKNOWN
                note = "Imported as unknown: the source said held-with-expiry but carried no date."
                flag("DATAERR", "holding", "$sam / $code", "held_expiry with no expiry date — imported as unknown.")
            }
            em.persist(
                QualificationHolding().apply {
                    this.person = person
                    this.requirement = requirement
                    this.status = status
                    expiryDate = if (status == HoldingStatus.HELD_EXPIRY) expiry else null
                    this.note = note
                    stampCreated(actor, now)
                },
            )
        }
    }

    // ------------------------------------------------------------------ matrix

    private fun loadMatrix(rows: List<Map<String, String>>) {
        val catalogueOrder = requirementsByCode.keys.withIndex().associate { (i, code) -> code to i }
        val versions = mapOf(
            "archive" to MatrixVersion().apply {
                label = ARCHIVE_MATRIX_LABEL
                status = MatrixStatus.SUPERSEDED
                notes = "The workbook's \"Template -Matrix Archive\" sheet — cell-for-cell identical to " +
                    "Matrix 29.06.2026 (the archive was never edited)."
                stampCreated(actor, now)
            },
            "matrix-29.06.2026" to MatrixVersion().apply {
                label = PUBLISHED_MATRIX_LABEL
                status = MatrixStatus.PUBLISHED
                effectiveFrom = LocalDate.of(2026, 6, 29)
                publishedAt = now
                publishedBy = actor
                notes = "Imported from the \"Template - Matrix 29.06.2026\" sheet. Slot-level rules " +
                    "collapsed to position level (see exceptions worklist). No tier footnote: the " +
                    "real matrix's M7 is a quota rule, not a tier rule."
                stampCreated(actor, now)
            },
        )
        versions.values.forEach(em::persist)

        rows.groupBy { it.getValue("version").trim() }.forEach { (versionKey, versionRows) ->
            val matrix = versions[versionKey] ?: run {
                flag("DATAERR", "requirement", versionKey, "matrix_rules.csv names an unknown version — rows skipped.")
                return@forEach
            }
            val collapsed = ExtractTransforms.collapseSlots(
                versionRows.map {
                    MatrixRuleRow(
                        it.getValue("slot_ref").trim().toInt(),
                        it.getValue("position").trim(),
                        it.getValue("requirement_code").trim(),
                        it.getValue("level").trim(),
                    )
                },
                catalogueOrder,
            )
            collapsed.levels.forEach { (key, level) ->
                val (positionName, code) = key
                em.persist(
                    RequirementRule().apply {
                        matrixVersion = matrix
                        position = position(positionName, "matrix ${matrix.label}")
                        requirement = requirementsByCode[code]
                            ?: error("Matrix rule references unknown requirement $code.")
                        this.level = RuleLevel(level)
                        stampCreated(actor, now)
                    },
                )
            }
            conditionalAndQuotaRules(matrix)

            if (matrix.status == MatrixStatus.PUBLISHED && collapsed.disagreements.isNotEmpty()) {
                flag(
                    "REVIEW", "requirement", "Chief Officer matrix rules",
                    "The source matrix sets different rules per slot; rules are stored per position, so the " +
                        "union was imported and the CoC cells became one-of set M8 (QL-01 Master accepted as " +
                        "superseding QL-02 Chief Mate — confirm). Slot-level detail: " +
                        collapsed.disagreements.joinToString(" · "),
                )
            }
        }
    }

    /**
     * The workbook's Notes block, as machine-checked rules (POC import): M1/M2/M8 conditional,
     * M3–M7 quotas. Recreated per version because both sheets carry the same notes. There is no
     * tier footnote in the real matrix.
     */
    private fun conditionalAndQuotaRules(matrix: MatrixVersion) {
        fun conditional(
            kind: ConditionalKind,
            positionName: String,
            label: String,
            members: List<Pair<String, ConditionalMemberRole>>,
            targetCode: String? = null,
        ) {
            val rule = ConditionalRule().apply {
                matrixVersion = matrix
                this.kind = kind
                position = position(positionName, "conditional rule $label")
                requirement = targetCode?.let { requirementsByCode.getValue(it) }
                this.label = label
                stampCreated(actor, now)
            }
            em.persist(rule)
            members.forEachIndexed { index, (code, role) ->
                em.persist(
                    ConditionalRuleMember().apply {
                        conditionalRule = rule
                        requirement = requirementsByCode.getValue(code)
                        this.role = role
                        ordinal = index
                    },
                )
            }
        }

        conditional(
            ConditionalKind.ONE_OF, "GPH", "M1",
            listOf("QL-08", "QL-09", "QL-10").map { it to ConditionalMemberRole.MEMBER },
        )
        conditional(
            ConditionalKind.DEPENDENT, "GPH", "M2",
            listOf(
                "QL-08" to ConditionalMemberRole.REQUIRED_IF_HOLDS,
                "QL-09" to ConditionalMemberRole.REQUIRED_IF_HOLDS,
                "QL-10" to ConditionalMemberRole.UNLESS_HOLDS,
            ),
            targetCode = "QL-12",
        )
        conditional(
            ConditionalKind.ONE_OF, "Chief Officer", "M8",
            listOf("QL-01", "QL-02", "QL-03").map { it to ConditionalMemberRole.MEMBER },
        )

        fun quota(footnote: String, code: String, scope: QuotaScope, min: Int, positionNames: List<String>) {
            em.persist(
                QuotaRule().apply {
                    matrixVersion = matrix
                    this.footnote = footnote
                    requirement = requirementsByCode.getValue(code)
                    minCount = min
                    this.scope = scope
                    positions = positionNames.map { position(it, "quota $footnote") }.toMutableSet()
                    stampCreated(actor, now)
                },
            )
        }

        quota("M3", "QL-16", QuotaScope.SWING, 4, emptyList())
        quota("M4", "QL-19", QuotaScope.SHIFT, 2, emptyList())
        quota("M5", "QL-20", QuotaScope.SHIFT, 1, listOf("GPH"))
        quota("M6", "HR-02", QuotaScope.SHIFT, 2, listOf("Chief Officer", "Second Mate", "Assistant Engineer", "GPH"))
        quota("M7", "PT-03", QuotaScope.SHIFT, 1, listOf("GPH"))
    }

    // ------------------------------------------------------------------ rosters

    private fun loadAssignments(rows: List<Map<String, String>>) {
        var imported = 0
        rows.forEach { r ->
            val partnership = partnershipsByName[r.getValue("partnership").trim()] ?: run {
                flag("DATAERR", "roster", r.getValue("partnership"), "Assignment names an unknown partnership — not imported.")
                return@forEach
            }
            val cc = crewChangesByKey["${r.getValue("cc_id").trim()}|${partnership.abbrev}"] ?: run {
                flag("DATAERR", "roster", r.getValue("cc_id"), "Assignment references a crew change absent from the calendar — not imported.")
                return@forEach
            }
            val person = personsBySam[r.getValue("sam_no").trim()] ?: run {
                flag("DATAERR", "roster", r.getValue("sam_no"), "Assignment Sam # resolves to no person — not imported.")
                return@forEach
            }
            em.persist(
                Assignment().apply {
                    this.person = person
                    crewChange = cc
                    this.partnership = partnership
                    slotRef = r.getValue("slot_ref").trim().toInt()
                    // The extract already assumed the crew-change window where sheet dates were
                    // missing (flagged in exceptions.csv); the fallback here mirrors that rule.
                    fromDate = r.getValue("from_date").trim().ifEmpty { null }?.let(LocalDate::parse) ?: cc.fromDate
                    toDate = r.getValue("to_date").trim().ifEmpty { null }?.let(LocalDate::parse) ?: cc.toDate
                    stampCreated(actor, now)
                },
            )
            imported++
        }
        counts["assignments"] = imported
    }

    // ------------------------------------------------------------------ register

    /**
     * The raw workbook register (29 columns, read positionally like the POC does; the header
     * carries duplicated column names, so names cannot key it). Columns 15/17 are the *requested*
     * window; 24/25 the *approval* window.
     */
    private fun loadRegister(rows: List<List<String>>) {
        var unrecSeq = 0
        val stats = mutableMapOf<String, Int>().withDefault { 0 }
        fun bump(key: String) = stats.put(key, stats.getValue(key) + 1)
        val unmappedTitles = sortedSetOf<String>()
        val seenIds = mutableSetOf<String>()
        var imported = 0

        rows.forEach { rawRow ->
            val r = (0 until 29).map { rawRow.getOrNull(it)?.trim().orEmpty() }
            val derivations = mutableListOf<String>()

            var recordId = r[0]
            if (recordId.isEmpty() || recordId.lowercase() == "n/a") {
                unrecSeq++
                recordId = "UNREC-%02d".format(unrecSeq)
                bump("no_id")
                derivations.add("no ID in the source — placeholder assigned")
            }
            if (!seenIds.add(recordId)) {
                var n = 2
                while (!seenIds.add("$recordId#$n")) n++
                recordId = "$recordId#$n"
                flag("DATAERR", "register", recordId, "Duplicate register ID in the source — suffixed on import.")
            }

            val sourceOutcome = r[23].ifEmpty { null }?.let {
                runCatching { RegisterOutcome.fromWire(it) }.getOrElse { _ ->
                    flag("DATAERR", "register", recordId, "Unrecognised outcome \"${r[23]}\" — not imported.")
                    null
                }
            }
            val closeout = ExtractTransforms.isoOrNull(r[6]) ?: ExtractTransforms.isoOrNull(r[28])
            val header = ExtractTransforms.deriveHeader(r[1], r[2], sourceOutcome, closeout != null)
            if (header.statusDerived) {
                bump("no_status")
                derivations.add("status derived (source: \"${r[1].ifEmpty { "blank" }}\")")
            }
            if (header.typeDerived) {
                bump("no_type")
                derivations.add("type derived (source: \"${r[2].ifEmpty { "blank" }}\")")
            }
            if (header.closedWithoutOutcome) {
                bump("closed_no_outcome")
                derivations.add("closed with no outcome recorded — imported as Closed - Admin Action")
            }

            val partnership = partnershipsByName[r[7]] ?: partnershipsByAbbrev.getValue("UNI")
            val ccRef = r[16].takeIf { Regex("^CC\\d{2}$").matches(it) }
                ?: Regex("^[A-Z]{2,4}(CC\\d{2})-").find(recordId)?.groupValues?.get(1)
                    .also { if (it != null) bump("no_cc") }
            val crewChange = ccRef?.let { crewChangesByKey["$it|${partnership.abbrev}"] }

            val effectiveFrom = ExtractTransforms.isoOrNull(r[15])
            val effectiveTo = ExtractTransforms.isoOrNull(r[17])
            var approvalFrom = ExtractTransforms.isoOrNull(r[24])
            var approvalTo = ExtractTransforms.isoOrNull(r[25])
            var outcome = sourceOutcome

            // §4.4 requires an approval window on every Approved outcome, and the engine treats a
            // windowless approval as never in effect (POC rule). Storing the outcome unrecorded —
            // with the status still Closed - Approved — preserves exactly that behaviour; a
            // fabricated window would silently exempt someone.
            if (outcome == RegisterOutcome.APPROVED &&
                (approvalFrom == null || approvalTo == null || approvalTo < approvalFrom)
            ) {
                outcome = null
                bump("approved_no_window")
                derivations.add(
                    "outcome Approved in the source but no usable approval window — imported without " +
                        "an outcome so the exemption is never applied",
                )
            }

            val raisedCandidates = listOf(r[3], r[4], r[5]).mapNotNull(ExtractTransforms::isoOrNull)
            val lodgement = ExtractTransforms.isoOrNull(r[19])
            var raised = raisedCandidates.minOrNull()
                ?: lodgement ?: effectiveFrom ?: closeout ?: approvalFrom ?: crewChange?.cutoffDate
            if (raised == null) {
                raised = LocalDate.of(2026, 1, 1)
                bump("no_raised")
                derivations.add("no date anywhere on the row — raised date is a sentinel")
            }

            val requirement = ExtractTransforms.normTitle(r[14]).ifEmpty { null }?.let { normalised ->
                (titleToCode[normalised] ?: ExtractTransforms.TITLE_ALIASES[normalised])
                    ?.let(requirementsByCode::getValue)
                    ?: run {
                        unmappedTitles.add(r[14])
                        bump("unmapped_title_rows")
                        null
                    }
            }
            val sam = r[10]
            if (sam.isEmpty()) bump("no_sam")

            val booking = ExtractTransforms.isoOrNull(r[18])
            val record = RegisterRecord().apply {
                this.recordId = recordId
                type = header.type
                person = personsBySam[sam]
                position = r[11].ifEmpty { null }?.let { position(it, "register $recordId") }
                this.requirement = requirement
                reqRaw = if (requirement == null) r[14].ifEmpty { null } else null
                this.partnership = partnership
                this.crewChange = crewChange
                this.effectiveFrom = effectiveFrom
                this.effectiveTo = effectiveTo
                raisedDate = raised
                status = header.status
                this.outcome = outcome
                this.approvalFrom = approvalFrom
                this.approvalTo = approvalTo
                bookingConfirmedDate = booking
                lodgementDate = lodgement
                stampCreated(actor, now)
            }
            em.persist(record)

            if (r[26].isNotEmpty()) {
                em.persist(
                    ApprovalCondition().apply {
                        registerRecord = record
                        type = au.crewcomp.workflow.ConditionType.OTHER
                        body = r[26]
                        stampCreated(actor, now)
                    },
                )
            }

            val noteBase = ((closeout ?: raised)).atStartOfDay(clock.zone).toInstant()
            var noteSeq = 0
            fun note(party: String, body: String) {
                em.persist(
                    RegisterNote().apply {
                        registerRecord = record
                        this.party = party
                        this.body = body
                        stampCreated(actor, noteBase.plusSeconds((noteSeq++).toLong()))
                    },
                )
            }
            listOf(r[20] to "OPS", r[21] to "PW", r[22] to "MRL", r[27] to "OPS")
                .forEach { (text, party) -> if (text.isNotEmpty()) note(party, text) }
            if (r[18].isNotEmpty() && booking == null) {
                note("OPS", "Booking confirmation (unparsed date): ${r[18]}")
            }

            var ordinal = 0
            fun trail(body: String, who: String, at: Instant) {
                em.persist(
                    RegisterAuditEntry().apply {
                        registerRecord = record
                        this.ordinal = ++ordinal
                        this.body = body
                        this.actor = who
                        occurredAt = at
                    },
                )
            }
            trail(
                "Record created (${header.type.wire}).",
                ExtractTransforms.partyOf(header.type.wire),
                raised.atStartOfDay(clock.zone).toInstant(),
            )
            if (sourceOutcome != null) {
                trail(
                    "Outcome recorded: ${sourceOutcome.wire}.",
                    "Workflow manager",
                    (closeout ?: raised).atStartOfDay(clock.zone).toInstant(),
                )
            }
            if (derivations.isNotEmpty()) {
                trail("Imported from the workbook register — ${derivations.joinToString("; ")}.", actor, now)
            }
            imported++
        }
        counts["register"] = imported

        // The aggregate anomaly flags, mirroring the POC's EX-054..EX-059.
        if (unmappedTitles.isNotEmpty()) {
            flag(
                "REVIEW", "register", "${unmappedTitles.size} legacy requirement titles",
                "Register rows reference pre-code-scheme requirement titles with no unambiguous catalogue " +
                    "mapping (${stats.getValue("unmapped_title_rows")} rows) — raw titles preserved on the " +
                    "records; confirm mappings: " + unmappedTitles.joinToString(" · "),
            )
        }
        fun aggregate(key: String, severity: String, issue: String) {
            val n = stats.getValue(key)
            if (n > 0) flag(severity, "register", "$n rows", issue)
        }
        aggregate("no_id", "DATAERR", "Register rows without an ID — placeholder IDs UNREC-01… assigned.")
        aggregate("no_status", "DATAERR", "Register rows without a status — derived from outcome/closeout, or open with the type's party.")
        aggregate("no_type", "DATAERR", "Register rows without a type — derived from the status's party (queries for PW/MRL, request for OPS).")
        aggregate("closed_no_outcome", "DATAERR", "Register rows closed by a party with no outcome recorded — imported as Closed - Admin Action with no outcome.")
        aggregate("no_cc", "DATAERR", "Register rows with a missing or invalid CC reference — derived from the record ID prefix where possible.")
        aggregate("approved_no_window", "WARN", "Approved exemptions without an approval window — imported with the outcome unrecorded, so the engine never applies them (POC rule).")
        aggregate("no_sam", "WARN", "Register rows without a Sam # — not linked to any person record.")
        aggregate("no_raised", "DATAERR", "Register rows with no usable date anywhere — raised date set to the 2026-01-01 sentinel.")
    }

    // ------------------------------------------------------------------ worklist and accounts

    private fun loadWorklist(rows: List<Map<String, String>>) {
        rows.forEach { r ->
            flag(r.getValue("severity"), r.getValue("area"), r.getValue("entity"), r.getValue("issue"))
        }
        flag(
            "INFO", "partnership", "all partnerships",
            "Vessel classes are not in the extracts — position-tier vs vessel-class semantics remain open (O-3).",
        )

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
     * Accounts are the one thing here the workbooks cannot supply. The five back-office accounts
     * are the same invented SEC-1b `local_test` rows the synthetic fixture creates (§9's fan-out
     * needs a row per role to address). Crew accounts are created for every active person — no
     * email, no identity linkage, `local_test` — so the crew app and the §9 scans work against
     * this dataset too; Masters get `vessel_master` scoped to their partnership (MOB-11).
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

        personsBySam.values
            .filter { it.status == PersonStatus.ACTIVE }
            .forEach { person ->
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
