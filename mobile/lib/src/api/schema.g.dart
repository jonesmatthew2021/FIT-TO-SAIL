// GENERATED FILE — DO NOT EDIT.
//
// Produced by `dart run tool/generate_api.dart` from the backend's OpenAPI
// schema (DEV-2). Committed so the app builds without a JDK; `--check` in CI
// fails the build when the backend contract has moved and this has not.
//
// LocalDate is String on purpose — see the generator for why.

// ignore_for_file: unnecessary_cast, prefer_const_constructors

class AcceptEvidenceRequest {
  final int requirementId;
  final String status;
  final String? expiry;
  final String? issueDate;
  final String? note;

  const AcceptEvidenceRequest({
    required this.requirementId,
    required this.status,
    this.expiry,
    this.issueDate,
    this.note,
  });

  factory AcceptEvidenceRequest.fromJson(Map<String, dynamic> json) => AcceptEvidenceRequest(
        requirementId: json['requirementId'] as int,
        status: json['status'] as String,
        expiry: json['expiry'] == null ? null : json['expiry'] as String,
        issueDate: json['issueDate'] == null ? null : json['issueDate'] as String,
        note: json['note'] == null ? null : json['note'] as String,
      );

  Map<String, dynamic> toJson() => {
        'requirementId': requirementId,
        'status': status,
        'expiry': expiry,
        'issueDate': issueDate,
        'note': note,
      };
}

class AddAliasRequest {
  final String alias;

  const AddAliasRequest({
    required this.alias,
  });

  factory AddAliasRequest.fromJson(Map<String, dynamic> json) => AddAliasRequest(
        alias: json['alias'] as String,
      );

  Map<String, dynamic> toJson() => {
        'alias': alias,
      };
}

class AddConditionRequest {
  final String type;
  final String body;

  const AddConditionRequest({
    required this.type,
    required this.body,
  });

  factory AddConditionRequest.fromJson(Map<String, dynamic> json) => AddConditionRequest(
        type: json['type'] as String,
        body: json['body'] as String,
      );

  Map<String, dynamic> toJson() => {
        'type': type,
        'body': body,
      };
}

class AddNoteRequest {
  final String party;
  final String body;

  const AddNoteRequest({
    required this.party,
    required this.body,
  });

  factory AddNoteRequest.fromJson(Map<String, dynamic> json) => AddNoteRequest(
        party: json['party'] as String,
        body: json['body'] as String,
      );

  Map<String, dynamic> toJson() => {
        'party': party,
        'body': body,
      };
}

class AdminNotificationDto {
  final int id;
  final String kind;
  final String? audience;
  final String title;
  final String? body;
  final String? deepLink;
  final DateTime createdAt;
  final DateTime? readAt;
  final bool read;
  final String recipient;

  const AdminNotificationDto({
    required this.id,
    required this.kind,
    this.audience,
    required this.title,
    this.body,
    this.deepLink,
    required this.createdAt,
    this.readAt,
    required this.read,
    required this.recipient,
  });

  factory AdminNotificationDto.fromJson(Map<String, dynamic> json) => AdminNotificationDto(
        id: json['id'] as int,
        kind: json['kind'] as String,
        audience: json['audience'] == null ? null : json['audience'] as String,
        title: json['title'] as String,
        body: json['body'] == null ? null : json['body'] as String,
        deepLink: json['deepLink'] == null ? null : json['deepLink'] as String,
        createdAt: DateTime.parse(json['createdAt'] as String).toUtc(),
        readAt: json['readAt'] == null ? null : DateTime.parse(json['readAt'] as String).toUtc(),
        read: json['read'] as bool,
        recipient: json['recipient'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'kind': kind,
        'audience': audience,
        'title': title,
        'body': body,
        'deepLink': deepLink,
        'createdAt': createdAt.toIso8601String(),
        'readAt': readAt?.toIso8601String(),
        'read': read,
        'recipient': recipient,
      };
}

class ApprovalConditionDto {
  final int id;
  final String type;
  final String body;
  final DateTime createdAt;

  const ApprovalConditionDto({
    required this.id,
    required this.type,
    required this.body,
    required this.createdAt,
  });

  factory ApprovalConditionDto.fromJson(Map<String, dynamic> json) => ApprovalConditionDto(
        id: json['id'] as int,
        type: json['type'] as String,
        body: json['body'] as String,
        createdAt: DateTime.parse(json['createdAt'] as String).toUtc(),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'type': type,
        'body': body,
        'createdAt': createdAt.toIso8601String(),
      };
}

class AssignRequest {
  final int slotRef;
  final int personId;
  final String? from;
  final String? to;
  final bool? acknowledgeClash;

  const AssignRequest({
    required this.slotRef,
    required this.personId,
    this.from,
    this.to,
    this.acknowledgeClash,
  });

  factory AssignRequest.fromJson(Map<String, dynamic> json) => AssignRequest(
        slotRef: json['slotRef'] as int,
        personId: json['personId'] as int,
        from: json['from'] == null ? null : json['from'] as String,
        to: json['to'] == null ? null : json['to'] as String,
        acknowledgeClash: json['acknowledgeClash'] == null ? null : json['acknowledgeClash'] as bool,
      );

  Map<String, dynamic> toJson() => {
        'slotRef': slotRef,
        'personId': personId,
        'from': from,
        'to': to,
        'acknowledgeClash': acknowledgeClash,
      };
}

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

class CloseRegisterRecordRequest {
  final String outcome;
  final String? approvalFrom;
  final String? approvalTo;
  final List<AddConditionRequest>? conditions;
  final String? note;

  const CloseRegisterRecordRequest({
    required this.outcome,
    this.approvalFrom,
    this.approvalTo,
    this.conditions,
    this.note,
  });

  factory CloseRegisterRecordRequest.fromJson(Map<String, dynamic> json) => CloseRegisterRecordRequest(
        outcome: json['outcome'] as String,
        approvalFrom: json['approvalFrom'] == null ? null : json['approvalFrom'] as String,
        approvalTo: json['approvalTo'] == null ? null : json['approvalTo'] as String,
        conditions: json['conditions'] == null ? null : (json['conditions'] as List<dynamic>).map((e) => AddConditionRequest.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        note: json['note'] == null ? null : json['note'] as String,
      );

  Map<String, dynamic> toJson() => {
        'outcome': outcome,
        'approvalFrom': approvalFrom,
        'approvalTo': approvalTo,
        'conditions': conditions?.map((e) => e.toJson()).toList(growable: false),
        'note': note,
      };
}

class ConfigSettingDto {
  final String key;
  final String kind;
  final String description;
  final String value;
  final String defaultValue;
  final bool overridden;
  final DateTime? updatedAt;
  final String? updatedBy;

  const ConfigSettingDto({
    required this.key,
    required this.kind,
    required this.description,
    required this.value,
    required this.defaultValue,
    required this.overridden,
    this.updatedAt,
    this.updatedBy,
  });

  factory ConfigSettingDto.fromJson(Map<String, dynamic> json) => ConfigSettingDto(
        key: json['key'] as String,
        kind: json['kind'] as String,
        description: json['description'] as String,
        value: json['value'] as String,
        defaultValue: json['defaultValue'] as String,
        overridden: json['overridden'] as bool,
        updatedAt: json['updatedAt'] == null ? null : DateTime.parse(json['updatedAt'] as String).toUtc(),
        updatedBy: json['updatedBy'] == null ? null : json['updatedBy'] as String,
      );

  Map<String, dynamic> toJson() => {
        'key': key,
        'kind': kind,
        'description': description,
        'value': value,
        'defaultValue': defaultValue,
        'overridden': overridden,
        'updatedAt': updatedAt?.toIso8601String(),
        'updatedBy': updatedBy,
      };
}

class CreateIdentityProviderRequest {
  final String provider;
  final String issuer;
  final String tenantOrDomain;
  final String displayName;

  const CreateIdentityProviderRequest({
    required this.provider,
    required this.issuer,
    required this.tenantOrDomain,
    required this.displayName,
  });

  factory CreateIdentityProviderRequest.fromJson(Map<String, dynamic> json) => CreateIdentityProviderRequest(
        provider: json['provider'] as String,
        issuer: json['issuer'] as String,
        tenantOrDomain: json['tenantOrDomain'] as String,
        displayName: json['displayName'] as String,
      );

  Map<String, dynamic> toJson() => {
        'provider': provider,
        'issuer': issuer,
        'tenantOrDomain': tenantOrDomain,
        'displayName': displayName,
      };
}

class CreateMatrixDraftRequest {
  final String label;
  final int? copyFromVersionId;
  final String? notes;

  const CreateMatrixDraftRequest({
    required this.label,
    this.copyFromVersionId,
    this.notes,
  });

  factory CreateMatrixDraftRequest.fromJson(Map<String, dynamic> json) => CreateMatrixDraftRequest(
        label: json['label'] as String,
        copyFromVersionId: json['copyFromVersionId'] == null ? null : json['copyFromVersionId'] as int,
        notes: json['notes'] == null ? null : json['notes'] as String,
      );

  Map<String, dynamic> toJson() => {
        'label': label,
        'copyFromVersionId': copyFromVersionId,
        'notes': notes,
      };
}

class CreateRegisterRecordRequest {
  final String type;
  final String partnership;
  final String cc;
  final int? personId;
  final int? requirementId;
  final String? reqRaw;
  final String? effectiveFrom;
  final String? effectiveTo;
  final String? status;
  final bool? acknowledgeLateSubmission;

  const CreateRegisterRecordRequest({
    required this.type,
    required this.partnership,
    required this.cc,
    this.personId,
    this.requirementId,
    this.reqRaw,
    this.effectiveFrom,
    this.effectiveTo,
    this.status,
    this.acknowledgeLateSubmission,
  });

  factory CreateRegisterRecordRequest.fromJson(Map<String, dynamic> json) => CreateRegisterRecordRequest(
        type: json['type'] as String,
        partnership: json['partnership'] as String,
        cc: json['cc'] as String,
        personId: json['personId'] == null ? null : json['personId'] as int,
        requirementId: json['requirementId'] == null ? null : json['requirementId'] as int,
        reqRaw: json['reqRaw'] == null ? null : json['reqRaw'] as String,
        effectiveFrom: json['effectiveFrom'] == null ? null : json['effectiveFrom'] as String,
        effectiveTo: json['effectiveTo'] == null ? null : json['effectiveTo'] as String,
        status: json['status'] == null ? null : json['status'] as String,
        acknowledgeLateSubmission: json['acknowledgeLateSubmission'] == null ? null : json['acknowledgeLateSubmission'] as bool,
      );

  Map<String, dynamic> toJson() => {
        'type': type,
        'partnership': partnership,
        'cc': cc,
        'personId': personId,
        'requirementId': requirementId,
        'reqRaw': reqRaw,
        'effectiveFrom': effectiveFrom,
        'effectiveTo': effectiveTo,
        'status': status,
        'acknowledgeLateSubmission': acknowledgeLateSubmission,
      };
}

class CreateTransitionalAccountRequest {
  final String displayName;
  final String? email;
  final List<String> roles;
  final int? personId;

  const CreateTransitionalAccountRequest({
    required this.displayName,
    this.email,
    required this.roles,
    this.personId,
  });

  factory CreateTransitionalAccountRequest.fromJson(Map<String, dynamic> json) => CreateTransitionalAccountRequest(
        displayName: json['displayName'] as String,
        email: json['email'] == null ? null : json['email'] as String,
        roles: (json['roles'] as List<dynamic>).map((e) => (e as String)).toList(growable: false),
        personId: json['personId'] == null ? null : json['personId'] as int,
      );

  Map<String, dynamic> toJson() => {
        'displayName': displayName,
        'email': email,
        'roles': roles,
        'personId': personId,
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

class EvidenceDocumentDto {
  final String publicId;
  final int personId;
  final String sam;
  final String personName;
  final String partnershipAbbrev;
  final String source;
  final String submittedBy;
  final DateTime submittedAt;
  final String verificationStatus;
  final String? contentType;
  final int? byteSize;
  final bool uploadComplete;
  final bool hasContent;
  final int? requirementHintId;
  final int? matchedRequirementId;
  final String? extractionModel;
  final List<ExtractedFieldDto> extraction;
  final String? reviewReason;
  final String? rejectionReason;
  final int? linkedHoldingId;

  const EvidenceDocumentDto({
    required this.publicId,
    required this.personId,
    required this.sam,
    required this.personName,
    required this.partnershipAbbrev,
    required this.source,
    required this.submittedBy,
    required this.submittedAt,
    required this.verificationStatus,
    this.contentType,
    this.byteSize,
    required this.uploadComplete,
    required this.hasContent,
    this.requirementHintId,
    this.matchedRequirementId,
    this.extractionModel,
    required this.extraction,
    this.reviewReason,
    this.rejectionReason,
    this.linkedHoldingId,
  });

  factory EvidenceDocumentDto.fromJson(Map<String, dynamic> json) => EvidenceDocumentDto(
        publicId: json['publicId'] as String,
        personId: json['personId'] as int,
        sam: json['sam'] as String,
        personName: json['personName'] as String,
        partnershipAbbrev: json['partnershipAbbrev'] as String,
        source: json['source'] as String,
        submittedBy: json['submittedBy'] as String,
        submittedAt: DateTime.parse(json['submittedAt'] as String).toUtc(),
        verificationStatus: json['verificationStatus'] as String,
        contentType: json['contentType'] == null ? null : json['contentType'] as String,
        byteSize: json['byteSize'] == null ? null : json['byteSize'] as int,
        uploadComplete: json['uploadComplete'] as bool,
        hasContent: json['hasContent'] as bool,
        requirementHintId: json['requirementHintId'] == null ? null : json['requirementHintId'] as int,
        matchedRequirementId: json['matchedRequirementId'] == null ? null : json['matchedRequirementId'] as int,
        extractionModel: json['extractionModel'] == null ? null : json['extractionModel'] as String,
        extraction: (json['extraction'] as List<dynamic>).map((e) => ExtractedFieldDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        reviewReason: json['reviewReason'] == null ? null : json['reviewReason'] as String,
        rejectionReason: json['rejectionReason'] == null ? null : json['rejectionReason'] as String,
        linkedHoldingId: json['linkedHoldingId'] == null ? null : json['linkedHoldingId'] as int,
      );

  Map<String, dynamic> toJson() => {
        'publicId': publicId,
        'personId': personId,
        'sam': sam,
        'personName': personName,
        'partnershipAbbrev': partnershipAbbrev,
        'source': source,
        'submittedBy': submittedBy,
        'submittedAt': submittedAt.toIso8601String(),
        'verificationStatus': verificationStatus,
        'contentType': contentType,
        'byteSize': byteSize,
        'uploadComplete': uploadComplete,
        'hasContent': hasContent,
        'requirementHintId': requirementHintId,
        'matchedRequirementId': matchedRequirementId,
        'extractionModel': extractionModel,
        'extraction': extraction.map((e) => e.toJson()).toList(growable: false),
        'reviewReason': reviewReason,
        'rejectionReason': rejectionReason,
        'linkedHoldingId': linkedHoldingId,
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

class ExceptionItemDto {
  final int id;
  final String area;
  final String description;
  final String state;
  final String? linkedEntityType;
  final int? linkedEntityId;
  final String? resolutionNote;
  final DateTime? resolvedAt;
  final String? resolvedBy;
  final DateTime createdAt;
  final String createdBy;

  const ExceptionItemDto({
    required this.id,
    required this.area,
    required this.description,
    required this.state,
    this.linkedEntityType,
    this.linkedEntityId,
    this.resolutionNote,
    this.resolvedAt,
    this.resolvedBy,
    required this.createdAt,
    required this.createdBy,
  });

  factory ExceptionItemDto.fromJson(Map<String, dynamic> json) => ExceptionItemDto(
        id: json['id'] as int,
        area: json['area'] as String,
        description: json['description'] as String,
        state: json['state'] as String,
        linkedEntityType: json['linkedEntityType'] == null ? null : json['linkedEntityType'] as String,
        linkedEntityId: json['linkedEntityId'] == null ? null : json['linkedEntityId'] as int,
        resolutionNote: json['resolutionNote'] == null ? null : json['resolutionNote'] as String,
        resolvedAt: json['resolvedAt'] == null ? null : DateTime.parse(json['resolvedAt'] as String).toUtc(),
        resolvedBy: json['resolvedBy'] == null ? null : json['resolvedBy'] as String,
        createdAt: DateTime.parse(json['createdAt'] as String).toUtc(),
        createdBy: json['createdBy'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'area': area,
        'description': description,
        'state': state,
        'linkedEntityType': linkedEntityType,
        'linkedEntityId': linkedEntityId,
        'resolutionNote': resolutionNote,
        'resolvedAt': resolvedAt?.toIso8601String(),
        'resolvedBy': resolvedBy,
        'createdAt': createdAt.toIso8601String(),
        'createdBy': createdBy,
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

class ExtractedFieldDto {
  final String name;
  final String? value;
  final double confidence;

  const ExtractedFieldDto({
    required this.name,
    this.value,
    required this.confidence,
  });

  factory ExtractedFieldDto.fromJson(Map<String, dynamic> json) => ExtractedFieldDto(
        name: json['name'] as String,
        value: json['value'] == null ? null : json['value'] as String,
        confidence: json['confidence'] as double,
      );

  Map<String, dynamic> toJson() => {
        'name': name,
        'value': value,
        'confidence': confidence,
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

class IdentityProviderDto {
  final int id;
  final String provider;
  final String issuer;
  final String tenantOrDomain;
  final String displayName;
  final bool enabled;

  const IdentityProviderDto({
    required this.id,
    required this.provider,
    required this.issuer,
    required this.tenantOrDomain,
    required this.displayName,
    required this.enabled,
  });

  factory IdentityProviderDto.fromJson(Map<String, dynamic> json) => IdentityProviderDto(
        id: json['id'] as int,
        provider: json['provider'] as String,
        issuer: json['issuer'] as String,
        tenantOrDomain: json['tenantOrDomain'] as String,
        displayName: json['displayName'] as String,
        enabled: json['enabled'] as bool,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'provider': provider,
        'issuer': issuer,
        'tenantOrDomain': tenantOrDomain,
        'displayName': displayName,
        'enabled': enabled,
      };
}

class JobRunDto {
  final DateTime startedAt;
  final DateTime? finishedAt;
  final String outcome;
  final String? detail;

  const JobRunDto({
    required this.startedAt,
    this.finishedAt,
    required this.outcome,
    this.detail,
  });

  factory JobRunDto.fromJson(Map<String, dynamic> json) => JobRunDto(
        startedAt: DateTime.parse(json['startedAt'] as String).toUtc(),
        finishedAt: json['finishedAt'] == null ? null : DateTime.parse(json['finishedAt'] as String).toUtc(),
        outcome: json['outcome'] as String,
        detail: json['detail'] == null ? null : json['detail'] as String,
      );

  Map<String, dynamic> toJson() => {
        'startedAt': startedAt.toIso8601String(),
        'finishedAt': finishedAt?.toIso8601String(),
        'outcome': outcome,
        'detail': detail,
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

class MatrixConditionalMemberDto {
  final int requirementId;
  final String role;
  final int ordinal;

  const MatrixConditionalMemberDto({
    required this.requirementId,
    required this.role,
    required this.ordinal,
  });

  factory MatrixConditionalMemberDto.fromJson(Map<String, dynamic> json) => MatrixConditionalMemberDto(
        requirementId: json['requirementId'] as int,
        role: json['role'] as String,
        ordinal: json['ordinal'] as int,
      );

  Map<String, dynamic> toJson() => {
        'requirementId': requirementId,
        'role': role,
        'ordinal': ordinal,
      };
}

class MatrixConditionalRuleDto {
  final int id;
  final String kind;
  final int positionId;
  final int? requirementId;
  final String? label;
  final List<MatrixConditionalMemberDto> members;

  const MatrixConditionalRuleDto({
    required this.id,
    required this.kind,
    required this.positionId,
    this.requirementId,
    this.label,
    required this.members,
  });

  factory MatrixConditionalRuleDto.fromJson(Map<String, dynamic> json) => MatrixConditionalRuleDto(
        id: json['id'] as int,
        kind: json['kind'] as String,
        positionId: json['positionId'] as int,
        requirementId: json['requirementId'] == null ? null : json['requirementId'] as int,
        label: json['label'] == null ? null : json['label'] as String,
        members: (json['members'] as List<dynamic>).map((e) => MatrixConditionalMemberDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'kind': kind,
        'positionId': positionId,
        'requirementId': requirementId,
        'label': label,
        'members': members.map((e) => e.toJson()).toList(growable: false),
      };
}

class MatrixDiffDto {
  final int fromVersionId;
  final int toVersionId;
  final List<RuleDiffEntryDto> rules;
  final List<QuotaDiffEntryDto> quotas;
  final bool empty;

  const MatrixDiffDto({
    required this.fromVersionId,
    required this.toVersionId,
    required this.rules,
    required this.quotas,
    required this.empty,
  });

  factory MatrixDiffDto.fromJson(Map<String, dynamic> json) => MatrixDiffDto(
        fromVersionId: json['fromVersionId'] as int,
        toVersionId: json['toVersionId'] as int,
        rules: (json['rules'] as List<dynamic>).map((e) => RuleDiffEntryDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        quotas: (json['quotas'] as List<dynamic>).map((e) => QuotaDiffEntryDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        empty: json['empty'] as bool,
      );

  Map<String, dynamic> toJson() => {
        'fromVersionId': fromVersionId,
        'toVersionId': toVersionId,
        'rules': rules.map((e) => e.toJson()).toList(growable: false),
        'quotas': quotas.map((e) => e.toJson()).toList(growable: false),
        'empty': empty,
      };
}

class MatrixQuotaRuleDto {
  final int id;
  final String footnote;
  final int requirementId;
  final int minCount;
  final String scope;
  final List<int> positionIds;

  const MatrixQuotaRuleDto({
    required this.id,
    required this.footnote,
    required this.requirementId,
    required this.minCount,
    required this.scope,
    required this.positionIds,
  });

  factory MatrixQuotaRuleDto.fromJson(Map<String, dynamic> json) => MatrixQuotaRuleDto(
        id: json['id'] as int,
        footnote: json['footnote'] as String,
        requirementId: json['requirementId'] as int,
        minCount: json['minCount'] as int,
        scope: json['scope'] as String,
        positionIds: (json['positionIds'] as List<dynamic>).map((e) => (e as int)).toList(growable: false),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'footnote': footnote,
        'requirementId': requirementId,
        'minCount': minCount,
        'scope': scope,
        'positionIds': positionIds,
      };
}

class MatrixRuleDto {
  final int id;
  final int? partnershipId;
  final int positionId;
  final int requirementId;
  final String level;

  const MatrixRuleDto({
    required this.id,
    this.partnershipId,
    required this.positionId,
    required this.requirementId,
    required this.level,
  });

  factory MatrixRuleDto.fromJson(Map<String, dynamic> json) => MatrixRuleDto(
        id: json['id'] as int,
        partnershipId: json['partnershipId'] == null ? null : json['partnershipId'] as int,
        positionId: json['positionId'] as int,
        requirementId: json['requirementId'] as int,
        level: json['level'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'partnershipId': partnershipId,
        'positionId': positionId,
        'requirementId': requirementId,
        'level': level,
      };
}

class MatrixVersionDetailDto {
  final MatrixVersionDto version;
  final List<MatrixRuleDto> rules;
  final List<MatrixConditionalRuleDto> conditionals;
  final List<MatrixQuotaRuleDto> quotas;

  const MatrixVersionDetailDto({
    required this.version,
    required this.rules,
    required this.conditionals,
    required this.quotas,
  });

  factory MatrixVersionDetailDto.fromJson(Map<String, dynamic> json) => MatrixVersionDetailDto(
        version: MatrixVersionDto.fromJson(json['version'] as Map<String, dynamic>),
        rules: (json['rules'] as List<dynamic>).map((e) => MatrixRuleDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        conditionals: (json['conditionals'] as List<dynamic>).map((e) => MatrixConditionalRuleDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        quotas: (json['quotas'] as List<dynamic>).map((e) => MatrixQuotaRuleDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
      );

  Map<String, dynamic> toJson() => {
        'version': version.toJson(),
        'rules': rules.map((e) => e.toJson()).toList(growable: false),
        'conditionals': conditionals.map((e) => e.toJson()).toList(growable: false),
        'quotas': quotas.map((e) => e.toJson()).toList(growable: false),
      };
}

class MatrixVersionDto {
  final int id;
  final String label;
  final String status;
  final String? effectiveFrom;
  final String? publishedBy;
  final DateTime? publishedAt;
  final String? notes;
  final String? tierFootnote;
  final bool editable;

  const MatrixVersionDto({
    required this.id,
    required this.label,
    required this.status,
    this.effectiveFrom,
    this.publishedBy,
    this.publishedAt,
    this.notes,
    this.tierFootnote,
    required this.editable,
  });

  factory MatrixVersionDto.fromJson(Map<String, dynamic> json) => MatrixVersionDto(
        id: json['id'] as int,
        label: json['label'] as String,
        status: json['status'] as String,
        effectiveFrom: json['effectiveFrom'] == null ? null : json['effectiveFrom'] as String,
        publishedBy: json['publishedBy'] == null ? null : json['publishedBy'] as String,
        publishedAt: json['publishedAt'] == null ? null : DateTime.parse(json['publishedAt'] as String).toUtc(),
        notes: json['notes'] == null ? null : json['notes'] as String,
        tierFootnote: json['tierFootnote'] == null ? null : json['tierFootnote'] as String,
        editable: json['editable'] as bool,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'label': label,
        'status': status,
        'effectiveFrom': effectiveFrom,
        'publishedBy': publishedBy,
        'publishedAt': publishedAt?.toIso8601String(),
        'notes': notes,
        'tierFootnote': tierFootnote,
        'editable': editable,
      };
}

class MatrixVersionSummaryDto {
  final MatrixVersionDto version;
  final int requirementRuleCount;
  final int conditionalRuleCount;
  final int quotaRuleCount;

  const MatrixVersionSummaryDto({
    required this.version,
    required this.requirementRuleCount,
    required this.conditionalRuleCount,
    required this.quotaRuleCount,
  });

  factory MatrixVersionSummaryDto.fromJson(Map<String, dynamic> json) => MatrixVersionSummaryDto(
        version: MatrixVersionDto.fromJson(json['version'] as Map<String, dynamic>),
        requirementRuleCount: json['requirementRuleCount'] as int,
        conditionalRuleCount: json['conditionalRuleCount'] as int,
        quotaRuleCount: json['quotaRuleCount'] as int,
      );

  Map<String, dynamic> toJson() => {
        'version': version.toJson(),
        'requirementRuleCount': requirementRuleCount,
        'conditionalRuleCount': conditionalRuleCount,
        'quotaRuleCount': quotaRuleCount,
      };
}

class NextRecordIdDto {
  final String recordId;

  const NextRecordIdDto({
    required this.recordId,
  });

  factory NextRecordIdDto.fromJson(Map<String, dynamic> json) => NextRecordIdDto(
        recordId: json['recordId'] as String,
      );

  Map<String, dynamic> toJson() => {
        'recordId': recordId,
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

class NotificationSummaryDto {
  final int total;
  final int unread;

  const NotificationSummaryDto({
    required this.total,
    required this.unread,
  });

  factory NotificationSummaryDto.fromJson(Map<String, dynamic> json) => NotificationSummaryDto(
        total: json['total'] as int,
        unread: json['unread'] as int,
      );

  Map<String, dynamic> toJson() => {
        'total': total,
        'unread': unread,
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

class PublicationResultDto {
  final MatrixVersionDto published;
  final MatrixVersionDto? superseded;

  const PublicationResultDto({
    required this.published,
    this.superseded,
  });

  factory PublicationResultDto.fromJson(Map<String, dynamic> json) => PublicationResultDto(
        published: MatrixVersionDto.fromJson(json['published'] as Map<String, dynamic>),
        superseded: json['superseded'] == null ? null : MatrixVersionDto.fromJson(json['superseded'] as Map<String, dynamic>),
      );

  Map<String, dynamic> toJson() => {
        'published': published.toJson(),
        'superseded': superseded?.toJson(),
      };
}

class PublishMatrixRequest {
  final String? effectiveFrom;

  const PublishMatrixRequest({
    this.effectiveFrom,
  });

  factory PublishMatrixRequest.fromJson(Map<String, dynamic> json) => PublishMatrixRequest(
        effectiveFrom: json['effectiveFrom'] == null ? null : json['effectiveFrom'] as String,
      );

  Map<String, dynamic> toJson() => {
        'effectiveFrom': effectiveFrom,
      };
}

class QuotaDiffEntryDto {
  final String footnote;
  final int requirementId;
  final String scope;
  final int? fromMin;
  final int? toMin;
  final String kind;

  const QuotaDiffEntryDto({
    required this.footnote,
    required this.requirementId,
    required this.scope,
    this.fromMin,
    this.toMin,
    required this.kind,
  });

  factory QuotaDiffEntryDto.fromJson(Map<String, dynamic> json) => QuotaDiffEntryDto(
        footnote: json['footnote'] as String,
        requirementId: json['requirementId'] as int,
        scope: json['scope'] as String,
        fromMin: json['fromMin'] == null ? null : json['fromMin'] as int,
        toMin: json['toMin'] == null ? null : json['toMin'] as int,
        kind: json['kind'] as String,
      );

  Map<String, dynamic> toJson() => {
        'footnote': footnote,
        'requirementId': requirementId,
        'scope': scope,
        'fromMin': fromMin,
        'toMin': toMin,
        'kind': kind,
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

class RaiseExceptionRequest {
  final String area;
  final String description;
  final String? linkedEntityType;
  final int? linkedEntityId;

  const RaiseExceptionRequest({
    required this.area,
    required this.description,
    this.linkedEntityType,
    this.linkedEntityId,
  });

  factory RaiseExceptionRequest.fromJson(Map<String, dynamic> json) => RaiseExceptionRequest(
        area: json['area'] as String,
        description: json['description'] as String,
        linkedEntityType: json['linkedEntityType'] == null ? null : json['linkedEntityType'] as String,
        linkedEntityId: json['linkedEntityId'] == null ? null : json['linkedEntityId'] as int,
      );

  Map<String, dynamic> toJson() => {
        'area': area,
        'description': description,
        'linkedEntityType': linkedEntityType,
        'linkedEntityId': linkedEntityId,
      };
}

class RegisterNoteDto {
  final int id;
  final String party;
  final String body;
  final DateTime createdAt;
  final String createdBy;

  const RegisterNoteDto({
    required this.id,
    required this.party,
    required this.body,
    required this.createdAt,
    required this.createdBy,
  });

  factory RegisterNoteDto.fromJson(Map<String, dynamic> json) => RegisterNoteDto(
        id: json['id'] as int,
        party: json['party'] as String,
        body: json['body'] as String,
        createdAt: DateTime.parse(json['createdAt'] as String).toUtc(),
        createdBy: json['createdBy'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'party': party,
        'body': body,
        'createdAt': createdAt.toIso8601String(),
        'createdBy': createdBy,
      };
}

class RegisterRecordDetailDto {
  final RegisterRecordDto record;
  final List<ApprovalConditionDto> conditions;
  final List<RegisterNoteDto> notes;
  final List<RegisterTrailEntryDto> trail;

  const RegisterRecordDetailDto({
    required this.record,
    required this.conditions,
    required this.notes,
    required this.trail,
  });

  factory RegisterRecordDetailDto.fromJson(Map<String, dynamic> json) => RegisterRecordDetailDto(
        record: RegisterRecordDto.fromJson(json['record'] as Map<String, dynamic>),
        conditions: (json['conditions'] as List<dynamic>).map((e) => ApprovalConditionDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        notes: (json['notes'] as List<dynamic>).map((e) => RegisterNoteDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        trail: (json['trail'] as List<dynamic>).map((e) => RegisterTrailEntryDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
      );

  Map<String, dynamic> toJson() => {
        'record': record.toJson(),
        'conditions': conditions.map((e) => e.toJson()).toList(growable: false),
        'notes': notes.map((e) => e.toJson()).toList(growable: false),
        'trail': trail.map((e) => e.toJson()).toList(growable: false),
      };
}

class RegisterRecordDto {
  final String recordId;
  final String type;
  final String status;
  final String? outcome;
  final bool open;
  final int? personId;
  final String? sam;
  final String? personName;
  final String? positionName;
  final int? requirementId;
  final String? requirementCode;
  final String? reqRaw;
  final String partnershipAbbrev;
  final String? ccId;
  final String? effectiveFrom;
  final String? effectiveTo;
  final String? approvalFrom;
  final String? approvalTo;
  final String raisedDate;
  final bool lateSubmissionAcknowledged;

  const RegisterRecordDto({
    required this.recordId,
    required this.type,
    required this.status,
    this.outcome,
    required this.open,
    this.personId,
    this.sam,
    this.personName,
    this.positionName,
    this.requirementId,
    this.requirementCode,
    this.reqRaw,
    required this.partnershipAbbrev,
    this.ccId,
    this.effectiveFrom,
    this.effectiveTo,
    this.approvalFrom,
    this.approvalTo,
    required this.raisedDate,
    required this.lateSubmissionAcknowledged,
  });

  factory RegisterRecordDto.fromJson(Map<String, dynamic> json) => RegisterRecordDto(
        recordId: json['recordId'] as String,
        type: json['type'] as String,
        status: json['status'] as String,
        outcome: json['outcome'] == null ? null : json['outcome'] as String,
        open: json['open'] as bool,
        personId: json['personId'] == null ? null : json['personId'] as int,
        sam: json['sam'] == null ? null : json['sam'] as String,
        personName: json['personName'] == null ? null : json['personName'] as String,
        positionName: json['positionName'] == null ? null : json['positionName'] as String,
        requirementId: json['requirementId'] == null ? null : json['requirementId'] as int,
        requirementCode: json['requirementCode'] == null ? null : json['requirementCode'] as String,
        reqRaw: json['reqRaw'] == null ? null : json['reqRaw'] as String,
        partnershipAbbrev: json['partnershipAbbrev'] as String,
        ccId: json['ccId'] == null ? null : json['ccId'] as String,
        effectiveFrom: json['effectiveFrom'] == null ? null : json['effectiveFrom'] as String,
        effectiveTo: json['effectiveTo'] == null ? null : json['effectiveTo'] as String,
        approvalFrom: json['approvalFrom'] == null ? null : json['approvalFrom'] as String,
        approvalTo: json['approvalTo'] == null ? null : json['approvalTo'] as String,
        raisedDate: json['raisedDate'] as String,
        lateSubmissionAcknowledged: json['lateSubmissionAcknowledged'] as bool,
      );

  Map<String, dynamic> toJson() => {
        'recordId': recordId,
        'type': type,
        'status': status,
        'outcome': outcome,
        'open': open,
        'personId': personId,
        'sam': sam,
        'personName': personName,
        'positionName': positionName,
        'requirementId': requirementId,
        'requirementCode': requirementCode,
        'reqRaw': reqRaw,
        'partnershipAbbrev': partnershipAbbrev,
        'ccId': ccId,
        'effectiveFrom': effectiveFrom,
        'effectiveTo': effectiveTo,
        'approvalFrom': approvalFrom,
        'approvalTo': approvalTo,
        'raisedDate': raisedDate,
        'lateSubmissionAcknowledged': lateSubmissionAcknowledged,
      };
}

class RegisterTrailEntryDto {
  final int ordinal;
  final String body;
  final String actor;
  final DateTime occurredAt;

  const RegisterTrailEntryDto({
    required this.ordinal,
    required this.body,
    required this.actor,
    required this.occurredAt,
  });

  factory RegisterTrailEntryDto.fromJson(Map<String, dynamic> json) => RegisterTrailEntryDto(
        ordinal: json['ordinal'] as int,
        body: json['body'] as String,
        actor: json['actor'] as String,
        occurredAt: DateTime.parse(json['occurredAt'] as String).toUtc(),
      );

  Map<String, dynamic> toJson() => {
        'ordinal': ordinal,
        'body': body,
        'actor': actor,
        'occurredAt': occurredAt.toIso8601String(),
      };
}

class RejectEvidenceRequest {
  final String reason;

  const RejectEvidenceRequest({
    required this.reason,
  });

  factory RejectEvidenceRequest.fromJson(Map<String, dynamic> json) => RejectEvidenceRequest(
        reason: json['reason'] as String,
      );

  Map<String, dynamic> toJson() => {
        'reason': reason,
      };
}

class RequirementAliasDto {
  final int id;
  final String alias;

  const RequirementAliasDto({
    required this.id,
    required this.alias,
  });

  factory RequirementAliasDto.fromJson(Map<String, dynamic> json) => RequirementAliasDto(
        id: json['id'] as int,
        alias: json['alias'] as String,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'alias': alias,
      };
}

class RequirementDetailDto {
  final int id;
  final String code;
  final String category;
  final String title;
  final String status;
  final String? issuingAuthority;
  final String? notes;
  final List<RequirementAliasDto> aliases;
  final RequirementUsageDto usage;

  const RequirementDetailDto({
    required this.id,
    required this.code,
    required this.category,
    required this.title,
    required this.status,
    this.issuingAuthority,
    this.notes,
    required this.aliases,
    required this.usage,
  });

  factory RequirementDetailDto.fromJson(Map<String, dynamic> json) => RequirementDetailDto(
        id: json['id'] as int,
        code: json['code'] as String,
        category: json['category'] as String,
        title: json['title'] as String,
        status: json['status'] as String,
        issuingAuthority: json['issuingAuthority'] == null ? null : json['issuingAuthority'] as String,
        notes: json['notes'] == null ? null : json['notes'] as String,
        aliases: (json['aliases'] as List<dynamic>).map((e) => RequirementAliasDto.fromJson(e as Map<String, dynamic>)).toList(growable: false),
        usage: RequirementUsageDto.fromJson(json['usage'] as Map<String, dynamic>),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'code': code,
        'category': category,
        'title': title,
        'status': status,
        'issuingAuthority': issuingAuthority,
        'notes': notes,
        'aliases': aliases.map((e) => e.toJson()).toList(growable: false),
        'usage': usage.toJson(),
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

class RequirementUsageDto {
  final int holdings;
  final int requirementRules;
  final int quotaRules;
  final int conditionalRules;
  final int registerRecords;
  final int total;

  const RequirementUsageDto({
    required this.holdings,
    required this.requirementRules,
    required this.quotaRules,
    required this.conditionalRules,
    required this.registerRecords,
    required this.total,
  });

  factory RequirementUsageDto.fromJson(Map<String, dynamic> json) => RequirementUsageDto(
        holdings: json['holdings'] as int,
        requirementRules: json['requirementRules'] as int,
        quotaRules: json['quotaRules'] as int,
        conditionalRules: json['conditionalRules'] as int,
        registerRecords: json['registerRecords'] as int,
        total: json['total'] as int,
      );

  Map<String, dynamic> toJson() => {
        'holdings': holdings,
        'requirementRules': requirementRules,
        'quotaRules': quotaRules,
        'conditionalRules': conditionalRules,
        'registerRecords': registerRecords,
        'total': total,
      };
}

class ResolveExceptionRequest {
  final String note;

  const ResolveExceptionRequest({
    required this.note,
  });

  factory ResolveExceptionRequest.fromJson(Map<String, dynamic> json) => ResolveExceptionRequest(
        note: json['note'] as String,
      );

  Map<String, dynamic> toJson() => {
        'note': note,
      };
}

class RuleDiffEntryDto {
  final int? partnershipId;
  final int positionId;
  final int requirementId;
  final String? from;
  final String? to;
  final String kind;

  const RuleDiffEntryDto({
    this.partnershipId,
    required this.positionId,
    required this.requirementId,
    this.from,
    this.to,
    required this.kind,
  });

  factory RuleDiffEntryDto.fromJson(Map<String, dynamic> json) => RuleDiffEntryDto(
        partnershipId: json['partnershipId'] == null ? null : json['partnershipId'] as int,
        positionId: json['positionId'] as int,
        requirementId: json['requirementId'] as int,
        from: json['from'] == null ? null : json['from'] as String,
        to: json['to'] == null ? null : json['to'] as String,
        kind: json['kind'] as String,
      );

  Map<String, dynamic> toJson() => {
        'partnershipId': partnershipId,
        'positionId': positionId,
        'requirementId': requirementId,
        'from': from,
        'to': to,
        'kind': kind,
      };
}

class SaveRequirementRequest {
  final String? code;
  final String category;
  final String title;
  final String? issuingAuthority;
  final String? notes;
  final String? status;

  const SaveRequirementRequest({
    this.code,
    required this.category,
    required this.title,
    this.issuingAuthority,
    this.notes,
    this.status,
  });

  factory SaveRequirementRequest.fromJson(Map<String, dynamic> json) => SaveRequirementRequest(
        code: json['code'] == null ? null : json['code'] as String,
        category: json['category'] as String,
        title: json['title'] as String,
        issuingAuthority: json['issuingAuthority'] == null ? null : json['issuingAuthority'] as String,
        notes: json['notes'] == null ? null : json['notes'] as String,
        status: json['status'] == null ? null : json['status'] as String,
      );

  Map<String, dynamic> toJson() => {
        'code': code,
        'category': category,
        'title': title,
        'issuingAuthority': issuingAuthority,
        'notes': notes,
        'status': status,
      };
}

class ScheduledJobDto {
  final String name;
  final String schedule;
  final String description;
  final JobRunDto? lastRun;

  const ScheduledJobDto({
    required this.name,
    required this.schedule,
    required this.description,
    this.lastRun,
  });

  factory ScheduledJobDto.fromJson(Map<String, dynamic> json) => ScheduledJobDto(
        name: json['name'] as String,
        schedule: json['schedule'] as String,
        description: json['description'] as String,
        lastRun: json['lastRun'] == null ? null : JobRunDto.fromJson(json['lastRun'] as Map<String, dynamic>),
      );

  Map<String, dynamic> toJson() => {
        'name': name,
        'schedule': schedule,
        'description': description,
        'lastRun': lastRun?.toJson(),
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

class SetAccountStatusRequest {
  final String status;

  const SetAccountStatusRequest({
    required this.status,
  });

  factory SetAccountStatusRequest.fromJson(Map<String, dynamic> json) => SetAccountStatusRequest(
        status: json['status'] as String,
      );

  Map<String, dynamic> toJson() => {
        'status': status,
      };
}

class SetConfigRequest {
  final String? value;

  const SetConfigRequest({
    this.value,
  });

  factory SetConfigRequest.fromJson(Map<String, dynamic> json) => SetConfigRequest(
        value: json['value'] == null ? null : json['value'] as String,
      );

  Map<String, dynamic> toJson() => {
        'value': value,
      };
}

class SetEnabledRequest {
  final bool enabled;

  const SetEnabledRequest({
    required this.enabled,
  });

  factory SetEnabledRequest.fromJson(Map<String, dynamic> json) => SetEnabledRequest(
        enabled: json['enabled'] as bool,
      );

  Map<String, dynamic> toJson() => {
        'enabled': enabled,
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

class SetMatrixCellRequest {
  final int? partnershipId;
  final int positionId;
  final int requirementId;
  final String level;

  const SetMatrixCellRequest({
    this.partnershipId,
    required this.positionId,
    required this.requirementId,
    required this.level,
  });

  factory SetMatrixCellRequest.fromJson(Map<String, dynamic> json) => SetMatrixCellRequest(
        partnershipId: json['partnershipId'] == null ? null : json['partnershipId'] as int,
        positionId: json['positionId'] as int,
        requirementId: json['requirementId'] as int,
        level: json['level'] as String,
      );

  Map<String, dynamic> toJson() => {
        'partnershipId': partnershipId,
        'positionId': positionId,
        'requirementId': requirementId,
        'level': level,
      };
}

class SetScopesRequest {
  final List<int> partnershipIds;

  const SetScopesRequest({
    required this.partnershipIds,
  });

  factory SetScopesRequest.fromJson(Map<String, dynamic> json) => SetScopesRequest(
        partnershipIds: (json['partnershipIds'] as List<dynamic>).map((e) => (e as int)).toList(growable: false),
      );

  Map<String, dynamic> toJson() => {
        'partnershipIds': partnershipIds,
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
  final int? requirementId;

  const SyncOperationDto({
    required this.opId,
    required this.type,
    this.notificationId,
    this.readAt,
    this.submission,
    this.requirementId,
  });

  factory SyncOperationDto.fromJson(Map<String, dynamic> json) => SyncOperationDto(
        opId: json['opId'] as String,
        type: json['type'] as String,
        notificationId: json['notificationId'] == null ? null : json['notificationId'] as int,
        readAt: json['readAt'] == null ? null : DateTime.parse(json['readAt'] as String).toUtc(),
        submission: json['submission'] == null ? null : EvidenceSubmitDto.fromJson(json['submission'] as Map<String, dynamic>),
        requirementId: json['requirementId'] == null ? null : json['requirementId'] as int,
      );

  Map<String, dynamic> toJson() => {
        'opId': opId,
        'type': type,
        'notificationId': notificationId,
        'readAt': readAt?.toIso8601String(),
        'submission': submission?.toJson(),
        'requirementId': requirementId,
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

class TransitionRegisterRecordRequest {
  final String status;

  const TransitionRegisterRecordRequest({
    required this.status,
  });

  factory TransitionRegisterRecordRequest.fromJson(Map<String, dynamic> json) => TransitionRegisterRecordRequest(
        status: json['status'] as String,
      );

  Map<String, dynamic> toJson() => {
        'status': status,
      };
}

class UnknownHoldingDto {
  final int personId;
  final String sam;
  final String name;
  final int requirementId;
  final String code;
  final String title;

  const UnknownHoldingDto({
    required this.personId,
    required this.sam,
    required this.name,
    required this.requirementId,
    required this.code,
    required this.title,
  });

  factory UnknownHoldingDto.fromJson(Map<String, dynamic> json) => UnknownHoldingDto(
        personId: json['personId'] as int,
        sam: json['sam'] as String,
        name: json['name'] as String,
        requirementId: json['requirementId'] as int,
        code: json['code'] as String,
        title: json['title'] as String,
      );

  Map<String, dynamic> toJson() => {
        'personId': personId,
        'sam': sam,
        'name': name,
        'requirementId': requirementId,
        'code': code,
        'title': title,
      };
}

class UpdateMatrixDraftRequest {
  final String label;
  final String? notes;
  final String? tierFootnote;

  const UpdateMatrixDraftRequest({
    required this.label,
    this.notes,
    this.tierFootnote,
  });

  factory UpdateMatrixDraftRequest.fromJson(Map<String, dynamic> json) => UpdateMatrixDraftRequest(
        label: json['label'] as String,
        notes: json['notes'] == null ? null : json['notes'] as String,
        tierFootnote: json['tierFootnote'] == null ? null : json['tierFootnote'] as String,
      );

  Map<String, dynamic> toJson() => {
        'label': label,
        'notes': notes,
        'tierFootnote': tierFootnote,
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

class UserAccountDto {
  final int id;
  final String displayName;
  final String? email;
  final String kind;
  final String status;
  final List<String> roles;
  final List<int> scopedPartnershipIds;
  final int? personId;
  final DateTime? lastLoginAt;
  final bool identityLinked;

  const UserAccountDto({
    required this.id,
    required this.displayName,
    this.email,
    required this.kind,
    required this.status,
    required this.roles,
    required this.scopedPartnershipIds,
    this.personId,
    this.lastLoginAt,
    required this.identityLinked,
  });

  factory UserAccountDto.fromJson(Map<String, dynamic> json) => UserAccountDto(
        id: json['id'] as int,
        displayName: json['displayName'] as String,
        email: json['email'] == null ? null : json['email'] as String,
        kind: json['kind'] as String,
        status: json['status'] as String,
        roles: (json['roles'] as List<dynamic>).map((e) => (e as String)).toList(growable: false),
        scopedPartnershipIds: (json['scopedPartnershipIds'] as List<dynamic>).map((e) => (e as int)).toList(growable: false),
        personId: json['personId'] == null ? null : json['personId'] as int,
        lastLoginAt: json['lastLoginAt'] == null ? null : DateTime.parse(json['lastLoginAt'] as String).toUtc(),
        identityLinked: json['identityLinked'] as bool,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'displayName': displayName,
        'email': email,
        'kind': kind,
        'status': status,
        'roles': roles,
        'scopedPartnershipIds': scopedPartnershipIds,
        'personId': personId,
        'lastLoginAt': lastLoginAt?.toIso8601String(),
        'identityLinked': identityLinked,
      };
}

