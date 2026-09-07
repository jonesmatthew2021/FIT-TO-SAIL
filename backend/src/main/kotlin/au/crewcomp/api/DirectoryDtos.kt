package au.crewcomp.api

import au.crewcomp.people.Assignment
import au.crewcomp.people.LeaveRecord
import au.crewcomp.people.Person
import au.crewcomp.people.QualificationHolding
import au.crewcomp.platform.security.Actor
import au.crewcomp.reference.CrewChange
import au.crewcomp.reference.CrewPosition
import au.crewcomp.reference.Partnership
import au.crewcomp.reference.PositionSlot
import au.crewcomp.reference.Requirement
import au.crewcomp.reference.RequirementUsage
import java.time.LocalDate

/**
 * Session, reference-layer and people-layer representations.
 *
 * The same rule as [Dtos.kt] applies: enough denormalised labelling that a row renders without a
 * second call, Appendix A wire strings for anything enumerated, and no engine value classes on
 * the wire. Ids that a client will join against a cached lookup (requirement, position) stay as
 * ids — the catalogue is small, static and fetched once.
 */

data class SessionDto(
    val label: String,
    /** Appendix-A-adjacent: the §3 role wire values. UI gating from these is defence in depth. */
    val roles: List<String>,
    /** Set for crew members; `null` for back-office users with no Person record (§4.3). */
    val personId: Long?,
    /** Non-empty only for a Vessel Master (§3). */
    val partnershipIds: List<Long>,
    /**
     * The server's business date in the operating timezone (NFR-5 / O-11).
     *
     * The browser's clock is in the viewer's timezone and the browser has no idea what the
     * operating timezone is, so a client that computes "today" locally will disagree with every
     * server-side compliance answer for part of each day. Clients take today from here.
     */
    val today: LocalDate,
    /** True when an administrator has pinned [today] for testing or audit reconstruction (§1). */
    val dateOverridden: Boolean,
)

data class PartnershipDto(
    val id: Long,
    val abbrev: String,
    val name: String,
    val vesselClass: String?,
    /** The customer this operation is run for (COM-1); null until the office attaches one. */
    val customerId: Long?,
    /** The swing pattern (V14): swing k flies out on anchor + k × cycle; null when there is none. */
    val rosterAnchor: LocalDate?,
    val rosterCycleDays: Int?,
    val rosterAnchorCrew: String?,
)

/** The ship's swing pattern, set by the office. Nulls clear it. */
data class SetPatternRequest(
    val rosterAnchor: LocalDate? = null,
    val rosterCycleDays: Int? = null,
    val rosterAnchorCrew: String? = null,
)

/** The office's dates for one swing, typed over the pattern's. */
data class SetSwingDatesRequest(val from: LocalDate, val to: LocalDate)

/** Which crew a person sails with; null takes them off the rotation. */
data class SetRotationRequest(val rotation: String? = null)

/** What ensure-upcoming did: the swings now on the calendar, and who could not be rostered. */
data class UpcomingSwingsDto(
    val swings: List<CrewChangeDto>,
    /** People on the rotation with no free slot that takes their position, per swing. */
    val unrostered: List<UnrosteredDto>,
)

data class UnrosteredDto(val ccId: String, val personId: Long, val name: String, val position: String, val reason: String)

/** COM-1 — a client company, with the partnerships run for it. */
data class CustomerDto(
    val id: Long,
    val name: String,
    val shortName: String?,
    val contactName: String?,
    val contactEmail: String?,
    val contactPhone: String?,
    val notes: String?,
    /** `active` · `former`. */
    val status: String,
    val partnershipIds: List<Long>,
)

/** A vessel in the fleet, on the operation it sails for. */
data class VesselDto(val id: Long, val name: String, val kind: String, val partnershipId: Long)

data class AddVesselRequest(val name: String, val kind: String)

/** A new operation under a customer: the code the calendar and register key on, its name, its class. */
data class CreatePartnershipRequest(val abbrev: String, val name: String, val vesselClass: String? = null)

/** Create and update share a body; status is only read on update (a new customer is active). */
data class SaveCustomerRequest(
    val name: String,
    val shortName: String? = null,
    val contactName: String? = null,
    val contactEmail: String? = null,
    val contactPhone: String? = null,
    val notes: String? = null,
    val status: String = "active",
)

data class CrewChangeDto(
    val id: Long,
    val ccId: String,
    val partnershipId: Long,
    val from: LocalDate,
    val to: LocalDate,
    val cutoff: LocalDate,
    /** The crew the swing carries and its number in the ship's pattern (V14); null off-pattern. */
    val rotation: String?,
    val patternK: Int?,
)

data class RequirementDto(
    val id: Long,
    val code: String,
    val category: String,
    val title: String,
    val status: String,
    val issuingAuthority: String?,
    /** The validity period (V12): months where plain, the client's words where not. Presentation only. */
    val validityMonths: Int?,
    val validityText: String?,
)

/** The office's "add a crew member" (see PersonDirectoryService.create). */
data class CreatePersonRequest(
    /** `SURNAME, Given names` — the form every other crew record uses. */
    val name: String,
    val sam: String,
    val positionId: Long,
    val partnershipId: Long,
    val email: String? = null,
    /** 'A' or 'B' to put them on the ship's rotation; omitted, they are not on one. */
    val rotation: String? = null,
)

/**
 * ADM-6's shape: the catalogue entry plus the two things the editing screen needs and the
 * lightweight [RequirementDto] deliberately omits — the legacy aliases, and how load-bearing the
 * entry is. Kept separate so the crew app's sync payload does not carry either.
 */
data class RequirementDetailDto(
    val id: Long,
    val code: String,
    val category: String,
    val title: String,
    val status: String,
    val issuingAuthority: String?,
    val notes: String?,
    val aliases: List<RequirementAliasDto>,
    val usage: RequirementUsageDto,
)

data class RequirementAliasDto(val id: Long, val alias: String)

data class RequirementUsageDto(
    val holdings: Long,
    val requirementRules: Long,
    val quotaRules: Long,
    val conditionalRules: Long,
    val registerRecords: Long,
    val total: Long,
)

/** Create and update share a body. The code is set once and never changes — see `ReferenceService`. */
data class SaveRequirementRequest(
    /** Ignored on update: a catalogue code is a business key, not an editable label. */
    val code: String? = null,
    val category: String,
    val title: String,
    val issuingAuthority: String? = null,
    val notes: String? = null,
    val status: String = "active",
)

data class AddAliasRequest(val alias: String)

data class PositionDto(val id: Long, val name: String)

data class SlotDto(
    val id: Long,
    val ref: Int,
    val shift: String,
    val allowedPositionIds: List<Long>,
    val notes: String?,
)

data class PersonDto(
    val id: Long,
    val sam: String,
    val name: String,
    val positionId: Long,
    val positionName: String,
    val tier: String?,
    val partnershipId: Long,
    val partnershipAbbrev: String,
    val status: String,
    val email: String?,
    /** The crew the person sails with — 'A' or 'B' — or null when not on a rotation (V14). */
    val rotation: String?,
)

data class AssignmentDto(
    val id: Long,
    val personId: Long,
    val crewChangeId: Long,
    val ccId: String,
    val partnershipAbbrev: String,
    val slotRef: Int,
    val from: LocalDate,
    val to: LocalDate,
)

/**
 * ADM-2 assign request. [from]/[to] are omitted for a whole-swing assignment and set only for a
 * handover leg; the server defaults them to the swing window rather than making every caller
 * restate it.
 */
data class AssignRequest(
    val slotRef: Int,
    val personId: Long,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    /** Proceed despite an overlapping assignment or leave record — recorded in the audit event. */
    val acknowledgeClash: Boolean = false,
)

data class LeaveRecordDto(
    val id: Long,
    val personId: Long,
    val kind: String,
    val from: LocalDate,
    val to: LocalDate,
    val status: String,
)

// ---------------------------------------------------------------------------
// Mapping
// ---------------------------------------------------------------------------

fun Actor.toSessionDto(today: LocalDate, dateOverridden: Boolean) = SessionDto(
    label = label,
    roles = roles.map { it.wire }.sorted(),
    personId = personId,
    partnershipIds = partnershipIds.sorted(),
    today = today,
    dateOverridden = dateOverridden,
)

fun Partnership.toDto() = PartnershipDto(
    id = requiredId,
    abbrev = abbrev,
    name = name,
    vesselClass = vesselClass,
    customerId = customer?.requiredId,
    rosterAnchor = rosterAnchor,
    rosterCycleDays = rosterCycleDays,
    rosterAnchorCrew = rosterAnchorCrew,
)

fun CrewChange.toDto() = CrewChangeDto(
    id = requiredId,
    ccId = ccId,
    partnershipId = partnership.requiredId,
    from = fromDate,
    to = toDate,
    cutoff = cutoffDate,
    rotation = rotation,
    patternK = patternK,
)

fun Requirement.toDto() = RequirementDto(
    id = requiredId,
    code = code,
    category = category,
    title = title,
    status = status,
    issuingAuthority = issuingAuthority,
    validityMonths = validityMonths,
    validityText = validityText,
)

fun Requirement.toDetailDto(usage: RequirementUsage?) = RequirementDetailDto(
    id = requiredId,
    code = code,
    category = category,
    title = title,
    status = status,
    issuingAuthority = issuingAuthority,
    notes = notes,
    aliases = aliases.map { RequirementAliasDto(it.requiredId, it.alias) }.sortedBy { it.alias },
    // A requirement nobody has used yet has no row in any of the count queries. Zeros are the
    // right answer there, and the reason the field is not nullable.
    usage = (usage ?: RequirementUsage(requiredId, 0, 0, 0, 0, 0)).toDto(),
)

fun RequirementUsage.toDto() = RequirementUsageDto(
    holdings = holdings,
    requirementRules = requirementRules,
    quotaRules = quotaRules,
    conditionalRules = conditionalRules,
    registerRecords = registerRecords,
    total = total,
)

fun CrewPosition.toDto() = PositionDto(id = requiredId, name = name)

fun PositionSlot.toDto() = SlotDto(
    id = requiredId,
    ref = ref,
    shift = shift.wire,
    allowedPositionIds = allowedPositions.map { it.requiredId }.sorted(),
    notes = notes,
)

fun Person.toDto() = PersonDto(
    id = requiredId,
    sam = sam,
    name = name,
    positionId = position.requiredId,
    positionName = position.name,
    tier = tier,
    partnershipId = partnership.requiredId,
    partnershipAbbrev = partnership.abbrev,
    status = status.wire,
    email = email,
    rotation = rotation,
)

fun QualificationHolding.toDto() = HoldingDto(
    id = requiredId,
    personId = person.requiredId,
    requirementId = requirement.requiredId,
    status = status.wire,
    expiry = expiryDate,
    issueDate = issueDate,
    note = note,
)

fun Assignment.toDto() = AssignmentDto(
    id = requiredId,
    personId = person.requiredId,
    crewChangeId = crewChange.requiredId,
    ccId = crewChange.ccId,
    partnershipAbbrev = partnership.abbrev,
    slotRef = slotRef,
    from = fromDate,
    to = toDate,
)

fun LeaveRecord.toDto() = LeaveRecordDto(
    id = requiredId,
    personId = person.requiredId,
    kind = kind,
    from = fromDate,
    to = toDate,
    status = status,
)
