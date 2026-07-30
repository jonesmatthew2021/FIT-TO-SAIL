package au.crewcomp.platform.dev

import au.crewcomp.engine.RegisterOutcome
import au.crewcomp.workflow.RegisterStatus
import au.crewcomp.workflow.RegisterType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The POC's fix-up rules (§11), ported verbatim from `build_real_seed.py` as pure functions so
 * they are unit-testable without a database. Every rule here exists because the source workbooks
 * actually contain the anomaly; none of them may "improve" the data, only normalise it far enough
 * to store, with the anomaly flagged (§11: preserved and flagged, never silently cleaned).
 */
internal object ExtractTransforms {

    /** The Sam-number format. Rows failing it in `persons.csv` are extraction artifacts. */
    val SAM_FORMAT = Regex("^[a-z]\\d{5}[A-Z]$")

    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** Long-form artifact seen in the register, e.g. "Friday, 23 January 2026 11:37 AM". */
    private val LONG_FORM = Regex("^\\w+, (\\d{1,2}) (\\w+) (\\d{4})")
    private val LONG_FORM_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)

    /** ISO date, the workbook's long-form date, or null — never a guess. */
    fun isoOrNull(value: String?): LocalDate? {
        val v = value?.trim().orEmpty()
        if (ISO_DATE.matches(v)) return LocalDate.parse(v)
        val m = LONG_FORM.find(v) ?: return null
        return try {
            LocalDate.parse("${m.groupValues[1]} ${m.groupValues[2]} ${m.groupValues[3]}", LONG_FORM_FORMAT)
        } catch (_: java.time.format.DateTimeParseException) {
            null
        }
    }

    /** `"Chief Officer - Unlimited"` → position + tier. Anything else is a plain position. */
    fun splitTier(raw: String): Pair<String, String?> {
        val m = Regex("^(.*?) - (Unlimited|100m)$").find(raw.trim()) ?: return raw.trim() to null
        return m.groupValues[1] to m.groupValues[2]
    }

    /** Title normalisation for legacy register titles: case, dash and apostrophe variants. */
    fun normTitle(title: String): String = title.trim().lowercase()
        .replace('—', '-').replace('–', '-').replace('’', '\'')
        .replace(Regex("\\s+"), " ")

    /**
     * Legacy pre-code-scheme register titles → catalogue codes. Only the unambiguous mappings the
     * POC review confirmed; everything else keeps its raw title (`req_raw`) and is flagged.
     * These also become [au.crewcomp.reference.RequirementAlias] rows, so the evidence pipeline's
     * title matching learns them too.
     */
    val TITLE_ALIASES = mapOf(
        "ecdis-stcw reg ii/1 & ii/2" to "QL-13",
        "gmdss-stcw reg iv/2" to "QL-14",
        "enter and work in confined spaces - riiwhs202e" to "PT-02",
        "work safely at heights - riiwhs204e" to "PT-03",
        "proficiency in fast rescue boats-stcw regulation vi/2 code a -vi/2 table a-vi/2-2" to "QL-16",
        "lms - minres - mooring operations awareness - onslow" to "PS-02",
        "passing in the lng swing basin - onboard" to "MS-06",
        "helm crew - basic & crew jobs" to "VS-04",
    )

    /** The party a register type or status string names: PW, MRL, or the partnership (OPS). */
    fun partyOf(text: String): String = when {
        text.contains("PW") -> "PW"
        text.contains("MRL") -> "MRL"
        else -> "OPS"
    }

    // ------------------------------------------------------------------ matrix slot collapse

    data class MatrixRuleRow(val slotRef: Int, val position: String, val requirementCode: String, val level: String)

    data class CollapsedRules(
        /** (position, requirement code) → level, requirements in catalogue order per position. */
        val levels: LinkedHashMap<Pair<String, String>, String>,
        /** Human-readable slot-level divergences, for the exceptions worklist. */
        val disagreements: List<String>,
    )

    /** Strictest-first union order when slots of one position disagree on a level. */
    private val LEVEL_PRIORITY = listOf("M", "M1", "M2", "M3", "M4", "M5", "M6", "M7", "R")

    /** The Chief Officer CoC cells behave as a one-of set in the workbook (footnote M8). */
    val COC_ONE_OF = mapOf("Chief Officer" to listOf("QL-01", "QL-02", "QL-03"))

    /**
     * The central import artifact: rules are per-slot in the workbook and per-position in the
     * system, so slots collapse (union, strictest level wins) and every divergence is flagged.
     * The diverging Chief Officer CoC cells are then overwritten as one-of set M8 — QL-01
     * included because Master supersedes Chief Mate.
     */
    fun collapseSlots(rows: List<MatrixRuleRow>, catalogueOrder: Map<String, Int>): CollapsedRules {
        val byPosition = LinkedHashMap<String, MutableMap<Int, MutableMap<String, String>>>()
        rows.forEach { r ->
            byPosition.getOrPut(r.position) { mutableMapOf() }
                .getOrPut(r.slotRef) { mutableMapOf() }[r.requirementCode] = r.level
        }

        val levels = LinkedHashMap<Pair<String, String>, String>()
        val disagreements = mutableListOf<String>()
        byPosition.forEach { (position, slotMap) ->
            val requirements = slotMap.values.flatMap { it.keys }.distinct()
                .sortedBy { catalogueOrder[it] ?: Int.MAX_VALUE }
            val merged = LinkedHashMap<String, String>()
            requirements.forEach { req ->
                val perSlot = slotMap.mapValues { (_, byReq) -> byReq[req] }
                val distinct = perSlot.values.filterNotNull().filter { it.isNotBlank() }.toSet()
                val level = if (distinct.size == 1) {
                    distinct.first()
                } else {
                    LEVEL_PRIORITY.firstOrNull { it in distinct } ?: distinct.sorted().first()
                }
                if (perSlot.values.toSet().size > 1) {
                    disagreements.add(
                        "$position $req: " + perSlot.entries.sortedBy { it.key }
                            .joinToString(", ") { (slot, l) -> "slot $slot=${l?.ifBlank { "—" } ?: "—"}" },
                    )
                }
                merged[req] = level
            }
            COC_ONE_OF[position]?.forEach { req -> merged[req] = "M8" }
            merged.forEach { (req, level) -> levels[position to req] = level }
        }
        return CollapsedRules(levels, disagreements)
    }

    // ------------------------------------------------------------------ register status / type

    data class DerivedHeader(
        val type: RegisterType,
        val typeDerived: Boolean,
        val status: RegisterStatus,
        val statusDerived: Boolean,
        /** The source said "Closed - {party}" (or closeout only) with no outcome recorded. */
        val closedWithoutOutcome: Boolean,
    )

    private val CLOSED_BY_PARTY = Regex("^Closed - (PW|MRL|OPS)$")

    /**
     * Appendix A's status vocabulary differs from the raw workbook's in three ways, and each is
     * derived rather than invented:
     *
     *  - `Open UNI` / `Closed UNI` / `- UNI` are the partnership acting as vessel operator → OPS.
     *  - A missing status is derived from the outcome ("Closed - {outcome}"), from a closeout
     *    date alone (closed, no outcome — see below), or defaults to open with the type's party.
     *  - `Closed - {party}` records the closing party, not the outcome. Where the outcome column
     *    has one, the two combine losslessly; where it does not, the closest Appendix A status is
     *    `Closed - Admin Action` with **no outcome stored**, and the row is flagged — inventing
     *    an outcome for a legal record would be worse than an imprecise status label.
     */
    fun deriveHeader(
        rawStatus: String,
        rawType: String,
        outcome: RegisterOutcome?,
        hasCloseout: Boolean,
    ): DerivedHeader {
        val statusText = rawStatus.trim()
            .replace("Closed UNI", "Closed - OPS")
            .replace("Open UNI", "Open - OPS")
        val typeText = rawType.trim().replace("- UNI", "- OPS")

        var statusDerived = false
        var closedWithoutOutcome = false

        fun openByParty(party: String): RegisterStatus = when (party) {
            "PW" -> RegisterStatus.OPEN_PW
            "MRL" -> RegisterStatus.OPEN_MRL
            else -> RegisterStatus.OPEN_OPS
        }

        fun derived(): RegisterStatus {
            statusDerived = true
            return when {
                outcome != null -> RegisterStatus.closedWith(outcome)
                hasCloseout -> {
                    closedWithoutOutcome = true
                    RegisterStatus.CLOSED_ADMIN_ACTION
                }
                else -> openByParty(partyOf(typeText))
            }
        }

        val status = when {
            statusText.isEmpty() -> derived()
            CLOSED_BY_PARTY.matches(statusText) ->
                if (outcome != null) {
                    RegisterStatus.closedWith(outcome)
                } else {
                    closedWithoutOutcome = true
                    RegisterStatus.CLOSED_ADMIN_ACTION
                }
            else -> runCatching { RegisterStatus.fromWire(statusText) }.getOrElse { derived() }
        }

        var typeDerived = false
        val type = if (typeText.isEmpty()) {
            typeDerived = true
            // No type recorded. A query is the weaker claim than an exemption request, so PW and
            // MRL rows become queries; there is no OPS query type, so OPS rows become requests.
            when (partyOf(status.wire)) {
                "PW" -> RegisterType.PW_QUERY
                "MRL" -> RegisterType.MRL_QUERY
                else -> RegisterType.EXEMPTION_REQUEST_OPS
            }
        } else {
            runCatching { RegisterType.fromWire(typeText) }.getOrElse {
                typeDerived = true
                RegisterType.EXEMPTION_REQUEST_OPS
            }
        }

        return DerivedHeader(type, typeDerived, status, statusDerived, closedWithoutOutcome)
    }
}
