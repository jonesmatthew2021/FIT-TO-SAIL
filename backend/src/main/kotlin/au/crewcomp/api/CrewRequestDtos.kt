package au.crewcomp.api

import au.crewcomp.people.CrewStatement
import java.time.Instant
import java.time.LocalDate

/**
 * ADM-11 — the crew request queue.
 *
 * Denormalised enough that a queue row renders without a second call, in the same way ADM-7's and
 * ADM-9's rows do: the coordinator is triaging a list, and a screen that fetched a person per row
 * would be sixty requests in flight to draw one page.
 *
 * Note what is **not** here. There is no cell state, no roll-up and no compliance verdict of any
 * kind, because a crew statement is not one (AUTH-1). The queue answers "who asked for what, and
 * has anyone dealt with it" — the person's actual standing is one click away on their page, where
 * the engine's answer lives.
 */
data class CrewRequestDto(
    val id: Long,
    /** `course_booked` | `help_requested` | `seat_requested` | `waitlisted`. */
    val kind: String,
    /** `open` | `actioned` | `dismissed`. Both non-open states are terminal. */
    val status: String,

    val personId: Long,
    val sam: String,
    val personName: String,
    val positionName: String,
    val partnershipAbbrev: String,

    val requirementId: Long,
    val code: String,
    val title: String,

    /**
     * The expiry the statement was about, as the holding stood when the crew member answered.
     *
     * Null when there was no expiring holding — a statement about a gap. It is on the wire because
     * it is the fact the coordinator is judging: "booked, and the certificate lapses on the 15th"
     * is a different request from "booked, and there is a year to run".
     */
    val aboutExpiry: LocalDate?,

    /**
     * MOB-8 only: the course date asked for, as one line.
     *
     * The server's rendering, stored when the request was made. It survives the option being
     * withdrawn from the catalogue, which is exactly the case where the coordinator most needs to
     * know which date the crew member meant — and it is why [subjectRef] is not enough on its own.
     */
    val subjectLabel: String?,
    /** The catalogue key, for a coordinator who needs to find the course itself. */
    val subjectRef: String?,

    /** When the crew member tapped, and who they are as the audit trail records them. */
    val raisedAt: Instant,

    val decisionNote: String?,
    val decidedAt: Instant?,
    val decidedBy: String?,
)

/**
 * The note is required by the service, not merely conventional: a queue emptied with no explanation
 * is indistinguishable from one emptied to clear the badge.
 */
data class DecideCrewRequestRequest(val note: String)

/**
 * The badge count, on its own endpoint for the same reason `NotificationSummaryDto` is: the badge
 * is polled and the list is not, and a count query is a great deal cheaper than the rows.
 */
data class CrewRequestSummaryDto(val open: Long)

fun CrewStatement.toDto() = CrewRequestDto(
    id = requiredId,
    kind = kind.wire,
    status = status.wire,
    personId = person.requiredId,
    sam = person.sam,
    personName = person.name,
    positionName = person.position.name,
    partnershipAbbrev = person.partnership.abbrev,
    requirementId = requirement.requiredId,
    code = requirement.code,
    title = requirement.title,
    aboutExpiry = aboutExpiry,
    subjectLabel = subjectLabel,
    subjectRef = subjectRef,
    raisedAt = createdAt,
    decisionNote = decisionNote,
    decidedAt = decidedAt,
    decidedBy = decidedBy,
)
