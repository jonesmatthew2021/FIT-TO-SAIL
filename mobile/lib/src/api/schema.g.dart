// GENERATED FILE — DO NOT EDIT.
//
// Produced by `dart run tool/generate_api.dart` from the backend's OpenAPI
// schema (DEV-2). Committed so the app builds without a JDK; `--check` in CI
// fails the build when the backend contract has moved and this has not.
//
// LocalDate is String on purpose — see the generator for why.

// ignore_for_file: unnecessary_cast, prefer_const_constructors

class AssignmentDto {
  final int id;
  final int personId;
  final int crewChangeId;
  final String ccId;
  final String partnershipAbbrev;
  final int slotRef;
  final String from;
  final String to;

  const AssignmentDto({
    required this.id,
    required this.personId,
    required this.crewChangeId,
    required this.ccId,
    required this.partnershipAbbrev,
    required this.slotRef,
    required this.from,
    required this.to,
  });

  factory AssignmentDto.fromJson(Map<String, dynamic> json) => AssignmentDto(
        id: json['id'] as int,
        personId: json['personId'] as int,
        crewChangeId: json['crewChangeId'] as int,
        ccId: json['ccId'] as String,
        partnershipAbbrev: json['partnershipAbbrev'] as String,
        slotRef: json['slotRef'] as int,
        from: json['from'] as String,
        to: json['to'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'personId': personId,
        'crewChangeId': crewChangeId,
        'ccId': ccId,
        'partnershipAbbrev': partnershipAbbrev,
        'slotRef': slotRef,
        'from': from,
        'to': to,
      };
}

class AssignmentEvaluationDto {
  final int assignmentId;
  final int personId;
  final String sam;
  final String name;
  final int slotRef;
  final String from;
  final String to;
  final PersonEvaluationDto evaluation;

  const AssignmentEvaluationDto({
    required this.assignmentId,
    required this.personId,
    required this.sam,
    required this.name,
    required this.slotRef,
    required this.from,
    required this.to,
    required this.evaluation,
  });

  factory AssignmentEvaluationDto.fromJson(Map<String, dynamic> json) => AssignmentEvaluationDto(
        assignmentId: json['assignmentId'] as int,
        personId: json['personId'] as int,
        sam: json['sam'] as String,
        name: json['name'] as String,
        slotRef: json['slotRef'] as int,
        from: json['from'] as String,
        to: json['to'] as String,
        evaluation: PersonEvaluationDto.fromJson(json['evaluation'] as Map<String, dynamic>),
      );

  Map<String, dynamic> toJson() => {
        'assignmentId': assignmentId,
        'personId': personId,
        'sam': sam,
        'name': name,
        'slotRef': slotRef,
        'from': from,
        'to': to,
        'evaluation': evaluation.toJson(),
      };
}

class CellDto {
  final int requirementId;
  final String level;
  final String state;
  final String? expiry;
  final List<String> notes;
  final String? registerRecordId;

  const CellDto({
    required this.requirementId,
    required this.level,
    required this.state,
    this.expiry,
    required this.notes,
    this.registerRecordId,
  });

  factory CellDto.fromJson(Map<String, dynamic> json) => CellDto(
        requirementId: json['requirementId'] as int,
        level: json['level'] as String,
        state: json['state'] as String,
        expiry: json['expiry'] == null ? null : json['expiry'] as String,
        notes: (json['notes'] as List<dynamic>).map((e) => (e as String)).toList(growable: false),
        registerRecordId: json['registerRecordId'] == null ? null : json['registerRecordId'] as String,
      );

  Map<String, dynamic> toJson() => {
        'requirementId': requirementId,
        'level': level,
        'state': state,
        'expiry': expiry,
        'notes': notes,
        'registerRecordId': registerRecordId,
      };
}

class CrewChangeDto {
  final int id;
  final String ccId;
  final int partnershipId;
  final String from;
  final String to;
  final String cutoff;

  const CrewChangeDto({
    required this.id,
    required this.ccId,
    required this.partnershipId,
    required this.from,
    required this.to,
    required this.cutoff,
  });

  factory CrewChangeDto.fromJson(Map<String, dynamic> json) => CrewChangeDto(
        id: json['id'] as int,
        ccId: json['ccId'] as String,
        partnershipId: json['partnershipId'] as int,
        from: json['from'] as String,
        to: json['to'] as String,
        cutoff: json['cutoff'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'ccId': ccId,
        'partnershipId': partnershipId,
        'from': from,
        'to': to,
        'cutoff': cutoff,
      };
}

class EvidenceSubmissionDto {
  final String publicId;
  final int? requirementHintId;
  final String source;
  final String? contentType;
  final int? declaredSize;
  final int uploadOffset;
  final bool uploadComplete;
  final String verificationStatus;
  final String? rejectionReason;
  final DateTime submittedAt;

  const EvidenceSubmissionDto({
    required this.publicId,
    this.requirementHintId,
    required this.source,
    this.contentType,
    this.declaredSize,
    required this.uploadOffset,
    required this.uploadComplete,
    required this.verificationStatus,
    this.rejectionReason,
    required this.submittedAt,
  });

  factory EvidenceSubmissionDto.fromJson(Map<String, dynamic> json) => EvidenceSubmissionDto(
        publicId: json['publicId'] as String,
        requirementHintId: json['requirementHintId'] == null ? null : json['requirementHintId'] as int,
        source: json['source'] as String,
        contentType: json['contentType'] == null ? null : json['contentType'] as String,
        declaredSize: json['declaredSize'] == null ? null : json['declaredSize'] as int,
        uploadOffset: json['uploadOffset'] as int,
        uploadComplete: json['uploadComplete'] as bool,
        verificationStatus: json['verificationStatus'] as String,
        rejectionReason: json['rejectionReason'] == null ? null : json['rejectionReason'] as String,
        submittedAt: DateTime.parse(json['submittedAt'] as String).toUtc(),
      );

  Map<String, dynamic> toJson() => {
        'publicId': publicId,
        'requirementHintId': requirementHintId,
        'source': source,
        'contentType': contentType,
        'declaredSize': declaredSize,
        'uploadOffset': uploadOffset,
        'uploadComplete': uploadComplete,
        'verificationStatus': verificationStatus,
        'rejectionReason': rejectionReason,
        'submittedAt': submittedAt.toIso8601String(),
      };
}

class EvidenceSubmitDto {
  final String publicId;
  final String source;
  final String? contentType;
  final int? declaredSize;
  final String? declaredSha256;
  final int? requirementHintId;

  const EvidenceSubmitDto({
    required this.publicId,
    required this.source,
    this.contentType,
    this.declaredSize,
    this.declaredSha256,
    this.requirementHintId,
  });

  factory EvidenceSubmitDto.fromJson(Map<String, dynamic> json) => EvidenceSubmitDto(
        publicId: json['publicId'] as String,
        source: json['source'] as String,
        contentType: json['contentType'] == null ? null : json['contentType'] as String,
        declaredSize: json['declaredSize'] == null ? null : json['declaredSize'] as int,
        declaredSha256: json['declaredSha256'] == null ? null : json['declaredSha256'] as String,
        requirementHintId: json['requirementHintId'] == null ? null : json['requirementHintId'] as int,
      );

  Map<String, dynamic> toJson() => {
        'publicId': publicId,
        'source': source,
        'contentType': contentType,
        'declaredSize': declaredSize,
        'declaredSha256': declaredSha256,
        'requirementHintId': requirementHintId,
      };
}

class ExpiryAlertDto {
  final int personId;
  final String sam;
  final String name;
  final int requirementId;
  final String expiry;
  final int daysRemaining;
  final String impact;

  const ExpiryAlertDto({
    required this.personId,
    required this.sam,
    required this.name,
    required this.requirementId,
    required this.expiry,
    required this.daysRemaining,
    required this.impact,
  });

  factory ExpiryAlertDto.fromJson(Map<String, dynamic> json) => ExpiryAlertDto(
        personId: json['personId'] as int,
        sam: json['sam'] as String,
        name: json['name'] as String,
        requirementId: json['requirementId'] as int,
        expiry: json['expiry'] as String,
        daysRemaining: json['daysRemaining'] as int,
        impact: json['impact'] as String,
      );

  Map<String, dynamic> toJson() => {
        'personId': personId,
        'sam': sam,
        'name': name,
        'requirementId': requirementId,
        'expiry': expiry,
        'daysRemaining': daysRemaining,
        'impact': impact,
      };
}

class GapReportRowDto {
  final int personId;
  final String sam;
  final String name;
  final int slotRef;
  final int assignmentId;
  final int requirementId;
  final String level;
  final String state;
  final String? expiry;
  final String? registerRecordId;
  final List<String> notes;

  const GapReportRowDto({
    required this.personId,
    required this.sam,
    required this.name,
    required this.slotRef,
    required this.assignmentId,
    required this.requirementId,
    required this.level,
    required this.state,
    this.expiry,
    this.registerRecordId,
    required this.notes,
  });

  factory GapReportRowDto.fromJson(Map<String, dynamic> json) => GapReportRowDto(
        personId: json['personId'] as int,
        sam: json['sam'] as String,
        name: json['name'] as String,
        slotRef: json['slotRef'] as int,
        assignmentId: json['assignmentId'] as int,
        requirementId: json['requirementId'] as int,
        level: json['level'] as String,
        state: json['state'] as String,
        expiry: json['expiry'] == null ? null : json['expiry'] as String,
        registerRecordId: json['registerRecordId'] == null ? null : json['registerRecordId'] as String,
        notes: (json['notes'] as List<dynamic>).map((e) => (e as String)).toList(growable: false),
      );

  Map<String, dynamic> toJson() => {
        'personId': personId,
        'sam': sam,
        'name': name,
        'slotRef': slotRef,
        'assignmentId': assignmentId,
        'requirementId': requirementId,
        'level': level,
        'state': state,
        'expiry': expiry,
        'registerRecordId': registerRecordId,
        'notes': notes,
      };
}

class HoldingDto {
  final int id;
  final int personId;
  final int requirementId;
  final String status;
  final String? expiry;
  final String? issueDate;
  final String? note;

  const HoldingDto({
    required this.id,
    required this.personId,
    required this.requirementId,
    required this.status,
    this.expiry,
    this.issueDate,
    this.note,
  });

  factory HoldingDto.fromJson(Map<String, dynamic> json) => HoldingDto(
        id: json['id'] as int,
        personId: json['personId'] as int,
        requirementId: json['requirementId'] as int,
        status: json['status'] as String,
        expiry: json['expiry'] == null ? null : json['expiry'] as String,
        issueDate: json['issueDate'] == null ? null : json['issueDate'] as String,
        note: json['note'] == null ? null : json['note'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'personId': personId,
        'requirementId': requirementId,
        'status': status,
        'expiry': expiry,
        'issueDate': issueDate,
        'note': note,
      };
}

class LeaveRecordDto {
  final int id;
  final int personId;
  final String kind;
  final String from;
  final String to;
  final String status;

  const LeaveRecordDto({
    required this.id,
    required this.personId,
    required this.kind,
    required this.from,
    required this.to,
    required this.status,
  });

  factory LeaveRecordDto.fromJson(Map<String, dynamic> json) => LeaveRecordDto(
        id: json['id'] as int,
        personId: json['personId'] as int,
        kind: json['kind'] as String,
        from: json['from'] as String,
        to: json['to'] as String,
        status: json['status'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'personId': personId,
        'kind': kind,
        'from': from,
        'to': to,
        'status': status,
      };
}

class NotificationDto {
  final int id;
  final String kind;
  final String title;
  final String? body;
  final String? deepLink;
  final DateTime createdAt;
  final DateTime? readAt;

  const NotificationDto({
    required this.id,
    required this.kind,
    required this.title,
    this.body,
    this.deepLink,
    required this.createdAt,
    this.readAt,
  });

  factory NotificationDto.fromJson(Map<String, dynamic> json) => NotificationDto(
        id: json['id'] as int,
        kind: json['kind'] as String,
        title: json['title'] as String,
        body: json['body'] == null ? null : json['body'] as String,
        deepLink: json['deepLink'] == null ? null : json['deepLink'] as String,
        createdAt: DateTime.parse(json['createdAt'] as String).toUtc(),
        readAt: json['readAt'] == null ? null : DateTime.parse(json['readAt'] as String).toUtc(),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'kind': kind,
        'title': title,
        'body': body,
        'deepLink': deepLink,
        'createdAt': createdAt.toIso8601String(),
        'readAt': readAt?.toIso8601String(),
      };
}

class OpenSlotDto {
  final int ref;
  final String shift;
  final List<int> allowedPositionIds;

  const OpenSlotDto({
    required this.ref,
    required this.shift,
    required this.allowedPositionIds,
  });

  factory OpenSlotDto.fromJson(Map<String, dynamic> json) => OpenSlotDto(
        ref: json['ref'] as int,
        shift: json['shift'] as String,
        allowedPositionIds: (json['allowedPositionIds'] as List<dynamic>).map((e) => (e as int)).toList(growable: false),
      );

  Map<String, dynamic> toJson() => {
        'ref': ref,
        'shift': shift,
        'allowedPositionIds': allowedPositionIds,
      };
}

class PartnershipDto {
  final int id;
  final String abbrev;
  final String name;
  final String? vesselClass;

  const PartnershipDto({
    required this.id,
    required this.abbrev,
    required this.name,
    this.vesselClass,
  });

  factory PartnershipDto.fromJson(Map<String, dynamic> json) => PartnershipDto(
        id: json['id'] as int,
        abbrev: json['abbrev'] as String,
        name: json['name'] as String,
        vesselClass: json['vesselClass'] == null ? null : json['vesselClass'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'abbrev': abbrev,
        'name': name,
        'vesselClass': vesselClass,
      };
}

class PersonDto {
  final int id;
  final String sam;
  final String name;
  final int positionId;
  final String positionName;
  final String? tier;
  final int partnershipId;
  final String partnershipAbbrev;
  final String status;
  final String? email;

  const PersonDto({
    required this.id,
    required this.sam,
    required this.name,
    required this.positionId,
    required this.positionName,
    this.tier,
    required this.partnershipId,
    required this.partnershipAbbrev,
    required this.status,
    this.email,
  });

  factory PersonDto.fromJson(Map<String, dynamic> json) => PersonDto(
        id: json['id'] as int,
        sam: json['sam'] as String,
        name: json['name'] as String,
        positionId: json['positionId'] as int,
        positionName: json['positionName'] as String,
        tier: json['tier'] == null ? null : json['tier'] as String,
        partnershipId: json['partnershipId'] as int,
        partnershipAbbrev: json['partnershipAbbrev'] as String,
        status: json['status'] as String,
        email: json['email'] == null ? null : json['email'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'sam': sam,
        'name': name,
        'positionId': positionId,
        'positionName': positionName,
        'tier': tier,
        'partnershipId': partnershipId,
        'partnershipAbbrev': partnershipAbbrev,
        'status': status,
        'email': email,
      };
}

class PersonEvaluationDto {
  final int personId;
  final String rollUp;
  final List<CellDto> cells;

  const PersonEvaluationDto({
    required this.personId,
    required this.rollUp,
    required this.cells,
  });

  factory PersonEvaluationDto.fromJson(Map<String, dynamic> json) => PersonEvaluationDto(
        personId: json['personId'] as int,
        rollUp: json['rollUp'] as String,
        cells: (json['cells'] as List<dynamic>).map((e) => CellDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
      );

  Map<String, dynamic> toJson() => {
        'personId': personId,
        'rollUp': rollUp,
        'cells': cells.map((e) => e.toJson()).toList(growable: false),
      };
}

class PositionDto {
  final int id;
  final String name;

  const PositionDto({
    required this.id,
    required this.name,
  });

  factory PositionDto.fromJson(Map<String, dynamic> json) => PositionDto(
        id: json['id'] as int,
        name: json['name'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
      };
}

class QuotaDto {
  final String footnote;
  final int requirementId;
  final String scope;
  final String? shift;
  final int min;
  final int actual;
  final bool satisfied;
  final int shortfall;

  const QuotaDto({
    required this.footnote,
    required this.requirementId,
    required this.scope,
    this.shift,
    required this.min,
    required this.actual,
    required this.satisfied,
    required this.shortfall,
  });

  factory QuotaDto.fromJson(Map<String, dynamic> json) => QuotaDto(
        footnote: json['footnote'] as String,
        requirementId: json['requirementId'] as int,
        scope: json['scope'] as String,
        shift: json['shift'] == null ? null : json['shift'] as String,
        min: json['min'] as int,
        actual: json['actual'] as int,
        satisfied: json['satisfied'] as bool,
        shortfall: json['shortfall'] as int,
      );

  Map<String, dynamic> toJson() => {
        'footnote': footnote,
        'requirementId': requirementId,
        'scope': scope,
        'shift': shift,
        'min': min,
        'actual': actual,
        'satisfied': satisfied,
        'shortfall': shortfall,
      };
}

class RequirementDto {
  final int id;
  final String code;
  final String category;
  final String title;
  final String status;
  final String? issuingAuthority;

  const RequirementDto({
    required this.id,
    required this.code,
    required this.category,
    required this.title,
    required this.status,
    this.issuingAuthority,
  });

  factory RequirementDto.fromJson(Map<String, dynamic> json) => RequirementDto(
        id: json['id'] as int,
        code: json['code'] as String,
        category: json['category'] as String,
        title: json['title'] as String,
        status: json['status'] as String,
        issuingAuthority: json['issuingAuthority'] == null ? null : json['issuingAuthority'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'code': code,
        'category': category,
        'title': title,
        'status': status,
        'issuingAuthority': issuingAuthority,
      };
}

class SessionDto {
  final String label;
  final List<String> roles;
  final int? personId;
  final List<int> partnershipIds;
  final String today;
  final bool dateOverridden;

  const SessionDto({
    required this.label,
    required this.roles,
    this.personId,
    required this.partnershipIds,
    required this.today,
    required this.dateOverridden,
  });

  factory SessionDto.fromJson(Map<String, dynamic> json) => SessionDto(
        label: json['label'] as String,
        roles: (json['roles'] as List<dynamic>).map((e) => (e as String)).toList(growable: false),
        personId: json['personId'] == null ? null : json['personId'] as int,
        partnershipIds: (json['partnershipIds'] as List<dynamic>).map((e) => (e as int)).toList(growable: false),
        today: json['today'] as String,
        dateOverridden: json['dateOverridden'] as bool,
      );

  Map<String, dynamic> toJson() => {
        'label': label,
        'roles': roles,
        'personId': personId,
        'partnershipIds': partnershipIds,
        'today': today,
        'dateOverridden': dateOverridden,
      };
}

class SetHoldingRequest {
  final String status;
  final String? expiry;
  final String? issueDate;
  final String? note;

  const SetHoldingRequest({
    required this.status,
    this.expiry,
    this.issueDate,
    this.note,
  });

  factory SetHoldingRequest.fromJson(Map<String, dynamic> json) => SetHoldingRequest(
        status: json['status'] as String,
        expiry: json['expiry'] == null ? null : json['expiry'] as String,
        issueDate: json['issueDate'] == null ? null : json['issueDate'] as String,
        note: json['note'] == null ? null : json['note'] as String,
      );

  Map<String, dynamic> toJson() => {
        'status': status,
        'expiry': expiry,
        'issueDate': issueDate,
        'note': note,
      };
}

class SlotDto {
  final int id;
  final int ref;
  final String shift;
  final List<int> allowedPositionIds;
  final String? notes;

  const SlotDto({
    required this.id,
    required this.ref,
    required this.shift,
    required this.allowedPositionIds,
    this.notes,
  });

  factory SlotDto.fromJson(Map<String, dynamic> json) => SlotDto(
        id: json['id'] as int,
        ref: json['ref'] as int,
        shift: json['shift'] as String,
        allowedPositionIds: (json['allowedPositionIds'] as List<dynamic>).map((e) => (e as int)).toList(growable: false),
        notes: json['notes'] == null ? null : json['notes'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'ref': ref,
        'shift': shift,
        'allowedPositionIds': allowedPositionIds,
        'notes': notes,
      };
}

class SuggestionDto {
  final int personId;
  final String sam;
  final String name;
  final int score;
  final int gapCount;
  final int unknownCount;
  final int expiringCount;
  final bool crossPartnership;
  final bool clash;
  final List<String> reasons;

  const SuggestionDto({
    required this.personId,
    required this.sam,
    required this.name,
    required this.score,
    required this.gapCount,
    required this.unknownCount,
    required this.expiringCount,
    required this.crossPartnership,
    required this.clash,
    required this.reasons,
  });

  factory SuggestionDto.fromJson(Map<String, dynamic> json) => SuggestionDto(
        personId: json['personId'] as int,
        sam: json['sam'] as String,
        name: json['name'] as String,
        score: json['score'] as int,
        gapCount: json['gapCount'] as int,
        unknownCount: json['unknownCount'] as int,
        expiringCount: json['expiringCount'] as int,
        crossPartnership: json['crossPartnership'] as bool,
        clash: json['clash'] as bool,
        reasons: (json['reasons'] as List<dynamic>).map((e) => (e as String)).toList(growable: false),
      );

  Map<String, dynamic> toJson() => {
        'personId': personId,
        'sam': sam,
        'name': name,
        'score': score,
        'gapCount': gapCount,
        'unknownCount': unknownCount,
        'expiringCount': expiringCount,
        'crossPartnership': crossPartnership,
        'clash': clash,
        'reasons': reasons,
      };
}

class SwingEvaluationDto {
  final String ccId;
  final int partnershipId;
  final String from;
  final String to;
  final String cutoff;
  final List<AssignmentEvaluationDto> assignments;
  final List<OpenSlotDto> openSlots;
  final List<OpenSlotDto> partiallyCoveredSlots;
  final Map<String, dynamic> stateCounts;
  final List<QuotaDto> quotas;

  const SwingEvaluationDto({
    required this.ccId,
    required this.partnershipId,
    required this.from,
    required this.to,
    required this.cutoff,
    required this.assignments,
    required this.openSlots,
    required this.partiallyCoveredSlots,
    required this.stateCounts,
    required this.quotas,
  });

  factory SwingEvaluationDto.fromJson(Map<String, dynamic> json) => SwingEvaluationDto(
        ccId: json['ccId'] as String,
        partnershipId: json['partnershipId'] as int,
        from: json['from'] as String,
        to: json['to'] as String,
        cutoff: json['cutoff'] as String,
        assignments: (json['assignments'] as List<dynamic>).map((e) => AssignmentEvaluationDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        openSlots: (json['openSlots'] as List<dynamic>).map((e) => OpenSlotDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        partiallyCoveredSlots: (json['partiallyCoveredSlots'] as List<dynamic>).map((e) => OpenSlotDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        stateCounts: json['stateCounts'] as Map<String, dynamic>,
        quotas: (json['quotas'] as List<dynamic>).map((e) => QuotaDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
      );

  Map<String, dynamic> toJson() => {
        'ccId': ccId,
        'partnershipId': partnershipId,
        'from': from,
        'to': to,
        'cutoff': cutoff,
        'assignments': assignments.map((e) => e.toJson()).toList(growable: false),
        'openSlots': openSlots.map((e) => e.toJson()).toList(growable: false),
        'partiallyCoveredSlots': partiallyCoveredSlots.map((e) => e.toJson()).toList(growable: false),
        'stateCounts': stateCounts,
        'quotas': quotas.map((e) => e.toJson()).toList(growable: false),
      };
}

class SyncDeltaDto {
  final int cursor;
  final int referenceCursor;
  final bool referenceStale;
  final String serverToday;
  final PersonDto? person;
  final List<HoldingDto> holdings;
  final List<AssignmentDto> assignments;
  final List<LeaveRecordDto> leave;
  final List<NotificationDto> notifications;
  final List<EvidenceSubmissionDto> submissions;
  final List<SyncTombstoneDto> tombstones;
  final SyncStandingDto? standing;

  const SyncDeltaDto({
    required this.cursor,
    required this.referenceCursor,
    required this.referenceStale,
    required this.serverToday,
    this.person,
    required this.holdings,
    required this.assignments,
    required this.leave,
    required this.notifications,
    required this.submissions,
    required this.tombstones,
    this.standing,
  });

  factory SyncDeltaDto.fromJson(Map<String, dynamic> json) => SyncDeltaDto(
        cursor: json['cursor'] as int,
        referenceCursor: json['referenceCursor'] as int,
        referenceStale: json['referenceStale'] as bool,
        serverToday: json['serverToday'] as String,
        person: json['person'] == null ? null : PersonDto.fromJson(json['person'] as Map<String, dynamic>),
        holdings: (json['holdings'] as List<dynamic>).map((e) => HoldingDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        assignments: (json['assignments'] as List<dynamic>).map((e) => AssignmentDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        leave: (json['leave'] as List<dynamic>).map((e) => LeaveRecordDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        notifications: (json['notifications'] as List<dynamic>).map((e) => NotificationDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        submissions: (json['submissions'] as List<dynamic>).map((e) => EvidenceSubmissionDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        tombstones: (json['tombstones'] as List<dynamic>).map((e) => SyncTombstoneDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        standing: json['standing'] == null ? null : SyncStandingDto.fromJson(json['standing'] as Map<String, dynamic>),
      );

  Map<String, dynamic> toJson() => {
        'cursor': cursor,
        'referenceCursor': referenceCursor,
        'referenceStale': referenceStale,
        'serverToday': serverToday,
        'person': person?.toJson(),
        'holdings': holdings.map((e) => e.toJson()).toList(growable: false),
        'assignments': assignments.map((e) => e.toJson()).toList(growable: false),
        'leave': leave.map((e) => e.toJson()).toList(growable: false),
        'notifications': notifications.map((e) => e.toJson()).toList(growable: false),
        'submissions': submissions.map((e) => e.toJson()).toList(growable: false),
        'tombstones': tombstones.map((e) => e.toJson()).toList(growable: false),
        'standing': standing?.toJson(),
      };
}

class SyncOperationDto {
  final String opId;
  final String type;
  final int? notificationId;
  final DateTime? readAt;
  final EvidenceSubmitDto? submission;

  const SyncOperationDto({
    required this.opId,
    required this.type,
    this.notificationId,
    this.readAt,
    this.submission,
  });

  factory SyncOperationDto.fromJson(Map<String, dynamic> json) => SyncOperationDto(
        opId: json['opId'] as String,
        type: json['type'] as String,
        notificationId: json['notificationId'] == null ? null : json['notificationId'] as int,
        readAt: json['readAt'] == null ? null : DateTime.parse(json['readAt'] as String).toUtc(),
        submission: json['submission'] == null ? null : EvidenceSubmitDto.fromJson(json['submission'] as Map<String, dynamic>),
      );

  Map<String, dynamic> toJson() => {
        'opId': opId,
        'type': type,
        'notificationId': notificationId,
        'readAt': readAt?.toIso8601String(),
        'submission': submission?.toJson(),
      };
}

class SyncOperationResultDto {
  final String opId;
  final String status;
  final String? detail;
  final int? uploadOffset;

  const SyncOperationResultDto({
    required this.opId,
    required this.status,
    this.detail,
    this.uploadOffset,
  });

  factory SyncOperationResultDto.fromJson(Map<String, dynamic> json) => SyncOperationResultDto(
        opId: json['opId'] as String,
        status: json['status'] as String,
        detail: json['detail'] == null ? null : json['detail'] as String,
        uploadOffset: json['uploadOffset'] == null ? null : json['uploadOffset'] as int,
      );

  Map<String, dynamic> toJson() => {
        'opId': opId,
        'status': status,
        'detail': detail,
        'uploadOffset': uploadOffset,
      };
}

class SyncQueueRequest {
  final List<SyncOperationDto> operations;

  const SyncQueueRequest({
    required this.operations,
  });

  factory SyncQueueRequest.fromJson(Map<String, dynamic> json) => SyncQueueRequest(
        operations: (json['operations'] as List<dynamic>).map((e) => SyncOperationDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
      );

  Map<String, dynamic> toJson() => {
        'operations': operations.map((e) => e.toJson()).toList(growable: false),
      };
}

class SyncQueueResultDto {
  final int cursor;
  final List<SyncOperationResultDto> results;

  const SyncQueueResultDto({
    required this.cursor,
    required this.results,
  });

  factory SyncQueueResultDto.fromJson(Map<String, dynamic> json) => SyncQueueResultDto(
        cursor: json['cursor'] as int,
        results: (json['results'] as List<dynamic>).map((e) => SyncOperationResultDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
      );

  Map<String, dynamic> toJson() => {
        'cursor': cursor,
        'results': results.map((e) => e.toJson()).toList(growable: false),
      };
}

class SyncReferenceDto {
  final int cursor;
  final int? matrixVersionId;
  final String? matrixVersionLabel;
  final List<RequirementDto> requirements;
  final List<PositionDto> positions;
  final List<PartnershipDto> partnerships;
  final List<CrewChangeDto> crewChanges;

  const SyncReferenceDto({
    required this.cursor,
    this.matrixVersionId,
    this.matrixVersionLabel,
    required this.requirements,
    required this.positions,
    required this.partnerships,
    required this.crewChanges,
  });

  factory SyncReferenceDto.fromJson(Map<String, dynamic> json) => SyncReferenceDto(
        cursor: json['cursor'] as int,
        matrixVersionId: json['matrixVersionId'] == null ? null : json['matrixVersionId'] as int,
        matrixVersionLabel: json['matrixVersionLabel'] == null ? null : json['matrixVersionLabel'] as String,
        requirements: (json['requirements'] as List<dynamic>).map((e) => RequirementDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        positions: (json['positions'] as List<dynamic>).map((e) => PositionDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        partnerships: (json['partnerships'] as List<dynamic>).map((e) => PartnershipDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        crewChanges: (json['crewChanges'] as List<dynamic>).map((e) => CrewChangeDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
      );

  Map<String, dynamic> toJson() => {
        'cursor': cursor,
        'matrixVersionId': matrixVersionId,
        'matrixVersionLabel': matrixVersionLabel,
        'requirements': requirements.map((e) => e.toJson()).toList(growable: false),
        'positions': positions.map((e) => e.toJson()).toList(growable: false),
        'partnerships': partnerships.map((e) => e.toJson()).toList(growable: false),
        'crewChanges': crewChanges.map((e) => e.toJson()).toList(growable: false),
      };
}

class SyncSnapshotDto {
  final int cursor;
  final int referenceCursor;
  final String serverToday;
  final PersonDto person;
  final List<HoldingDto> holdings;
  final List<AssignmentDto> assignments;
  final List<LeaveRecordDto> leave;
  final List<NotificationDto> notifications;
  final List<EvidenceSubmissionDto> submissions;
  final SyncReferenceDto reference;
  final SyncStandingDto? standing;

  const SyncSnapshotDto({
    required this.cursor,
    required this.referenceCursor,
    required this.serverToday,
    required this.person,
    required this.holdings,
    required this.assignments,
    required this.leave,
    required this.notifications,
    required this.submissions,
    required this.reference,
    this.standing,
  });

  factory SyncSnapshotDto.fromJson(Map<String, dynamic> json) => SyncSnapshotDto(
        cursor: json['cursor'] as int,
        referenceCursor: json['referenceCursor'] as int,
        serverToday: json['serverToday'] as String,
        person: PersonDto.fromJson(json['person'] as Map<String, dynamic>),
        holdings: (json['holdings'] as List<dynamic>).map((e) => HoldingDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        assignments: (json['assignments'] as List<dynamic>).map((e) => AssignmentDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        leave: (json['leave'] as List<dynamic>).map((e) => LeaveRecordDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        notifications: (json['notifications'] as List<dynamic>).map((e) => NotificationDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        submissions: (json['submissions'] as List<dynamic>).map((e) => EvidenceSubmissionDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        reference: SyncReferenceDto.fromJson(json['reference'] as Map<String, dynamic>),
        standing: json['standing'] == null ? null : SyncStandingDto.fromJson(json['standing'] as Map<String, dynamic>),
      );

  Map<String, dynamic> toJson() => {
        'cursor': cursor,
        'referenceCursor': referenceCursor,
        'serverToday': serverToday,
        'person': person.toJson(),
        'holdings': holdings.map((e) => e.toJson()).toList(growable: false),
        'assignments': assignments.map((e) => e.toJson()).toList(growable: false),
        'leave': leave.map((e) => e.toJson()).toList(growable: false),
        'notifications': notifications.map((e) => e.toJson()).toList(growable: false),
        'submissions': submissions.map((e) => e.toJson()).toList(growable: false),
        'reference': reference.toJson(),
        'standing': standing?.toJson(),
      };
}

class SyncStandingDto {
  final String ccId;
  final String partnershipAbbrev;
  final String from;
  final String to;
  final bool current;
  final PersonEvaluationDto evaluation;

  const SyncStandingDto({
    required this.ccId,
    required this.partnershipAbbrev,
    required this.from,
    required this.to,
    required this.current,
    required this.evaluation,
  });

  factory SyncStandingDto.fromJson(Map<String, dynamic> json) => SyncStandingDto(
        ccId: json['ccId'] as String,
        partnershipAbbrev: json['partnershipAbbrev'] as String,
        from: json['from'] as String,
        to: json['to'] as String,
        current: json['current'] as bool,
        evaluation: PersonEvaluationDto.fromJson(json['evaluation'] as Map<String, dynamic>),
      );

  Map<String, dynamic> toJson() => {
        'ccId': ccId,
        'partnershipAbbrev': partnershipAbbrev,
        'from': from,
        'to': to,
        'current': current,
        'evaluation': evaluation.toJson(),
      };
}

class SyncTombstoneDto {
  final String entityType;
  final int entityId;
  final int seq;

  const SyncTombstoneDto({
    required this.entityType,
    required this.entityId,
    required this.seq,
  });

  factory SyncTombstoneDto.fromJson(Map<String, dynamic> json) => SyncTombstoneDto(
        entityType: json['entityType'] as String,
        entityId: json['entityId'] as int,
        seq: json['seq'] as int,
      );

  Map<String, dynamic> toJson() => {
        'entityType': entityType,
        'entityId': entityId,
        'seq': seq,
      };
}

class UploadStateDto {
  final int offset;
  final bool complete;
  final int? declaredSize;

  const UploadStateDto({
    required this.offset,
    required this.complete,
    this.declaredSize,
  });

  factory UploadStateDto.fromJson(Map<String, dynamic> json) => UploadStateDto(
        offset: json['offset'] as int,
        complete: json['complete'] as bool,
        declaredSize: json['declaredSize'] == null ? null : json['declaredSize'] as int,
      );

  Map<String, dynamic> toJson() => {
        'offset': offset,
        'complete': complete,
        'declaredSize': declaredSize,
      };
}

