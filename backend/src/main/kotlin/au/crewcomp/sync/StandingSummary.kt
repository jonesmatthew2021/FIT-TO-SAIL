package au.crewcomp.sync

import au.crewcomp.engine.CellState
import au.crewcomp.engine.PersonEvaluation

/**
 * MOB-0's headline sentence and readiness count, composed from the §5.2 evaluation.
 *
 * Both used to be derived on the device, and the derivation was where the crew app told its one
 * lie: `pending` and `expiring` both mapped to "You can sail this swing.", so *sending an
 * exemption request* read as being cleared — nothing had been granted and the certificate was
 * still lapsing. A compliance sentence is the engine's to phrase (AUTH-1's spirit), which is why
 * this lives on the server and travels in the sync payload, and why the copy below never
 * reassures past what the roll-up actually says.
 *
 * Pure on purpose, like the engine it summarises: values in, values out, testable without a
 * database.
 */
object StandingSummary {

    /**
     * Ready / total, excluding `na` from both halves — a requirement that does not apply is not
     * something to be ready for.
     *
     * `pending` is deliberately **not** ready: an unanswered request is not a granted one.
     * `exempt` is ready — the decision exists. `quota_only` and `recommended` are ready because
     * neither is individually mandatory (§5.1).
     */
    fun readiness(evaluation: PersonEvaluation): Readiness {
        val applicable = evaluation.cells.filter { it.state != CellState.NA }
        val ready = applicable.count { it.state in READY_STATES }
        return Readiness(ready = ready, total = applicable.size)
    }

    /** The one sentence at the top of MOB-0, keyed on the roll-up and count-accurate. */
    fun headline(evaluation: PersonEvaluation): String {
        val cells = evaluation.cells
        fun count(vararg states: CellState) = cells.count { it.state in states }

        return when (evaluation.rollUp) {
            CellState.GAP -> when (val n = count(CellState.GAP)) {
                1 -> "Something is missing for this swing."
                else -> "$n things are missing for this swing."
            }

            CellState.EXPIRING -> when (count(CellState.EXPIRING)) {
                1 -> "A certificate lapses before this swing ends."
                else -> "${count(CellState.EXPIRING)} certificates lapse before this swing ends."
            }

            // An ask is not an answer: the request is with the office and nothing is granted yet.
            CellState.PENDING -> when (count(CellState.PENDING)) {
                1 -> "A request is with the office — not yet granted."
                else -> "${count(CellState.PENDING)} requests are with the office — not yet granted."
            }

            CellState.UNKNOWN, CellState.REVIEW -> when (val n = count(CellState.UNKNOWN, CellState.REVIEW)) {
                1 -> "Almost — one thing to confirm."
                else -> "Almost — $n things to confirm."
            }

            // A granted exemption is a decision, and saying so beats implying the certificate exists.
            CellState.EXEMPT -> "You can sail this swing — an exemption covers you."

            CellState.OK, CellState.QUOTA_ONLY, CellState.RECOMMENDED, CellState.NA ->
                "You can sail this swing."
        }
    }

    private val READY_STATES = setOf(
        CellState.OK, CellState.EXEMPT, CellState.QUOTA_ONLY, CellState.RECOMMENDED,
    )
}

data class Readiness(val ready: Int, val total: Int)
