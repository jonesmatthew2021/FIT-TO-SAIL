// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'local_store.dart';

// ignore_for_file: type=lint
class $PeopleTable extends People with TableInfo<$PeopleTable, LocalPerson> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $PeopleTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _samMeta = const VerificationMeta('sam');
  @override
  late final GeneratedColumn<String> sam = GeneratedColumn<String>(
    'sam',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  @override
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
    'name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _positionIdMeta = const VerificationMeta(
    'positionId',
  );
  @override
  late final GeneratedColumn<int> positionId = GeneratedColumn<int>(
    'position_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _positionNameMeta = const VerificationMeta(
    'positionName',
  );
  @override
  late final GeneratedColumn<String> positionName = GeneratedColumn<String>(
    'position_name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tierMeta = const VerificationMeta('tier');
  @override
  late final GeneratedColumn<String> tier = GeneratedColumn<String>(
    'tier',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _partnershipAbbrevMeta = const VerificationMeta(
    'partnershipAbbrev',
  );
  @override
  late final GeneratedColumn<String> partnershipAbbrev =
      GeneratedColumn<String>(
        'partnership_abbrev',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
      );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<String> status = GeneratedColumn<String>(
    'status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _emailMeta = const VerificationMeta('email');
  @override
  late final GeneratedColumn<String> email = GeneratedColumn<String>(
    'email',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    sam,
    name,
    positionId,
    positionName,
    tier,
    partnershipAbbrev,
    status,
    email,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'people';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalPerson> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('sam')) {
      context.handle(
        _samMeta,
        sam.isAcceptableOrUnknown(data['sam']!, _samMeta),
      );
    } else if (isInserting) {
      context.missing(_samMeta);
    }
    if (data.containsKey('name')) {
      context.handle(
        _nameMeta,
        name.isAcceptableOrUnknown(data['name']!, _nameMeta),
      );
    } else if (isInserting) {
      context.missing(_nameMeta);
    }
    if (data.containsKey('position_id')) {
      context.handle(
        _positionIdMeta,
        positionId.isAcceptableOrUnknown(data['position_id']!, _positionIdMeta),
      );
    } else if (isInserting) {
      context.missing(_positionIdMeta);
    }
    if (data.containsKey('position_name')) {
      context.handle(
        _positionNameMeta,
        positionName.isAcceptableOrUnknown(
          data['position_name']!,
          _positionNameMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_positionNameMeta);
    }
    if (data.containsKey('tier')) {
      context.handle(
        _tierMeta,
        tier.isAcceptableOrUnknown(data['tier']!, _tierMeta),
      );
    }
    if (data.containsKey('partnership_abbrev')) {
      context.handle(
        _partnershipAbbrevMeta,
        partnershipAbbrev.isAcceptableOrUnknown(
          data['partnership_abbrev']!,
          _partnershipAbbrevMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_partnershipAbbrevMeta);
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    } else if (isInserting) {
      context.missing(_statusMeta);
    }
    if (data.containsKey('email')) {
      context.handle(
        _emailMeta,
        email.isAcceptableOrUnknown(data['email']!, _emailMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalPerson map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalPerson(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      sam: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}sam'],
      )!,
      name: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name'],
      )!,
      positionId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}position_id'],
      )!,
      positionName: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}position_name'],
      )!,
      tier: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tier'],
      ),
      partnershipAbbrev: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}partnership_abbrev'],
      )!,
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}status'],
      )!,
      email: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}email'],
      ),
    );
  }

  @override
  $PeopleTable createAlias(String alias) {
    return $PeopleTable(attachedDatabase, alias);
  }
}

class LocalPerson extends DataClass implements Insertable<LocalPerson> {
  final int id;
  final String sam;
  final String name;
  final int positionId;
  final String positionName;
  final String? tier;
  final String partnershipAbbrev;
  final String status;
  final String? email;
  const LocalPerson({
    required this.id,
    required this.sam,
    required this.name,
    required this.positionId,
    required this.positionName,
    this.tier,
    required this.partnershipAbbrev,
    required this.status,
    this.email,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['sam'] = Variable<String>(sam);
    map['name'] = Variable<String>(name);
    map['position_id'] = Variable<int>(positionId);
    map['position_name'] = Variable<String>(positionName);
    if (!nullToAbsent || tier != null) {
      map['tier'] = Variable<String>(tier);
    }
    map['partnership_abbrev'] = Variable<String>(partnershipAbbrev);
    map['status'] = Variable<String>(status);
    if (!nullToAbsent || email != null) {
      map['email'] = Variable<String>(email);
    }
    return map;
  }

  PeopleCompanion toCompanion(bool nullToAbsent) {
    return PeopleCompanion(
      id: Value(id),
      sam: Value(sam),
      name: Value(name),
      positionId: Value(positionId),
      positionName: Value(positionName),
      tier: tier == null && nullToAbsent ? const Value.absent() : Value(tier),
      partnershipAbbrev: Value(partnershipAbbrev),
      status: Value(status),
      email: email == null && nullToAbsent
          ? const Value.absent()
          : Value(email),
    );
  }

  factory LocalPerson.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalPerson(
      id: serializer.fromJson<int>(json['id']),
      sam: serializer.fromJson<String>(json['sam']),
      name: serializer.fromJson<String>(json['name']),
      positionId: serializer.fromJson<int>(json['positionId']),
      positionName: serializer.fromJson<String>(json['positionName']),
      tier: serializer.fromJson<String?>(json['tier']),
      partnershipAbbrev: serializer.fromJson<String>(json['partnershipAbbrev']),
      status: serializer.fromJson<String>(json['status']),
      email: serializer.fromJson<String?>(json['email']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'sam': serializer.toJson<String>(sam),
      'name': serializer.toJson<String>(name),
      'positionId': serializer.toJson<int>(positionId),
      'positionName': serializer.toJson<String>(positionName),
      'tier': serializer.toJson<String?>(tier),
      'partnershipAbbrev': serializer.toJson<String>(partnershipAbbrev),
      'status': serializer.toJson<String>(status),
      'email': serializer.toJson<String?>(email),
    };
  }

  LocalPerson copyWith({
    int? id,
    String? sam,
    String? name,
    int? positionId,
    String? positionName,
    Value<String?> tier = const Value.absent(),
    String? partnershipAbbrev,
    String? status,
    Value<String?> email = const Value.absent(),
  }) => LocalPerson(
    id: id ?? this.id,
    sam: sam ?? this.sam,
    name: name ?? this.name,
    positionId: positionId ?? this.positionId,
    positionName: positionName ?? this.positionName,
    tier: tier.present ? tier.value : this.tier,
    partnershipAbbrev: partnershipAbbrev ?? this.partnershipAbbrev,
    status: status ?? this.status,
    email: email.present ? email.value : this.email,
  );
  LocalPerson copyWithCompanion(PeopleCompanion data) {
    return LocalPerson(
      id: data.id.present ? data.id.value : this.id,
      sam: data.sam.present ? data.sam.value : this.sam,
      name: data.name.present ? data.name.value : this.name,
      positionId: data.positionId.present
          ? data.positionId.value
          : this.positionId,
      positionName: data.positionName.present
          ? data.positionName.value
          : this.positionName,
      tier: data.tier.present ? data.tier.value : this.tier,
      partnershipAbbrev: data.partnershipAbbrev.present
          ? data.partnershipAbbrev.value
          : this.partnershipAbbrev,
      status: data.status.present ? data.status.value : this.status,
      email: data.email.present ? data.email.value : this.email,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalPerson(')
          ..write('id: $id, ')
          ..write('sam: $sam, ')
          ..write('name: $name, ')
          ..write('positionId: $positionId, ')
          ..write('positionName: $positionName, ')
          ..write('tier: $tier, ')
          ..write('partnershipAbbrev: $partnershipAbbrev, ')
          ..write('status: $status, ')
          ..write('email: $email')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    sam,
    name,
    positionId,
    positionName,
    tier,
    partnershipAbbrev,
    status,
    email,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalPerson &&
          other.id == this.id &&
          other.sam == this.sam &&
          other.name == this.name &&
          other.positionId == this.positionId &&
          other.positionName == this.positionName &&
          other.tier == this.tier &&
          other.partnershipAbbrev == this.partnershipAbbrev &&
          other.status == this.status &&
          other.email == this.email);
}

class PeopleCompanion extends UpdateCompanion<LocalPerson> {
  final Value<int> id;
  final Value<String> sam;
  final Value<String> name;
  final Value<int> positionId;
  final Value<String> positionName;
  final Value<String?> tier;
  final Value<String> partnershipAbbrev;
  final Value<String> status;
  final Value<String?> email;
  const PeopleCompanion({
    this.id = const Value.absent(),
    this.sam = const Value.absent(),
    this.name = const Value.absent(),
    this.positionId = const Value.absent(),
    this.positionName = const Value.absent(),
    this.tier = const Value.absent(),
    this.partnershipAbbrev = const Value.absent(),
    this.status = const Value.absent(),
    this.email = const Value.absent(),
  });
  PeopleCompanion.insert({
    this.id = const Value.absent(),
    required String sam,
    required String name,
    required int positionId,
    required String positionName,
    this.tier = const Value.absent(),
    required String partnershipAbbrev,
    required String status,
    this.email = const Value.absent(),
  }) : sam = Value(sam),
       name = Value(name),
       positionId = Value(positionId),
       positionName = Value(positionName),
       partnershipAbbrev = Value(partnershipAbbrev),
       status = Value(status);
  static Insertable<LocalPerson> custom({
    Expression<int>? id,
    Expression<String>? sam,
    Expression<String>? name,
    Expression<int>? positionId,
    Expression<String>? positionName,
    Expression<String>? tier,
    Expression<String>? partnershipAbbrev,
    Expression<String>? status,
    Expression<String>? email,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (sam != null) 'sam': sam,
      if (name != null) 'name': name,
      if (positionId != null) 'position_id': positionId,
      if (positionName != null) 'position_name': positionName,
      if (tier != null) 'tier': tier,
      if (partnershipAbbrev != null) 'partnership_abbrev': partnershipAbbrev,
      if (status != null) 'status': status,
      if (email != null) 'email': email,
    });
  }

  PeopleCompanion copyWith({
    Value<int>? id,
    Value<String>? sam,
    Value<String>? name,
    Value<int>? positionId,
    Value<String>? positionName,
    Value<String?>? tier,
    Value<String>? partnershipAbbrev,
    Value<String>? status,
    Value<String?>? email,
  }) {
    return PeopleCompanion(
      id: id ?? this.id,
      sam: sam ?? this.sam,
      name: name ?? this.name,
      positionId: positionId ?? this.positionId,
      positionName: positionName ?? this.positionName,
      tier: tier ?? this.tier,
      partnershipAbbrev: partnershipAbbrev ?? this.partnershipAbbrev,
      status: status ?? this.status,
      email: email ?? this.email,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (sam.present) {
      map['sam'] = Variable<String>(sam.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (positionId.present) {
      map['position_id'] = Variable<int>(positionId.value);
    }
    if (positionName.present) {
      map['position_name'] = Variable<String>(positionName.value);
    }
    if (tier.present) {
      map['tier'] = Variable<String>(tier.value);
    }
    if (partnershipAbbrev.present) {
      map['partnership_abbrev'] = Variable<String>(partnershipAbbrev.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(status.value);
    }
    if (email.present) {
      map['email'] = Variable<String>(email.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('PeopleCompanion(')
          ..write('id: $id, ')
          ..write('sam: $sam, ')
          ..write('name: $name, ')
          ..write('positionId: $positionId, ')
          ..write('positionName: $positionName, ')
          ..write('tier: $tier, ')
          ..write('partnershipAbbrev: $partnershipAbbrev, ')
          ..write('status: $status, ')
          ..write('email: $email')
          ..write(')'))
        .toString();
  }
}

class $HoldingsTable extends Holdings
    with TableInfo<$HoldingsTable, LocalHolding> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $HoldingsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _requirementIdMeta = const VerificationMeta(
    'requirementId',
  );
  @override
  late final GeneratedColumn<int> requirementId = GeneratedColumn<int>(
    'requirement_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<String> status = GeneratedColumn<String>(
    'status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _expiryMeta = const VerificationMeta('expiry');
  @override
  late final GeneratedColumn<String> expiry = GeneratedColumn<String>(
    'expiry',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _issueDateMeta = const VerificationMeta(
    'issueDate',
  );
  @override
  late final GeneratedColumn<String> issueDate = GeneratedColumn<String>(
    'issue_date',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _noteMeta = const VerificationMeta('note');
  @override
  late final GeneratedColumn<String> note = GeneratedColumn<String>(
    'note',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    requirementId,
    status,
    expiry,
    issueDate,
    note,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'holdings';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalHolding> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('requirement_id')) {
      context.handle(
        _requirementIdMeta,
        requirementId.isAcceptableOrUnknown(
          data['requirement_id']!,
          _requirementIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_requirementIdMeta);
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    } else if (isInserting) {
      context.missing(_statusMeta);
    }
    if (data.containsKey('expiry')) {
      context.handle(
        _expiryMeta,
        expiry.isAcceptableOrUnknown(data['expiry']!, _expiryMeta),
      );
    }
    if (data.containsKey('issue_date')) {
      context.handle(
        _issueDateMeta,
        issueDate.isAcceptableOrUnknown(data['issue_date']!, _issueDateMeta),
      );
    }
    if (data.containsKey('note')) {
      context.handle(
        _noteMeta,
        note.isAcceptableOrUnknown(data['note']!, _noteMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalHolding map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalHolding(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      requirementId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}requirement_id'],
      )!,
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}status'],
      )!,
      expiry: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}expiry'],
      ),
      issueDate: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}issue_date'],
      ),
      note: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}note'],
      ),
    );
  }

  @override
  $HoldingsTable createAlias(String alias) {
    return $HoldingsTable(attachedDatabase, alias);
  }
}

class LocalHolding extends DataClass implements Insertable<LocalHolding> {
  final int id;
  final int requirementId;
  final String status;
  final String? expiry;
  final String? issueDate;
  final String? note;
  const LocalHolding({
    required this.id,
    required this.requirementId,
    required this.status,
    this.expiry,
    this.issueDate,
    this.note,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['requirement_id'] = Variable<int>(requirementId);
    map['status'] = Variable<String>(status);
    if (!nullToAbsent || expiry != null) {
      map['expiry'] = Variable<String>(expiry);
    }
    if (!nullToAbsent || issueDate != null) {
      map['issue_date'] = Variable<String>(issueDate);
    }
    if (!nullToAbsent || note != null) {
      map['note'] = Variable<String>(note);
    }
    return map;
  }

  HoldingsCompanion toCompanion(bool nullToAbsent) {
    return HoldingsCompanion(
      id: Value(id),
      requirementId: Value(requirementId),
      status: Value(status),
      expiry: expiry == null && nullToAbsent
          ? const Value.absent()
          : Value(expiry),
      issueDate: issueDate == null && nullToAbsent
          ? const Value.absent()
          : Value(issueDate),
      note: note == null && nullToAbsent ? const Value.absent() : Value(note),
    );
  }

  factory LocalHolding.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalHolding(
      id: serializer.fromJson<int>(json['id']),
      requirementId: serializer.fromJson<int>(json['requirementId']),
      status: serializer.fromJson<String>(json['status']),
      expiry: serializer.fromJson<String?>(json['expiry']),
      issueDate: serializer.fromJson<String?>(json['issueDate']),
      note: serializer.fromJson<String?>(json['note']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'requirementId': serializer.toJson<int>(requirementId),
      'status': serializer.toJson<String>(status),
      'expiry': serializer.toJson<String?>(expiry),
      'issueDate': serializer.toJson<String?>(issueDate),
      'note': serializer.toJson<String?>(note),
    };
  }

  LocalHolding copyWith({
    int? id,
    int? requirementId,
    String? status,
    Value<String?> expiry = const Value.absent(),
    Value<String?> issueDate = const Value.absent(),
    Value<String?> note = const Value.absent(),
  }) => LocalHolding(
    id: id ?? this.id,
    requirementId: requirementId ?? this.requirementId,
    status: status ?? this.status,
    expiry: expiry.present ? expiry.value : this.expiry,
    issueDate: issueDate.present ? issueDate.value : this.issueDate,
    note: note.present ? note.value : this.note,
  );
  LocalHolding copyWithCompanion(HoldingsCompanion data) {
    return LocalHolding(
      id: data.id.present ? data.id.value : this.id,
      requirementId: data.requirementId.present
          ? data.requirementId.value
          : this.requirementId,
      status: data.status.present ? data.status.value : this.status,
      expiry: data.expiry.present ? data.expiry.value : this.expiry,
      issueDate: data.issueDate.present ? data.issueDate.value : this.issueDate,
      note: data.note.present ? data.note.value : this.note,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalHolding(')
          ..write('id: $id, ')
          ..write('requirementId: $requirementId, ')
          ..write('status: $status, ')
          ..write('expiry: $expiry, ')
          ..write('issueDate: $issueDate, ')
          ..write('note: $note')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(id, requirementId, status, expiry, issueDate, note);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalHolding &&
          other.id == this.id &&
          other.requirementId == this.requirementId &&
          other.status == this.status &&
          other.expiry == this.expiry &&
          other.issueDate == this.issueDate &&
          other.note == this.note);
}

class HoldingsCompanion extends UpdateCompanion<LocalHolding> {
  final Value<int> id;
  final Value<int> requirementId;
  final Value<String> status;
  final Value<String?> expiry;
  final Value<String?> issueDate;
  final Value<String?> note;
  const HoldingsCompanion({
    this.id = const Value.absent(),
    this.requirementId = const Value.absent(),
    this.status = const Value.absent(),
    this.expiry = const Value.absent(),
    this.issueDate = const Value.absent(),
    this.note = const Value.absent(),
  });
  HoldingsCompanion.insert({
    this.id = const Value.absent(),
    required int requirementId,
    required String status,
    this.expiry = const Value.absent(),
    this.issueDate = const Value.absent(),
    this.note = const Value.absent(),
  }) : requirementId = Value(requirementId),
       status = Value(status);
  static Insertable<LocalHolding> custom({
    Expression<int>? id,
    Expression<int>? requirementId,
    Expression<String>? status,
    Expression<String>? expiry,
    Expression<String>? issueDate,
    Expression<String>? note,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (requirementId != null) 'requirement_id': requirementId,
      if (status != null) 'status': status,
      if (expiry != null) 'expiry': expiry,
      if (issueDate != null) 'issue_date': issueDate,
      if (note != null) 'note': note,
    });
  }

  HoldingsCompanion copyWith({
    Value<int>? id,
    Value<int>? requirementId,
    Value<String>? status,
    Value<String?>? expiry,
    Value<String?>? issueDate,
    Value<String?>? note,
  }) {
    return HoldingsCompanion(
      id: id ?? this.id,
      requirementId: requirementId ?? this.requirementId,
      status: status ?? this.status,
      expiry: expiry ?? this.expiry,
      issueDate: issueDate ?? this.issueDate,
      note: note ?? this.note,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (requirementId.present) {
      map['requirement_id'] = Variable<int>(requirementId.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(status.value);
    }
    if (expiry.present) {
      map['expiry'] = Variable<String>(expiry.value);
    }
    if (issueDate.present) {
      map['issue_date'] = Variable<String>(issueDate.value);
    }
    if (note.present) {
      map['note'] = Variable<String>(note.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('HoldingsCompanion(')
          ..write('id: $id, ')
          ..write('requirementId: $requirementId, ')
          ..write('status: $status, ')
          ..write('expiry: $expiry, ')
          ..write('issueDate: $issueDate, ')
          ..write('note: $note')
          ..write(')'))
        .toString();
  }
}

class $AssignmentsTable extends Assignments
    with TableInfo<$AssignmentsTable, LocalAssignment> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $AssignmentsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _crewChangeIdMeta = const VerificationMeta(
    'crewChangeId',
  );
  @override
  late final GeneratedColumn<int> crewChangeId = GeneratedColumn<int>(
    'crew_change_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _ccIdMeta = const VerificationMeta('ccId');
  @override
  late final GeneratedColumn<String> ccId = GeneratedColumn<String>(
    'cc_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _partnershipAbbrevMeta = const VerificationMeta(
    'partnershipAbbrev',
  );
  @override
  late final GeneratedColumn<String> partnershipAbbrev =
      GeneratedColumn<String>(
        'partnership_abbrev',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
      );
  static const VerificationMeta _slotRefMeta = const VerificationMeta(
    'slotRef',
  );
  @override
  late final GeneratedColumn<int> slotRef = GeneratedColumn<int>(
    'slot_ref',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _fromDateMeta = const VerificationMeta(
    'fromDate',
  );
  @override
  late final GeneratedColumn<String> fromDate = GeneratedColumn<String>(
    'from_date',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _toDateMeta = const VerificationMeta('toDate');
  @override
  late final GeneratedColumn<String> toDate = GeneratedColumn<String>(
    'to_date',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    crewChangeId,
    ccId,
    partnershipAbbrev,
    slotRef,
    fromDate,
    toDate,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'assignments';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalAssignment> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('crew_change_id')) {
      context.handle(
        _crewChangeIdMeta,
        crewChangeId.isAcceptableOrUnknown(
          data['crew_change_id']!,
          _crewChangeIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_crewChangeIdMeta);
    }
    if (data.containsKey('cc_id')) {
      context.handle(
        _ccIdMeta,
        ccId.isAcceptableOrUnknown(data['cc_id']!, _ccIdMeta),
      );
    } else if (isInserting) {
      context.missing(_ccIdMeta);
    }
    if (data.containsKey('partnership_abbrev')) {
      context.handle(
        _partnershipAbbrevMeta,
        partnershipAbbrev.isAcceptableOrUnknown(
          data['partnership_abbrev']!,
          _partnershipAbbrevMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_partnershipAbbrevMeta);
    }
    if (data.containsKey('slot_ref')) {
      context.handle(
        _slotRefMeta,
        slotRef.isAcceptableOrUnknown(data['slot_ref']!, _slotRefMeta),
      );
    } else if (isInserting) {
      context.missing(_slotRefMeta);
    }
    if (data.containsKey('from_date')) {
      context.handle(
        _fromDateMeta,
        fromDate.isAcceptableOrUnknown(data['from_date']!, _fromDateMeta),
      );
    } else if (isInserting) {
      context.missing(_fromDateMeta);
    }
    if (data.containsKey('to_date')) {
      context.handle(
        _toDateMeta,
        toDate.isAcceptableOrUnknown(data['to_date']!, _toDateMeta),
      );
    } else if (isInserting) {
      context.missing(_toDateMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalAssignment map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalAssignment(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      crewChangeId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}crew_change_id'],
      )!,
      ccId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}cc_id'],
      )!,
      partnershipAbbrev: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}partnership_abbrev'],
      )!,
      slotRef: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}slot_ref'],
      )!,
      fromDate: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}from_date'],
      )!,
      toDate: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}to_date'],
      )!,
    );
  }

  @override
  $AssignmentsTable createAlias(String alias) {
    return $AssignmentsTable(attachedDatabase, alias);
  }
}

class LocalAssignment extends DataClass implements Insertable<LocalAssignment> {
  final int id;
  final int crewChangeId;
  final String ccId;
  final String partnershipAbbrev;
  final int slotRef;
  final String fromDate;
  final String toDate;
  const LocalAssignment({
    required this.id,
    required this.crewChangeId,
    required this.ccId,
    required this.partnershipAbbrev,
    required this.slotRef,
    required this.fromDate,
    required this.toDate,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['crew_change_id'] = Variable<int>(crewChangeId);
    map['cc_id'] = Variable<String>(ccId);
    map['partnership_abbrev'] = Variable<String>(partnershipAbbrev);
    map['slot_ref'] = Variable<int>(slotRef);
    map['from_date'] = Variable<String>(fromDate);
    map['to_date'] = Variable<String>(toDate);
    return map;
  }

  AssignmentsCompanion toCompanion(bool nullToAbsent) {
    return AssignmentsCompanion(
      id: Value(id),
      crewChangeId: Value(crewChangeId),
      ccId: Value(ccId),
      partnershipAbbrev: Value(partnershipAbbrev),
      slotRef: Value(slotRef),
      fromDate: Value(fromDate),
      toDate: Value(toDate),
    );
  }

  factory LocalAssignment.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalAssignment(
      id: serializer.fromJson<int>(json['id']),
      crewChangeId: serializer.fromJson<int>(json['crewChangeId']),
      ccId: serializer.fromJson<String>(json['ccId']),
      partnershipAbbrev: serializer.fromJson<String>(json['partnershipAbbrev']),
      slotRef: serializer.fromJson<int>(json['slotRef']),
      fromDate: serializer.fromJson<String>(json['fromDate']),
      toDate: serializer.fromJson<String>(json['toDate']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'crewChangeId': serializer.toJson<int>(crewChangeId),
      'ccId': serializer.toJson<String>(ccId),
      'partnershipAbbrev': serializer.toJson<String>(partnershipAbbrev),
      'slotRef': serializer.toJson<int>(slotRef),
      'fromDate': serializer.toJson<String>(fromDate),
      'toDate': serializer.toJson<String>(toDate),
    };
  }

  LocalAssignment copyWith({
    int? id,
    int? crewChangeId,
    String? ccId,
    String? partnershipAbbrev,
    int? slotRef,
    String? fromDate,
    String? toDate,
  }) => LocalAssignment(
    id: id ?? this.id,
    crewChangeId: crewChangeId ?? this.crewChangeId,
    ccId: ccId ?? this.ccId,
    partnershipAbbrev: partnershipAbbrev ?? this.partnershipAbbrev,
    slotRef: slotRef ?? this.slotRef,
    fromDate: fromDate ?? this.fromDate,
    toDate: toDate ?? this.toDate,
  );
  LocalAssignment copyWithCompanion(AssignmentsCompanion data) {
    return LocalAssignment(
      id: data.id.present ? data.id.value : this.id,
      crewChangeId: data.crewChangeId.present
          ? data.crewChangeId.value
          : this.crewChangeId,
      ccId: data.ccId.present ? data.ccId.value : this.ccId,
      partnershipAbbrev: data.partnershipAbbrev.present
          ? data.partnershipAbbrev.value
          : this.partnershipAbbrev,
      slotRef: data.slotRef.present ? data.slotRef.value : this.slotRef,
      fromDate: data.fromDate.present ? data.fromDate.value : this.fromDate,
      toDate: data.toDate.present ? data.toDate.value : this.toDate,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalAssignment(')
          ..write('id: $id, ')
          ..write('crewChangeId: $crewChangeId, ')
          ..write('ccId: $ccId, ')
          ..write('partnershipAbbrev: $partnershipAbbrev, ')
          ..write('slotRef: $slotRef, ')
          ..write('fromDate: $fromDate, ')
          ..write('toDate: $toDate')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    crewChangeId,
    ccId,
    partnershipAbbrev,
    slotRef,
    fromDate,
    toDate,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalAssignment &&
          other.id == this.id &&
          other.crewChangeId == this.crewChangeId &&
          other.ccId == this.ccId &&
          other.partnershipAbbrev == this.partnershipAbbrev &&
          other.slotRef == this.slotRef &&
          other.fromDate == this.fromDate &&
          other.toDate == this.toDate);
}

class AssignmentsCompanion extends UpdateCompanion<LocalAssignment> {
  final Value<int> id;
  final Value<int> crewChangeId;
  final Value<String> ccId;
  final Value<String> partnershipAbbrev;
  final Value<int> slotRef;
  final Value<String> fromDate;
  final Value<String> toDate;
  const AssignmentsCompanion({
    this.id = const Value.absent(),
    this.crewChangeId = const Value.absent(),
    this.ccId = const Value.absent(),
    this.partnershipAbbrev = const Value.absent(),
    this.slotRef = const Value.absent(),
    this.fromDate = const Value.absent(),
    this.toDate = const Value.absent(),
  });
  AssignmentsCompanion.insert({
    this.id = const Value.absent(),
    required int crewChangeId,
    required String ccId,
    required String partnershipAbbrev,
    required int slotRef,
    required String fromDate,
    required String toDate,
  }) : crewChangeId = Value(crewChangeId),
       ccId = Value(ccId),
       partnershipAbbrev = Value(partnershipAbbrev),
       slotRef = Value(slotRef),
       fromDate = Value(fromDate),
       toDate = Value(toDate);
  static Insertable<LocalAssignment> custom({
    Expression<int>? id,
    Expression<int>? crewChangeId,
    Expression<String>? ccId,
    Expression<String>? partnershipAbbrev,
    Expression<int>? slotRef,
    Expression<String>? fromDate,
    Expression<String>? toDate,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (crewChangeId != null) 'crew_change_id': crewChangeId,
      if (ccId != null) 'cc_id': ccId,
      if (partnershipAbbrev != null) 'partnership_abbrev': partnershipAbbrev,
      if (slotRef != null) 'slot_ref': slotRef,
      if (fromDate != null) 'from_date': fromDate,
      if (toDate != null) 'to_date': toDate,
    });
  }

  AssignmentsCompanion copyWith({
    Value<int>? id,
    Value<int>? crewChangeId,
    Value<String>? ccId,
    Value<String>? partnershipAbbrev,
    Value<int>? slotRef,
    Value<String>? fromDate,
    Value<String>? toDate,
  }) {
    return AssignmentsCompanion(
      id: id ?? this.id,
      crewChangeId: crewChangeId ?? this.crewChangeId,
      ccId: ccId ?? this.ccId,
      partnershipAbbrev: partnershipAbbrev ?? this.partnershipAbbrev,
      slotRef: slotRef ?? this.slotRef,
      fromDate: fromDate ?? this.fromDate,
      toDate: toDate ?? this.toDate,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (crewChangeId.present) {
      map['crew_change_id'] = Variable<int>(crewChangeId.value);
    }
    if (ccId.present) {
      map['cc_id'] = Variable<String>(ccId.value);
    }
    if (partnershipAbbrev.present) {
      map['partnership_abbrev'] = Variable<String>(partnershipAbbrev.value);
    }
    if (slotRef.present) {
      map['slot_ref'] = Variable<int>(slotRef.value);
    }
    if (fromDate.present) {
      map['from_date'] = Variable<String>(fromDate.value);
    }
    if (toDate.present) {
      map['to_date'] = Variable<String>(toDate.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('AssignmentsCompanion(')
          ..write('id: $id, ')
          ..write('crewChangeId: $crewChangeId, ')
          ..write('ccId: $ccId, ')
          ..write('partnershipAbbrev: $partnershipAbbrev, ')
          ..write('slotRef: $slotRef, ')
          ..write('fromDate: $fromDate, ')
          ..write('toDate: $toDate')
          ..write(')'))
        .toString();
  }
}

class $LeaveRecordsTable extends LeaveRecords
    with TableInfo<$LeaveRecordsTable, LocalLeave> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LeaveRecordsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _fromDateMeta = const VerificationMeta(
    'fromDate',
  );
  @override
  late final GeneratedColumn<String> fromDate = GeneratedColumn<String>(
    'from_date',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _toDateMeta = const VerificationMeta('toDate');
  @override
  late final GeneratedColumn<String> toDate = GeneratedColumn<String>(
    'to_date',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<String> status = GeneratedColumn<String>(
    'status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [id, kind, fromDate, toDate, status];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'leave_records';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalLeave> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('from_date')) {
      context.handle(
        _fromDateMeta,
        fromDate.isAcceptableOrUnknown(data['from_date']!, _fromDateMeta),
      );
    } else if (isInserting) {
      context.missing(_fromDateMeta);
    }
    if (data.containsKey('to_date')) {
      context.handle(
        _toDateMeta,
        toDate.isAcceptableOrUnknown(data['to_date']!, _toDateMeta),
      );
    } else if (isInserting) {
      context.missing(_toDateMeta);
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    } else if (isInserting) {
      context.missing(_statusMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalLeave map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalLeave(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      fromDate: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}from_date'],
      )!,
      toDate: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}to_date'],
      )!,
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}status'],
      )!,
    );
  }

  @override
  $LeaveRecordsTable createAlias(String alias) {
    return $LeaveRecordsTable(attachedDatabase, alias);
  }
}

class LocalLeave extends DataClass implements Insertable<LocalLeave> {
  final int id;
  final String kind;
  final String fromDate;
  final String toDate;
  final String status;
  const LocalLeave({
    required this.id,
    required this.kind,
    required this.fromDate,
    required this.toDate,
    required this.status,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['kind'] = Variable<String>(kind);
    map['from_date'] = Variable<String>(fromDate);
    map['to_date'] = Variable<String>(toDate);
    map['status'] = Variable<String>(status);
    return map;
  }

  LeaveRecordsCompanion toCompanion(bool nullToAbsent) {
    return LeaveRecordsCompanion(
      id: Value(id),
      kind: Value(kind),
      fromDate: Value(fromDate),
      toDate: Value(toDate),
      status: Value(status),
    );
  }

  factory LocalLeave.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalLeave(
      id: serializer.fromJson<int>(json['id']),
      kind: serializer.fromJson<String>(json['kind']),
      fromDate: serializer.fromJson<String>(json['fromDate']),
      toDate: serializer.fromJson<String>(json['toDate']),
      status: serializer.fromJson<String>(json['status']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'kind': serializer.toJson<String>(kind),
      'fromDate': serializer.toJson<String>(fromDate),
      'toDate': serializer.toJson<String>(toDate),
      'status': serializer.toJson<String>(status),
    };
  }

  LocalLeave copyWith({
    int? id,
    String? kind,
    String? fromDate,
    String? toDate,
    String? status,
  }) => LocalLeave(
    id: id ?? this.id,
    kind: kind ?? this.kind,
    fromDate: fromDate ?? this.fromDate,
    toDate: toDate ?? this.toDate,
    status: status ?? this.status,
  );
  LocalLeave copyWithCompanion(LeaveRecordsCompanion data) {
    return LocalLeave(
      id: data.id.present ? data.id.value : this.id,
      kind: data.kind.present ? data.kind.value : this.kind,
      fromDate: data.fromDate.present ? data.fromDate.value : this.fromDate,
      toDate: data.toDate.present ? data.toDate.value : this.toDate,
      status: data.status.present ? data.status.value : this.status,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalLeave(')
          ..write('id: $id, ')
          ..write('kind: $kind, ')
          ..write('fromDate: $fromDate, ')
          ..write('toDate: $toDate, ')
          ..write('status: $status')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, kind, fromDate, toDate, status);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalLeave &&
          other.id == this.id &&
          other.kind == this.kind &&
          other.fromDate == this.fromDate &&
          other.toDate == this.toDate &&
          other.status == this.status);
}

class LeaveRecordsCompanion extends UpdateCompanion<LocalLeave> {
  final Value<int> id;
  final Value<String> kind;
  final Value<String> fromDate;
  final Value<String> toDate;
  final Value<String> status;
  const LeaveRecordsCompanion({
    this.id = const Value.absent(),
    this.kind = const Value.absent(),
    this.fromDate = const Value.absent(),
    this.toDate = const Value.absent(),
    this.status = const Value.absent(),
  });
  LeaveRecordsCompanion.insert({
    this.id = const Value.absent(),
    required String kind,
    required String fromDate,
    required String toDate,
    required String status,
  }) : kind = Value(kind),
       fromDate = Value(fromDate),
       toDate = Value(toDate),
       status = Value(status);
  static Insertable<LocalLeave> custom({
    Expression<int>? id,
    Expression<String>? kind,
    Expression<String>? fromDate,
    Expression<String>? toDate,
    Expression<String>? status,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (kind != null) 'kind': kind,
      if (fromDate != null) 'from_date': fromDate,
      if (toDate != null) 'to_date': toDate,
      if (status != null) 'status': status,
    });
  }

  LeaveRecordsCompanion copyWith({
    Value<int>? id,
    Value<String>? kind,
    Value<String>? fromDate,
    Value<String>? toDate,
    Value<String>? status,
  }) {
    return LeaveRecordsCompanion(
      id: id ?? this.id,
      kind: kind ?? this.kind,
      fromDate: fromDate ?? this.fromDate,
      toDate: toDate ?? this.toDate,
      status: status ?? this.status,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (fromDate.present) {
      map['from_date'] = Variable<String>(fromDate.value);
    }
    if (toDate.present) {
      map['to_date'] = Variable<String>(toDate.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(status.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LeaveRecordsCompanion(')
          ..write('id: $id, ')
          ..write('kind: $kind, ')
          ..write('fromDate: $fromDate, ')
          ..write('toDate: $toDate, ')
          ..write('status: $status')
          ..write(')'))
        .toString();
  }
}

class $NotificationsTable extends Notifications
    with TableInfo<$NotificationsTable, LocalNotification> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $NotificationsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _bodyMeta = const VerificationMeta('body');
  @override
  late final GeneratedColumn<String> body = GeneratedColumn<String>(
    'body',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _deepLinkMeta = const VerificationMeta(
    'deepLink',
  );
  @override
  late final GeneratedColumn<String> deepLink = GeneratedColumn<String>(
    'deep_link',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _readAtMeta = const VerificationMeta('readAt');
  @override
  late final GeneratedColumn<DateTime> readAt = GeneratedColumn<DateTime>(
    'read_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    kind,
    title,
    body,
    deepLink,
    createdAt,
    readAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'notifications';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalNotification> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('body')) {
      context.handle(
        _bodyMeta,
        body.isAcceptableOrUnknown(data['body']!, _bodyMeta),
      );
    }
    if (data.containsKey('deep_link')) {
      context.handle(
        _deepLinkMeta,
        deepLink.isAcceptableOrUnknown(data['deep_link']!, _deepLinkMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    if (data.containsKey('read_at')) {
      context.handle(
        _readAtMeta,
        readAt.isAcceptableOrUnknown(data['read_at']!, _readAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalNotification map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalNotification(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      body: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}body'],
      ),
      deepLink: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}deep_link'],
      ),
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      readAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}read_at'],
      ),
    );
  }

  @override
  $NotificationsTable createAlias(String alias) {
    return $NotificationsTable(attachedDatabase, alias);
  }
}

class LocalNotification extends DataClass
    implements Insertable<LocalNotification> {
  final int id;
  final String kind;
  final String title;
  final String? body;
  final String? deepLink;
  final DateTime createdAt;
  final DateTime? readAt;
  const LocalNotification({
    required this.id,
    required this.kind,
    required this.title,
    this.body,
    this.deepLink,
    required this.createdAt,
    this.readAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['kind'] = Variable<String>(kind);
    map['title'] = Variable<String>(title);
    if (!nullToAbsent || body != null) {
      map['body'] = Variable<String>(body);
    }
    if (!nullToAbsent || deepLink != null) {
      map['deep_link'] = Variable<String>(deepLink);
    }
    map['created_at'] = Variable<DateTime>(createdAt);
    if (!nullToAbsent || readAt != null) {
      map['read_at'] = Variable<DateTime>(readAt);
    }
    return map;
  }

  NotificationsCompanion toCompanion(bool nullToAbsent) {
    return NotificationsCompanion(
      id: Value(id),
      kind: Value(kind),
      title: Value(title),
      body: body == null && nullToAbsent ? const Value.absent() : Value(body),
      deepLink: deepLink == null && nullToAbsent
          ? const Value.absent()
          : Value(deepLink),
      createdAt: Value(createdAt),
      readAt: readAt == null && nullToAbsent
          ? const Value.absent()
          : Value(readAt),
    );
  }

  factory LocalNotification.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalNotification(
      id: serializer.fromJson<int>(json['id']),
      kind: serializer.fromJson<String>(json['kind']),
      title: serializer.fromJson<String>(json['title']),
      body: serializer.fromJson<String?>(json['body']),
      deepLink: serializer.fromJson<String?>(json['deepLink']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      readAt: serializer.fromJson<DateTime?>(json['readAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'kind': serializer.toJson<String>(kind),
      'title': serializer.toJson<String>(title),
      'body': serializer.toJson<String?>(body),
      'deepLink': serializer.toJson<String?>(deepLink),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'readAt': serializer.toJson<DateTime?>(readAt),
    };
  }

  LocalNotification copyWith({
    int? id,
    String? kind,
    String? title,
    Value<String?> body = const Value.absent(),
    Value<String?> deepLink = const Value.absent(),
    DateTime? createdAt,
    Value<DateTime?> readAt = const Value.absent(),
  }) => LocalNotification(
    id: id ?? this.id,
    kind: kind ?? this.kind,
    title: title ?? this.title,
    body: body.present ? body.value : this.body,
    deepLink: deepLink.present ? deepLink.value : this.deepLink,
    createdAt: createdAt ?? this.createdAt,
    readAt: readAt.present ? readAt.value : this.readAt,
  );
  LocalNotification copyWithCompanion(NotificationsCompanion data) {
    return LocalNotification(
      id: data.id.present ? data.id.value : this.id,
      kind: data.kind.present ? data.kind.value : this.kind,
      title: data.title.present ? data.title.value : this.title,
      body: data.body.present ? data.body.value : this.body,
      deepLink: data.deepLink.present ? data.deepLink.value : this.deepLink,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      readAt: data.readAt.present ? data.readAt.value : this.readAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalNotification(')
          ..write('id: $id, ')
          ..write('kind: $kind, ')
          ..write('title: $title, ')
          ..write('body: $body, ')
          ..write('deepLink: $deepLink, ')
          ..write('createdAt: $createdAt, ')
          ..write('readAt: $readAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(id, kind, title, body, deepLink, createdAt, readAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalNotification &&
          other.id == this.id &&
          other.kind == this.kind &&
          other.title == this.title &&
          other.body == this.body &&
          other.deepLink == this.deepLink &&
          other.createdAt == this.createdAt &&
          other.readAt == this.readAt);
}

class NotificationsCompanion extends UpdateCompanion<LocalNotification> {
  final Value<int> id;
  final Value<String> kind;
  final Value<String> title;
  final Value<String?> body;
  final Value<String?> deepLink;
  final Value<DateTime> createdAt;
  final Value<DateTime?> readAt;
  const NotificationsCompanion({
    this.id = const Value.absent(),
    this.kind = const Value.absent(),
    this.title = const Value.absent(),
    this.body = const Value.absent(),
    this.deepLink = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.readAt = const Value.absent(),
  });
  NotificationsCompanion.insert({
    this.id = const Value.absent(),
    required String kind,
    required String title,
    this.body = const Value.absent(),
    this.deepLink = const Value.absent(),
    required DateTime createdAt,
    this.readAt = const Value.absent(),
  }) : kind = Value(kind),
       title = Value(title),
       createdAt = Value(createdAt);
  static Insertable<LocalNotification> custom({
    Expression<int>? id,
    Expression<String>? kind,
    Expression<String>? title,
    Expression<String>? body,
    Expression<String>? deepLink,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? readAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (kind != null) 'kind': kind,
      if (title != null) 'title': title,
      if (body != null) 'body': body,
      if (deepLink != null) 'deep_link': deepLink,
      if (createdAt != null) 'created_at': createdAt,
      if (readAt != null) 'read_at': readAt,
    });
  }

  NotificationsCompanion copyWith({
    Value<int>? id,
    Value<String>? kind,
    Value<String>? title,
    Value<String?>? body,
    Value<String?>? deepLink,
    Value<DateTime>? createdAt,
    Value<DateTime?>? readAt,
  }) {
    return NotificationsCompanion(
      id: id ?? this.id,
      kind: kind ?? this.kind,
      title: title ?? this.title,
      body: body ?? this.body,
      deepLink: deepLink ?? this.deepLink,
      createdAt: createdAt ?? this.createdAt,
      readAt: readAt ?? this.readAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (body.present) {
      map['body'] = Variable<String>(body.value);
    }
    if (deepLink.present) {
      map['deep_link'] = Variable<String>(deepLink.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (readAt.present) {
      map['read_at'] = Variable<DateTime>(readAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('NotificationsCompanion(')
          ..write('id: $id, ')
          ..write('kind: $kind, ')
          ..write('title: $title, ')
          ..write('body: $body, ')
          ..write('deepLink: $deepLink, ')
          ..write('createdAt: $createdAt, ')
          ..write('readAt: $readAt')
          ..write(')'))
        .toString();
  }
}

class $SubmissionsTable extends Submissions
    with TableInfo<$SubmissionsTable, LocalSubmission> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $SubmissionsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _publicIdMeta = const VerificationMeta(
    'publicId',
  );
  @override
  late final GeneratedColumn<String> publicId = GeneratedColumn<String>(
    'public_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _requirementHintIdMeta = const VerificationMeta(
    'requirementHintId',
  );
  @override
  late final GeneratedColumn<int> requirementHintId = GeneratedColumn<int>(
    'requirement_hint_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _sourceMeta = const VerificationMeta('source');
  @override
  late final GeneratedColumn<String> source = GeneratedColumn<String>(
    'source',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _contentTypeMeta = const VerificationMeta(
    'contentType',
  );
  @override
  late final GeneratedColumn<String> contentType = GeneratedColumn<String>(
    'content_type',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _declaredSizeMeta = const VerificationMeta(
    'declaredSize',
  );
  @override
  late final GeneratedColumn<int> declaredSize = GeneratedColumn<int>(
    'declared_size',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _uploadOffsetMeta = const VerificationMeta(
    'uploadOffset',
  );
  @override
  late final GeneratedColumn<int> uploadOffset = GeneratedColumn<int>(
    'upload_offset',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _uploadCompleteMeta = const VerificationMeta(
    'uploadComplete',
  );
  @override
  late final GeneratedColumn<bool> uploadComplete = GeneratedColumn<bool>(
    'upload_complete',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("upload_complete" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _verificationStatusMeta =
      const VerificationMeta('verificationStatus');
  @override
  late final GeneratedColumn<String> verificationStatus =
      GeneratedColumn<String>(
        'verification_status',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
      );
  static const VerificationMeta _rejectionReasonMeta = const VerificationMeta(
    'rejectionReason',
  );
  @override
  late final GeneratedColumn<String> rejectionReason = GeneratedColumn<String>(
    'rejection_reason',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _submittedAtMeta = const VerificationMeta(
    'submittedAt',
  );
  @override
  late final GeneratedColumn<DateTime> submittedAt = GeneratedColumn<DateTime>(
    'submitted_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _localPathMeta = const VerificationMeta(
    'localPath',
  );
  @override
  late final GeneratedColumn<String> localPath = GeneratedColumn<String>(
    'local_path',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    publicId,
    requirementHintId,
    source,
    contentType,
    declaredSize,
    uploadOffset,
    uploadComplete,
    verificationStatus,
    rejectionReason,
    submittedAt,
    localPath,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'submissions';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalSubmission> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('public_id')) {
      context.handle(
        _publicIdMeta,
        publicId.isAcceptableOrUnknown(data['public_id']!, _publicIdMeta),
      );
    } else if (isInserting) {
      context.missing(_publicIdMeta);
    }
    if (data.containsKey('requirement_hint_id')) {
      context.handle(
        _requirementHintIdMeta,
        requirementHintId.isAcceptableOrUnknown(
          data['requirement_hint_id']!,
          _requirementHintIdMeta,
        ),
      );
    }
    if (data.containsKey('source')) {
      context.handle(
        _sourceMeta,
        source.isAcceptableOrUnknown(data['source']!, _sourceMeta),
      );
    } else if (isInserting) {
      context.missing(_sourceMeta);
    }
    if (data.containsKey('content_type')) {
      context.handle(
        _contentTypeMeta,
        contentType.isAcceptableOrUnknown(
          data['content_type']!,
          _contentTypeMeta,
        ),
      );
    }
    if (data.containsKey('declared_size')) {
      context.handle(
        _declaredSizeMeta,
        declaredSize.isAcceptableOrUnknown(
          data['declared_size']!,
          _declaredSizeMeta,
        ),
      );
    }
    if (data.containsKey('upload_offset')) {
      context.handle(
        _uploadOffsetMeta,
        uploadOffset.isAcceptableOrUnknown(
          data['upload_offset']!,
          _uploadOffsetMeta,
        ),
      );
    }
    if (data.containsKey('upload_complete')) {
      context.handle(
        _uploadCompleteMeta,
        uploadComplete.isAcceptableOrUnknown(
          data['upload_complete']!,
          _uploadCompleteMeta,
        ),
      );
    }
    if (data.containsKey('verification_status')) {
      context.handle(
        _verificationStatusMeta,
        verificationStatus.isAcceptableOrUnknown(
          data['verification_status']!,
          _verificationStatusMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_verificationStatusMeta);
    }
    if (data.containsKey('rejection_reason')) {
      context.handle(
        _rejectionReasonMeta,
        rejectionReason.isAcceptableOrUnknown(
          data['rejection_reason']!,
          _rejectionReasonMeta,
        ),
      );
    }
    if (data.containsKey('submitted_at')) {
      context.handle(
        _submittedAtMeta,
        submittedAt.isAcceptableOrUnknown(
          data['submitted_at']!,
          _submittedAtMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_submittedAtMeta);
    }
    if (data.containsKey('local_path')) {
      context.handle(
        _localPathMeta,
        localPath.isAcceptableOrUnknown(data['local_path']!, _localPathMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {publicId};
  @override
  LocalSubmission map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalSubmission(
      publicId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}public_id'],
      )!,
      requirementHintId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}requirement_hint_id'],
      ),
      source: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}source'],
      )!,
      contentType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}content_type'],
      ),
      declaredSize: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}declared_size'],
      ),
      uploadOffset: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}upload_offset'],
      )!,
      uploadComplete: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}upload_complete'],
      )!,
      verificationStatus: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}verification_status'],
      )!,
      rejectionReason: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}rejection_reason'],
      ),
      submittedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}submitted_at'],
      )!,
      localPath: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}local_path'],
      ),
    );
  }

  @override
  $SubmissionsTable createAlias(String alias) {
    return $SubmissionsTable(attachedDatabase, alias);
  }
}

class LocalSubmission extends DataClass implements Insertable<LocalSubmission> {
  /// The device-generated id: primary key here as well as on the server, because the device
  /// minted it before the server ever saw the submission (§7.6).
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

  /// Local path of the captured file, if it is still on the device awaiting upload.
  final String? localPath;
  const LocalSubmission({
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
    this.localPath,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['public_id'] = Variable<String>(publicId);
    if (!nullToAbsent || requirementHintId != null) {
      map['requirement_hint_id'] = Variable<int>(requirementHintId);
    }
    map['source'] = Variable<String>(source);
    if (!nullToAbsent || contentType != null) {
      map['content_type'] = Variable<String>(contentType);
    }
    if (!nullToAbsent || declaredSize != null) {
      map['declared_size'] = Variable<int>(declaredSize);
    }
    map['upload_offset'] = Variable<int>(uploadOffset);
    map['upload_complete'] = Variable<bool>(uploadComplete);
    map['verification_status'] = Variable<String>(verificationStatus);
    if (!nullToAbsent || rejectionReason != null) {
      map['rejection_reason'] = Variable<String>(rejectionReason);
    }
    map['submitted_at'] = Variable<DateTime>(submittedAt);
    if (!nullToAbsent || localPath != null) {
      map['local_path'] = Variable<String>(localPath);
    }
    return map;
  }

  SubmissionsCompanion toCompanion(bool nullToAbsent) {
    return SubmissionsCompanion(
      publicId: Value(publicId),
      requirementHintId: requirementHintId == null && nullToAbsent
          ? const Value.absent()
          : Value(requirementHintId),
      source: Value(source),
      contentType: contentType == null && nullToAbsent
          ? const Value.absent()
          : Value(contentType),
      declaredSize: declaredSize == null && nullToAbsent
          ? const Value.absent()
          : Value(declaredSize),
      uploadOffset: Value(uploadOffset),
      uploadComplete: Value(uploadComplete),
      verificationStatus: Value(verificationStatus),
      rejectionReason: rejectionReason == null && nullToAbsent
          ? const Value.absent()
          : Value(rejectionReason),
      submittedAt: Value(submittedAt),
      localPath: localPath == null && nullToAbsent
          ? const Value.absent()
          : Value(localPath),
    );
  }

  factory LocalSubmission.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalSubmission(
      publicId: serializer.fromJson<String>(json['publicId']),
      requirementHintId: serializer.fromJson<int?>(json['requirementHintId']),
      source: serializer.fromJson<String>(json['source']),
      contentType: serializer.fromJson<String?>(json['contentType']),
      declaredSize: serializer.fromJson<int?>(json['declaredSize']),
      uploadOffset: serializer.fromJson<int>(json['uploadOffset']),
      uploadComplete: serializer.fromJson<bool>(json['uploadComplete']),
      verificationStatus: serializer.fromJson<String>(
        json['verificationStatus'],
      ),
      rejectionReason: serializer.fromJson<String?>(json['rejectionReason']),
      submittedAt: serializer.fromJson<DateTime>(json['submittedAt']),
      localPath: serializer.fromJson<String?>(json['localPath']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'publicId': serializer.toJson<String>(publicId),
      'requirementHintId': serializer.toJson<int?>(requirementHintId),
      'source': serializer.toJson<String>(source),
      'contentType': serializer.toJson<String?>(contentType),
      'declaredSize': serializer.toJson<int?>(declaredSize),
      'uploadOffset': serializer.toJson<int>(uploadOffset),
      'uploadComplete': serializer.toJson<bool>(uploadComplete),
      'verificationStatus': serializer.toJson<String>(verificationStatus),
      'rejectionReason': serializer.toJson<String?>(rejectionReason),
      'submittedAt': serializer.toJson<DateTime>(submittedAt),
      'localPath': serializer.toJson<String?>(localPath),
    };
  }

  LocalSubmission copyWith({
    String? publicId,
    Value<int?> requirementHintId = const Value.absent(),
    String? source,
    Value<String?> contentType = const Value.absent(),
    Value<int?> declaredSize = const Value.absent(),
    int? uploadOffset,
    bool? uploadComplete,
    String? verificationStatus,
    Value<String?> rejectionReason = const Value.absent(),
    DateTime? submittedAt,
    Value<String?> localPath = const Value.absent(),
  }) => LocalSubmission(
    publicId: publicId ?? this.publicId,
    requirementHintId: requirementHintId.present
        ? requirementHintId.value
        : this.requirementHintId,
    source: source ?? this.source,
    contentType: contentType.present ? contentType.value : this.contentType,
    declaredSize: declaredSize.present ? declaredSize.value : this.declaredSize,
    uploadOffset: uploadOffset ?? this.uploadOffset,
    uploadComplete: uploadComplete ?? this.uploadComplete,
    verificationStatus: verificationStatus ?? this.verificationStatus,
    rejectionReason: rejectionReason.present
        ? rejectionReason.value
        : this.rejectionReason,
    submittedAt: submittedAt ?? this.submittedAt,
    localPath: localPath.present ? localPath.value : this.localPath,
  );
  LocalSubmission copyWithCompanion(SubmissionsCompanion data) {
    return LocalSubmission(
      publicId: data.publicId.present ? data.publicId.value : this.publicId,
      requirementHintId: data.requirementHintId.present
          ? data.requirementHintId.value
          : this.requirementHintId,
      source: data.source.present ? data.source.value : this.source,
      contentType: data.contentType.present
          ? data.contentType.value
          : this.contentType,
      declaredSize: data.declaredSize.present
          ? data.declaredSize.value
          : this.declaredSize,
      uploadOffset: data.uploadOffset.present
          ? data.uploadOffset.value
          : this.uploadOffset,
      uploadComplete: data.uploadComplete.present
          ? data.uploadComplete.value
          : this.uploadComplete,
      verificationStatus: data.verificationStatus.present
          ? data.verificationStatus.value
          : this.verificationStatus,
      rejectionReason: data.rejectionReason.present
          ? data.rejectionReason.value
          : this.rejectionReason,
      submittedAt: data.submittedAt.present
          ? data.submittedAt.value
          : this.submittedAt,
      localPath: data.localPath.present ? data.localPath.value : this.localPath,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalSubmission(')
          ..write('publicId: $publicId, ')
          ..write('requirementHintId: $requirementHintId, ')
          ..write('source: $source, ')
          ..write('contentType: $contentType, ')
          ..write('declaredSize: $declaredSize, ')
          ..write('uploadOffset: $uploadOffset, ')
          ..write('uploadComplete: $uploadComplete, ')
          ..write('verificationStatus: $verificationStatus, ')
          ..write('rejectionReason: $rejectionReason, ')
          ..write('submittedAt: $submittedAt, ')
          ..write('localPath: $localPath')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    publicId,
    requirementHintId,
    source,
    contentType,
    declaredSize,
    uploadOffset,
    uploadComplete,
    verificationStatus,
    rejectionReason,
    submittedAt,
    localPath,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalSubmission &&
          other.publicId == this.publicId &&
          other.requirementHintId == this.requirementHintId &&
          other.source == this.source &&
          other.contentType == this.contentType &&
          other.declaredSize == this.declaredSize &&
          other.uploadOffset == this.uploadOffset &&
          other.uploadComplete == this.uploadComplete &&
          other.verificationStatus == this.verificationStatus &&
          other.rejectionReason == this.rejectionReason &&
          other.submittedAt == this.submittedAt &&
          other.localPath == this.localPath);
}

class SubmissionsCompanion extends UpdateCompanion<LocalSubmission> {
  final Value<String> publicId;
  final Value<int?> requirementHintId;
  final Value<String> source;
  final Value<String?> contentType;
  final Value<int?> declaredSize;
  final Value<int> uploadOffset;
  final Value<bool> uploadComplete;
  final Value<String> verificationStatus;
  final Value<String?> rejectionReason;
  final Value<DateTime> submittedAt;
  final Value<String?> localPath;
  final Value<int> rowid;
  const SubmissionsCompanion({
    this.publicId = const Value.absent(),
    this.requirementHintId = const Value.absent(),
    this.source = const Value.absent(),
    this.contentType = const Value.absent(),
    this.declaredSize = const Value.absent(),
    this.uploadOffset = const Value.absent(),
    this.uploadComplete = const Value.absent(),
    this.verificationStatus = const Value.absent(),
    this.rejectionReason = const Value.absent(),
    this.submittedAt = const Value.absent(),
    this.localPath = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  SubmissionsCompanion.insert({
    required String publicId,
    this.requirementHintId = const Value.absent(),
    required String source,
    this.contentType = const Value.absent(),
    this.declaredSize = const Value.absent(),
    this.uploadOffset = const Value.absent(),
    this.uploadComplete = const Value.absent(),
    required String verificationStatus,
    this.rejectionReason = const Value.absent(),
    required DateTime submittedAt,
    this.localPath = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : publicId = Value(publicId),
       source = Value(source),
       verificationStatus = Value(verificationStatus),
       submittedAt = Value(submittedAt);
  static Insertable<LocalSubmission> custom({
    Expression<String>? publicId,
    Expression<int>? requirementHintId,
    Expression<String>? source,
    Expression<String>? contentType,
    Expression<int>? declaredSize,
    Expression<int>? uploadOffset,
    Expression<bool>? uploadComplete,
    Expression<String>? verificationStatus,
    Expression<String>? rejectionReason,
    Expression<DateTime>? submittedAt,
    Expression<String>? localPath,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (publicId != null) 'public_id': publicId,
      if (requirementHintId != null) 'requirement_hint_id': requirementHintId,
      if (source != null) 'source': source,
      if (contentType != null) 'content_type': contentType,
      if (declaredSize != null) 'declared_size': declaredSize,
      if (uploadOffset != null) 'upload_offset': uploadOffset,
      if (uploadComplete != null) 'upload_complete': uploadComplete,
      if (verificationStatus != null) 'verification_status': verificationStatus,
      if (rejectionReason != null) 'rejection_reason': rejectionReason,
      if (submittedAt != null) 'submitted_at': submittedAt,
      if (localPath != null) 'local_path': localPath,
      if (rowid != null) 'rowid': rowid,
    });
  }

  SubmissionsCompanion copyWith({
    Value<String>? publicId,
    Value<int?>? requirementHintId,
    Value<String>? source,
    Value<String?>? contentType,
    Value<int?>? declaredSize,
    Value<int>? uploadOffset,
    Value<bool>? uploadComplete,
    Value<String>? verificationStatus,
    Value<String?>? rejectionReason,
    Value<DateTime>? submittedAt,
    Value<String?>? localPath,
    Value<int>? rowid,
  }) {
    return SubmissionsCompanion(
      publicId: publicId ?? this.publicId,
      requirementHintId: requirementHintId ?? this.requirementHintId,
      source: source ?? this.source,
      contentType: contentType ?? this.contentType,
      declaredSize: declaredSize ?? this.declaredSize,
      uploadOffset: uploadOffset ?? this.uploadOffset,
      uploadComplete: uploadComplete ?? this.uploadComplete,
      verificationStatus: verificationStatus ?? this.verificationStatus,
      rejectionReason: rejectionReason ?? this.rejectionReason,
      submittedAt: submittedAt ?? this.submittedAt,
      localPath: localPath ?? this.localPath,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (publicId.present) {
      map['public_id'] = Variable<String>(publicId.value);
    }
    if (requirementHintId.present) {
      map['requirement_hint_id'] = Variable<int>(requirementHintId.value);
    }
    if (source.present) {
      map['source'] = Variable<String>(source.value);
    }
    if (contentType.present) {
      map['content_type'] = Variable<String>(contentType.value);
    }
    if (declaredSize.present) {
      map['declared_size'] = Variable<int>(declaredSize.value);
    }
    if (uploadOffset.present) {
      map['upload_offset'] = Variable<int>(uploadOffset.value);
    }
    if (uploadComplete.present) {
      map['upload_complete'] = Variable<bool>(uploadComplete.value);
    }
    if (verificationStatus.present) {
      map['verification_status'] = Variable<String>(verificationStatus.value);
    }
    if (rejectionReason.present) {
      map['rejection_reason'] = Variable<String>(rejectionReason.value);
    }
    if (submittedAt.present) {
      map['submitted_at'] = Variable<DateTime>(submittedAt.value);
    }
    if (localPath.present) {
      map['local_path'] = Variable<String>(localPath.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('SubmissionsCompanion(')
          ..write('publicId: $publicId, ')
          ..write('requirementHintId: $requirementHintId, ')
          ..write('source: $source, ')
          ..write('contentType: $contentType, ')
          ..write('declaredSize: $declaredSize, ')
          ..write('uploadOffset: $uploadOffset, ')
          ..write('uploadComplete: $uploadComplete, ')
          ..write('verificationStatus: $verificationStatus, ')
          ..write('rejectionReason: $rejectionReason, ')
          ..write('submittedAt: $submittedAt, ')
          ..write('localPath: $localPath, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $RequirementsTable extends Requirements
    with TableInfo<$RequirementsTable, LocalRequirement> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $RequirementsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _codeMeta = const VerificationMeta('code');
  @override
  late final GeneratedColumn<String> code = GeneratedColumn<String>(
    'code',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _categoryMeta = const VerificationMeta(
    'category',
  );
  @override
  late final GeneratedColumn<String> category = GeneratedColumn<String>(
    'category',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _titleMeta = const VerificationMeta('title');
  @override
  late final GeneratedColumn<String> title = GeneratedColumn<String>(
    'title',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<String> status = GeneratedColumn<String>(
    'status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _issuingAuthorityMeta = const VerificationMeta(
    'issuingAuthority',
  );
  @override
  late final GeneratedColumn<String> issuingAuthority = GeneratedColumn<String>(
    'issuing_authority',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    code,
    category,
    title,
    status,
    issuingAuthority,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'requirements';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalRequirement> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('code')) {
      context.handle(
        _codeMeta,
        code.isAcceptableOrUnknown(data['code']!, _codeMeta),
      );
    } else if (isInserting) {
      context.missing(_codeMeta);
    }
    if (data.containsKey('category')) {
      context.handle(
        _categoryMeta,
        category.isAcceptableOrUnknown(data['category']!, _categoryMeta),
      );
    } else if (isInserting) {
      context.missing(_categoryMeta);
    }
    if (data.containsKey('title')) {
      context.handle(
        _titleMeta,
        title.isAcceptableOrUnknown(data['title']!, _titleMeta),
      );
    } else if (isInserting) {
      context.missing(_titleMeta);
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    } else if (isInserting) {
      context.missing(_statusMeta);
    }
    if (data.containsKey('issuing_authority')) {
      context.handle(
        _issuingAuthorityMeta,
        issuingAuthority.isAcceptableOrUnknown(
          data['issuing_authority']!,
          _issuingAuthorityMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalRequirement map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalRequirement(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      code: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}code'],
      )!,
      category: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}category'],
      )!,
      title: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}title'],
      )!,
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}status'],
      )!,
      issuingAuthority: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}issuing_authority'],
      ),
    );
  }

  @override
  $RequirementsTable createAlias(String alias) {
    return $RequirementsTable(attachedDatabase, alias);
  }
}

class LocalRequirement extends DataClass
    implements Insertable<LocalRequirement> {
  final int id;
  final String code;
  final String category;
  final String title;
  final String status;
  final String? issuingAuthority;
  const LocalRequirement({
    required this.id,
    required this.code,
    required this.category,
    required this.title,
    required this.status,
    this.issuingAuthority,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['code'] = Variable<String>(code);
    map['category'] = Variable<String>(category);
    map['title'] = Variable<String>(title);
    map['status'] = Variable<String>(status);
    if (!nullToAbsent || issuingAuthority != null) {
      map['issuing_authority'] = Variable<String>(issuingAuthority);
    }
    return map;
  }

  RequirementsCompanion toCompanion(bool nullToAbsent) {
    return RequirementsCompanion(
      id: Value(id),
      code: Value(code),
      category: Value(category),
      title: Value(title),
      status: Value(status),
      issuingAuthority: issuingAuthority == null && nullToAbsent
          ? const Value.absent()
          : Value(issuingAuthority),
    );
  }

  factory LocalRequirement.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalRequirement(
      id: serializer.fromJson<int>(json['id']),
      code: serializer.fromJson<String>(json['code']),
      category: serializer.fromJson<String>(json['category']),
      title: serializer.fromJson<String>(json['title']),
      status: serializer.fromJson<String>(json['status']),
      issuingAuthority: serializer.fromJson<String?>(json['issuingAuthority']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'code': serializer.toJson<String>(code),
      'category': serializer.toJson<String>(category),
      'title': serializer.toJson<String>(title),
      'status': serializer.toJson<String>(status),
      'issuingAuthority': serializer.toJson<String?>(issuingAuthority),
    };
  }

  LocalRequirement copyWith({
    int? id,
    String? code,
    String? category,
    String? title,
    String? status,
    Value<String?> issuingAuthority = const Value.absent(),
  }) => LocalRequirement(
    id: id ?? this.id,
    code: code ?? this.code,
    category: category ?? this.category,
    title: title ?? this.title,
    status: status ?? this.status,
    issuingAuthority: issuingAuthority.present
        ? issuingAuthority.value
        : this.issuingAuthority,
  );
  LocalRequirement copyWithCompanion(RequirementsCompanion data) {
    return LocalRequirement(
      id: data.id.present ? data.id.value : this.id,
      code: data.code.present ? data.code.value : this.code,
      category: data.category.present ? data.category.value : this.category,
      title: data.title.present ? data.title.value : this.title,
      status: data.status.present ? data.status.value : this.status,
      issuingAuthority: data.issuingAuthority.present
          ? data.issuingAuthority.value
          : this.issuingAuthority,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalRequirement(')
          ..write('id: $id, ')
          ..write('code: $code, ')
          ..write('category: $category, ')
          ..write('title: $title, ')
          ..write('status: $status, ')
          ..write('issuingAuthority: $issuingAuthority')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(id, code, category, title, status, issuingAuthority);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalRequirement &&
          other.id == this.id &&
          other.code == this.code &&
          other.category == this.category &&
          other.title == this.title &&
          other.status == this.status &&
          other.issuingAuthority == this.issuingAuthority);
}

class RequirementsCompanion extends UpdateCompanion<LocalRequirement> {
  final Value<int> id;
  final Value<String> code;
  final Value<String> category;
  final Value<String> title;
  final Value<String> status;
  final Value<String?> issuingAuthority;
  const RequirementsCompanion({
    this.id = const Value.absent(),
    this.code = const Value.absent(),
    this.category = const Value.absent(),
    this.title = const Value.absent(),
    this.status = const Value.absent(),
    this.issuingAuthority = const Value.absent(),
  });
  RequirementsCompanion.insert({
    this.id = const Value.absent(),
    required String code,
    required String category,
    required String title,
    required String status,
    this.issuingAuthority = const Value.absent(),
  }) : code = Value(code),
       category = Value(category),
       title = Value(title),
       status = Value(status);
  static Insertable<LocalRequirement> custom({
    Expression<int>? id,
    Expression<String>? code,
    Expression<String>? category,
    Expression<String>? title,
    Expression<String>? status,
    Expression<String>? issuingAuthority,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (code != null) 'code': code,
      if (category != null) 'category': category,
      if (title != null) 'title': title,
      if (status != null) 'status': status,
      if (issuingAuthority != null) 'issuing_authority': issuingAuthority,
    });
  }

  RequirementsCompanion copyWith({
    Value<int>? id,
    Value<String>? code,
    Value<String>? category,
    Value<String>? title,
    Value<String>? status,
    Value<String?>? issuingAuthority,
  }) {
    return RequirementsCompanion(
      id: id ?? this.id,
      code: code ?? this.code,
      category: category ?? this.category,
      title: title ?? this.title,
      status: status ?? this.status,
      issuingAuthority: issuingAuthority ?? this.issuingAuthority,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (code.present) {
      map['code'] = Variable<String>(code.value);
    }
    if (category.present) {
      map['category'] = Variable<String>(category.value);
    }
    if (title.present) {
      map['title'] = Variable<String>(title.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(status.value);
    }
    if (issuingAuthority.present) {
      map['issuing_authority'] = Variable<String>(issuingAuthority.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('RequirementsCompanion(')
          ..write('id: $id, ')
          ..write('code: $code, ')
          ..write('category: $category, ')
          ..write('title: $title, ')
          ..write('status: $status, ')
          ..write('issuingAuthority: $issuingAuthority')
          ..write(')'))
        .toString();
  }
}

class $CrewChangesTable extends CrewChanges
    with TableInfo<$CrewChangesTable, LocalCrewChange> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CrewChangesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _ccIdMeta = const VerificationMeta('ccId');
  @override
  late final GeneratedColumn<String> ccId = GeneratedColumn<String>(
    'cc_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _fromDateMeta = const VerificationMeta(
    'fromDate',
  );
  @override
  late final GeneratedColumn<String> fromDate = GeneratedColumn<String>(
    'from_date',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _toDateMeta = const VerificationMeta('toDate');
  @override
  late final GeneratedColumn<String> toDate = GeneratedColumn<String>(
    'to_date',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _cutoffMeta = const VerificationMeta('cutoff');
  @override
  late final GeneratedColumn<String> cutoff = GeneratedColumn<String>(
    'cutoff',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [id, ccId, fromDate, toDate, cutoff];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'crew_changes';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalCrewChange> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('cc_id')) {
      context.handle(
        _ccIdMeta,
        ccId.isAcceptableOrUnknown(data['cc_id']!, _ccIdMeta),
      );
    } else if (isInserting) {
      context.missing(_ccIdMeta);
    }
    if (data.containsKey('from_date')) {
      context.handle(
        _fromDateMeta,
        fromDate.isAcceptableOrUnknown(data['from_date']!, _fromDateMeta),
      );
    } else if (isInserting) {
      context.missing(_fromDateMeta);
    }
    if (data.containsKey('to_date')) {
      context.handle(
        _toDateMeta,
        toDate.isAcceptableOrUnknown(data['to_date']!, _toDateMeta),
      );
    } else if (isInserting) {
      context.missing(_toDateMeta);
    }
    if (data.containsKey('cutoff')) {
      context.handle(
        _cutoffMeta,
        cutoff.isAcceptableOrUnknown(data['cutoff']!, _cutoffMeta),
      );
    } else if (isInserting) {
      context.missing(_cutoffMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalCrewChange map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalCrewChange(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      ccId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}cc_id'],
      )!,
      fromDate: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}from_date'],
      )!,
      toDate: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}to_date'],
      )!,
      cutoff: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}cutoff'],
      )!,
    );
  }

  @override
  $CrewChangesTable createAlias(String alias) {
    return $CrewChangesTable(attachedDatabase, alias);
  }
}

class LocalCrewChange extends DataClass implements Insertable<LocalCrewChange> {
  final int id;
  final String ccId;
  final String fromDate;
  final String toDate;
  final String cutoff;
  const LocalCrewChange({
    required this.id,
    required this.ccId,
    required this.fromDate,
    required this.toDate,
    required this.cutoff,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['cc_id'] = Variable<String>(ccId);
    map['from_date'] = Variable<String>(fromDate);
    map['to_date'] = Variable<String>(toDate);
    map['cutoff'] = Variable<String>(cutoff);
    return map;
  }

  CrewChangesCompanion toCompanion(bool nullToAbsent) {
    return CrewChangesCompanion(
      id: Value(id),
      ccId: Value(ccId),
      fromDate: Value(fromDate),
      toDate: Value(toDate),
      cutoff: Value(cutoff),
    );
  }

  factory LocalCrewChange.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalCrewChange(
      id: serializer.fromJson<int>(json['id']),
      ccId: serializer.fromJson<String>(json['ccId']),
      fromDate: serializer.fromJson<String>(json['fromDate']),
      toDate: serializer.fromJson<String>(json['toDate']),
      cutoff: serializer.fromJson<String>(json['cutoff']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'ccId': serializer.toJson<String>(ccId),
      'fromDate': serializer.toJson<String>(fromDate),
      'toDate': serializer.toJson<String>(toDate),
      'cutoff': serializer.toJson<String>(cutoff),
    };
  }

  LocalCrewChange copyWith({
    int? id,
    String? ccId,
    String? fromDate,
    String? toDate,
    String? cutoff,
  }) => LocalCrewChange(
    id: id ?? this.id,
    ccId: ccId ?? this.ccId,
    fromDate: fromDate ?? this.fromDate,
    toDate: toDate ?? this.toDate,
    cutoff: cutoff ?? this.cutoff,
  );
  LocalCrewChange copyWithCompanion(CrewChangesCompanion data) {
    return LocalCrewChange(
      id: data.id.present ? data.id.value : this.id,
      ccId: data.ccId.present ? data.ccId.value : this.ccId,
      fromDate: data.fromDate.present ? data.fromDate.value : this.fromDate,
      toDate: data.toDate.present ? data.toDate.value : this.toDate,
      cutoff: data.cutoff.present ? data.cutoff.value : this.cutoff,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalCrewChange(')
          ..write('id: $id, ')
          ..write('ccId: $ccId, ')
          ..write('fromDate: $fromDate, ')
          ..write('toDate: $toDate, ')
          ..write('cutoff: $cutoff')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(id, ccId, fromDate, toDate, cutoff);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalCrewChange &&
          other.id == this.id &&
          other.ccId == this.ccId &&
          other.fromDate == this.fromDate &&
          other.toDate == this.toDate &&
          other.cutoff == this.cutoff);
}

class CrewChangesCompanion extends UpdateCompanion<LocalCrewChange> {
  final Value<int> id;
  final Value<String> ccId;
  final Value<String> fromDate;
  final Value<String> toDate;
  final Value<String> cutoff;
  const CrewChangesCompanion({
    this.id = const Value.absent(),
    this.ccId = const Value.absent(),
    this.fromDate = const Value.absent(),
    this.toDate = const Value.absent(),
    this.cutoff = const Value.absent(),
  });
  CrewChangesCompanion.insert({
    this.id = const Value.absent(),
    required String ccId,
    required String fromDate,
    required String toDate,
    required String cutoff,
  }) : ccId = Value(ccId),
       fromDate = Value(fromDate),
       toDate = Value(toDate),
       cutoff = Value(cutoff);
  static Insertable<LocalCrewChange> custom({
    Expression<int>? id,
    Expression<String>? ccId,
    Expression<String>? fromDate,
    Expression<String>? toDate,
    Expression<String>? cutoff,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (ccId != null) 'cc_id': ccId,
      if (fromDate != null) 'from_date': fromDate,
      if (toDate != null) 'to_date': toDate,
      if (cutoff != null) 'cutoff': cutoff,
    });
  }

  CrewChangesCompanion copyWith({
    Value<int>? id,
    Value<String>? ccId,
    Value<String>? fromDate,
    Value<String>? toDate,
    Value<String>? cutoff,
  }) {
    return CrewChangesCompanion(
      id: id ?? this.id,
      ccId: ccId ?? this.ccId,
      fromDate: fromDate ?? this.fromDate,
      toDate: toDate ?? this.toDate,
      cutoff: cutoff ?? this.cutoff,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (ccId.present) {
      map['cc_id'] = Variable<String>(ccId.value);
    }
    if (fromDate.present) {
      map['from_date'] = Variable<String>(fromDate.value);
    }
    if (toDate.present) {
      map['to_date'] = Variable<String>(toDate.value);
    }
    if (cutoff.present) {
      map['cutoff'] = Variable<String>(cutoff.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CrewChangesCompanion(')
          ..write('id: $id, ')
          ..write('ccId: $ccId, ')
          ..write('fromDate: $fromDate, ')
          ..write('toDate: $toDate, ')
          ..write('cutoff: $cutoff')
          ..write(')'))
        .toString();
  }
}

class $StandingCellsTable extends StandingCells
    with TableInfo<$StandingCellsTable, LocalStandingCell> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $StandingCellsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _requirementIdMeta = const VerificationMeta(
    'requirementId',
  );
  @override
  late final GeneratedColumn<int> requirementId = GeneratedColumn<int>(
    'requirement_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _levelMeta = const VerificationMeta('level');
  @override
  late final GeneratedColumn<String> level = GeneratedColumn<String>(
    'level',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _stateMeta = const VerificationMeta('state');
  @override
  late final GeneratedColumn<String> state = GeneratedColumn<String>(
    'state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _expiryMeta = const VerificationMeta('expiry');
  @override
  late final GeneratedColumn<String> expiry = GeneratedColumn<String>(
    'expiry',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _notesMeta = const VerificationMeta('notes');
  @override
  late final GeneratedColumn<String> notes = GeneratedColumn<String>(
    'notes',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _registerRecordIdMeta = const VerificationMeta(
    'registerRecordId',
  );
  @override
  late final GeneratedColumn<String> registerRecordId = GeneratedColumn<String>(
    'register_record_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    requirementId,
    level,
    state,
    expiry,
    notes,
    registerRecordId,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'standing_cells';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalStandingCell> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('requirement_id')) {
      context.handle(
        _requirementIdMeta,
        requirementId.isAcceptableOrUnknown(
          data['requirement_id']!,
          _requirementIdMeta,
        ),
      );
    }
    if (data.containsKey('level')) {
      context.handle(
        _levelMeta,
        level.isAcceptableOrUnknown(data['level']!, _levelMeta),
      );
    } else if (isInserting) {
      context.missing(_levelMeta);
    }
    if (data.containsKey('state')) {
      context.handle(
        _stateMeta,
        state.isAcceptableOrUnknown(data['state']!, _stateMeta),
      );
    } else if (isInserting) {
      context.missing(_stateMeta);
    }
    if (data.containsKey('expiry')) {
      context.handle(
        _expiryMeta,
        expiry.isAcceptableOrUnknown(data['expiry']!, _expiryMeta),
      );
    }
    if (data.containsKey('notes')) {
      context.handle(
        _notesMeta,
        notes.isAcceptableOrUnknown(data['notes']!, _notesMeta),
      );
    }
    if (data.containsKey('register_record_id')) {
      context.handle(
        _registerRecordIdMeta,
        registerRecordId.isAcceptableOrUnknown(
          data['register_record_id']!,
          _registerRecordIdMeta,
        ),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {requirementId};
  @override
  LocalStandingCell map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalStandingCell(
      requirementId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}requirement_id'],
      )!,
      level: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}level'],
      )!,
      state: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}state'],
      )!,
      expiry: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}expiry'],
      ),
      notes: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}notes'],
      ),
      registerRecordId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}register_record_id'],
      ),
    );
  }

  @override
  $StandingCellsTable createAlias(String alias) {
    return $StandingCellsTable(attachedDatabase, alias);
  }
}

class LocalStandingCell extends DataClass
    implements Insertable<LocalStandingCell> {
  final int requirementId;
  final String level;
  final String state;
  final String? expiry;
  final String? notes;
  final String? registerRecordId;
  const LocalStandingCell({
    required this.requirementId,
    required this.level,
    required this.state,
    this.expiry,
    this.notes,
    this.registerRecordId,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['requirement_id'] = Variable<int>(requirementId);
    map['level'] = Variable<String>(level);
    map['state'] = Variable<String>(state);
    if (!nullToAbsent || expiry != null) {
      map['expiry'] = Variable<String>(expiry);
    }
    if (!nullToAbsent || notes != null) {
      map['notes'] = Variable<String>(notes);
    }
    if (!nullToAbsent || registerRecordId != null) {
      map['register_record_id'] = Variable<String>(registerRecordId);
    }
    return map;
  }

  StandingCellsCompanion toCompanion(bool nullToAbsent) {
    return StandingCellsCompanion(
      requirementId: Value(requirementId),
      level: Value(level),
      state: Value(state),
      expiry: expiry == null && nullToAbsent
          ? const Value.absent()
          : Value(expiry),
      notes: notes == null && nullToAbsent
          ? const Value.absent()
          : Value(notes),
      registerRecordId: registerRecordId == null && nullToAbsent
          ? const Value.absent()
          : Value(registerRecordId),
    );
  }

  factory LocalStandingCell.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalStandingCell(
      requirementId: serializer.fromJson<int>(json['requirementId']),
      level: serializer.fromJson<String>(json['level']),
      state: serializer.fromJson<String>(json['state']),
      expiry: serializer.fromJson<String?>(json['expiry']),
      notes: serializer.fromJson<String?>(json['notes']),
      registerRecordId: serializer.fromJson<String?>(json['registerRecordId']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'requirementId': serializer.toJson<int>(requirementId),
      'level': serializer.toJson<String>(level),
      'state': serializer.toJson<String>(state),
      'expiry': serializer.toJson<String?>(expiry),
      'notes': serializer.toJson<String?>(notes),
      'registerRecordId': serializer.toJson<String?>(registerRecordId),
    };
  }

  LocalStandingCell copyWith({
    int? requirementId,
    String? level,
    String? state,
    Value<String?> expiry = const Value.absent(),
    Value<String?> notes = const Value.absent(),
    Value<String?> registerRecordId = const Value.absent(),
  }) => LocalStandingCell(
    requirementId: requirementId ?? this.requirementId,
    level: level ?? this.level,
    state: state ?? this.state,
    expiry: expiry.present ? expiry.value : this.expiry,
    notes: notes.present ? notes.value : this.notes,
    registerRecordId: registerRecordId.present
        ? registerRecordId.value
        : this.registerRecordId,
  );
  LocalStandingCell copyWithCompanion(StandingCellsCompanion data) {
    return LocalStandingCell(
      requirementId: data.requirementId.present
          ? data.requirementId.value
          : this.requirementId,
      level: data.level.present ? data.level.value : this.level,
      state: data.state.present ? data.state.value : this.state,
      expiry: data.expiry.present ? data.expiry.value : this.expiry,
      notes: data.notes.present ? data.notes.value : this.notes,
      registerRecordId: data.registerRecordId.present
          ? data.registerRecordId.value
          : this.registerRecordId,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalStandingCell(')
          ..write('requirementId: $requirementId, ')
          ..write('level: $level, ')
          ..write('state: $state, ')
          ..write('expiry: $expiry, ')
          ..write('notes: $notes, ')
          ..write('registerRecordId: $registerRecordId')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(requirementId, level, state, expiry, notes, registerRecordId);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalStandingCell &&
          other.requirementId == this.requirementId &&
          other.level == this.level &&
          other.state == this.state &&
          other.expiry == this.expiry &&
          other.notes == this.notes &&
          other.registerRecordId == this.registerRecordId);
}

class StandingCellsCompanion extends UpdateCompanion<LocalStandingCell> {
  final Value<int> requirementId;
  final Value<String> level;
  final Value<String> state;
  final Value<String?> expiry;
  final Value<String?> notes;
  final Value<String?> registerRecordId;
  const StandingCellsCompanion({
    this.requirementId = const Value.absent(),
    this.level = const Value.absent(),
    this.state = const Value.absent(),
    this.expiry = const Value.absent(),
    this.notes = const Value.absent(),
    this.registerRecordId = const Value.absent(),
  });
  StandingCellsCompanion.insert({
    this.requirementId = const Value.absent(),
    required String level,
    required String state,
    this.expiry = const Value.absent(),
    this.notes = const Value.absent(),
    this.registerRecordId = const Value.absent(),
  }) : level = Value(level),
       state = Value(state);
  static Insertable<LocalStandingCell> custom({
    Expression<int>? requirementId,
    Expression<String>? level,
    Expression<String>? state,
    Expression<String>? expiry,
    Expression<String>? notes,
    Expression<String>? registerRecordId,
  }) {
    return RawValuesInsertable({
      if (requirementId != null) 'requirement_id': requirementId,
      if (level != null) 'level': level,
      if (state != null) 'state': state,
      if (expiry != null) 'expiry': expiry,
      if (notes != null) 'notes': notes,
      if (registerRecordId != null) 'register_record_id': registerRecordId,
    });
  }

  StandingCellsCompanion copyWith({
    Value<int>? requirementId,
    Value<String>? level,
    Value<String>? state,
    Value<String?>? expiry,
    Value<String?>? notes,
    Value<String?>? registerRecordId,
  }) {
    return StandingCellsCompanion(
      requirementId: requirementId ?? this.requirementId,
      level: level ?? this.level,
      state: state ?? this.state,
      expiry: expiry ?? this.expiry,
      notes: notes ?? this.notes,
      registerRecordId: registerRecordId ?? this.registerRecordId,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (requirementId.present) {
      map['requirement_id'] = Variable<int>(requirementId.value);
    }
    if (level.present) {
      map['level'] = Variable<String>(level.value);
    }
    if (state.present) {
      map['state'] = Variable<String>(state.value);
    }
    if (expiry.present) {
      map['expiry'] = Variable<String>(expiry.value);
    }
    if (notes.present) {
      map['notes'] = Variable<String>(notes.value);
    }
    if (registerRecordId.present) {
      map['register_record_id'] = Variable<String>(registerRecordId.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('StandingCellsCompanion(')
          ..write('requirementId: $requirementId, ')
          ..write('level: $level, ')
          ..write('state: $state, ')
          ..write('expiry: $expiry, ')
          ..write('notes: $notes, ')
          ..write('registerRecordId: $registerRecordId')
          ..write(')'))
        .toString();
  }
}

class $OutboxTable extends Outbox with TableInfo<$OutboxTable, OutboxEntry> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $OutboxTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _opIdMeta = const VerificationMeta('opId');
  @override
  late final GeneratedColumn<String> opId = GeneratedColumn<String>(
    'op_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _typeMeta = const VerificationMeta('type');
  @override
  late final GeneratedColumn<String> type = GeneratedColumn<String>(
    'type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _payloadMeta = const VerificationMeta(
    'payload',
  );
  @override
  late final GeneratedColumn<String> payload = GeneratedColumn<String>(
    'payload',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _queuedAtMeta = const VerificationMeta(
    'queuedAt',
  );
  @override
  late final GeneratedColumn<DateTime> queuedAt = GeneratedColumn<DateTime>(
    'queued_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _attemptsMeta = const VerificationMeta(
    'attempts',
  );
  @override
  late final GeneratedColumn<int> attempts = GeneratedColumn<int>(
    'attempts',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _nextAttemptAtMeta = const VerificationMeta(
    'nextAttemptAt',
  );
  @override
  late final GeneratedColumn<DateTime> nextAttemptAt =
      GeneratedColumn<DateTime>(
        'next_attempt_at',
        aliasedName,
        true,
        type: DriftSqlType.dateTime,
        requiredDuringInsert: false,
      );
  static const VerificationMeta _lastErrorMeta = const VerificationMeta(
    'lastError',
  );
  @override
  late final GeneratedColumn<String> lastError = GeneratedColumn<String>(
    'last_error',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    opId,
    type,
    payload,
    queuedAt,
    attempts,
    nextAttemptAt,
    lastError,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'outbox';
  @override
  VerificationContext validateIntegrity(
    Insertable<OutboxEntry> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('op_id')) {
      context.handle(
        _opIdMeta,
        opId.isAcceptableOrUnknown(data['op_id']!, _opIdMeta),
      );
    } else if (isInserting) {
      context.missing(_opIdMeta);
    }
    if (data.containsKey('type')) {
      context.handle(
        _typeMeta,
        type.isAcceptableOrUnknown(data['type']!, _typeMeta),
      );
    } else if (isInserting) {
      context.missing(_typeMeta);
    }
    if (data.containsKey('payload')) {
      context.handle(
        _payloadMeta,
        payload.isAcceptableOrUnknown(data['payload']!, _payloadMeta),
      );
    } else if (isInserting) {
      context.missing(_payloadMeta);
    }
    if (data.containsKey('queued_at')) {
      context.handle(
        _queuedAtMeta,
        queuedAt.isAcceptableOrUnknown(data['queued_at']!, _queuedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_queuedAtMeta);
    }
    if (data.containsKey('attempts')) {
      context.handle(
        _attemptsMeta,
        attempts.isAcceptableOrUnknown(data['attempts']!, _attemptsMeta),
      );
    }
    if (data.containsKey('next_attempt_at')) {
      context.handle(
        _nextAttemptAtMeta,
        nextAttemptAt.isAcceptableOrUnknown(
          data['next_attempt_at']!,
          _nextAttemptAtMeta,
        ),
      );
    }
    if (data.containsKey('last_error')) {
      context.handle(
        _lastErrorMeta,
        lastError.isAcceptableOrUnknown(data['last_error']!, _lastErrorMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {opId};
  @override
  OutboxEntry map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return OutboxEntry(
      opId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}op_id'],
      )!,
      type: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}type'],
      )!,
      payload: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}payload'],
      )!,
      queuedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}queued_at'],
      )!,
      attempts: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}attempts'],
      )!,
      nextAttemptAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}next_attempt_at'],
      ),
      lastError: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}last_error'],
      ),
    );
  }

  @override
  $OutboxTable createAlias(String alias) {
    return $OutboxTable(attachedDatabase, alias);
  }
}

class OutboxEntry extends DataClass implements Insertable<OutboxEntry> {
  /// Client-generated, and the idempotency key the server echoes back.
  final String opId;
  final String type;

  /// The operation's JSON payload, ready to post.
  final String payload;
  final DateTime queuedAt;
  final int attempts;
  final DateTime? nextAttemptAt;

  /// Set when the server refused it permanently — kept, briefly, so the UI can explain why.
  final String? lastError;
  const OutboxEntry({
    required this.opId,
    required this.type,
    required this.payload,
    required this.queuedAt,
    required this.attempts,
    this.nextAttemptAt,
    this.lastError,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['op_id'] = Variable<String>(opId);
    map['type'] = Variable<String>(type);
    map['payload'] = Variable<String>(payload);
    map['queued_at'] = Variable<DateTime>(queuedAt);
    map['attempts'] = Variable<int>(attempts);
    if (!nullToAbsent || nextAttemptAt != null) {
      map['next_attempt_at'] = Variable<DateTime>(nextAttemptAt);
    }
    if (!nullToAbsent || lastError != null) {
      map['last_error'] = Variable<String>(lastError);
    }
    return map;
  }

  OutboxCompanion toCompanion(bool nullToAbsent) {
    return OutboxCompanion(
      opId: Value(opId),
      type: Value(type),
      payload: Value(payload),
      queuedAt: Value(queuedAt),
      attempts: Value(attempts),
      nextAttemptAt: nextAttemptAt == null && nullToAbsent
          ? const Value.absent()
          : Value(nextAttemptAt),
      lastError: lastError == null && nullToAbsent
          ? const Value.absent()
          : Value(lastError),
    );
  }

  factory OutboxEntry.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return OutboxEntry(
      opId: serializer.fromJson<String>(json['opId']),
      type: serializer.fromJson<String>(json['type']),
      payload: serializer.fromJson<String>(json['payload']),
      queuedAt: serializer.fromJson<DateTime>(json['queuedAt']),
      attempts: serializer.fromJson<int>(json['attempts']),
      nextAttemptAt: serializer.fromJson<DateTime?>(json['nextAttemptAt']),
      lastError: serializer.fromJson<String?>(json['lastError']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'opId': serializer.toJson<String>(opId),
      'type': serializer.toJson<String>(type),
      'payload': serializer.toJson<String>(payload),
      'queuedAt': serializer.toJson<DateTime>(queuedAt),
      'attempts': serializer.toJson<int>(attempts),
      'nextAttemptAt': serializer.toJson<DateTime?>(nextAttemptAt),
      'lastError': serializer.toJson<String?>(lastError),
    };
  }

  OutboxEntry copyWith({
    String? opId,
    String? type,
    String? payload,
    DateTime? queuedAt,
    int? attempts,
    Value<DateTime?> nextAttemptAt = const Value.absent(),
    Value<String?> lastError = const Value.absent(),
  }) => OutboxEntry(
    opId: opId ?? this.opId,
    type: type ?? this.type,
    payload: payload ?? this.payload,
    queuedAt: queuedAt ?? this.queuedAt,
    attempts: attempts ?? this.attempts,
    nextAttemptAt: nextAttemptAt.present
        ? nextAttemptAt.value
        : this.nextAttemptAt,
    lastError: lastError.present ? lastError.value : this.lastError,
  );
  OutboxEntry copyWithCompanion(OutboxCompanion data) {
    return OutboxEntry(
      opId: data.opId.present ? data.opId.value : this.opId,
      type: data.type.present ? data.type.value : this.type,
      payload: data.payload.present ? data.payload.value : this.payload,
      queuedAt: data.queuedAt.present ? data.queuedAt.value : this.queuedAt,
      attempts: data.attempts.present ? data.attempts.value : this.attempts,
      nextAttemptAt: data.nextAttemptAt.present
          ? data.nextAttemptAt.value
          : this.nextAttemptAt,
      lastError: data.lastError.present ? data.lastError.value : this.lastError,
    );
  }

  @override
  String toString() {
    return (StringBuffer('OutboxEntry(')
          ..write('opId: $opId, ')
          ..write('type: $type, ')
          ..write('payload: $payload, ')
          ..write('queuedAt: $queuedAt, ')
          ..write('attempts: $attempts, ')
          ..write('nextAttemptAt: $nextAttemptAt, ')
          ..write('lastError: $lastError')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    opId,
    type,
    payload,
    queuedAt,
    attempts,
    nextAttemptAt,
    lastError,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is OutboxEntry &&
          other.opId == this.opId &&
          other.type == this.type &&
          other.payload == this.payload &&
          other.queuedAt == this.queuedAt &&
          other.attempts == this.attempts &&
          other.nextAttemptAt == this.nextAttemptAt &&
          other.lastError == this.lastError);
}

class OutboxCompanion extends UpdateCompanion<OutboxEntry> {
  final Value<String> opId;
  final Value<String> type;
  final Value<String> payload;
  final Value<DateTime> queuedAt;
  final Value<int> attempts;
  final Value<DateTime?> nextAttemptAt;
  final Value<String?> lastError;
  final Value<int> rowid;
  const OutboxCompanion({
    this.opId = const Value.absent(),
    this.type = const Value.absent(),
    this.payload = const Value.absent(),
    this.queuedAt = const Value.absent(),
    this.attempts = const Value.absent(),
    this.nextAttemptAt = const Value.absent(),
    this.lastError = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  OutboxCompanion.insert({
    required String opId,
    required String type,
    required String payload,
    required DateTime queuedAt,
    this.attempts = const Value.absent(),
    this.nextAttemptAt = const Value.absent(),
    this.lastError = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : opId = Value(opId),
       type = Value(type),
       payload = Value(payload),
       queuedAt = Value(queuedAt);
  static Insertable<OutboxEntry> custom({
    Expression<String>? opId,
    Expression<String>? type,
    Expression<String>? payload,
    Expression<DateTime>? queuedAt,
    Expression<int>? attempts,
    Expression<DateTime>? nextAttemptAt,
    Expression<String>? lastError,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (opId != null) 'op_id': opId,
      if (type != null) 'type': type,
      if (payload != null) 'payload': payload,
      if (queuedAt != null) 'queued_at': queuedAt,
      if (attempts != null) 'attempts': attempts,
      if (nextAttemptAt != null) 'next_attempt_at': nextAttemptAt,
      if (lastError != null) 'last_error': lastError,
      if (rowid != null) 'rowid': rowid,
    });
  }

  OutboxCompanion copyWith({
    Value<String>? opId,
    Value<String>? type,
    Value<String>? payload,
    Value<DateTime>? queuedAt,
    Value<int>? attempts,
    Value<DateTime?>? nextAttemptAt,
    Value<String?>? lastError,
    Value<int>? rowid,
  }) {
    return OutboxCompanion(
      opId: opId ?? this.opId,
      type: type ?? this.type,
      payload: payload ?? this.payload,
      queuedAt: queuedAt ?? this.queuedAt,
      attempts: attempts ?? this.attempts,
      nextAttemptAt: nextAttemptAt ?? this.nextAttemptAt,
      lastError: lastError ?? this.lastError,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (opId.present) {
      map['op_id'] = Variable<String>(opId.value);
    }
    if (type.present) {
      map['type'] = Variable<String>(type.value);
    }
    if (payload.present) {
      map['payload'] = Variable<String>(payload.value);
    }
    if (queuedAt.present) {
      map['queued_at'] = Variable<DateTime>(queuedAt.value);
    }
    if (attempts.present) {
      map['attempts'] = Variable<int>(attempts.value);
    }
    if (nextAttemptAt.present) {
      map['next_attempt_at'] = Variable<DateTime>(nextAttemptAt.value);
    }
    if (lastError.present) {
      map['last_error'] = Variable<String>(lastError.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('OutboxCompanion(')
          ..write('opId: $opId, ')
          ..write('type: $type, ')
          ..write('payload: $payload, ')
          ..write('queuedAt: $queuedAt, ')
          ..write('attempts: $attempts, ')
          ..write('nextAttemptAt: $nextAttemptAt, ')
          ..write('lastError: $lastError, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $CrewIntentsTable extends CrewIntents
    with TableInfo<$CrewIntentsTable, LocalCrewIntent> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CrewIntentsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _opIdMeta = const VerificationMeta('opId');
  @override
  late final GeneratedColumn<String> opId = GeneratedColumn<String>(
    'op_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _requirementIdMeta = const VerificationMeta(
    'requirementId',
  );
  @override
  late final GeneratedColumn<int> requirementId = GeneratedColumn<int>(
    'requirement_id',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _subjectRefMeta = const VerificationMeta(
    'subjectRef',
  );
  @override
  late final GeneratedColumn<String> subjectRef = GeneratedColumn<String>(
    'subject_ref',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _summaryMeta = const VerificationMeta(
    'summary',
  );
  @override
  late final GeneratedColumn<String> summary = GeneratedColumn<String>(
    'summary',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _payloadMeta = const VerificationMeta(
    'payload',
  );
  @override
  late final GeneratedColumn<String> payload = GeneratedColumn<String>(
    'payload',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _queuedAtMeta = const VerificationMeta(
    'queuedAt',
  );
  @override
  late final GeneratedColumn<DateTime> queuedAt = GeneratedColumn<DateTime>(
    'queued_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _stateMeta = const VerificationMeta('state');
  @override
  late final GeneratedColumn<String> state = GeneratedColumn<String>(
    'state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('queued'),
  );
  static const VerificationMeta _detailMeta = const VerificationMeta('detail');
  @override
  late final GeneratedColumn<String> detail = GeneratedColumn<String>(
    'detail',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    opId,
    kind,
    requirementId,
    subjectRef,
    summary,
    payload,
    queuedAt,
    state,
    detail,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'crew_intents';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalCrewIntent> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('op_id')) {
      context.handle(
        _opIdMeta,
        opId.isAcceptableOrUnknown(data['op_id']!, _opIdMeta),
      );
    } else if (isInserting) {
      context.missing(_opIdMeta);
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('requirement_id')) {
      context.handle(
        _requirementIdMeta,
        requirementId.isAcceptableOrUnknown(
          data['requirement_id']!,
          _requirementIdMeta,
        ),
      );
    }
    if (data.containsKey('subject_ref')) {
      context.handle(
        _subjectRefMeta,
        subjectRef.isAcceptableOrUnknown(data['subject_ref']!, _subjectRefMeta),
      );
    }
    if (data.containsKey('summary')) {
      context.handle(
        _summaryMeta,
        summary.isAcceptableOrUnknown(data['summary']!, _summaryMeta),
      );
    } else if (isInserting) {
      context.missing(_summaryMeta);
    }
    if (data.containsKey('payload')) {
      context.handle(
        _payloadMeta,
        payload.isAcceptableOrUnknown(data['payload']!, _payloadMeta),
      );
    } else if (isInserting) {
      context.missing(_payloadMeta);
    }
    if (data.containsKey('queued_at')) {
      context.handle(
        _queuedAtMeta,
        queuedAt.isAcceptableOrUnknown(data['queued_at']!, _queuedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_queuedAtMeta);
    }
    if (data.containsKey('state')) {
      context.handle(
        _stateMeta,
        state.isAcceptableOrUnknown(data['state']!, _stateMeta),
      );
    }
    if (data.containsKey('detail')) {
      context.handle(
        _detailMeta,
        detail.isAcceptableOrUnknown(data['detail']!, _detailMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {opId};
  @override
  LocalCrewIntent map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalCrewIntent(
      opId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}op_id'],
      )!,
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      requirementId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}requirement_id'],
      ),
      subjectRef: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}subject_ref'],
      ),
      summary: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}summary'],
      )!,
      payload: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}payload'],
      )!,
      queuedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}queued_at'],
      )!,
      state: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}state'],
      )!,
      detail: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}detail'],
      ),
    );
  }

  @override
  $CrewIntentsTable createAlias(String alias) {
    return $CrewIntentsTable(attachedDatabase, alias);
  }
}

class LocalCrewIntent extends DataClass implements Insertable<LocalCrewIntent> {
  /// Shared with the outbox entry that carries it, which is also the server's idempotency key.
  final String opId;

  /// `requirement.progress`, `requirement.help`, `evidence.reading`, `course.seat_request`,
  /// `course.waitlist`, `register.exemption_request`, `attestation.sign_off`, `team.nudge`.
  final String kind;

  /// What it is about, where that is a requirement. Lets a detail screen find its own intents.
  final int? requirementId;

  /// The non-requirement subject: a course option id, a crew change id, a colleague's Sam #.
  final String? subjectRef;

  /// One line, already written, for the row that reports it. Composed at queue time because the
  /// screen that shows it may not be the screen that raised it.
  final String summary;

  /// The operation's JSON body, kept here as well as on the outbox entry.
  ///
  /// Duplication with a purpose: a server rejection deletes the outbox entry, and without a copy
  /// the "Retry" the failed row offers would have nothing to send. Retrying re-posts under the
  /// *same* `opId`, so a request the server actually applied before losing the connection cannot
  /// be applied twice.
  final String payload;
  final DateTime queuedAt;

  /// `queued` · `sent` · `failed`. Nothing else, and no state that means "probably".
  final String state;

  /// Why it failed, in the server's words where there are any.
  final String? detail;
  const LocalCrewIntent({
    required this.opId,
    required this.kind,
    this.requirementId,
    this.subjectRef,
    required this.summary,
    required this.payload,
    required this.queuedAt,
    required this.state,
    this.detail,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['op_id'] = Variable<String>(opId);
    map['kind'] = Variable<String>(kind);
    if (!nullToAbsent || requirementId != null) {
      map['requirement_id'] = Variable<int>(requirementId);
    }
    if (!nullToAbsent || subjectRef != null) {
      map['subject_ref'] = Variable<String>(subjectRef);
    }
    map['summary'] = Variable<String>(summary);
    map['payload'] = Variable<String>(payload);
    map['queued_at'] = Variable<DateTime>(queuedAt);
    map['state'] = Variable<String>(state);
    if (!nullToAbsent || detail != null) {
      map['detail'] = Variable<String>(detail);
    }
    return map;
  }

  CrewIntentsCompanion toCompanion(bool nullToAbsent) {
    return CrewIntentsCompanion(
      opId: Value(opId),
      kind: Value(kind),
      requirementId: requirementId == null && nullToAbsent
          ? const Value.absent()
          : Value(requirementId),
      subjectRef: subjectRef == null && nullToAbsent
          ? const Value.absent()
          : Value(subjectRef),
      summary: Value(summary),
      payload: Value(payload),
      queuedAt: Value(queuedAt),
      state: Value(state),
      detail: detail == null && nullToAbsent
          ? const Value.absent()
          : Value(detail),
    );
  }

  factory LocalCrewIntent.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalCrewIntent(
      opId: serializer.fromJson<String>(json['opId']),
      kind: serializer.fromJson<String>(json['kind']),
      requirementId: serializer.fromJson<int?>(json['requirementId']),
      subjectRef: serializer.fromJson<String?>(json['subjectRef']),
      summary: serializer.fromJson<String>(json['summary']),
      payload: serializer.fromJson<String>(json['payload']),
      queuedAt: serializer.fromJson<DateTime>(json['queuedAt']),
      state: serializer.fromJson<String>(json['state']),
      detail: serializer.fromJson<String?>(json['detail']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'opId': serializer.toJson<String>(opId),
      'kind': serializer.toJson<String>(kind),
      'requirementId': serializer.toJson<int?>(requirementId),
      'subjectRef': serializer.toJson<String?>(subjectRef),
      'summary': serializer.toJson<String>(summary),
      'payload': serializer.toJson<String>(payload),
      'queuedAt': serializer.toJson<DateTime>(queuedAt),
      'state': serializer.toJson<String>(state),
      'detail': serializer.toJson<String?>(detail),
    };
  }

  LocalCrewIntent copyWith({
    String? opId,
    String? kind,
    Value<int?> requirementId = const Value.absent(),
    Value<String?> subjectRef = const Value.absent(),
    String? summary,
    String? payload,
    DateTime? queuedAt,
    String? state,
    Value<String?> detail = const Value.absent(),
  }) => LocalCrewIntent(
    opId: opId ?? this.opId,
    kind: kind ?? this.kind,
    requirementId: requirementId.present
        ? requirementId.value
        : this.requirementId,
    subjectRef: subjectRef.present ? subjectRef.value : this.subjectRef,
    summary: summary ?? this.summary,
    payload: payload ?? this.payload,
    queuedAt: queuedAt ?? this.queuedAt,
    state: state ?? this.state,
    detail: detail.present ? detail.value : this.detail,
  );
  LocalCrewIntent copyWithCompanion(CrewIntentsCompanion data) {
    return LocalCrewIntent(
      opId: data.opId.present ? data.opId.value : this.opId,
      kind: data.kind.present ? data.kind.value : this.kind,
      requirementId: data.requirementId.present
          ? data.requirementId.value
          : this.requirementId,
      subjectRef: data.subjectRef.present
          ? data.subjectRef.value
          : this.subjectRef,
      summary: data.summary.present ? data.summary.value : this.summary,
      payload: data.payload.present ? data.payload.value : this.payload,
      queuedAt: data.queuedAt.present ? data.queuedAt.value : this.queuedAt,
      state: data.state.present ? data.state.value : this.state,
      detail: data.detail.present ? data.detail.value : this.detail,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalCrewIntent(')
          ..write('opId: $opId, ')
          ..write('kind: $kind, ')
          ..write('requirementId: $requirementId, ')
          ..write('subjectRef: $subjectRef, ')
          ..write('summary: $summary, ')
          ..write('payload: $payload, ')
          ..write('queuedAt: $queuedAt, ')
          ..write('state: $state, ')
          ..write('detail: $detail')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    opId,
    kind,
    requirementId,
    subjectRef,
    summary,
    payload,
    queuedAt,
    state,
    detail,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalCrewIntent &&
          other.opId == this.opId &&
          other.kind == this.kind &&
          other.requirementId == this.requirementId &&
          other.subjectRef == this.subjectRef &&
          other.summary == this.summary &&
          other.payload == this.payload &&
          other.queuedAt == this.queuedAt &&
          other.state == this.state &&
          other.detail == this.detail);
}

class CrewIntentsCompanion extends UpdateCompanion<LocalCrewIntent> {
  final Value<String> opId;
  final Value<String> kind;
  final Value<int?> requirementId;
  final Value<String?> subjectRef;
  final Value<String> summary;
  final Value<String> payload;
  final Value<DateTime> queuedAt;
  final Value<String> state;
  final Value<String?> detail;
  final Value<int> rowid;
  const CrewIntentsCompanion({
    this.opId = const Value.absent(),
    this.kind = const Value.absent(),
    this.requirementId = const Value.absent(),
    this.subjectRef = const Value.absent(),
    this.summary = const Value.absent(),
    this.payload = const Value.absent(),
    this.queuedAt = const Value.absent(),
    this.state = const Value.absent(),
    this.detail = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  CrewIntentsCompanion.insert({
    required String opId,
    required String kind,
    this.requirementId = const Value.absent(),
    this.subjectRef = const Value.absent(),
    required String summary,
    required String payload,
    required DateTime queuedAt,
    this.state = const Value.absent(),
    this.detail = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : opId = Value(opId),
       kind = Value(kind),
       summary = Value(summary),
       payload = Value(payload),
       queuedAt = Value(queuedAt);
  static Insertable<LocalCrewIntent> custom({
    Expression<String>? opId,
    Expression<String>? kind,
    Expression<int>? requirementId,
    Expression<String>? subjectRef,
    Expression<String>? summary,
    Expression<String>? payload,
    Expression<DateTime>? queuedAt,
    Expression<String>? state,
    Expression<String>? detail,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (opId != null) 'op_id': opId,
      if (kind != null) 'kind': kind,
      if (requirementId != null) 'requirement_id': requirementId,
      if (subjectRef != null) 'subject_ref': subjectRef,
      if (summary != null) 'summary': summary,
      if (payload != null) 'payload': payload,
      if (queuedAt != null) 'queued_at': queuedAt,
      if (state != null) 'state': state,
      if (detail != null) 'detail': detail,
      if (rowid != null) 'rowid': rowid,
    });
  }

  CrewIntentsCompanion copyWith({
    Value<String>? opId,
    Value<String>? kind,
    Value<int?>? requirementId,
    Value<String?>? subjectRef,
    Value<String>? summary,
    Value<String>? payload,
    Value<DateTime>? queuedAt,
    Value<String>? state,
    Value<String?>? detail,
    Value<int>? rowid,
  }) {
    return CrewIntentsCompanion(
      opId: opId ?? this.opId,
      kind: kind ?? this.kind,
      requirementId: requirementId ?? this.requirementId,
      subjectRef: subjectRef ?? this.subjectRef,
      summary: summary ?? this.summary,
      payload: payload ?? this.payload,
      queuedAt: queuedAt ?? this.queuedAt,
      state: state ?? this.state,
      detail: detail ?? this.detail,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (opId.present) {
      map['op_id'] = Variable<String>(opId.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (requirementId.present) {
      map['requirement_id'] = Variable<int>(requirementId.value);
    }
    if (subjectRef.present) {
      map['subject_ref'] = Variable<String>(subjectRef.value);
    }
    if (summary.present) {
      map['summary'] = Variable<String>(summary.value);
    }
    if (payload.present) {
      map['payload'] = Variable<String>(payload.value);
    }
    if (queuedAt.present) {
      map['queued_at'] = Variable<DateTime>(queuedAt.value);
    }
    if (state.present) {
      map['state'] = Variable<String>(state.value);
    }
    if (detail.present) {
      map['detail'] = Variable<String>(detail.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CrewIntentsCompanion(')
          ..write('opId: $opId, ')
          ..write('kind: $kind, ')
          ..write('requirementId: $requirementId, ')
          ..write('subjectRef: $subjectRef, ')
          ..write('summary: $summary, ')
          ..write('payload: $payload, ')
          ..write('queuedAt: $queuedAt, ')
          ..write('state: $state, ')
          ..write('detail: $detail, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $CrewStatementsTable extends CrewStatements
    with TableInfo<$CrewStatementsTable, LocalCrewStatement> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CrewStatementsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _opIdMeta = const VerificationMeta('opId');
  @override
  late final GeneratedColumn<String> opId = GeneratedColumn<String>(
    'op_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _kindMeta = const VerificationMeta('kind');
  @override
  late final GeneratedColumn<String> kind = GeneratedColumn<String>(
    'kind',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _requirementIdMeta = const VerificationMeta(
    'requirementId',
  );
  @override
  late final GeneratedColumn<int> requirementId = GeneratedColumn<int>(
    'requirement_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<String> status = GeneratedColumn<String>(
    'status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _subjectRefMeta = const VerificationMeta(
    'subjectRef',
  );
  @override
  late final GeneratedColumn<String> subjectRef = GeneratedColumn<String>(
    'subject_ref',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _aboutExpiryMeta = const VerificationMeta(
    'aboutExpiry',
  );
  @override
  late final GeneratedColumn<String> aboutExpiry = GeneratedColumn<String>(
    'about_expiry',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _raisedAtMeta = const VerificationMeta(
    'raisedAt',
  );
  @override
  late final GeneratedColumn<DateTime> raisedAt = GeneratedColumn<DateTime>(
    'raised_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _decisionNoteMeta = const VerificationMeta(
    'decisionNote',
  );
  @override
  late final GeneratedColumn<String> decisionNote = GeneratedColumn<String>(
    'decision_note',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _decidedAtMeta = const VerificationMeta(
    'decidedAt',
  );
  @override
  late final GeneratedColumn<DateTime> decidedAt = GeneratedColumn<DateTime>(
    'decided_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    opId,
    kind,
    requirementId,
    status,
    subjectRef,
    aboutExpiry,
    raisedAt,
    decisionNote,
    decidedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'crew_statements';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalCrewStatement> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('op_id')) {
      context.handle(
        _opIdMeta,
        opId.isAcceptableOrUnknown(data['op_id']!, _opIdMeta),
      );
    } else if (isInserting) {
      context.missing(_opIdMeta);
    }
    if (data.containsKey('kind')) {
      context.handle(
        _kindMeta,
        kind.isAcceptableOrUnknown(data['kind']!, _kindMeta),
      );
    } else if (isInserting) {
      context.missing(_kindMeta);
    }
    if (data.containsKey('requirement_id')) {
      context.handle(
        _requirementIdMeta,
        requirementId.isAcceptableOrUnknown(
          data['requirement_id']!,
          _requirementIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_requirementIdMeta);
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    } else if (isInserting) {
      context.missing(_statusMeta);
    }
    if (data.containsKey('subject_ref')) {
      context.handle(
        _subjectRefMeta,
        subjectRef.isAcceptableOrUnknown(data['subject_ref']!, _subjectRefMeta),
      );
    }
    if (data.containsKey('about_expiry')) {
      context.handle(
        _aboutExpiryMeta,
        aboutExpiry.isAcceptableOrUnknown(
          data['about_expiry']!,
          _aboutExpiryMeta,
        ),
      );
    }
    if (data.containsKey('raised_at')) {
      context.handle(
        _raisedAtMeta,
        raisedAt.isAcceptableOrUnknown(data['raised_at']!, _raisedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_raisedAtMeta);
    }
    if (data.containsKey('decision_note')) {
      context.handle(
        _decisionNoteMeta,
        decisionNote.isAcceptableOrUnknown(
          data['decision_note']!,
          _decisionNoteMeta,
        ),
      );
    }
    if (data.containsKey('decided_at')) {
      context.handle(
        _decidedAtMeta,
        decidedAt.isAcceptableOrUnknown(data['decided_at']!, _decidedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalCrewStatement map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalCrewStatement(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      opId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}op_id'],
      )!,
      kind: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kind'],
      )!,
      requirementId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}requirement_id'],
      )!,
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}status'],
      )!,
      subjectRef: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}subject_ref'],
      ),
      aboutExpiry: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}about_expiry'],
      ),
      raisedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}raised_at'],
      )!,
      decisionNote: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}decision_note'],
      ),
      decidedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}decided_at'],
      ),
    );
  }

  @override
  $CrewStatementsTable createAlias(String alias) {
    return $CrewStatementsTable(attachedDatabase, alias);
  }
}

class LocalCrewStatement extends DataClass
    implements Insertable<LocalCrewStatement> {
  final int id;

  /// The device's queue-entry id. Matches [CrewIntents.opId] for a statement this device raised.
  final String opId;
  final String kind;
  final int requirementId;

  /// `open` · `actioned` · `dismissed`.
  final String status;

  /// The course option a seat request / waitlist named, null for the other kinds. This is what
  /// keeps the right MOB-8 card reading "Requested" after the intent is pruned (issue #15) —
  /// without it the server's statement can only say "you asked about *something*".
  final String? subjectRef;
  final String? aboutExpiry;
  final DateTime raisedAt;

  /// The coordinator's answer, written knowing the crew member reads it (ADM-11 says so on the
  /// form). Shown verbatim — it is the office's own words, exactly like a rejection detail.
  final String? decisionNote;
  final DateTime? decidedAt;
  const LocalCrewStatement({
    required this.id,
    required this.opId,
    required this.kind,
    required this.requirementId,
    required this.status,
    this.subjectRef,
    this.aboutExpiry,
    required this.raisedAt,
    this.decisionNote,
    this.decidedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['op_id'] = Variable<String>(opId);
    map['kind'] = Variable<String>(kind);
    map['requirement_id'] = Variable<int>(requirementId);
    map['status'] = Variable<String>(status);
    if (!nullToAbsent || subjectRef != null) {
      map['subject_ref'] = Variable<String>(subjectRef);
    }
    if (!nullToAbsent || aboutExpiry != null) {
      map['about_expiry'] = Variable<String>(aboutExpiry);
    }
    map['raised_at'] = Variable<DateTime>(raisedAt);
    if (!nullToAbsent || decisionNote != null) {
      map['decision_note'] = Variable<String>(decisionNote);
    }
    if (!nullToAbsent || decidedAt != null) {
      map['decided_at'] = Variable<DateTime>(decidedAt);
    }
    return map;
  }

  CrewStatementsCompanion toCompanion(bool nullToAbsent) {
    return CrewStatementsCompanion(
      id: Value(id),
      opId: Value(opId),
      kind: Value(kind),
      requirementId: Value(requirementId),
      status: Value(status),
      subjectRef: subjectRef == null && nullToAbsent
          ? const Value.absent()
          : Value(subjectRef),
      aboutExpiry: aboutExpiry == null && nullToAbsent
          ? const Value.absent()
          : Value(aboutExpiry),
      raisedAt: Value(raisedAt),
      decisionNote: decisionNote == null && nullToAbsent
          ? const Value.absent()
          : Value(decisionNote),
      decidedAt: decidedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(decidedAt),
    );
  }

  factory LocalCrewStatement.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalCrewStatement(
      id: serializer.fromJson<int>(json['id']),
      opId: serializer.fromJson<String>(json['opId']),
      kind: serializer.fromJson<String>(json['kind']),
      requirementId: serializer.fromJson<int>(json['requirementId']),
      status: serializer.fromJson<String>(json['status']),
      subjectRef: serializer.fromJson<String?>(json['subjectRef']),
      aboutExpiry: serializer.fromJson<String?>(json['aboutExpiry']),
      raisedAt: serializer.fromJson<DateTime>(json['raisedAt']),
      decisionNote: serializer.fromJson<String?>(json['decisionNote']),
      decidedAt: serializer.fromJson<DateTime?>(json['decidedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'opId': serializer.toJson<String>(opId),
      'kind': serializer.toJson<String>(kind),
      'requirementId': serializer.toJson<int>(requirementId),
      'status': serializer.toJson<String>(status),
      'subjectRef': serializer.toJson<String?>(subjectRef),
      'aboutExpiry': serializer.toJson<String?>(aboutExpiry),
      'raisedAt': serializer.toJson<DateTime>(raisedAt),
      'decisionNote': serializer.toJson<String?>(decisionNote),
      'decidedAt': serializer.toJson<DateTime?>(decidedAt),
    };
  }

  LocalCrewStatement copyWith({
    int? id,
    String? opId,
    String? kind,
    int? requirementId,
    String? status,
    Value<String?> subjectRef = const Value.absent(),
    Value<String?> aboutExpiry = const Value.absent(),
    DateTime? raisedAt,
    Value<String?> decisionNote = const Value.absent(),
    Value<DateTime?> decidedAt = const Value.absent(),
  }) => LocalCrewStatement(
    id: id ?? this.id,
    opId: opId ?? this.opId,
    kind: kind ?? this.kind,
    requirementId: requirementId ?? this.requirementId,
    status: status ?? this.status,
    subjectRef: subjectRef.present ? subjectRef.value : this.subjectRef,
    aboutExpiry: aboutExpiry.present ? aboutExpiry.value : this.aboutExpiry,
    raisedAt: raisedAt ?? this.raisedAt,
    decisionNote: decisionNote.present ? decisionNote.value : this.decisionNote,
    decidedAt: decidedAt.present ? decidedAt.value : this.decidedAt,
  );
  LocalCrewStatement copyWithCompanion(CrewStatementsCompanion data) {
    return LocalCrewStatement(
      id: data.id.present ? data.id.value : this.id,
      opId: data.opId.present ? data.opId.value : this.opId,
      kind: data.kind.present ? data.kind.value : this.kind,
      requirementId: data.requirementId.present
          ? data.requirementId.value
          : this.requirementId,
      status: data.status.present ? data.status.value : this.status,
      subjectRef: data.subjectRef.present
          ? data.subjectRef.value
          : this.subjectRef,
      aboutExpiry: data.aboutExpiry.present
          ? data.aboutExpiry.value
          : this.aboutExpiry,
      raisedAt: data.raisedAt.present ? data.raisedAt.value : this.raisedAt,
      decisionNote: data.decisionNote.present
          ? data.decisionNote.value
          : this.decisionNote,
      decidedAt: data.decidedAt.present ? data.decidedAt.value : this.decidedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalCrewStatement(')
          ..write('id: $id, ')
          ..write('opId: $opId, ')
          ..write('kind: $kind, ')
          ..write('requirementId: $requirementId, ')
          ..write('status: $status, ')
          ..write('subjectRef: $subjectRef, ')
          ..write('aboutExpiry: $aboutExpiry, ')
          ..write('raisedAt: $raisedAt, ')
          ..write('decisionNote: $decisionNote, ')
          ..write('decidedAt: $decidedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    opId,
    kind,
    requirementId,
    status,
    subjectRef,
    aboutExpiry,
    raisedAt,
    decisionNote,
    decidedAt,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalCrewStatement &&
          other.id == this.id &&
          other.opId == this.opId &&
          other.kind == this.kind &&
          other.requirementId == this.requirementId &&
          other.status == this.status &&
          other.subjectRef == this.subjectRef &&
          other.aboutExpiry == this.aboutExpiry &&
          other.raisedAt == this.raisedAt &&
          other.decisionNote == this.decisionNote &&
          other.decidedAt == this.decidedAt);
}

class CrewStatementsCompanion extends UpdateCompanion<LocalCrewStatement> {
  final Value<int> id;
  final Value<String> opId;
  final Value<String> kind;
  final Value<int> requirementId;
  final Value<String> status;
  final Value<String?> subjectRef;
  final Value<String?> aboutExpiry;
  final Value<DateTime> raisedAt;
  final Value<String?> decisionNote;
  final Value<DateTime?> decidedAt;
  const CrewStatementsCompanion({
    this.id = const Value.absent(),
    this.opId = const Value.absent(),
    this.kind = const Value.absent(),
    this.requirementId = const Value.absent(),
    this.status = const Value.absent(),
    this.subjectRef = const Value.absent(),
    this.aboutExpiry = const Value.absent(),
    this.raisedAt = const Value.absent(),
    this.decisionNote = const Value.absent(),
    this.decidedAt = const Value.absent(),
  });
  CrewStatementsCompanion.insert({
    this.id = const Value.absent(),
    required String opId,
    required String kind,
    required int requirementId,
    required String status,
    this.subjectRef = const Value.absent(),
    this.aboutExpiry = const Value.absent(),
    required DateTime raisedAt,
    this.decisionNote = const Value.absent(),
    this.decidedAt = const Value.absent(),
  }) : opId = Value(opId),
       kind = Value(kind),
       requirementId = Value(requirementId),
       status = Value(status),
       raisedAt = Value(raisedAt);
  static Insertable<LocalCrewStatement> custom({
    Expression<int>? id,
    Expression<String>? opId,
    Expression<String>? kind,
    Expression<int>? requirementId,
    Expression<String>? status,
    Expression<String>? subjectRef,
    Expression<String>? aboutExpiry,
    Expression<DateTime>? raisedAt,
    Expression<String>? decisionNote,
    Expression<DateTime>? decidedAt,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (opId != null) 'op_id': opId,
      if (kind != null) 'kind': kind,
      if (requirementId != null) 'requirement_id': requirementId,
      if (status != null) 'status': status,
      if (subjectRef != null) 'subject_ref': subjectRef,
      if (aboutExpiry != null) 'about_expiry': aboutExpiry,
      if (raisedAt != null) 'raised_at': raisedAt,
      if (decisionNote != null) 'decision_note': decisionNote,
      if (decidedAt != null) 'decided_at': decidedAt,
    });
  }

  CrewStatementsCompanion copyWith({
    Value<int>? id,
    Value<String>? opId,
    Value<String>? kind,
    Value<int>? requirementId,
    Value<String>? status,
    Value<String?>? subjectRef,
    Value<String?>? aboutExpiry,
    Value<DateTime>? raisedAt,
    Value<String?>? decisionNote,
    Value<DateTime?>? decidedAt,
  }) {
    return CrewStatementsCompanion(
      id: id ?? this.id,
      opId: opId ?? this.opId,
      kind: kind ?? this.kind,
      requirementId: requirementId ?? this.requirementId,
      status: status ?? this.status,
      subjectRef: subjectRef ?? this.subjectRef,
      aboutExpiry: aboutExpiry ?? this.aboutExpiry,
      raisedAt: raisedAt ?? this.raisedAt,
      decisionNote: decisionNote ?? this.decisionNote,
      decidedAt: decidedAt ?? this.decidedAt,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (opId.present) {
      map['op_id'] = Variable<String>(opId.value);
    }
    if (kind.present) {
      map['kind'] = Variable<String>(kind.value);
    }
    if (requirementId.present) {
      map['requirement_id'] = Variable<int>(requirementId.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(status.value);
    }
    if (subjectRef.present) {
      map['subject_ref'] = Variable<String>(subjectRef.value);
    }
    if (aboutExpiry.present) {
      map['about_expiry'] = Variable<String>(aboutExpiry.value);
    }
    if (raisedAt.present) {
      map['raised_at'] = Variable<DateTime>(raisedAt.value);
    }
    if (decisionNote.present) {
      map['decision_note'] = Variable<String>(decisionNote.value);
    }
    if (decidedAt.present) {
      map['decided_at'] = Variable<DateTime>(decidedAt.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CrewStatementsCompanion(')
          ..write('id: $id, ')
          ..write('opId: $opId, ')
          ..write('kind: $kind, ')
          ..write('requirementId: $requirementId, ')
          ..write('status: $status, ')
          ..write('subjectRef: $subjectRef, ')
          ..write('aboutExpiry: $aboutExpiry, ')
          ..write('raisedAt: $raisedAt, ')
          ..write('decisionNote: $decisionNote, ')
          ..write('decidedAt: $decidedAt')
          ..write(')'))
        .toString();
  }
}

class $AttestationsTable extends Attestations
    with TableInfo<$AttestationsTable, LocalAttestation> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $AttestationsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _opIdMeta = const VerificationMeta('opId');
  @override
  late final GeneratedColumn<String> opId = GeneratedColumn<String>(
    'op_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _assignmentIdMeta = const VerificationMeta(
    'assignmentId',
  );
  @override
  late final GeneratedColumn<int> assignmentId = GeneratedColumn<int>(
    'assignment_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _ccIdMeta = const VerificationMeta('ccId');
  @override
  late final GeneratedColumn<String> ccId = GeneratedColumn<String>(
    'cc_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _declarationsMeta = const VerificationMeta(
    'declarations',
  );
  @override
  late final GeneratedColumn<String> declarations = GeneratedColumn<String>(
    'declarations',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _signedAtMeta = const VerificationMeta(
    'signedAt',
  );
  @override
  late final GeneratedColumn<DateTime> signedAt = GeneratedColumn<DateTime>(
    'signed_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _signedAtDisplayMeta = const VerificationMeta(
    'signedAtDisplay',
  );
  @override
  late final GeneratedColumn<String> signedAtDisplay = GeneratedColumn<String>(
    'signed_at_display',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    opId,
    assignmentId,
    ccId,
    declarations,
    signedAt,
    signedAtDisplay,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'attestations';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalAttestation> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('op_id')) {
      context.handle(
        _opIdMeta,
        opId.isAcceptableOrUnknown(data['op_id']!, _opIdMeta),
      );
    } else if (isInserting) {
      context.missing(_opIdMeta);
    }
    if (data.containsKey('assignment_id')) {
      context.handle(
        _assignmentIdMeta,
        assignmentId.isAcceptableOrUnknown(
          data['assignment_id']!,
          _assignmentIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_assignmentIdMeta);
    }
    if (data.containsKey('cc_id')) {
      context.handle(
        _ccIdMeta,
        ccId.isAcceptableOrUnknown(data['cc_id']!, _ccIdMeta),
      );
    } else if (isInserting) {
      context.missing(_ccIdMeta);
    }
    if (data.containsKey('declarations')) {
      context.handle(
        _declarationsMeta,
        declarations.isAcceptableOrUnknown(
          data['declarations']!,
          _declarationsMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_declarationsMeta);
    }
    if (data.containsKey('signed_at')) {
      context.handle(
        _signedAtMeta,
        signedAt.isAcceptableOrUnknown(data['signed_at']!, _signedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_signedAtMeta);
    }
    if (data.containsKey('signed_at_display')) {
      context.handle(
        _signedAtDisplayMeta,
        signedAtDisplay.isAcceptableOrUnknown(
          data['signed_at_display']!,
          _signedAtDisplayMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_signedAtDisplayMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalAttestation map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalAttestation(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      opId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}op_id'],
      )!,
      assignmentId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}assignment_id'],
      )!,
      ccId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}cc_id'],
      )!,
      declarations: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}declarations'],
      )!,
      signedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}signed_at'],
      )!,
      signedAtDisplay: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}signed_at_display'],
      )!,
    );
  }

  @override
  $AttestationsTable createAlias(String alias) {
    return $AttestationsTable(attachedDatabase, alias);
  }
}

class LocalAttestation extends DataClass
    implements Insertable<LocalAttestation> {
  final int id;
  final String opId;
  final int assignmentId;
  final String ccId;

  /// The confirmed declaration ids, newline-separated.
  ///
  /// A delimited column rather than a child table: this is a replica of a server row that is only
  /// ever read whole, and drift's relational modelling would buy nothing for it. Newline because a
  /// declaration id is a slug and can never contain one.
  final String declarations;
  final DateTime signedAt;

  /// The signature line as the server wrote it, in the vessel's timezone.
  ///
  /// Stored rather than formatted here: a phone knows neither the operating timezone (O-11) nor
  /// the admin date override, and this string sits on a legal record. Rendering it from a device
  /// set to UTC would be eight hours wrong, for some viewers only, silently.
  final String signedAtDisplay;
  const LocalAttestation({
    required this.id,
    required this.opId,
    required this.assignmentId,
    required this.ccId,
    required this.declarations,
    required this.signedAt,
    required this.signedAtDisplay,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['op_id'] = Variable<String>(opId);
    map['assignment_id'] = Variable<int>(assignmentId);
    map['cc_id'] = Variable<String>(ccId);
    map['declarations'] = Variable<String>(declarations);
    map['signed_at'] = Variable<DateTime>(signedAt);
    map['signed_at_display'] = Variable<String>(signedAtDisplay);
    return map;
  }

  AttestationsCompanion toCompanion(bool nullToAbsent) {
    return AttestationsCompanion(
      id: Value(id),
      opId: Value(opId),
      assignmentId: Value(assignmentId),
      ccId: Value(ccId),
      declarations: Value(declarations),
      signedAt: Value(signedAt),
      signedAtDisplay: Value(signedAtDisplay),
    );
  }

  factory LocalAttestation.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalAttestation(
      id: serializer.fromJson<int>(json['id']),
      opId: serializer.fromJson<String>(json['opId']),
      assignmentId: serializer.fromJson<int>(json['assignmentId']),
      ccId: serializer.fromJson<String>(json['ccId']),
      declarations: serializer.fromJson<String>(json['declarations']),
      signedAt: serializer.fromJson<DateTime>(json['signedAt']),
      signedAtDisplay: serializer.fromJson<String>(json['signedAtDisplay']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'opId': serializer.toJson<String>(opId),
      'assignmentId': serializer.toJson<int>(assignmentId),
      'ccId': serializer.toJson<String>(ccId),
      'declarations': serializer.toJson<String>(declarations),
      'signedAt': serializer.toJson<DateTime>(signedAt),
      'signedAtDisplay': serializer.toJson<String>(signedAtDisplay),
    };
  }

  LocalAttestation copyWith({
    int? id,
    String? opId,
    int? assignmentId,
    String? ccId,
    String? declarations,
    DateTime? signedAt,
    String? signedAtDisplay,
  }) => LocalAttestation(
    id: id ?? this.id,
    opId: opId ?? this.opId,
    assignmentId: assignmentId ?? this.assignmentId,
    ccId: ccId ?? this.ccId,
    declarations: declarations ?? this.declarations,
    signedAt: signedAt ?? this.signedAt,
    signedAtDisplay: signedAtDisplay ?? this.signedAtDisplay,
  );
  LocalAttestation copyWithCompanion(AttestationsCompanion data) {
    return LocalAttestation(
      id: data.id.present ? data.id.value : this.id,
      opId: data.opId.present ? data.opId.value : this.opId,
      assignmentId: data.assignmentId.present
          ? data.assignmentId.value
          : this.assignmentId,
      ccId: data.ccId.present ? data.ccId.value : this.ccId,
      declarations: data.declarations.present
          ? data.declarations.value
          : this.declarations,
      signedAt: data.signedAt.present ? data.signedAt.value : this.signedAt,
      signedAtDisplay: data.signedAtDisplay.present
          ? data.signedAtDisplay.value
          : this.signedAtDisplay,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalAttestation(')
          ..write('id: $id, ')
          ..write('opId: $opId, ')
          ..write('assignmentId: $assignmentId, ')
          ..write('ccId: $ccId, ')
          ..write('declarations: $declarations, ')
          ..write('signedAt: $signedAt, ')
          ..write('signedAtDisplay: $signedAtDisplay')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    opId,
    assignmentId,
    ccId,
    declarations,
    signedAt,
    signedAtDisplay,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalAttestation &&
          other.id == this.id &&
          other.opId == this.opId &&
          other.assignmentId == this.assignmentId &&
          other.ccId == this.ccId &&
          other.declarations == this.declarations &&
          other.signedAt == this.signedAt &&
          other.signedAtDisplay == this.signedAtDisplay);
}

class AttestationsCompanion extends UpdateCompanion<LocalAttestation> {
  final Value<int> id;
  final Value<String> opId;
  final Value<int> assignmentId;
  final Value<String> ccId;
  final Value<String> declarations;
  final Value<DateTime> signedAt;
  final Value<String> signedAtDisplay;
  const AttestationsCompanion({
    this.id = const Value.absent(),
    this.opId = const Value.absent(),
    this.assignmentId = const Value.absent(),
    this.ccId = const Value.absent(),
    this.declarations = const Value.absent(),
    this.signedAt = const Value.absent(),
    this.signedAtDisplay = const Value.absent(),
  });
  AttestationsCompanion.insert({
    this.id = const Value.absent(),
    required String opId,
    required int assignmentId,
    required String ccId,
    required String declarations,
    required DateTime signedAt,
    required String signedAtDisplay,
  }) : opId = Value(opId),
       assignmentId = Value(assignmentId),
       ccId = Value(ccId),
       declarations = Value(declarations),
       signedAt = Value(signedAt),
       signedAtDisplay = Value(signedAtDisplay);
  static Insertable<LocalAttestation> custom({
    Expression<int>? id,
    Expression<String>? opId,
    Expression<int>? assignmentId,
    Expression<String>? ccId,
    Expression<String>? declarations,
    Expression<DateTime>? signedAt,
    Expression<String>? signedAtDisplay,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (opId != null) 'op_id': opId,
      if (assignmentId != null) 'assignment_id': assignmentId,
      if (ccId != null) 'cc_id': ccId,
      if (declarations != null) 'declarations': declarations,
      if (signedAt != null) 'signed_at': signedAt,
      if (signedAtDisplay != null) 'signed_at_display': signedAtDisplay,
    });
  }

  AttestationsCompanion copyWith({
    Value<int>? id,
    Value<String>? opId,
    Value<int>? assignmentId,
    Value<String>? ccId,
    Value<String>? declarations,
    Value<DateTime>? signedAt,
    Value<String>? signedAtDisplay,
  }) {
    return AttestationsCompanion(
      id: id ?? this.id,
      opId: opId ?? this.opId,
      assignmentId: assignmentId ?? this.assignmentId,
      ccId: ccId ?? this.ccId,
      declarations: declarations ?? this.declarations,
      signedAt: signedAt ?? this.signedAt,
      signedAtDisplay: signedAtDisplay ?? this.signedAtDisplay,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (opId.present) {
      map['op_id'] = Variable<String>(opId.value);
    }
    if (assignmentId.present) {
      map['assignment_id'] = Variable<int>(assignmentId.value);
    }
    if (ccId.present) {
      map['cc_id'] = Variable<String>(ccId.value);
    }
    if (declarations.present) {
      map['declarations'] = Variable<String>(declarations.value);
    }
    if (signedAt.present) {
      map['signed_at'] = Variable<DateTime>(signedAt.value);
    }
    if (signedAtDisplay.present) {
      map['signed_at_display'] = Variable<String>(signedAtDisplay.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('AttestationsCompanion(')
          ..write('id: $id, ')
          ..write('opId: $opId, ')
          ..write('assignmentId: $assignmentId, ')
          ..write('ccId: $ccId, ')
          ..write('declarations: $declarations, ')
          ..write('signedAt: $signedAt, ')
          ..write('signedAtDisplay: $signedAtDisplay')
          ..write(')'))
        .toString();
  }
}

class $CourseOptionsTable extends CourseOptions
    with TableInfo<$CourseOptionsTable, LocalCourseOption> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $CourseOptionsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _requirementIdMeta = const VerificationMeta(
    'requirementId',
  );
  @override
  late final GeneratedColumn<int> requirementId = GeneratedColumn<int>(
    'requirement_id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _startsMeta = const VerificationMeta('starts');
  @override
  late final GeneratedColumn<String> starts = GeneratedColumn<String>(
    'starts',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _finishesMeta = const VerificationMeta(
    'finishes',
  );
  @override
  late final GeneratedColumn<String> finishes = GeneratedColumn<String>(
    'finishes',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _providerMeta = const VerificationMeta(
    'provider',
  );
  @override
  late final GeneratedColumn<String> provider = GeneratedColumn<String>(
    'provider',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _locationMeta = const VerificationMeta(
    'location',
  );
  @override
  late final GeneratedColumn<String> location = GeneratedColumn<String>(
    'location',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _durationLabelMeta = const VerificationMeta(
    'durationLabel',
  );
  @override
  late final GeneratedColumn<String> durationLabel = GeneratedColumn<String>(
    'duration_label',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _seatsMeta = const VerificationMeta('seats');
  @override
  late final GeneratedColumn<int> seats = GeneratedColumn<int>(
    'seats',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _noteMeta = const VerificationMeta('note');
  @override
  late final GeneratedColumn<String> note = GeneratedColumn<String>(
    'note',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _recommendedMeta = const VerificationMeta(
    'recommended',
  );
  @override
  late final GeneratedColumn<bool> recommended = GeneratedColumn<bool>(
    'recommended',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("recommended" IN (0, 1))',
    ),
  );
  static const VerificationMeta _waitlistOnlyMeta = const VerificationMeta(
    'waitlistOnly',
  );
  @override
  late final GeneratedColumn<bool> waitlistOnly = GeneratedColumn<bool>(
    'waitlist_only',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("waitlist_only" IN (0, 1))',
    ),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    requirementId,
    starts,
    finishes,
    provider,
    location,
    durationLabel,
    seats,
    note,
    recommended,
    waitlistOnly,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'course_options';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalCourseOption> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('requirement_id')) {
      context.handle(
        _requirementIdMeta,
        requirementId.isAcceptableOrUnknown(
          data['requirement_id']!,
          _requirementIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_requirementIdMeta);
    }
    if (data.containsKey('starts')) {
      context.handle(
        _startsMeta,
        starts.isAcceptableOrUnknown(data['starts']!, _startsMeta),
      );
    } else if (isInserting) {
      context.missing(_startsMeta);
    }
    if (data.containsKey('finishes')) {
      context.handle(
        _finishesMeta,
        finishes.isAcceptableOrUnknown(data['finishes']!, _finishesMeta),
      );
    } else if (isInserting) {
      context.missing(_finishesMeta);
    }
    if (data.containsKey('provider')) {
      context.handle(
        _providerMeta,
        provider.isAcceptableOrUnknown(data['provider']!, _providerMeta),
      );
    } else if (isInserting) {
      context.missing(_providerMeta);
    }
    if (data.containsKey('location')) {
      context.handle(
        _locationMeta,
        location.isAcceptableOrUnknown(data['location']!, _locationMeta),
      );
    } else if (isInserting) {
      context.missing(_locationMeta);
    }
    if (data.containsKey('duration_label')) {
      context.handle(
        _durationLabelMeta,
        durationLabel.isAcceptableOrUnknown(
          data['duration_label']!,
          _durationLabelMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_durationLabelMeta);
    }
    if (data.containsKey('seats')) {
      context.handle(
        _seatsMeta,
        seats.isAcceptableOrUnknown(data['seats']!, _seatsMeta),
      );
    } else if (isInserting) {
      context.missing(_seatsMeta);
    }
    if (data.containsKey('note')) {
      context.handle(
        _noteMeta,
        note.isAcceptableOrUnknown(data['note']!, _noteMeta),
      );
    } else if (isInserting) {
      context.missing(_noteMeta);
    }
    if (data.containsKey('recommended')) {
      context.handle(
        _recommendedMeta,
        recommended.isAcceptableOrUnknown(
          data['recommended']!,
          _recommendedMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_recommendedMeta);
    }
    if (data.containsKey('waitlist_only')) {
      context.handle(
        _waitlistOnlyMeta,
        waitlistOnly.isAcceptableOrUnknown(
          data['waitlist_only']!,
          _waitlistOnlyMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_waitlistOnlyMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalCourseOption map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalCourseOption(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      requirementId: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}requirement_id'],
      )!,
      starts: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}starts'],
      )!,
      finishes: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}finishes'],
      )!,
      provider: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}provider'],
      )!,
      location: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}location'],
      )!,
      durationLabel: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}duration_label'],
      )!,
      seats: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}seats'],
      )!,
      note: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}note'],
      )!,
      recommended: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}recommended'],
      )!,
      waitlistOnly: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}waitlist_only'],
      )!,
    );
  }

  @override
  $CourseOptionsTable createAlias(String alias) {
    return $CourseOptionsTable(attachedDatabase, alias);
  }
}

class LocalCourseOption extends DataClass
    implements Insertable<LocalCourseOption> {
  /// The catalogue's business key — what goes back as `subjectRef` when a seat is requested.
  final String id;
  final int requirementId;
  final String starts;
  final String finishes;
  final String provider;
  final String location;
  final String durationLabel;

  /// What the provider last said. Zero means waitlist-only; nothing here reserves a place.
  final int seats;
  final String note;
  final bool recommended;
  final bool waitlistOnly;
  const LocalCourseOption({
    required this.id,
    required this.requirementId,
    required this.starts,
    required this.finishes,
    required this.provider,
    required this.location,
    required this.durationLabel,
    required this.seats,
    required this.note,
    required this.recommended,
    required this.waitlistOnly,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['requirement_id'] = Variable<int>(requirementId);
    map['starts'] = Variable<String>(starts);
    map['finishes'] = Variable<String>(finishes);
    map['provider'] = Variable<String>(provider);
    map['location'] = Variable<String>(location);
    map['duration_label'] = Variable<String>(durationLabel);
    map['seats'] = Variable<int>(seats);
    map['note'] = Variable<String>(note);
    map['recommended'] = Variable<bool>(recommended);
    map['waitlist_only'] = Variable<bool>(waitlistOnly);
    return map;
  }

  CourseOptionsCompanion toCompanion(bool nullToAbsent) {
    return CourseOptionsCompanion(
      id: Value(id),
      requirementId: Value(requirementId),
      starts: Value(starts),
      finishes: Value(finishes),
      provider: Value(provider),
      location: Value(location),
      durationLabel: Value(durationLabel),
      seats: Value(seats),
      note: Value(note),
      recommended: Value(recommended),
      waitlistOnly: Value(waitlistOnly),
    );
  }

  factory LocalCourseOption.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalCourseOption(
      id: serializer.fromJson<String>(json['id']),
      requirementId: serializer.fromJson<int>(json['requirementId']),
      starts: serializer.fromJson<String>(json['starts']),
      finishes: serializer.fromJson<String>(json['finishes']),
      provider: serializer.fromJson<String>(json['provider']),
      location: serializer.fromJson<String>(json['location']),
      durationLabel: serializer.fromJson<String>(json['durationLabel']),
      seats: serializer.fromJson<int>(json['seats']),
      note: serializer.fromJson<String>(json['note']),
      recommended: serializer.fromJson<bool>(json['recommended']),
      waitlistOnly: serializer.fromJson<bool>(json['waitlistOnly']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'requirementId': serializer.toJson<int>(requirementId),
      'starts': serializer.toJson<String>(starts),
      'finishes': serializer.toJson<String>(finishes),
      'provider': serializer.toJson<String>(provider),
      'location': serializer.toJson<String>(location),
      'durationLabel': serializer.toJson<String>(durationLabel),
      'seats': serializer.toJson<int>(seats),
      'note': serializer.toJson<String>(note),
      'recommended': serializer.toJson<bool>(recommended),
      'waitlistOnly': serializer.toJson<bool>(waitlistOnly),
    };
  }

  LocalCourseOption copyWith({
    String? id,
    int? requirementId,
    String? starts,
    String? finishes,
    String? provider,
    String? location,
    String? durationLabel,
    int? seats,
    String? note,
    bool? recommended,
    bool? waitlistOnly,
  }) => LocalCourseOption(
    id: id ?? this.id,
    requirementId: requirementId ?? this.requirementId,
    starts: starts ?? this.starts,
    finishes: finishes ?? this.finishes,
    provider: provider ?? this.provider,
    location: location ?? this.location,
    durationLabel: durationLabel ?? this.durationLabel,
    seats: seats ?? this.seats,
    note: note ?? this.note,
    recommended: recommended ?? this.recommended,
    waitlistOnly: waitlistOnly ?? this.waitlistOnly,
  );
  LocalCourseOption copyWithCompanion(CourseOptionsCompanion data) {
    return LocalCourseOption(
      id: data.id.present ? data.id.value : this.id,
      requirementId: data.requirementId.present
          ? data.requirementId.value
          : this.requirementId,
      starts: data.starts.present ? data.starts.value : this.starts,
      finishes: data.finishes.present ? data.finishes.value : this.finishes,
      provider: data.provider.present ? data.provider.value : this.provider,
      location: data.location.present ? data.location.value : this.location,
      durationLabel: data.durationLabel.present
          ? data.durationLabel.value
          : this.durationLabel,
      seats: data.seats.present ? data.seats.value : this.seats,
      note: data.note.present ? data.note.value : this.note,
      recommended: data.recommended.present
          ? data.recommended.value
          : this.recommended,
      waitlistOnly: data.waitlistOnly.present
          ? data.waitlistOnly.value
          : this.waitlistOnly,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalCourseOption(')
          ..write('id: $id, ')
          ..write('requirementId: $requirementId, ')
          ..write('starts: $starts, ')
          ..write('finishes: $finishes, ')
          ..write('provider: $provider, ')
          ..write('location: $location, ')
          ..write('durationLabel: $durationLabel, ')
          ..write('seats: $seats, ')
          ..write('note: $note, ')
          ..write('recommended: $recommended, ')
          ..write('waitlistOnly: $waitlistOnly')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    requirementId,
    starts,
    finishes,
    provider,
    location,
    durationLabel,
    seats,
    note,
    recommended,
    waitlistOnly,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalCourseOption &&
          other.id == this.id &&
          other.requirementId == this.requirementId &&
          other.starts == this.starts &&
          other.finishes == this.finishes &&
          other.provider == this.provider &&
          other.location == this.location &&
          other.durationLabel == this.durationLabel &&
          other.seats == this.seats &&
          other.note == this.note &&
          other.recommended == this.recommended &&
          other.waitlistOnly == this.waitlistOnly);
}

class CourseOptionsCompanion extends UpdateCompanion<LocalCourseOption> {
  final Value<String> id;
  final Value<int> requirementId;
  final Value<String> starts;
  final Value<String> finishes;
  final Value<String> provider;
  final Value<String> location;
  final Value<String> durationLabel;
  final Value<int> seats;
  final Value<String> note;
  final Value<bool> recommended;
  final Value<bool> waitlistOnly;
  final Value<int> rowid;
  const CourseOptionsCompanion({
    this.id = const Value.absent(),
    this.requirementId = const Value.absent(),
    this.starts = const Value.absent(),
    this.finishes = const Value.absent(),
    this.provider = const Value.absent(),
    this.location = const Value.absent(),
    this.durationLabel = const Value.absent(),
    this.seats = const Value.absent(),
    this.note = const Value.absent(),
    this.recommended = const Value.absent(),
    this.waitlistOnly = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  CourseOptionsCompanion.insert({
    required String id,
    required int requirementId,
    required String starts,
    required String finishes,
    required String provider,
    required String location,
    required String durationLabel,
    required int seats,
    required String note,
    required bool recommended,
    required bool waitlistOnly,
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       requirementId = Value(requirementId),
       starts = Value(starts),
       finishes = Value(finishes),
       provider = Value(provider),
       location = Value(location),
       durationLabel = Value(durationLabel),
       seats = Value(seats),
       note = Value(note),
       recommended = Value(recommended),
       waitlistOnly = Value(waitlistOnly);
  static Insertable<LocalCourseOption> custom({
    Expression<String>? id,
    Expression<int>? requirementId,
    Expression<String>? starts,
    Expression<String>? finishes,
    Expression<String>? provider,
    Expression<String>? location,
    Expression<String>? durationLabel,
    Expression<int>? seats,
    Expression<String>? note,
    Expression<bool>? recommended,
    Expression<bool>? waitlistOnly,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (requirementId != null) 'requirement_id': requirementId,
      if (starts != null) 'starts': starts,
      if (finishes != null) 'finishes': finishes,
      if (provider != null) 'provider': provider,
      if (location != null) 'location': location,
      if (durationLabel != null) 'duration_label': durationLabel,
      if (seats != null) 'seats': seats,
      if (note != null) 'note': note,
      if (recommended != null) 'recommended': recommended,
      if (waitlistOnly != null) 'waitlist_only': waitlistOnly,
      if (rowid != null) 'rowid': rowid,
    });
  }

  CourseOptionsCompanion copyWith({
    Value<String>? id,
    Value<int>? requirementId,
    Value<String>? starts,
    Value<String>? finishes,
    Value<String>? provider,
    Value<String>? location,
    Value<String>? durationLabel,
    Value<int>? seats,
    Value<String>? note,
    Value<bool>? recommended,
    Value<bool>? waitlistOnly,
    Value<int>? rowid,
  }) {
    return CourseOptionsCompanion(
      id: id ?? this.id,
      requirementId: requirementId ?? this.requirementId,
      starts: starts ?? this.starts,
      finishes: finishes ?? this.finishes,
      provider: provider ?? this.provider,
      location: location ?? this.location,
      durationLabel: durationLabel ?? this.durationLabel,
      seats: seats ?? this.seats,
      note: note ?? this.note,
      recommended: recommended ?? this.recommended,
      waitlistOnly: waitlistOnly ?? this.waitlistOnly,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (requirementId.present) {
      map['requirement_id'] = Variable<int>(requirementId.value);
    }
    if (starts.present) {
      map['starts'] = Variable<String>(starts.value);
    }
    if (finishes.present) {
      map['finishes'] = Variable<String>(finishes.value);
    }
    if (provider.present) {
      map['provider'] = Variable<String>(provider.value);
    }
    if (location.present) {
      map['location'] = Variable<String>(location.value);
    }
    if (durationLabel.present) {
      map['duration_label'] = Variable<String>(durationLabel.value);
    }
    if (seats.present) {
      map['seats'] = Variable<int>(seats.value);
    }
    if (note.present) {
      map['note'] = Variable<String>(note.value);
    }
    if (recommended.present) {
      map['recommended'] = Variable<bool>(recommended.value);
    }
    if (waitlistOnly.present) {
      map['waitlist_only'] = Variable<bool>(waitlistOnly.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('CourseOptionsCompanion(')
          ..write('id: $id, ')
          ..write('requirementId: $requirementId, ')
          ..write('starts: $starts, ')
          ..write('finishes: $finishes, ')
          ..write('provider: $provider, ')
          ..write('location: $location, ')
          ..write('durationLabel: $durationLabel, ')
          ..write('seats: $seats, ')
          ..write('note: $note, ')
          ..write('recommended: $recommended, ')
          ..write('waitlistOnly: $waitlistOnly, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $TeamMembersTable extends TeamMembers
    with TableInfo<$TeamMembersTable, LocalTeamMember> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $TeamMembersTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _samMeta = const VerificationMeta('sam');
  @override
  late final GeneratedColumn<String> sam = GeneratedColumn<String>(
    'sam',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _nameMeta = const VerificationMeta('name');
  @override
  late final GeneratedColumn<String> name = GeneratedColumn<String>(
    'name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _worstStateMeta = const VerificationMeta(
    'worstState',
  );
  @override
  late final GeneratedColumn<String> worstState = GeneratedColumn<String>(
    'worst_state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _reasonMeta = const VerificationMeta('reason');
  @override
  late final GeneratedColumn<String> reason = GeneratedColumn<String>(
    'reason',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _inHandMeta = const VerificationMeta('inHand');
  @override
  late final GeneratedColumn<bool> inHand = GeneratedColumn<bool>(
    'in_hand',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: true,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("in_hand" IN (0, 1))',
    ),
  );
  static const VerificationMeta _nudgedAtMeta = const VerificationMeta(
    'nudgedAt',
  );
  @override
  late final GeneratedColumn<DateTime> nudgedAt = GeneratedColumn<DateTime>(
    'nudged_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    sam,
    name,
    worstState,
    reason,
    inHand,
    nudgedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'team_members';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalTeamMember> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('sam')) {
      context.handle(
        _samMeta,
        sam.isAcceptableOrUnknown(data['sam']!, _samMeta),
      );
    } else if (isInserting) {
      context.missing(_samMeta);
    }
    if (data.containsKey('name')) {
      context.handle(
        _nameMeta,
        name.isAcceptableOrUnknown(data['name']!, _nameMeta),
      );
    } else if (isInserting) {
      context.missing(_nameMeta);
    }
    if (data.containsKey('worst_state')) {
      context.handle(
        _worstStateMeta,
        worstState.isAcceptableOrUnknown(data['worst_state']!, _worstStateMeta),
      );
    } else if (isInserting) {
      context.missing(_worstStateMeta);
    }
    if (data.containsKey('reason')) {
      context.handle(
        _reasonMeta,
        reason.isAcceptableOrUnknown(data['reason']!, _reasonMeta),
      );
    }
    if (data.containsKey('in_hand')) {
      context.handle(
        _inHandMeta,
        inHand.isAcceptableOrUnknown(data['in_hand']!, _inHandMeta),
      );
    } else if (isInserting) {
      context.missing(_inHandMeta);
    }
    if (data.containsKey('nudged_at')) {
      context.handle(
        _nudgedAtMeta,
        nudgedAt.isAcceptableOrUnknown(data['nudged_at']!, _nudgedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {sam};
  @override
  LocalTeamMember map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalTeamMember(
      sam: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}sam'],
      )!,
      name: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}name'],
      )!,
      worstState: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}worst_state'],
      )!,
      reason: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}reason'],
      ),
      inHand: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}in_hand'],
      )!,
      nudgedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}nudged_at'],
      ),
    );
  }

  @override
  $TeamMembersTable createAlias(String alias) {
    return $TeamMembersTable(attachedDatabase, alias);
  }
}

class LocalTeamMember extends DataClass implements Insertable<LocalTeamMember> {
  final String sam;
  final String name;

  /// A §5.1 cell state, the engine's own roll-up.
  final String worstState;
  final String? reason;

  /// Something is already moving, so the nudge is suppressed.
  final bool inHand;
  final DateTime? nudgedAt;
  const LocalTeamMember({
    required this.sam,
    required this.name,
    required this.worstState,
    this.reason,
    required this.inHand,
    this.nudgedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['sam'] = Variable<String>(sam);
    map['name'] = Variable<String>(name);
    map['worst_state'] = Variable<String>(worstState);
    if (!nullToAbsent || reason != null) {
      map['reason'] = Variable<String>(reason);
    }
    map['in_hand'] = Variable<bool>(inHand);
    if (!nullToAbsent || nudgedAt != null) {
      map['nudged_at'] = Variable<DateTime>(nudgedAt);
    }
    return map;
  }

  TeamMembersCompanion toCompanion(bool nullToAbsent) {
    return TeamMembersCompanion(
      sam: Value(sam),
      name: Value(name),
      worstState: Value(worstState),
      reason: reason == null && nullToAbsent
          ? const Value.absent()
          : Value(reason),
      inHand: Value(inHand),
      nudgedAt: nudgedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(nudgedAt),
    );
  }

  factory LocalTeamMember.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalTeamMember(
      sam: serializer.fromJson<String>(json['sam']),
      name: serializer.fromJson<String>(json['name']),
      worstState: serializer.fromJson<String>(json['worstState']),
      reason: serializer.fromJson<String?>(json['reason']),
      inHand: serializer.fromJson<bool>(json['inHand']),
      nudgedAt: serializer.fromJson<DateTime?>(json['nudgedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'sam': serializer.toJson<String>(sam),
      'name': serializer.toJson<String>(name),
      'worstState': serializer.toJson<String>(worstState),
      'reason': serializer.toJson<String?>(reason),
      'inHand': serializer.toJson<bool>(inHand),
      'nudgedAt': serializer.toJson<DateTime?>(nudgedAt),
    };
  }

  LocalTeamMember copyWith({
    String? sam,
    String? name,
    String? worstState,
    Value<String?> reason = const Value.absent(),
    bool? inHand,
    Value<DateTime?> nudgedAt = const Value.absent(),
  }) => LocalTeamMember(
    sam: sam ?? this.sam,
    name: name ?? this.name,
    worstState: worstState ?? this.worstState,
    reason: reason.present ? reason.value : this.reason,
    inHand: inHand ?? this.inHand,
    nudgedAt: nudgedAt.present ? nudgedAt.value : this.nudgedAt,
  );
  LocalTeamMember copyWithCompanion(TeamMembersCompanion data) {
    return LocalTeamMember(
      sam: data.sam.present ? data.sam.value : this.sam,
      name: data.name.present ? data.name.value : this.name,
      worstState: data.worstState.present
          ? data.worstState.value
          : this.worstState,
      reason: data.reason.present ? data.reason.value : this.reason,
      inHand: data.inHand.present ? data.inHand.value : this.inHand,
      nudgedAt: data.nudgedAt.present ? data.nudgedAt.value : this.nudgedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalTeamMember(')
          ..write('sam: $sam, ')
          ..write('name: $name, ')
          ..write('worstState: $worstState, ')
          ..write('reason: $reason, ')
          ..write('inHand: $inHand, ')
          ..write('nudgedAt: $nudgedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode =>
      Object.hash(sam, name, worstState, reason, inHand, nudgedAt);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalTeamMember &&
          other.sam == this.sam &&
          other.name == this.name &&
          other.worstState == this.worstState &&
          other.reason == this.reason &&
          other.inHand == this.inHand &&
          other.nudgedAt == this.nudgedAt);
}

class TeamMembersCompanion extends UpdateCompanion<LocalTeamMember> {
  final Value<String> sam;
  final Value<String> name;
  final Value<String> worstState;
  final Value<String?> reason;
  final Value<bool> inHand;
  final Value<DateTime?> nudgedAt;
  final Value<int> rowid;
  const TeamMembersCompanion({
    this.sam = const Value.absent(),
    this.name = const Value.absent(),
    this.worstState = const Value.absent(),
    this.reason = const Value.absent(),
    this.inHand = const Value.absent(),
    this.nudgedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  TeamMembersCompanion.insert({
    required String sam,
    required String name,
    required String worstState,
    this.reason = const Value.absent(),
    required bool inHand,
    this.nudgedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : sam = Value(sam),
       name = Value(name),
       worstState = Value(worstState),
       inHand = Value(inHand);
  static Insertable<LocalTeamMember> custom({
    Expression<String>? sam,
    Expression<String>? name,
    Expression<String>? worstState,
    Expression<String>? reason,
    Expression<bool>? inHand,
    Expression<DateTime>? nudgedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (sam != null) 'sam': sam,
      if (name != null) 'name': name,
      if (worstState != null) 'worst_state': worstState,
      if (reason != null) 'reason': reason,
      if (inHand != null) 'in_hand': inHand,
      if (nudgedAt != null) 'nudged_at': nudgedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  TeamMembersCompanion copyWith({
    Value<String>? sam,
    Value<String>? name,
    Value<String>? worstState,
    Value<String?>? reason,
    Value<bool>? inHand,
    Value<DateTime?>? nudgedAt,
    Value<int>? rowid,
  }) {
    return TeamMembersCompanion(
      sam: sam ?? this.sam,
      name: name ?? this.name,
      worstState: worstState ?? this.worstState,
      reason: reason ?? this.reason,
      inHand: inHand ?? this.inHand,
      nudgedAt: nudgedAt ?? this.nudgedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (sam.present) {
      map['sam'] = Variable<String>(sam.value);
    }
    if (name.present) {
      map['name'] = Variable<String>(name.value);
    }
    if (worstState.present) {
      map['worst_state'] = Variable<String>(worstState.value);
    }
    if (reason.present) {
      map['reason'] = Variable<String>(reason.value);
    }
    if (inHand.present) {
      map['in_hand'] = Variable<bool>(inHand.value);
    }
    if (nudgedAt.present) {
      map['nudged_at'] = Variable<DateTime>(nudgedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('TeamMembersCompanion(')
          ..write('sam: $sam, ')
          ..write('name: $name, ')
          ..write('worstState: $worstState, ')
          ..write('reason: $reason, ')
          ..write('inHand: $inHand, ')
          ..write('nudgedAt: $nudgedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $SyncStatesTable extends SyncStates
    with TableInfo<$SyncStatesTable, LocalSyncState> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $SyncStatesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<int> id = GeneratedColumn<int>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _cursorMeta = const VerificationMeta('cursor');
  @override
  late final GeneratedColumn<int> cursor = GeneratedColumn<int>(
    'cursor',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _referenceCursorMeta = const VerificationMeta(
    'referenceCursor',
  );
  @override
  late final GeneratedColumn<int> referenceCursor = GeneratedColumn<int>(
    'reference_cursor',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _serverTodayMeta = const VerificationMeta(
    'serverToday',
  );
  @override
  late final GeneratedColumn<String> serverToday = GeneratedColumn<String>(
    'server_today',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _lastSyncedAtMeta = const VerificationMeta(
    'lastSyncedAt',
  );
  @override
  late final GeneratedColumn<DateTime> lastSyncedAt = GeneratedColumn<DateTime>(
    'last_synced_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _standingCcIdMeta = const VerificationMeta(
    'standingCcId',
  );
  @override
  late final GeneratedColumn<String> standingCcId = GeneratedColumn<String>(
    'standing_cc_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _standingPartnershipMeta =
      const VerificationMeta('standingPartnership');
  @override
  late final GeneratedColumn<String> standingPartnership =
      GeneratedColumn<String>(
        'standing_partnership',
        aliasedName,
        true,
        type: DriftSqlType.string,
        requiredDuringInsert: false,
      );
  static const VerificationMeta _standingFromMeta = const VerificationMeta(
    'standingFrom',
  );
  @override
  late final GeneratedColumn<String> standingFrom = GeneratedColumn<String>(
    'standing_from',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _standingToMeta = const VerificationMeta(
    'standingTo',
  );
  @override
  late final GeneratedColumn<String> standingTo = GeneratedColumn<String>(
    'standing_to',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _standingCurrentMeta = const VerificationMeta(
    'standingCurrent',
  );
  @override
  late final GeneratedColumn<bool> standingCurrent = GeneratedColumn<bool>(
    'standing_current',
    aliasedName,
    true,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("standing_current" IN (0, 1))',
    ),
  );
  static const VerificationMeta _standingRollUpMeta = const VerificationMeta(
    'standingRollUp',
  );
  @override
  late final GeneratedColumn<String> standingRollUp = GeneratedColumn<String>(
    'standing_roll_up',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _standingHeadlineMeta = const VerificationMeta(
    'standingHeadline',
  );
  @override
  late final GeneratedColumn<String> standingHeadline = GeneratedColumn<String>(
    'standing_headline',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _standingReadyMeta = const VerificationMeta(
    'standingReady',
  );
  @override
  late final GeneratedColumn<int> standingReady = GeneratedColumn<int>(
    'standing_ready',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _standingTotalMeta = const VerificationMeta(
    'standingTotal',
  );
  @override
  late final GeneratedColumn<int> standingTotal = GeneratedColumn<int>(
    'standing_total',
    aliasedName,
    true,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _supervisorMeta = const VerificationMeta(
    'supervisor',
  );
  @override
  late final GeneratedColumn<bool> supervisor = GeneratedColumn<bool>(
    'supervisor',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("supervisor" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _teamCcIdMeta = const VerificationMeta(
    'teamCcId',
  );
  @override
  late final GeneratedColumn<String> teamCcId = GeneratedColumn<String>(
    'team_cc_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    cursor,
    referenceCursor,
    serverToday,
    lastSyncedAt,
    standingCcId,
    standingPartnership,
    standingFrom,
    standingTo,
    standingCurrent,
    standingRollUp,
    standingHeadline,
    standingReady,
    standingTotal,
    supervisor,
    teamCcId,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'sync_states';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalSyncState> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    }
    if (data.containsKey('cursor')) {
      context.handle(
        _cursorMeta,
        cursor.isAcceptableOrUnknown(data['cursor']!, _cursorMeta),
      );
    }
    if (data.containsKey('reference_cursor')) {
      context.handle(
        _referenceCursorMeta,
        referenceCursor.isAcceptableOrUnknown(
          data['reference_cursor']!,
          _referenceCursorMeta,
        ),
      );
    }
    if (data.containsKey('server_today')) {
      context.handle(
        _serverTodayMeta,
        serverToday.isAcceptableOrUnknown(
          data['server_today']!,
          _serverTodayMeta,
        ),
      );
    }
    if (data.containsKey('last_synced_at')) {
      context.handle(
        _lastSyncedAtMeta,
        lastSyncedAt.isAcceptableOrUnknown(
          data['last_synced_at']!,
          _lastSyncedAtMeta,
        ),
      );
    }
    if (data.containsKey('standing_cc_id')) {
      context.handle(
        _standingCcIdMeta,
        standingCcId.isAcceptableOrUnknown(
          data['standing_cc_id']!,
          _standingCcIdMeta,
        ),
      );
    }
    if (data.containsKey('standing_partnership')) {
      context.handle(
        _standingPartnershipMeta,
        standingPartnership.isAcceptableOrUnknown(
          data['standing_partnership']!,
          _standingPartnershipMeta,
        ),
      );
    }
    if (data.containsKey('standing_from')) {
      context.handle(
        _standingFromMeta,
        standingFrom.isAcceptableOrUnknown(
          data['standing_from']!,
          _standingFromMeta,
        ),
      );
    }
    if (data.containsKey('standing_to')) {
      context.handle(
        _standingToMeta,
        standingTo.isAcceptableOrUnknown(data['standing_to']!, _standingToMeta),
      );
    }
    if (data.containsKey('standing_current')) {
      context.handle(
        _standingCurrentMeta,
        standingCurrent.isAcceptableOrUnknown(
          data['standing_current']!,
          _standingCurrentMeta,
        ),
      );
    }
    if (data.containsKey('standing_roll_up')) {
      context.handle(
        _standingRollUpMeta,
        standingRollUp.isAcceptableOrUnknown(
          data['standing_roll_up']!,
          _standingRollUpMeta,
        ),
      );
    }
    if (data.containsKey('standing_headline')) {
      context.handle(
        _standingHeadlineMeta,
        standingHeadline.isAcceptableOrUnknown(
          data['standing_headline']!,
          _standingHeadlineMeta,
        ),
      );
    }
    if (data.containsKey('standing_ready')) {
      context.handle(
        _standingReadyMeta,
        standingReady.isAcceptableOrUnknown(
          data['standing_ready']!,
          _standingReadyMeta,
        ),
      );
    }
    if (data.containsKey('standing_total')) {
      context.handle(
        _standingTotalMeta,
        standingTotal.isAcceptableOrUnknown(
          data['standing_total']!,
          _standingTotalMeta,
        ),
      );
    }
    if (data.containsKey('supervisor')) {
      context.handle(
        _supervisorMeta,
        supervisor.isAcceptableOrUnknown(data['supervisor']!, _supervisorMeta),
      );
    }
    if (data.containsKey('team_cc_id')) {
      context.handle(
        _teamCcIdMeta,
        teamCcId.isAcceptableOrUnknown(data['team_cc_id']!, _teamCcIdMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalSyncState map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalSyncState(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}id'],
      )!,
      cursor: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}cursor'],
      )!,
      referenceCursor: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}reference_cursor'],
      )!,
      serverToday: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}server_today'],
      ),
      lastSyncedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}last_synced_at'],
      ),
      standingCcId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}standing_cc_id'],
      ),
      standingPartnership: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}standing_partnership'],
      ),
      standingFrom: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}standing_from'],
      ),
      standingTo: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}standing_to'],
      ),
      standingCurrent: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}standing_current'],
      ),
      standingRollUp: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}standing_roll_up'],
      ),
      standingHeadline: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}standing_headline'],
      ),
      standingReady: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}standing_ready'],
      ),
      standingTotal: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}standing_total'],
      ),
      supervisor: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}supervisor'],
      )!,
      teamCcId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}team_cc_id'],
      ),
    );
  }

  @override
  $SyncStatesTable createAlias(String alias) {
    return $SyncStatesTable(attachedDatabase, alias);
  }
}

class LocalSyncState extends DataClass implements Insertable<LocalSyncState> {
  final int id;
  final int cursor;
  final int referenceCursor;

  /// The server's business date as of the last sync — the app's only source of "today".
  final String? serverToday;
  final DateTime? lastSyncedAt;

  /// Denormalised from the standing payload so the certifications screen can name the swing.
  final String? standingCcId;
  final String? standingPartnership;
  final String? standingFrom;
  final String? standingTo;
  final bool? standingCurrent;
  final String? standingRollUp;

  /// MOB-0's one sentence, composed by the server (issue #12). Null until the first sync against
  /// a backend that sends it — the screen shows a neutral "sync to update" line, never a verdict.
  final String? standingHeadline;

  /// MOB-0's ring, counted by the engine — which states count as ready is its judgement.
  final int? standingReady;
  final int? standingTotal;

  /// Whether the **server** says this person supervises a watch (MOB-11).
  ///
  /// Persisted rather than held in memory so that the Team tab is there on a cold start in a dead
  /// spot — a supervisor who opens the app at sea should see their last watch, not lose the tab
  /// until the next successful sync.
  ///
  /// It replaces a `kDebugMode` `--dart-define`, which a release build could never reach. The
  /// server decides: `/me/team` answers 403 for anyone who does not hold the role, and that is a
  /// role check in one place rather than a compile-time guess in another.
  final bool supervisor;

  /// The swing the watch is over, or null when the supervisor is not rostered anywhere.
  ///
  /// Distinct from an empty [TeamMembers] on purpose: "you are not on a swing" and "everyone on
  /// your watch is fine" are opposite messages, and a screen that collapsed them would show a
  /// reassuring blank to a supervisor whose team simply is not loaded.
  final String? teamCcId;
  const LocalSyncState({
    required this.id,
    required this.cursor,
    required this.referenceCursor,
    this.serverToday,
    this.lastSyncedAt,
    this.standingCcId,
    this.standingPartnership,
    this.standingFrom,
    this.standingTo,
    this.standingCurrent,
    this.standingRollUp,
    this.standingHeadline,
    this.standingReady,
    this.standingTotal,
    required this.supervisor,
    this.teamCcId,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<int>(id);
    map['cursor'] = Variable<int>(cursor);
    map['reference_cursor'] = Variable<int>(referenceCursor);
    if (!nullToAbsent || serverToday != null) {
      map['server_today'] = Variable<String>(serverToday);
    }
    if (!nullToAbsent || lastSyncedAt != null) {
      map['last_synced_at'] = Variable<DateTime>(lastSyncedAt);
    }
    if (!nullToAbsent || standingCcId != null) {
      map['standing_cc_id'] = Variable<String>(standingCcId);
    }
    if (!nullToAbsent || standingPartnership != null) {
      map['standing_partnership'] = Variable<String>(standingPartnership);
    }
    if (!nullToAbsent || standingFrom != null) {
      map['standing_from'] = Variable<String>(standingFrom);
    }
    if (!nullToAbsent || standingTo != null) {
      map['standing_to'] = Variable<String>(standingTo);
    }
    if (!nullToAbsent || standingCurrent != null) {
      map['standing_current'] = Variable<bool>(standingCurrent);
    }
    if (!nullToAbsent || standingRollUp != null) {
      map['standing_roll_up'] = Variable<String>(standingRollUp);
    }
    if (!nullToAbsent || standingHeadline != null) {
      map['standing_headline'] = Variable<String>(standingHeadline);
    }
    if (!nullToAbsent || standingReady != null) {
      map['standing_ready'] = Variable<int>(standingReady);
    }
    if (!nullToAbsent || standingTotal != null) {
      map['standing_total'] = Variable<int>(standingTotal);
    }
    map['supervisor'] = Variable<bool>(supervisor);
    if (!nullToAbsent || teamCcId != null) {
      map['team_cc_id'] = Variable<String>(teamCcId);
    }
    return map;
  }

  SyncStatesCompanion toCompanion(bool nullToAbsent) {
    return SyncStatesCompanion(
      id: Value(id),
      cursor: Value(cursor),
      referenceCursor: Value(referenceCursor),
      serverToday: serverToday == null && nullToAbsent
          ? const Value.absent()
          : Value(serverToday),
      lastSyncedAt: lastSyncedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(lastSyncedAt),
      standingCcId: standingCcId == null && nullToAbsent
          ? const Value.absent()
          : Value(standingCcId),
      standingPartnership: standingPartnership == null && nullToAbsent
          ? const Value.absent()
          : Value(standingPartnership),
      standingFrom: standingFrom == null && nullToAbsent
          ? const Value.absent()
          : Value(standingFrom),
      standingTo: standingTo == null && nullToAbsent
          ? const Value.absent()
          : Value(standingTo),
      standingCurrent: standingCurrent == null && nullToAbsent
          ? const Value.absent()
          : Value(standingCurrent),
      standingRollUp: standingRollUp == null && nullToAbsent
          ? const Value.absent()
          : Value(standingRollUp),
      standingHeadline: standingHeadline == null && nullToAbsent
          ? const Value.absent()
          : Value(standingHeadline),
      standingReady: standingReady == null && nullToAbsent
          ? const Value.absent()
          : Value(standingReady),
      standingTotal: standingTotal == null && nullToAbsent
          ? const Value.absent()
          : Value(standingTotal),
      supervisor: Value(supervisor),
      teamCcId: teamCcId == null && nullToAbsent
          ? const Value.absent()
          : Value(teamCcId),
    );
  }

  factory LocalSyncState.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalSyncState(
      id: serializer.fromJson<int>(json['id']),
      cursor: serializer.fromJson<int>(json['cursor']),
      referenceCursor: serializer.fromJson<int>(json['referenceCursor']),
      serverToday: serializer.fromJson<String?>(json['serverToday']),
      lastSyncedAt: serializer.fromJson<DateTime?>(json['lastSyncedAt']),
      standingCcId: serializer.fromJson<String?>(json['standingCcId']),
      standingPartnership: serializer.fromJson<String?>(
        json['standingPartnership'],
      ),
      standingFrom: serializer.fromJson<String?>(json['standingFrom']),
      standingTo: serializer.fromJson<String?>(json['standingTo']),
      standingCurrent: serializer.fromJson<bool?>(json['standingCurrent']),
      standingRollUp: serializer.fromJson<String?>(json['standingRollUp']),
      standingHeadline: serializer.fromJson<String?>(json['standingHeadline']),
      standingReady: serializer.fromJson<int?>(json['standingReady']),
      standingTotal: serializer.fromJson<int?>(json['standingTotal']),
      supervisor: serializer.fromJson<bool>(json['supervisor']),
      teamCcId: serializer.fromJson<String?>(json['teamCcId']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<int>(id),
      'cursor': serializer.toJson<int>(cursor),
      'referenceCursor': serializer.toJson<int>(referenceCursor),
      'serverToday': serializer.toJson<String?>(serverToday),
      'lastSyncedAt': serializer.toJson<DateTime?>(lastSyncedAt),
      'standingCcId': serializer.toJson<String?>(standingCcId),
      'standingPartnership': serializer.toJson<String?>(standingPartnership),
      'standingFrom': serializer.toJson<String?>(standingFrom),
      'standingTo': serializer.toJson<String?>(standingTo),
      'standingCurrent': serializer.toJson<bool?>(standingCurrent),
      'standingRollUp': serializer.toJson<String?>(standingRollUp),
      'standingHeadline': serializer.toJson<String?>(standingHeadline),
      'standingReady': serializer.toJson<int?>(standingReady),
      'standingTotal': serializer.toJson<int?>(standingTotal),
      'supervisor': serializer.toJson<bool>(supervisor),
      'teamCcId': serializer.toJson<String?>(teamCcId),
    };
  }

  LocalSyncState copyWith({
    int? id,
    int? cursor,
    int? referenceCursor,
    Value<String?> serverToday = const Value.absent(),
    Value<DateTime?> lastSyncedAt = const Value.absent(),
    Value<String?> standingCcId = const Value.absent(),
    Value<String?> standingPartnership = const Value.absent(),
    Value<String?> standingFrom = const Value.absent(),
    Value<String?> standingTo = const Value.absent(),
    Value<bool?> standingCurrent = const Value.absent(),
    Value<String?> standingRollUp = const Value.absent(),
    Value<String?> standingHeadline = const Value.absent(),
    Value<int?> standingReady = const Value.absent(),
    Value<int?> standingTotal = const Value.absent(),
    bool? supervisor,
    Value<String?> teamCcId = const Value.absent(),
  }) => LocalSyncState(
    id: id ?? this.id,
    cursor: cursor ?? this.cursor,
    referenceCursor: referenceCursor ?? this.referenceCursor,
    serverToday: serverToday.present ? serverToday.value : this.serverToday,
    lastSyncedAt: lastSyncedAt.present ? lastSyncedAt.value : this.lastSyncedAt,
    standingCcId: standingCcId.present ? standingCcId.value : this.standingCcId,
    standingPartnership: standingPartnership.present
        ? standingPartnership.value
        : this.standingPartnership,
    standingFrom: standingFrom.present ? standingFrom.value : this.standingFrom,
    standingTo: standingTo.present ? standingTo.value : this.standingTo,
    standingCurrent: standingCurrent.present
        ? standingCurrent.value
        : this.standingCurrent,
    standingRollUp: standingRollUp.present
        ? standingRollUp.value
        : this.standingRollUp,
    standingHeadline: standingHeadline.present
        ? standingHeadline.value
        : this.standingHeadline,
    standingReady: standingReady.present
        ? standingReady.value
        : this.standingReady,
    standingTotal: standingTotal.present
        ? standingTotal.value
        : this.standingTotal,
    supervisor: supervisor ?? this.supervisor,
    teamCcId: teamCcId.present ? teamCcId.value : this.teamCcId,
  );
  LocalSyncState copyWithCompanion(SyncStatesCompanion data) {
    return LocalSyncState(
      id: data.id.present ? data.id.value : this.id,
      cursor: data.cursor.present ? data.cursor.value : this.cursor,
      referenceCursor: data.referenceCursor.present
          ? data.referenceCursor.value
          : this.referenceCursor,
      serverToday: data.serverToday.present
          ? data.serverToday.value
          : this.serverToday,
      lastSyncedAt: data.lastSyncedAt.present
          ? data.lastSyncedAt.value
          : this.lastSyncedAt,
      standingCcId: data.standingCcId.present
          ? data.standingCcId.value
          : this.standingCcId,
      standingPartnership: data.standingPartnership.present
          ? data.standingPartnership.value
          : this.standingPartnership,
      standingFrom: data.standingFrom.present
          ? data.standingFrom.value
          : this.standingFrom,
      standingTo: data.standingTo.present
          ? data.standingTo.value
          : this.standingTo,
      standingCurrent: data.standingCurrent.present
          ? data.standingCurrent.value
          : this.standingCurrent,
      standingRollUp: data.standingRollUp.present
          ? data.standingRollUp.value
          : this.standingRollUp,
      standingHeadline: data.standingHeadline.present
          ? data.standingHeadline.value
          : this.standingHeadline,
      standingReady: data.standingReady.present
          ? data.standingReady.value
          : this.standingReady,
      standingTotal: data.standingTotal.present
          ? data.standingTotal.value
          : this.standingTotal,
      supervisor: data.supervisor.present
          ? data.supervisor.value
          : this.supervisor,
      teamCcId: data.teamCcId.present ? data.teamCcId.value : this.teamCcId,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalSyncState(')
          ..write('id: $id, ')
          ..write('cursor: $cursor, ')
          ..write('referenceCursor: $referenceCursor, ')
          ..write('serverToday: $serverToday, ')
          ..write('lastSyncedAt: $lastSyncedAt, ')
          ..write('standingCcId: $standingCcId, ')
          ..write('standingPartnership: $standingPartnership, ')
          ..write('standingFrom: $standingFrom, ')
          ..write('standingTo: $standingTo, ')
          ..write('standingCurrent: $standingCurrent, ')
          ..write('standingRollUp: $standingRollUp, ')
          ..write('standingHeadline: $standingHeadline, ')
          ..write('standingReady: $standingReady, ')
          ..write('standingTotal: $standingTotal, ')
          ..write('supervisor: $supervisor, ')
          ..write('teamCcId: $teamCcId')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    cursor,
    referenceCursor,
    serverToday,
    lastSyncedAt,
    standingCcId,
    standingPartnership,
    standingFrom,
    standingTo,
    standingCurrent,
    standingRollUp,
    standingHeadline,
    standingReady,
    standingTotal,
    supervisor,
    teamCcId,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalSyncState &&
          other.id == this.id &&
          other.cursor == this.cursor &&
          other.referenceCursor == this.referenceCursor &&
          other.serverToday == this.serverToday &&
          other.lastSyncedAt == this.lastSyncedAt &&
          other.standingCcId == this.standingCcId &&
          other.standingPartnership == this.standingPartnership &&
          other.standingFrom == this.standingFrom &&
          other.standingTo == this.standingTo &&
          other.standingCurrent == this.standingCurrent &&
          other.standingRollUp == this.standingRollUp &&
          other.standingHeadline == this.standingHeadline &&
          other.standingReady == this.standingReady &&
          other.standingTotal == this.standingTotal &&
          other.supervisor == this.supervisor &&
          other.teamCcId == this.teamCcId);
}

class SyncStatesCompanion extends UpdateCompanion<LocalSyncState> {
  final Value<int> id;
  final Value<int> cursor;
  final Value<int> referenceCursor;
  final Value<String?> serverToday;
  final Value<DateTime?> lastSyncedAt;
  final Value<String?> standingCcId;
  final Value<String?> standingPartnership;
  final Value<String?> standingFrom;
  final Value<String?> standingTo;
  final Value<bool?> standingCurrent;
  final Value<String?> standingRollUp;
  final Value<String?> standingHeadline;
  final Value<int?> standingReady;
  final Value<int?> standingTotal;
  final Value<bool> supervisor;
  final Value<String?> teamCcId;
  const SyncStatesCompanion({
    this.id = const Value.absent(),
    this.cursor = const Value.absent(),
    this.referenceCursor = const Value.absent(),
    this.serverToday = const Value.absent(),
    this.lastSyncedAt = const Value.absent(),
    this.standingCcId = const Value.absent(),
    this.standingPartnership = const Value.absent(),
    this.standingFrom = const Value.absent(),
    this.standingTo = const Value.absent(),
    this.standingCurrent = const Value.absent(),
    this.standingRollUp = const Value.absent(),
    this.standingHeadline = const Value.absent(),
    this.standingReady = const Value.absent(),
    this.standingTotal = const Value.absent(),
    this.supervisor = const Value.absent(),
    this.teamCcId = const Value.absent(),
  });
  SyncStatesCompanion.insert({
    this.id = const Value.absent(),
    this.cursor = const Value.absent(),
    this.referenceCursor = const Value.absent(),
    this.serverToday = const Value.absent(),
    this.lastSyncedAt = const Value.absent(),
    this.standingCcId = const Value.absent(),
    this.standingPartnership = const Value.absent(),
    this.standingFrom = const Value.absent(),
    this.standingTo = const Value.absent(),
    this.standingCurrent = const Value.absent(),
    this.standingRollUp = const Value.absent(),
    this.standingHeadline = const Value.absent(),
    this.standingReady = const Value.absent(),
    this.standingTotal = const Value.absent(),
    this.supervisor = const Value.absent(),
    this.teamCcId = const Value.absent(),
  });
  static Insertable<LocalSyncState> custom({
    Expression<int>? id,
    Expression<int>? cursor,
    Expression<int>? referenceCursor,
    Expression<String>? serverToday,
    Expression<DateTime>? lastSyncedAt,
    Expression<String>? standingCcId,
    Expression<String>? standingPartnership,
    Expression<String>? standingFrom,
    Expression<String>? standingTo,
    Expression<bool>? standingCurrent,
    Expression<String>? standingRollUp,
    Expression<String>? standingHeadline,
    Expression<int>? standingReady,
    Expression<int>? standingTotal,
    Expression<bool>? supervisor,
    Expression<String>? teamCcId,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (cursor != null) 'cursor': cursor,
      if (referenceCursor != null) 'reference_cursor': referenceCursor,
      if (serverToday != null) 'server_today': serverToday,
      if (lastSyncedAt != null) 'last_synced_at': lastSyncedAt,
      if (standingCcId != null) 'standing_cc_id': standingCcId,
      if (standingPartnership != null)
        'standing_partnership': standingPartnership,
      if (standingFrom != null) 'standing_from': standingFrom,
      if (standingTo != null) 'standing_to': standingTo,
      if (standingCurrent != null) 'standing_current': standingCurrent,
      if (standingRollUp != null) 'standing_roll_up': standingRollUp,
      if (standingHeadline != null) 'standing_headline': standingHeadline,
      if (standingReady != null) 'standing_ready': standingReady,
      if (standingTotal != null) 'standing_total': standingTotal,
      if (supervisor != null) 'supervisor': supervisor,
      if (teamCcId != null) 'team_cc_id': teamCcId,
    });
  }

  SyncStatesCompanion copyWith({
    Value<int>? id,
    Value<int>? cursor,
    Value<int>? referenceCursor,
    Value<String?>? serverToday,
    Value<DateTime?>? lastSyncedAt,
    Value<String?>? standingCcId,
    Value<String?>? standingPartnership,
    Value<String?>? standingFrom,
    Value<String?>? standingTo,
    Value<bool?>? standingCurrent,
    Value<String?>? standingRollUp,
    Value<String?>? standingHeadline,
    Value<int?>? standingReady,
    Value<int?>? standingTotal,
    Value<bool>? supervisor,
    Value<String?>? teamCcId,
  }) {
    return SyncStatesCompanion(
      id: id ?? this.id,
      cursor: cursor ?? this.cursor,
      referenceCursor: referenceCursor ?? this.referenceCursor,
      serverToday: serverToday ?? this.serverToday,
      lastSyncedAt: lastSyncedAt ?? this.lastSyncedAt,
      standingCcId: standingCcId ?? this.standingCcId,
      standingPartnership: standingPartnership ?? this.standingPartnership,
      standingFrom: standingFrom ?? this.standingFrom,
      standingTo: standingTo ?? this.standingTo,
      standingCurrent: standingCurrent ?? this.standingCurrent,
      standingRollUp: standingRollUp ?? this.standingRollUp,
      standingHeadline: standingHeadline ?? this.standingHeadline,
      standingReady: standingReady ?? this.standingReady,
      standingTotal: standingTotal ?? this.standingTotal,
      supervisor: supervisor ?? this.supervisor,
      teamCcId: teamCcId ?? this.teamCcId,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<int>(id.value);
    }
    if (cursor.present) {
      map['cursor'] = Variable<int>(cursor.value);
    }
    if (referenceCursor.present) {
      map['reference_cursor'] = Variable<int>(referenceCursor.value);
    }
    if (serverToday.present) {
      map['server_today'] = Variable<String>(serverToday.value);
    }
    if (lastSyncedAt.present) {
      map['last_synced_at'] = Variable<DateTime>(lastSyncedAt.value);
    }
    if (standingCcId.present) {
      map['standing_cc_id'] = Variable<String>(standingCcId.value);
    }
    if (standingPartnership.present) {
      map['standing_partnership'] = Variable<String>(standingPartnership.value);
    }
    if (standingFrom.present) {
      map['standing_from'] = Variable<String>(standingFrom.value);
    }
    if (standingTo.present) {
      map['standing_to'] = Variable<String>(standingTo.value);
    }
    if (standingCurrent.present) {
      map['standing_current'] = Variable<bool>(standingCurrent.value);
    }
    if (standingRollUp.present) {
      map['standing_roll_up'] = Variable<String>(standingRollUp.value);
    }
    if (standingHeadline.present) {
      map['standing_headline'] = Variable<String>(standingHeadline.value);
    }
    if (standingReady.present) {
      map['standing_ready'] = Variable<int>(standingReady.value);
    }
    if (standingTotal.present) {
      map['standing_total'] = Variable<int>(standingTotal.value);
    }
    if (supervisor.present) {
      map['supervisor'] = Variable<bool>(supervisor.value);
    }
    if (teamCcId.present) {
      map['team_cc_id'] = Variable<String>(teamCcId.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('SyncStatesCompanion(')
          ..write('id: $id, ')
          ..write('cursor: $cursor, ')
          ..write('referenceCursor: $referenceCursor, ')
          ..write('serverToday: $serverToday, ')
          ..write('lastSyncedAt: $lastSyncedAt, ')
          ..write('standingCcId: $standingCcId, ')
          ..write('standingPartnership: $standingPartnership, ')
          ..write('standingFrom: $standingFrom, ')
          ..write('standingTo: $standingTo, ')
          ..write('standingCurrent: $standingCurrent, ')
          ..write('standingRollUp: $standingRollUp, ')
          ..write('standingHeadline: $standingHeadline, ')
          ..write('standingReady: $standingReady, ')
          ..write('standingTotal: $standingTotal, ')
          ..write('supervisor: $supervisor, ')
          ..write('teamCcId: $teamCcId')
          ..write(')'))
        .toString();
  }
}

abstract class _$LocalStore extends GeneratedDatabase {
  _$LocalStore(QueryExecutor e) : super(e);
  $LocalStoreManager get managers => $LocalStoreManager(this);
  late final $PeopleTable people = $PeopleTable(this);
  late final $HoldingsTable holdings = $HoldingsTable(this);
  late final $AssignmentsTable assignments = $AssignmentsTable(this);
  late final $LeaveRecordsTable leaveRecords = $LeaveRecordsTable(this);
  late final $NotificationsTable notifications = $NotificationsTable(this);
  late final $SubmissionsTable submissions = $SubmissionsTable(this);
  late final $RequirementsTable requirements = $RequirementsTable(this);
  late final $CrewChangesTable crewChanges = $CrewChangesTable(this);
  late final $StandingCellsTable standingCells = $StandingCellsTable(this);
  late final $OutboxTable outbox = $OutboxTable(this);
  late final $CrewIntentsTable crewIntents = $CrewIntentsTable(this);
  late final $CrewStatementsTable crewStatements = $CrewStatementsTable(this);
  late final $AttestationsTable attestations = $AttestationsTable(this);
  late final $CourseOptionsTable courseOptions = $CourseOptionsTable(this);
  late final $TeamMembersTable teamMembers = $TeamMembersTable(this);
  late final $SyncStatesTable syncStates = $SyncStatesTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    people,
    holdings,
    assignments,
    leaveRecords,
    notifications,
    submissions,
    requirements,
    crewChanges,
    standingCells,
    outbox,
    crewIntents,
    crewStatements,
    attestations,
    courseOptions,
    teamMembers,
    syncStates,
  ];
}

typedef $$PeopleTableCreateCompanionBuilder =
    PeopleCompanion Function({
      Value<int> id,
      required String sam,
      required String name,
      required int positionId,
      required String positionName,
      Value<String?> tier,
      required String partnershipAbbrev,
      required String status,
      Value<String?> email,
    });
typedef $$PeopleTableUpdateCompanionBuilder =
    PeopleCompanion Function({
      Value<int> id,
      Value<String> sam,
      Value<String> name,
      Value<int> positionId,
      Value<String> positionName,
      Value<String?> tier,
      Value<String> partnershipAbbrev,
      Value<String> status,
      Value<String?> email,
    });

class $$PeopleTableFilterComposer extends Composer<_$LocalStore, $PeopleTable> {
  $$PeopleTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get sam => $composableBuilder(
    column: $table.sam,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get positionId => $composableBuilder(
    column: $table.positionId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get positionName => $composableBuilder(
    column: $table.positionName,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tier => $composableBuilder(
    column: $table.tier,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get partnershipAbbrev => $composableBuilder(
    column: $table.partnershipAbbrev,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get email => $composableBuilder(
    column: $table.email,
    builder: (column) => ColumnFilters(column),
  );
}

class $$PeopleTableOrderingComposer
    extends Composer<_$LocalStore, $PeopleTable> {
  $$PeopleTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get sam => $composableBuilder(
    column: $table.sam,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get positionId => $composableBuilder(
    column: $table.positionId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get positionName => $composableBuilder(
    column: $table.positionName,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tier => $composableBuilder(
    column: $table.tier,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get partnershipAbbrev => $composableBuilder(
    column: $table.partnershipAbbrev,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get email => $composableBuilder(
    column: $table.email,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$PeopleTableAnnotationComposer
    extends Composer<_$LocalStore, $PeopleTable> {
  $$PeopleTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get sam =>
      $composableBuilder(column: $table.sam, builder: (column) => column);

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<int> get positionId => $composableBuilder(
    column: $table.positionId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get positionName => $composableBuilder(
    column: $table.positionName,
    builder: (column) => column,
  );

  GeneratedColumn<String> get tier =>
      $composableBuilder(column: $table.tier, builder: (column) => column);

  GeneratedColumn<String> get partnershipAbbrev => $composableBuilder(
    column: $table.partnershipAbbrev,
    builder: (column) => column,
  );

  GeneratedColumn<String> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<String> get email =>
      $composableBuilder(column: $table.email, builder: (column) => column);
}

class $$PeopleTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $PeopleTable,
          LocalPerson,
          $$PeopleTableFilterComposer,
          $$PeopleTableOrderingComposer,
          $$PeopleTableAnnotationComposer,
          $$PeopleTableCreateCompanionBuilder,
          $$PeopleTableUpdateCompanionBuilder,
          (
            LocalPerson,
            BaseReferences<_$LocalStore, $PeopleTable, LocalPerson>,
          ),
          LocalPerson,
          PrefetchHooks Function()
        > {
  $$PeopleTableTableManager(_$LocalStore db, $PeopleTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$PeopleTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$PeopleTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$PeopleTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> sam = const Value.absent(),
                Value<String> name = const Value.absent(),
                Value<int> positionId = const Value.absent(),
                Value<String> positionName = const Value.absent(),
                Value<String?> tier = const Value.absent(),
                Value<String> partnershipAbbrev = const Value.absent(),
                Value<String> status = const Value.absent(),
                Value<String?> email = const Value.absent(),
              }) => PeopleCompanion(
                id: id,
                sam: sam,
                name: name,
                positionId: positionId,
                positionName: positionName,
                tier: tier,
                partnershipAbbrev: partnershipAbbrev,
                status: status,
                email: email,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String sam,
                required String name,
                required int positionId,
                required String positionName,
                Value<String?> tier = const Value.absent(),
                required String partnershipAbbrev,
                required String status,
                Value<String?> email = const Value.absent(),
              }) => PeopleCompanion.insert(
                id: id,
                sam: sam,
                name: name,
                positionId: positionId,
                positionName: positionName,
                tier: tier,
                partnershipAbbrev: partnershipAbbrev,
                status: status,
                email: email,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$PeopleTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $PeopleTable,
      LocalPerson,
      $$PeopleTableFilterComposer,
      $$PeopleTableOrderingComposer,
      $$PeopleTableAnnotationComposer,
      $$PeopleTableCreateCompanionBuilder,
      $$PeopleTableUpdateCompanionBuilder,
      (LocalPerson, BaseReferences<_$LocalStore, $PeopleTable, LocalPerson>),
      LocalPerson,
      PrefetchHooks Function()
    >;
typedef $$HoldingsTableCreateCompanionBuilder =
    HoldingsCompanion Function({
      Value<int> id,
      required int requirementId,
      required String status,
      Value<String?> expiry,
      Value<String?> issueDate,
      Value<String?> note,
    });
typedef $$HoldingsTableUpdateCompanionBuilder =
    HoldingsCompanion Function({
      Value<int> id,
      Value<int> requirementId,
      Value<String> status,
      Value<String?> expiry,
      Value<String?> issueDate,
      Value<String?> note,
    });

class $$HoldingsTableFilterComposer
    extends Composer<_$LocalStore, $HoldingsTable> {
  $$HoldingsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get expiry => $composableBuilder(
    column: $table.expiry,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get issueDate => $composableBuilder(
    column: $table.issueDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnFilters(column),
  );
}

class $$HoldingsTableOrderingComposer
    extends Composer<_$LocalStore, $HoldingsTable> {
  $$HoldingsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get expiry => $composableBuilder(
    column: $table.expiry,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get issueDate => $composableBuilder(
    column: $table.issueDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$HoldingsTableAnnotationComposer
    extends Composer<_$LocalStore, $HoldingsTable> {
  $$HoldingsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<String> get expiry =>
      $composableBuilder(column: $table.expiry, builder: (column) => column);

  GeneratedColumn<String> get issueDate =>
      $composableBuilder(column: $table.issueDate, builder: (column) => column);

  GeneratedColumn<String> get note =>
      $composableBuilder(column: $table.note, builder: (column) => column);
}

class $$HoldingsTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $HoldingsTable,
          LocalHolding,
          $$HoldingsTableFilterComposer,
          $$HoldingsTableOrderingComposer,
          $$HoldingsTableAnnotationComposer,
          $$HoldingsTableCreateCompanionBuilder,
          $$HoldingsTableUpdateCompanionBuilder,
          (
            LocalHolding,
            BaseReferences<_$LocalStore, $HoldingsTable, LocalHolding>,
          ),
          LocalHolding,
          PrefetchHooks Function()
        > {
  $$HoldingsTableTableManager(_$LocalStore db, $HoldingsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$HoldingsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$HoldingsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$HoldingsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int> requirementId = const Value.absent(),
                Value<String> status = const Value.absent(),
                Value<String?> expiry = const Value.absent(),
                Value<String?> issueDate = const Value.absent(),
                Value<String?> note = const Value.absent(),
              }) => HoldingsCompanion(
                id: id,
                requirementId: requirementId,
                status: status,
                expiry: expiry,
                issueDate: issueDate,
                note: note,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required int requirementId,
                required String status,
                Value<String?> expiry = const Value.absent(),
                Value<String?> issueDate = const Value.absent(),
                Value<String?> note = const Value.absent(),
              }) => HoldingsCompanion.insert(
                id: id,
                requirementId: requirementId,
                status: status,
                expiry: expiry,
                issueDate: issueDate,
                note: note,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$HoldingsTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $HoldingsTable,
      LocalHolding,
      $$HoldingsTableFilterComposer,
      $$HoldingsTableOrderingComposer,
      $$HoldingsTableAnnotationComposer,
      $$HoldingsTableCreateCompanionBuilder,
      $$HoldingsTableUpdateCompanionBuilder,
      (
        LocalHolding,
        BaseReferences<_$LocalStore, $HoldingsTable, LocalHolding>,
      ),
      LocalHolding,
      PrefetchHooks Function()
    >;
typedef $$AssignmentsTableCreateCompanionBuilder =
    AssignmentsCompanion Function({
      Value<int> id,
      required int crewChangeId,
      required String ccId,
      required String partnershipAbbrev,
      required int slotRef,
      required String fromDate,
      required String toDate,
    });
typedef $$AssignmentsTableUpdateCompanionBuilder =
    AssignmentsCompanion Function({
      Value<int> id,
      Value<int> crewChangeId,
      Value<String> ccId,
      Value<String> partnershipAbbrev,
      Value<int> slotRef,
      Value<String> fromDate,
      Value<String> toDate,
    });

class $$AssignmentsTableFilterComposer
    extends Composer<_$LocalStore, $AssignmentsTable> {
  $$AssignmentsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get crewChangeId => $composableBuilder(
    column: $table.crewChangeId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get ccId => $composableBuilder(
    column: $table.ccId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get partnershipAbbrev => $composableBuilder(
    column: $table.partnershipAbbrev,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get slotRef => $composableBuilder(
    column: $table.slotRef,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get fromDate => $composableBuilder(
    column: $table.fromDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get toDate => $composableBuilder(
    column: $table.toDate,
    builder: (column) => ColumnFilters(column),
  );
}

class $$AssignmentsTableOrderingComposer
    extends Composer<_$LocalStore, $AssignmentsTable> {
  $$AssignmentsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get crewChangeId => $composableBuilder(
    column: $table.crewChangeId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get ccId => $composableBuilder(
    column: $table.ccId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get partnershipAbbrev => $composableBuilder(
    column: $table.partnershipAbbrev,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get slotRef => $composableBuilder(
    column: $table.slotRef,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get fromDate => $composableBuilder(
    column: $table.fromDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get toDate => $composableBuilder(
    column: $table.toDate,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$AssignmentsTableAnnotationComposer
    extends Composer<_$LocalStore, $AssignmentsTable> {
  $$AssignmentsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<int> get crewChangeId => $composableBuilder(
    column: $table.crewChangeId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get ccId =>
      $composableBuilder(column: $table.ccId, builder: (column) => column);

  GeneratedColumn<String> get partnershipAbbrev => $composableBuilder(
    column: $table.partnershipAbbrev,
    builder: (column) => column,
  );

  GeneratedColumn<int> get slotRef =>
      $composableBuilder(column: $table.slotRef, builder: (column) => column);

  GeneratedColumn<String> get fromDate =>
      $composableBuilder(column: $table.fromDate, builder: (column) => column);

  GeneratedColumn<String> get toDate =>
      $composableBuilder(column: $table.toDate, builder: (column) => column);
}

class $$AssignmentsTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $AssignmentsTable,
          LocalAssignment,
          $$AssignmentsTableFilterComposer,
          $$AssignmentsTableOrderingComposer,
          $$AssignmentsTableAnnotationComposer,
          $$AssignmentsTableCreateCompanionBuilder,
          $$AssignmentsTableUpdateCompanionBuilder,
          (
            LocalAssignment,
            BaseReferences<_$LocalStore, $AssignmentsTable, LocalAssignment>,
          ),
          LocalAssignment,
          PrefetchHooks Function()
        > {
  $$AssignmentsTableTableManager(_$LocalStore db, $AssignmentsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$AssignmentsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$AssignmentsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$AssignmentsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int> crewChangeId = const Value.absent(),
                Value<String> ccId = const Value.absent(),
                Value<String> partnershipAbbrev = const Value.absent(),
                Value<int> slotRef = const Value.absent(),
                Value<String> fromDate = const Value.absent(),
                Value<String> toDate = const Value.absent(),
              }) => AssignmentsCompanion(
                id: id,
                crewChangeId: crewChangeId,
                ccId: ccId,
                partnershipAbbrev: partnershipAbbrev,
                slotRef: slotRef,
                fromDate: fromDate,
                toDate: toDate,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required int crewChangeId,
                required String ccId,
                required String partnershipAbbrev,
                required int slotRef,
                required String fromDate,
                required String toDate,
              }) => AssignmentsCompanion.insert(
                id: id,
                crewChangeId: crewChangeId,
                ccId: ccId,
                partnershipAbbrev: partnershipAbbrev,
                slotRef: slotRef,
                fromDate: fromDate,
                toDate: toDate,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$AssignmentsTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $AssignmentsTable,
      LocalAssignment,
      $$AssignmentsTableFilterComposer,
      $$AssignmentsTableOrderingComposer,
      $$AssignmentsTableAnnotationComposer,
      $$AssignmentsTableCreateCompanionBuilder,
      $$AssignmentsTableUpdateCompanionBuilder,
      (
        LocalAssignment,
        BaseReferences<_$LocalStore, $AssignmentsTable, LocalAssignment>,
      ),
      LocalAssignment,
      PrefetchHooks Function()
    >;
typedef $$LeaveRecordsTableCreateCompanionBuilder =
    LeaveRecordsCompanion Function({
      Value<int> id,
      required String kind,
      required String fromDate,
      required String toDate,
      required String status,
    });
typedef $$LeaveRecordsTableUpdateCompanionBuilder =
    LeaveRecordsCompanion Function({
      Value<int> id,
      Value<String> kind,
      Value<String> fromDate,
      Value<String> toDate,
      Value<String> status,
    });

class $$LeaveRecordsTableFilterComposer
    extends Composer<_$LocalStore, $LeaveRecordsTable> {
  $$LeaveRecordsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get fromDate => $composableBuilder(
    column: $table.fromDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get toDate => $composableBuilder(
    column: $table.toDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnFilters(column),
  );
}

class $$LeaveRecordsTableOrderingComposer
    extends Composer<_$LocalStore, $LeaveRecordsTable> {
  $$LeaveRecordsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get fromDate => $composableBuilder(
    column: $table.fromDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get toDate => $composableBuilder(
    column: $table.toDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$LeaveRecordsTableAnnotationComposer
    extends Composer<_$LocalStore, $LeaveRecordsTable> {
  $$LeaveRecordsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<String> get fromDate =>
      $composableBuilder(column: $table.fromDate, builder: (column) => column);

  GeneratedColumn<String> get toDate =>
      $composableBuilder(column: $table.toDate, builder: (column) => column);

  GeneratedColumn<String> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);
}

class $$LeaveRecordsTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $LeaveRecordsTable,
          LocalLeave,
          $$LeaveRecordsTableFilterComposer,
          $$LeaveRecordsTableOrderingComposer,
          $$LeaveRecordsTableAnnotationComposer,
          $$LeaveRecordsTableCreateCompanionBuilder,
          $$LeaveRecordsTableUpdateCompanionBuilder,
          (
            LocalLeave,
            BaseReferences<_$LocalStore, $LeaveRecordsTable, LocalLeave>,
          ),
          LocalLeave,
          PrefetchHooks Function()
        > {
  $$LeaveRecordsTableTableManager(_$LocalStore db, $LeaveRecordsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$LeaveRecordsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$LeaveRecordsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$LeaveRecordsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<String> fromDate = const Value.absent(),
                Value<String> toDate = const Value.absent(),
                Value<String> status = const Value.absent(),
              }) => LeaveRecordsCompanion(
                id: id,
                kind: kind,
                fromDate: fromDate,
                toDate: toDate,
                status: status,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String kind,
                required String fromDate,
                required String toDate,
                required String status,
              }) => LeaveRecordsCompanion.insert(
                id: id,
                kind: kind,
                fromDate: fromDate,
                toDate: toDate,
                status: status,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$LeaveRecordsTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $LeaveRecordsTable,
      LocalLeave,
      $$LeaveRecordsTableFilterComposer,
      $$LeaveRecordsTableOrderingComposer,
      $$LeaveRecordsTableAnnotationComposer,
      $$LeaveRecordsTableCreateCompanionBuilder,
      $$LeaveRecordsTableUpdateCompanionBuilder,
      (
        LocalLeave,
        BaseReferences<_$LocalStore, $LeaveRecordsTable, LocalLeave>,
      ),
      LocalLeave,
      PrefetchHooks Function()
    >;
typedef $$NotificationsTableCreateCompanionBuilder =
    NotificationsCompanion Function({
      Value<int> id,
      required String kind,
      required String title,
      Value<String?> body,
      Value<String?> deepLink,
      required DateTime createdAt,
      Value<DateTime?> readAt,
    });
typedef $$NotificationsTableUpdateCompanionBuilder =
    NotificationsCompanion Function({
      Value<int> id,
      Value<String> kind,
      Value<String> title,
      Value<String?> body,
      Value<String?> deepLink,
      Value<DateTime> createdAt,
      Value<DateTime?> readAt,
    });

class $$NotificationsTableFilterComposer
    extends Composer<_$LocalStore, $NotificationsTable> {
  $$NotificationsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get body => $composableBuilder(
    column: $table.body,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get deepLink => $composableBuilder(
    column: $table.deepLink,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get readAt => $composableBuilder(
    column: $table.readAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$NotificationsTableOrderingComposer
    extends Composer<_$LocalStore, $NotificationsTable> {
  $$NotificationsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get body => $composableBuilder(
    column: $table.body,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get deepLink => $composableBuilder(
    column: $table.deepLink,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get readAt => $composableBuilder(
    column: $table.readAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$NotificationsTableAnnotationComposer
    extends Composer<_$LocalStore, $NotificationsTable> {
  $$NotificationsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get body =>
      $composableBuilder(column: $table.body, builder: (column) => column);

  GeneratedColumn<String> get deepLink =>
      $composableBuilder(column: $table.deepLink, builder: (column) => column);

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get readAt =>
      $composableBuilder(column: $table.readAt, builder: (column) => column);
}

class $$NotificationsTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $NotificationsTable,
          LocalNotification,
          $$NotificationsTableFilterComposer,
          $$NotificationsTableOrderingComposer,
          $$NotificationsTableAnnotationComposer,
          $$NotificationsTableCreateCompanionBuilder,
          $$NotificationsTableUpdateCompanionBuilder,
          (
            LocalNotification,
            BaseReferences<
              _$LocalStore,
              $NotificationsTable,
              LocalNotification
            >,
          ),
          LocalNotification,
          PrefetchHooks Function()
        > {
  $$NotificationsTableTableManager(_$LocalStore db, $NotificationsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$NotificationsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$NotificationsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$NotificationsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<String?> body = const Value.absent(),
                Value<String?> deepLink = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime?> readAt = const Value.absent(),
              }) => NotificationsCompanion(
                id: id,
                kind: kind,
                title: title,
                body: body,
                deepLink: deepLink,
                createdAt: createdAt,
                readAt: readAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String kind,
                required String title,
                Value<String?> body = const Value.absent(),
                Value<String?> deepLink = const Value.absent(),
                required DateTime createdAt,
                Value<DateTime?> readAt = const Value.absent(),
              }) => NotificationsCompanion.insert(
                id: id,
                kind: kind,
                title: title,
                body: body,
                deepLink: deepLink,
                createdAt: createdAt,
                readAt: readAt,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$NotificationsTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $NotificationsTable,
      LocalNotification,
      $$NotificationsTableFilterComposer,
      $$NotificationsTableOrderingComposer,
      $$NotificationsTableAnnotationComposer,
      $$NotificationsTableCreateCompanionBuilder,
      $$NotificationsTableUpdateCompanionBuilder,
      (
        LocalNotification,
        BaseReferences<_$LocalStore, $NotificationsTable, LocalNotification>,
      ),
      LocalNotification,
      PrefetchHooks Function()
    >;
typedef $$SubmissionsTableCreateCompanionBuilder =
    SubmissionsCompanion Function({
      required String publicId,
      Value<int?> requirementHintId,
      required String source,
      Value<String?> contentType,
      Value<int?> declaredSize,
      Value<int> uploadOffset,
      Value<bool> uploadComplete,
      required String verificationStatus,
      Value<String?> rejectionReason,
      required DateTime submittedAt,
      Value<String?> localPath,
      Value<int> rowid,
    });
typedef $$SubmissionsTableUpdateCompanionBuilder =
    SubmissionsCompanion Function({
      Value<String> publicId,
      Value<int?> requirementHintId,
      Value<String> source,
      Value<String?> contentType,
      Value<int?> declaredSize,
      Value<int> uploadOffset,
      Value<bool> uploadComplete,
      Value<String> verificationStatus,
      Value<String?> rejectionReason,
      Value<DateTime> submittedAt,
      Value<String?> localPath,
      Value<int> rowid,
    });

class $$SubmissionsTableFilterComposer
    extends Composer<_$LocalStore, $SubmissionsTable> {
  $$SubmissionsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get publicId => $composableBuilder(
    column: $table.publicId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get requirementHintId => $composableBuilder(
    column: $table.requirementHintId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get source => $composableBuilder(
    column: $table.source,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get contentType => $composableBuilder(
    column: $table.contentType,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get declaredSize => $composableBuilder(
    column: $table.declaredSize,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get uploadOffset => $composableBuilder(
    column: $table.uploadOffset,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get uploadComplete => $composableBuilder(
    column: $table.uploadComplete,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get verificationStatus => $composableBuilder(
    column: $table.verificationStatus,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get rejectionReason => $composableBuilder(
    column: $table.rejectionReason,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get submittedAt => $composableBuilder(
    column: $table.submittedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get localPath => $composableBuilder(
    column: $table.localPath,
    builder: (column) => ColumnFilters(column),
  );
}

class $$SubmissionsTableOrderingComposer
    extends Composer<_$LocalStore, $SubmissionsTable> {
  $$SubmissionsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get publicId => $composableBuilder(
    column: $table.publicId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get requirementHintId => $composableBuilder(
    column: $table.requirementHintId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get source => $composableBuilder(
    column: $table.source,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get contentType => $composableBuilder(
    column: $table.contentType,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get declaredSize => $composableBuilder(
    column: $table.declaredSize,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get uploadOffset => $composableBuilder(
    column: $table.uploadOffset,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get uploadComplete => $composableBuilder(
    column: $table.uploadComplete,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get verificationStatus => $composableBuilder(
    column: $table.verificationStatus,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get rejectionReason => $composableBuilder(
    column: $table.rejectionReason,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get submittedAt => $composableBuilder(
    column: $table.submittedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get localPath => $composableBuilder(
    column: $table.localPath,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$SubmissionsTableAnnotationComposer
    extends Composer<_$LocalStore, $SubmissionsTable> {
  $$SubmissionsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get publicId =>
      $composableBuilder(column: $table.publicId, builder: (column) => column);

  GeneratedColumn<int> get requirementHintId => $composableBuilder(
    column: $table.requirementHintId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get source =>
      $composableBuilder(column: $table.source, builder: (column) => column);

  GeneratedColumn<String> get contentType => $composableBuilder(
    column: $table.contentType,
    builder: (column) => column,
  );

  GeneratedColumn<int> get declaredSize => $composableBuilder(
    column: $table.declaredSize,
    builder: (column) => column,
  );

  GeneratedColumn<int> get uploadOffset => $composableBuilder(
    column: $table.uploadOffset,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get uploadComplete => $composableBuilder(
    column: $table.uploadComplete,
    builder: (column) => column,
  );

  GeneratedColumn<String> get verificationStatus => $composableBuilder(
    column: $table.verificationStatus,
    builder: (column) => column,
  );

  GeneratedColumn<String> get rejectionReason => $composableBuilder(
    column: $table.rejectionReason,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get submittedAt => $composableBuilder(
    column: $table.submittedAt,
    builder: (column) => column,
  );

  GeneratedColumn<String> get localPath =>
      $composableBuilder(column: $table.localPath, builder: (column) => column);
}

class $$SubmissionsTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $SubmissionsTable,
          LocalSubmission,
          $$SubmissionsTableFilterComposer,
          $$SubmissionsTableOrderingComposer,
          $$SubmissionsTableAnnotationComposer,
          $$SubmissionsTableCreateCompanionBuilder,
          $$SubmissionsTableUpdateCompanionBuilder,
          (
            LocalSubmission,
            BaseReferences<_$LocalStore, $SubmissionsTable, LocalSubmission>,
          ),
          LocalSubmission,
          PrefetchHooks Function()
        > {
  $$SubmissionsTableTableManager(_$LocalStore db, $SubmissionsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$SubmissionsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$SubmissionsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$SubmissionsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> publicId = const Value.absent(),
                Value<int?> requirementHintId = const Value.absent(),
                Value<String> source = const Value.absent(),
                Value<String?> contentType = const Value.absent(),
                Value<int?> declaredSize = const Value.absent(),
                Value<int> uploadOffset = const Value.absent(),
                Value<bool> uploadComplete = const Value.absent(),
                Value<String> verificationStatus = const Value.absent(),
                Value<String?> rejectionReason = const Value.absent(),
                Value<DateTime> submittedAt = const Value.absent(),
                Value<String?> localPath = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => SubmissionsCompanion(
                publicId: publicId,
                requirementHintId: requirementHintId,
                source: source,
                contentType: contentType,
                declaredSize: declaredSize,
                uploadOffset: uploadOffset,
                uploadComplete: uploadComplete,
                verificationStatus: verificationStatus,
                rejectionReason: rejectionReason,
                submittedAt: submittedAt,
                localPath: localPath,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String publicId,
                Value<int?> requirementHintId = const Value.absent(),
                required String source,
                Value<String?> contentType = const Value.absent(),
                Value<int?> declaredSize = const Value.absent(),
                Value<int> uploadOffset = const Value.absent(),
                Value<bool> uploadComplete = const Value.absent(),
                required String verificationStatus,
                Value<String?> rejectionReason = const Value.absent(),
                required DateTime submittedAt,
                Value<String?> localPath = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => SubmissionsCompanion.insert(
                publicId: publicId,
                requirementHintId: requirementHintId,
                source: source,
                contentType: contentType,
                declaredSize: declaredSize,
                uploadOffset: uploadOffset,
                uploadComplete: uploadComplete,
                verificationStatus: verificationStatus,
                rejectionReason: rejectionReason,
                submittedAt: submittedAt,
                localPath: localPath,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$SubmissionsTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $SubmissionsTable,
      LocalSubmission,
      $$SubmissionsTableFilterComposer,
      $$SubmissionsTableOrderingComposer,
      $$SubmissionsTableAnnotationComposer,
      $$SubmissionsTableCreateCompanionBuilder,
      $$SubmissionsTableUpdateCompanionBuilder,
      (
        LocalSubmission,
        BaseReferences<_$LocalStore, $SubmissionsTable, LocalSubmission>,
      ),
      LocalSubmission,
      PrefetchHooks Function()
    >;
typedef $$RequirementsTableCreateCompanionBuilder =
    RequirementsCompanion Function({
      Value<int> id,
      required String code,
      required String category,
      required String title,
      required String status,
      Value<String?> issuingAuthority,
    });
typedef $$RequirementsTableUpdateCompanionBuilder =
    RequirementsCompanion Function({
      Value<int> id,
      Value<String> code,
      Value<String> category,
      Value<String> title,
      Value<String> status,
      Value<String?> issuingAuthority,
    });

class $$RequirementsTableFilterComposer
    extends Composer<_$LocalStore, $RequirementsTable> {
  $$RequirementsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get code => $composableBuilder(
    column: $table.code,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get category => $composableBuilder(
    column: $table.category,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get issuingAuthority => $composableBuilder(
    column: $table.issuingAuthority,
    builder: (column) => ColumnFilters(column),
  );
}

class $$RequirementsTableOrderingComposer
    extends Composer<_$LocalStore, $RequirementsTable> {
  $$RequirementsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get code => $composableBuilder(
    column: $table.code,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get category => $composableBuilder(
    column: $table.category,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get title => $composableBuilder(
    column: $table.title,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get issuingAuthority => $composableBuilder(
    column: $table.issuingAuthority,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$RequirementsTableAnnotationComposer
    extends Composer<_$LocalStore, $RequirementsTable> {
  $$RequirementsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get code =>
      $composableBuilder(column: $table.code, builder: (column) => column);

  GeneratedColumn<String> get category =>
      $composableBuilder(column: $table.category, builder: (column) => column);

  GeneratedColumn<String> get title =>
      $composableBuilder(column: $table.title, builder: (column) => column);

  GeneratedColumn<String> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<String> get issuingAuthority => $composableBuilder(
    column: $table.issuingAuthority,
    builder: (column) => column,
  );
}

class $$RequirementsTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $RequirementsTable,
          LocalRequirement,
          $$RequirementsTableFilterComposer,
          $$RequirementsTableOrderingComposer,
          $$RequirementsTableAnnotationComposer,
          $$RequirementsTableCreateCompanionBuilder,
          $$RequirementsTableUpdateCompanionBuilder,
          (
            LocalRequirement,
            BaseReferences<_$LocalStore, $RequirementsTable, LocalRequirement>,
          ),
          LocalRequirement,
          PrefetchHooks Function()
        > {
  $$RequirementsTableTableManager(_$LocalStore db, $RequirementsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$RequirementsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$RequirementsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$RequirementsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> code = const Value.absent(),
                Value<String> category = const Value.absent(),
                Value<String> title = const Value.absent(),
                Value<String> status = const Value.absent(),
                Value<String?> issuingAuthority = const Value.absent(),
              }) => RequirementsCompanion(
                id: id,
                code: code,
                category: category,
                title: title,
                status: status,
                issuingAuthority: issuingAuthority,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String code,
                required String category,
                required String title,
                required String status,
                Value<String?> issuingAuthority = const Value.absent(),
              }) => RequirementsCompanion.insert(
                id: id,
                code: code,
                category: category,
                title: title,
                status: status,
                issuingAuthority: issuingAuthority,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$RequirementsTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $RequirementsTable,
      LocalRequirement,
      $$RequirementsTableFilterComposer,
      $$RequirementsTableOrderingComposer,
      $$RequirementsTableAnnotationComposer,
      $$RequirementsTableCreateCompanionBuilder,
      $$RequirementsTableUpdateCompanionBuilder,
      (
        LocalRequirement,
        BaseReferences<_$LocalStore, $RequirementsTable, LocalRequirement>,
      ),
      LocalRequirement,
      PrefetchHooks Function()
    >;
typedef $$CrewChangesTableCreateCompanionBuilder =
    CrewChangesCompanion Function({
      Value<int> id,
      required String ccId,
      required String fromDate,
      required String toDate,
      required String cutoff,
    });
typedef $$CrewChangesTableUpdateCompanionBuilder =
    CrewChangesCompanion Function({
      Value<int> id,
      Value<String> ccId,
      Value<String> fromDate,
      Value<String> toDate,
      Value<String> cutoff,
    });

class $$CrewChangesTableFilterComposer
    extends Composer<_$LocalStore, $CrewChangesTable> {
  $$CrewChangesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get ccId => $composableBuilder(
    column: $table.ccId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get fromDate => $composableBuilder(
    column: $table.fromDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get toDate => $composableBuilder(
    column: $table.toDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get cutoff => $composableBuilder(
    column: $table.cutoff,
    builder: (column) => ColumnFilters(column),
  );
}

class $$CrewChangesTableOrderingComposer
    extends Composer<_$LocalStore, $CrewChangesTable> {
  $$CrewChangesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get ccId => $composableBuilder(
    column: $table.ccId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get fromDate => $composableBuilder(
    column: $table.fromDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get toDate => $composableBuilder(
    column: $table.toDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get cutoff => $composableBuilder(
    column: $table.cutoff,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$CrewChangesTableAnnotationComposer
    extends Composer<_$LocalStore, $CrewChangesTable> {
  $$CrewChangesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get ccId =>
      $composableBuilder(column: $table.ccId, builder: (column) => column);

  GeneratedColumn<String> get fromDate =>
      $composableBuilder(column: $table.fromDate, builder: (column) => column);

  GeneratedColumn<String> get toDate =>
      $composableBuilder(column: $table.toDate, builder: (column) => column);

  GeneratedColumn<String> get cutoff =>
      $composableBuilder(column: $table.cutoff, builder: (column) => column);
}

class $$CrewChangesTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $CrewChangesTable,
          LocalCrewChange,
          $$CrewChangesTableFilterComposer,
          $$CrewChangesTableOrderingComposer,
          $$CrewChangesTableAnnotationComposer,
          $$CrewChangesTableCreateCompanionBuilder,
          $$CrewChangesTableUpdateCompanionBuilder,
          (
            LocalCrewChange,
            BaseReferences<_$LocalStore, $CrewChangesTable, LocalCrewChange>,
          ),
          LocalCrewChange,
          PrefetchHooks Function()
        > {
  $$CrewChangesTableTableManager(_$LocalStore db, $CrewChangesTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$CrewChangesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$CrewChangesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$CrewChangesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> ccId = const Value.absent(),
                Value<String> fromDate = const Value.absent(),
                Value<String> toDate = const Value.absent(),
                Value<String> cutoff = const Value.absent(),
              }) => CrewChangesCompanion(
                id: id,
                ccId: ccId,
                fromDate: fromDate,
                toDate: toDate,
                cutoff: cutoff,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String ccId,
                required String fromDate,
                required String toDate,
                required String cutoff,
              }) => CrewChangesCompanion.insert(
                id: id,
                ccId: ccId,
                fromDate: fromDate,
                toDate: toDate,
                cutoff: cutoff,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$CrewChangesTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $CrewChangesTable,
      LocalCrewChange,
      $$CrewChangesTableFilterComposer,
      $$CrewChangesTableOrderingComposer,
      $$CrewChangesTableAnnotationComposer,
      $$CrewChangesTableCreateCompanionBuilder,
      $$CrewChangesTableUpdateCompanionBuilder,
      (
        LocalCrewChange,
        BaseReferences<_$LocalStore, $CrewChangesTable, LocalCrewChange>,
      ),
      LocalCrewChange,
      PrefetchHooks Function()
    >;
typedef $$StandingCellsTableCreateCompanionBuilder =
    StandingCellsCompanion Function({
      Value<int> requirementId,
      required String level,
      required String state,
      Value<String?> expiry,
      Value<String?> notes,
      Value<String?> registerRecordId,
    });
typedef $$StandingCellsTableUpdateCompanionBuilder =
    StandingCellsCompanion Function({
      Value<int> requirementId,
      Value<String> level,
      Value<String> state,
      Value<String?> expiry,
      Value<String?> notes,
      Value<String?> registerRecordId,
    });

class $$StandingCellsTableFilterComposer
    extends Composer<_$LocalStore, $StandingCellsTable> {
  $$StandingCellsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get level => $composableBuilder(
    column: $table.level,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get expiry => $composableBuilder(
    column: $table.expiry,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get notes => $composableBuilder(
    column: $table.notes,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get registerRecordId => $composableBuilder(
    column: $table.registerRecordId,
    builder: (column) => ColumnFilters(column),
  );
}

class $$StandingCellsTableOrderingComposer
    extends Composer<_$LocalStore, $StandingCellsTable> {
  $$StandingCellsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get level => $composableBuilder(
    column: $table.level,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get expiry => $composableBuilder(
    column: $table.expiry,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get notes => $composableBuilder(
    column: $table.notes,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get registerRecordId => $composableBuilder(
    column: $table.registerRecordId,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$StandingCellsTableAnnotationComposer
    extends Composer<_$LocalStore, $StandingCellsTable> {
  $$StandingCellsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get level =>
      $composableBuilder(column: $table.level, builder: (column) => column);

  GeneratedColumn<String> get state =>
      $composableBuilder(column: $table.state, builder: (column) => column);

  GeneratedColumn<String> get expiry =>
      $composableBuilder(column: $table.expiry, builder: (column) => column);

  GeneratedColumn<String> get notes =>
      $composableBuilder(column: $table.notes, builder: (column) => column);

  GeneratedColumn<String> get registerRecordId => $composableBuilder(
    column: $table.registerRecordId,
    builder: (column) => column,
  );
}

class $$StandingCellsTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $StandingCellsTable,
          LocalStandingCell,
          $$StandingCellsTableFilterComposer,
          $$StandingCellsTableOrderingComposer,
          $$StandingCellsTableAnnotationComposer,
          $$StandingCellsTableCreateCompanionBuilder,
          $$StandingCellsTableUpdateCompanionBuilder,
          (
            LocalStandingCell,
            BaseReferences<
              _$LocalStore,
              $StandingCellsTable,
              LocalStandingCell
            >,
          ),
          LocalStandingCell,
          PrefetchHooks Function()
        > {
  $$StandingCellsTableTableManager(_$LocalStore db, $StandingCellsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$StandingCellsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$StandingCellsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$StandingCellsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> requirementId = const Value.absent(),
                Value<String> level = const Value.absent(),
                Value<String> state = const Value.absent(),
                Value<String?> expiry = const Value.absent(),
                Value<String?> notes = const Value.absent(),
                Value<String?> registerRecordId = const Value.absent(),
              }) => StandingCellsCompanion(
                requirementId: requirementId,
                level: level,
                state: state,
                expiry: expiry,
                notes: notes,
                registerRecordId: registerRecordId,
              ),
          createCompanionCallback:
              ({
                Value<int> requirementId = const Value.absent(),
                required String level,
                required String state,
                Value<String?> expiry = const Value.absent(),
                Value<String?> notes = const Value.absent(),
                Value<String?> registerRecordId = const Value.absent(),
              }) => StandingCellsCompanion.insert(
                requirementId: requirementId,
                level: level,
                state: state,
                expiry: expiry,
                notes: notes,
                registerRecordId: registerRecordId,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$StandingCellsTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $StandingCellsTable,
      LocalStandingCell,
      $$StandingCellsTableFilterComposer,
      $$StandingCellsTableOrderingComposer,
      $$StandingCellsTableAnnotationComposer,
      $$StandingCellsTableCreateCompanionBuilder,
      $$StandingCellsTableUpdateCompanionBuilder,
      (
        LocalStandingCell,
        BaseReferences<_$LocalStore, $StandingCellsTable, LocalStandingCell>,
      ),
      LocalStandingCell,
      PrefetchHooks Function()
    >;
typedef $$OutboxTableCreateCompanionBuilder =
    OutboxCompanion Function({
      required String opId,
      required String type,
      required String payload,
      required DateTime queuedAt,
      Value<int> attempts,
      Value<DateTime?> nextAttemptAt,
      Value<String?> lastError,
      Value<int> rowid,
    });
typedef $$OutboxTableUpdateCompanionBuilder =
    OutboxCompanion Function({
      Value<String> opId,
      Value<String> type,
      Value<String> payload,
      Value<DateTime> queuedAt,
      Value<int> attempts,
      Value<DateTime?> nextAttemptAt,
      Value<String?> lastError,
      Value<int> rowid,
    });

class $$OutboxTableFilterComposer extends Composer<_$LocalStore, $OutboxTable> {
  $$OutboxTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get opId => $composableBuilder(
    column: $table.opId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get type => $composableBuilder(
    column: $table.type,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get payload => $composableBuilder(
    column: $table.payload,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get queuedAt => $composableBuilder(
    column: $table.queuedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get attempts => $composableBuilder(
    column: $table.attempts,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get nextAttemptAt => $composableBuilder(
    column: $table.nextAttemptAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get lastError => $composableBuilder(
    column: $table.lastError,
    builder: (column) => ColumnFilters(column),
  );
}

class $$OutboxTableOrderingComposer
    extends Composer<_$LocalStore, $OutboxTable> {
  $$OutboxTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get opId => $composableBuilder(
    column: $table.opId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get type => $composableBuilder(
    column: $table.type,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get payload => $composableBuilder(
    column: $table.payload,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get queuedAt => $composableBuilder(
    column: $table.queuedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get attempts => $composableBuilder(
    column: $table.attempts,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get nextAttemptAt => $composableBuilder(
    column: $table.nextAttemptAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get lastError => $composableBuilder(
    column: $table.lastError,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$OutboxTableAnnotationComposer
    extends Composer<_$LocalStore, $OutboxTable> {
  $$OutboxTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get opId =>
      $composableBuilder(column: $table.opId, builder: (column) => column);

  GeneratedColumn<String> get type =>
      $composableBuilder(column: $table.type, builder: (column) => column);

  GeneratedColumn<String> get payload =>
      $composableBuilder(column: $table.payload, builder: (column) => column);

  GeneratedColumn<DateTime> get queuedAt =>
      $composableBuilder(column: $table.queuedAt, builder: (column) => column);

  GeneratedColumn<int> get attempts =>
      $composableBuilder(column: $table.attempts, builder: (column) => column);

  GeneratedColumn<DateTime> get nextAttemptAt => $composableBuilder(
    column: $table.nextAttemptAt,
    builder: (column) => column,
  );

  GeneratedColumn<String> get lastError =>
      $composableBuilder(column: $table.lastError, builder: (column) => column);
}

class $$OutboxTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $OutboxTable,
          OutboxEntry,
          $$OutboxTableFilterComposer,
          $$OutboxTableOrderingComposer,
          $$OutboxTableAnnotationComposer,
          $$OutboxTableCreateCompanionBuilder,
          $$OutboxTableUpdateCompanionBuilder,
          (
            OutboxEntry,
            BaseReferences<_$LocalStore, $OutboxTable, OutboxEntry>,
          ),
          OutboxEntry,
          PrefetchHooks Function()
        > {
  $$OutboxTableTableManager(_$LocalStore db, $OutboxTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$OutboxTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$OutboxTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$OutboxTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> opId = const Value.absent(),
                Value<String> type = const Value.absent(),
                Value<String> payload = const Value.absent(),
                Value<DateTime> queuedAt = const Value.absent(),
                Value<int> attempts = const Value.absent(),
                Value<DateTime?> nextAttemptAt = const Value.absent(),
                Value<String?> lastError = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => OutboxCompanion(
                opId: opId,
                type: type,
                payload: payload,
                queuedAt: queuedAt,
                attempts: attempts,
                nextAttemptAt: nextAttemptAt,
                lastError: lastError,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String opId,
                required String type,
                required String payload,
                required DateTime queuedAt,
                Value<int> attempts = const Value.absent(),
                Value<DateTime?> nextAttemptAt = const Value.absent(),
                Value<String?> lastError = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => OutboxCompanion.insert(
                opId: opId,
                type: type,
                payload: payload,
                queuedAt: queuedAt,
                attempts: attempts,
                nextAttemptAt: nextAttemptAt,
                lastError: lastError,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$OutboxTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $OutboxTable,
      OutboxEntry,
      $$OutboxTableFilterComposer,
      $$OutboxTableOrderingComposer,
      $$OutboxTableAnnotationComposer,
      $$OutboxTableCreateCompanionBuilder,
      $$OutboxTableUpdateCompanionBuilder,
      (OutboxEntry, BaseReferences<_$LocalStore, $OutboxTable, OutboxEntry>),
      OutboxEntry,
      PrefetchHooks Function()
    >;
typedef $$CrewIntentsTableCreateCompanionBuilder =
    CrewIntentsCompanion Function({
      required String opId,
      required String kind,
      Value<int?> requirementId,
      Value<String?> subjectRef,
      required String summary,
      required String payload,
      required DateTime queuedAt,
      Value<String> state,
      Value<String?> detail,
      Value<int> rowid,
    });
typedef $$CrewIntentsTableUpdateCompanionBuilder =
    CrewIntentsCompanion Function({
      Value<String> opId,
      Value<String> kind,
      Value<int?> requirementId,
      Value<String?> subjectRef,
      Value<String> summary,
      Value<String> payload,
      Value<DateTime> queuedAt,
      Value<String> state,
      Value<String?> detail,
      Value<int> rowid,
    });

class $$CrewIntentsTableFilterComposer
    extends Composer<_$LocalStore, $CrewIntentsTable> {
  $$CrewIntentsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get opId => $composableBuilder(
    column: $table.opId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get subjectRef => $composableBuilder(
    column: $table.subjectRef,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get summary => $composableBuilder(
    column: $table.summary,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get payload => $composableBuilder(
    column: $table.payload,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get queuedAt => $composableBuilder(
    column: $table.queuedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get detail => $composableBuilder(
    column: $table.detail,
    builder: (column) => ColumnFilters(column),
  );
}

class $$CrewIntentsTableOrderingComposer
    extends Composer<_$LocalStore, $CrewIntentsTable> {
  $$CrewIntentsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get opId => $composableBuilder(
    column: $table.opId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get subjectRef => $composableBuilder(
    column: $table.subjectRef,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get summary => $composableBuilder(
    column: $table.summary,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get payload => $composableBuilder(
    column: $table.payload,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get queuedAt => $composableBuilder(
    column: $table.queuedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get state => $composableBuilder(
    column: $table.state,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get detail => $composableBuilder(
    column: $table.detail,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$CrewIntentsTableAnnotationComposer
    extends Composer<_$LocalStore, $CrewIntentsTable> {
  $$CrewIntentsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get opId =>
      $composableBuilder(column: $table.opId, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get subjectRef => $composableBuilder(
    column: $table.subjectRef,
    builder: (column) => column,
  );

  GeneratedColumn<String> get summary =>
      $composableBuilder(column: $table.summary, builder: (column) => column);

  GeneratedColumn<String> get payload =>
      $composableBuilder(column: $table.payload, builder: (column) => column);

  GeneratedColumn<DateTime> get queuedAt =>
      $composableBuilder(column: $table.queuedAt, builder: (column) => column);

  GeneratedColumn<String> get state =>
      $composableBuilder(column: $table.state, builder: (column) => column);

  GeneratedColumn<String> get detail =>
      $composableBuilder(column: $table.detail, builder: (column) => column);
}

class $$CrewIntentsTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $CrewIntentsTable,
          LocalCrewIntent,
          $$CrewIntentsTableFilterComposer,
          $$CrewIntentsTableOrderingComposer,
          $$CrewIntentsTableAnnotationComposer,
          $$CrewIntentsTableCreateCompanionBuilder,
          $$CrewIntentsTableUpdateCompanionBuilder,
          (
            LocalCrewIntent,
            BaseReferences<_$LocalStore, $CrewIntentsTable, LocalCrewIntent>,
          ),
          LocalCrewIntent,
          PrefetchHooks Function()
        > {
  $$CrewIntentsTableTableManager(_$LocalStore db, $CrewIntentsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$CrewIntentsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$CrewIntentsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$CrewIntentsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> opId = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<int?> requirementId = const Value.absent(),
                Value<String?> subjectRef = const Value.absent(),
                Value<String> summary = const Value.absent(),
                Value<String> payload = const Value.absent(),
                Value<DateTime> queuedAt = const Value.absent(),
                Value<String> state = const Value.absent(),
                Value<String?> detail = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => CrewIntentsCompanion(
                opId: opId,
                kind: kind,
                requirementId: requirementId,
                subjectRef: subjectRef,
                summary: summary,
                payload: payload,
                queuedAt: queuedAt,
                state: state,
                detail: detail,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String opId,
                required String kind,
                Value<int?> requirementId = const Value.absent(),
                Value<String?> subjectRef = const Value.absent(),
                required String summary,
                required String payload,
                required DateTime queuedAt,
                Value<String> state = const Value.absent(),
                Value<String?> detail = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => CrewIntentsCompanion.insert(
                opId: opId,
                kind: kind,
                requirementId: requirementId,
                subjectRef: subjectRef,
                summary: summary,
                payload: payload,
                queuedAt: queuedAt,
                state: state,
                detail: detail,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$CrewIntentsTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $CrewIntentsTable,
      LocalCrewIntent,
      $$CrewIntentsTableFilterComposer,
      $$CrewIntentsTableOrderingComposer,
      $$CrewIntentsTableAnnotationComposer,
      $$CrewIntentsTableCreateCompanionBuilder,
      $$CrewIntentsTableUpdateCompanionBuilder,
      (
        LocalCrewIntent,
        BaseReferences<_$LocalStore, $CrewIntentsTable, LocalCrewIntent>,
      ),
      LocalCrewIntent,
      PrefetchHooks Function()
    >;
typedef $$CrewStatementsTableCreateCompanionBuilder =
    CrewStatementsCompanion Function({
      Value<int> id,
      required String opId,
      required String kind,
      required int requirementId,
      required String status,
      Value<String?> subjectRef,
      Value<String?> aboutExpiry,
      required DateTime raisedAt,
      Value<String?> decisionNote,
      Value<DateTime?> decidedAt,
    });
typedef $$CrewStatementsTableUpdateCompanionBuilder =
    CrewStatementsCompanion Function({
      Value<int> id,
      Value<String> opId,
      Value<String> kind,
      Value<int> requirementId,
      Value<String> status,
      Value<String?> subjectRef,
      Value<String?> aboutExpiry,
      Value<DateTime> raisedAt,
      Value<String?> decisionNote,
      Value<DateTime?> decidedAt,
    });

class $$CrewStatementsTableFilterComposer
    extends Composer<_$LocalStore, $CrewStatementsTable> {
  $$CrewStatementsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get opId => $composableBuilder(
    column: $table.opId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get subjectRef => $composableBuilder(
    column: $table.subjectRef,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get aboutExpiry => $composableBuilder(
    column: $table.aboutExpiry,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get raisedAt => $composableBuilder(
    column: $table.raisedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get decisionNote => $composableBuilder(
    column: $table.decisionNote,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get decidedAt => $composableBuilder(
    column: $table.decidedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$CrewStatementsTableOrderingComposer
    extends Composer<_$LocalStore, $CrewStatementsTable> {
  $$CrewStatementsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get opId => $composableBuilder(
    column: $table.opId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kind => $composableBuilder(
    column: $table.kind,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get subjectRef => $composableBuilder(
    column: $table.subjectRef,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get aboutExpiry => $composableBuilder(
    column: $table.aboutExpiry,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get raisedAt => $composableBuilder(
    column: $table.raisedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get decisionNote => $composableBuilder(
    column: $table.decisionNote,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get decidedAt => $composableBuilder(
    column: $table.decidedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$CrewStatementsTableAnnotationComposer
    extends Composer<_$LocalStore, $CrewStatementsTable> {
  $$CrewStatementsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get opId =>
      $composableBuilder(column: $table.opId, builder: (column) => column);

  GeneratedColumn<String> get kind =>
      $composableBuilder(column: $table.kind, builder: (column) => column);

  GeneratedColumn<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<String> get subjectRef => $composableBuilder(
    column: $table.subjectRef,
    builder: (column) => column,
  );

  GeneratedColumn<String> get aboutExpiry => $composableBuilder(
    column: $table.aboutExpiry,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get raisedAt =>
      $composableBuilder(column: $table.raisedAt, builder: (column) => column);

  GeneratedColumn<String> get decisionNote => $composableBuilder(
    column: $table.decisionNote,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get decidedAt =>
      $composableBuilder(column: $table.decidedAt, builder: (column) => column);
}

class $$CrewStatementsTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $CrewStatementsTable,
          LocalCrewStatement,
          $$CrewStatementsTableFilterComposer,
          $$CrewStatementsTableOrderingComposer,
          $$CrewStatementsTableAnnotationComposer,
          $$CrewStatementsTableCreateCompanionBuilder,
          $$CrewStatementsTableUpdateCompanionBuilder,
          (
            LocalCrewStatement,
            BaseReferences<
              _$LocalStore,
              $CrewStatementsTable,
              LocalCrewStatement
            >,
          ),
          LocalCrewStatement,
          PrefetchHooks Function()
        > {
  $$CrewStatementsTableTableManager(_$LocalStore db, $CrewStatementsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$CrewStatementsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$CrewStatementsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$CrewStatementsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> opId = const Value.absent(),
                Value<String> kind = const Value.absent(),
                Value<int> requirementId = const Value.absent(),
                Value<String> status = const Value.absent(),
                Value<String?> subjectRef = const Value.absent(),
                Value<String?> aboutExpiry = const Value.absent(),
                Value<DateTime> raisedAt = const Value.absent(),
                Value<String?> decisionNote = const Value.absent(),
                Value<DateTime?> decidedAt = const Value.absent(),
              }) => CrewStatementsCompanion(
                id: id,
                opId: opId,
                kind: kind,
                requirementId: requirementId,
                status: status,
                subjectRef: subjectRef,
                aboutExpiry: aboutExpiry,
                raisedAt: raisedAt,
                decisionNote: decisionNote,
                decidedAt: decidedAt,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String opId,
                required String kind,
                required int requirementId,
                required String status,
                Value<String?> subjectRef = const Value.absent(),
                Value<String?> aboutExpiry = const Value.absent(),
                required DateTime raisedAt,
                Value<String?> decisionNote = const Value.absent(),
                Value<DateTime?> decidedAt = const Value.absent(),
              }) => CrewStatementsCompanion.insert(
                id: id,
                opId: opId,
                kind: kind,
                requirementId: requirementId,
                status: status,
                subjectRef: subjectRef,
                aboutExpiry: aboutExpiry,
                raisedAt: raisedAt,
                decisionNote: decisionNote,
                decidedAt: decidedAt,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$CrewStatementsTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $CrewStatementsTable,
      LocalCrewStatement,
      $$CrewStatementsTableFilterComposer,
      $$CrewStatementsTableOrderingComposer,
      $$CrewStatementsTableAnnotationComposer,
      $$CrewStatementsTableCreateCompanionBuilder,
      $$CrewStatementsTableUpdateCompanionBuilder,
      (
        LocalCrewStatement,
        BaseReferences<_$LocalStore, $CrewStatementsTable, LocalCrewStatement>,
      ),
      LocalCrewStatement,
      PrefetchHooks Function()
    >;
typedef $$AttestationsTableCreateCompanionBuilder =
    AttestationsCompanion Function({
      Value<int> id,
      required String opId,
      required int assignmentId,
      required String ccId,
      required String declarations,
      required DateTime signedAt,
      required String signedAtDisplay,
    });
typedef $$AttestationsTableUpdateCompanionBuilder =
    AttestationsCompanion Function({
      Value<int> id,
      Value<String> opId,
      Value<int> assignmentId,
      Value<String> ccId,
      Value<String> declarations,
      Value<DateTime> signedAt,
      Value<String> signedAtDisplay,
    });

class $$AttestationsTableFilterComposer
    extends Composer<_$LocalStore, $AttestationsTable> {
  $$AttestationsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get opId => $composableBuilder(
    column: $table.opId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get assignmentId => $composableBuilder(
    column: $table.assignmentId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get ccId => $composableBuilder(
    column: $table.ccId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get declarations => $composableBuilder(
    column: $table.declarations,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get signedAt => $composableBuilder(
    column: $table.signedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get signedAtDisplay => $composableBuilder(
    column: $table.signedAtDisplay,
    builder: (column) => ColumnFilters(column),
  );
}

class $$AttestationsTableOrderingComposer
    extends Composer<_$LocalStore, $AttestationsTable> {
  $$AttestationsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get opId => $composableBuilder(
    column: $table.opId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get assignmentId => $composableBuilder(
    column: $table.assignmentId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get ccId => $composableBuilder(
    column: $table.ccId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get declarations => $composableBuilder(
    column: $table.declarations,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get signedAt => $composableBuilder(
    column: $table.signedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get signedAtDisplay => $composableBuilder(
    column: $table.signedAtDisplay,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$AttestationsTableAnnotationComposer
    extends Composer<_$LocalStore, $AttestationsTable> {
  $$AttestationsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get opId =>
      $composableBuilder(column: $table.opId, builder: (column) => column);

  GeneratedColumn<int> get assignmentId => $composableBuilder(
    column: $table.assignmentId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get ccId =>
      $composableBuilder(column: $table.ccId, builder: (column) => column);

  GeneratedColumn<String> get declarations => $composableBuilder(
    column: $table.declarations,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get signedAt =>
      $composableBuilder(column: $table.signedAt, builder: (column) => column);

  GeneratedColumn<String> get signedAtDisplay => $composableBuilder(
    column: $table.signedAtDisplay,
    builder: (column) => column,
  );
}

class $$AttestationsTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $AttestationsTable,
          LocalAttestation,
          $$AttestationsTableFilterComposer,
          $$AttestationsTableOrderingComposer,
          $$AttestationsTableAnnotationComposer,
          $$AttestationsTableCreateCompanionBuilder,
          $$AttestationsTableUpdateCompanionBuilder,
          (
            LocalAttestation,
            BaseReferences<_$LocalStore, $AttestationsTable, LocalAttestation>,
          ),
          LocalAttestation,
          PrefetchHooks Function()
        > {
  $$AttestationsTableTableManager(_$LocalStore db, $AttestationsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$AttestationsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$AttestationsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$AttestationsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<String> opId = const Value.absent(),
                Value<int> assignmentId = const Value.absent(),
                Value<String> ccId = const Value.absent(),
                Value<String> declarations = const Value.absent(),
                Value<DateTime> signedAt = const Value.absent(),
                Value<String> signedAtDisplay = const Value.absent(),
              }) => AttestationsCompanion(
                id: id,
                opId: opId,
                assignmentId: assignmentId,
                ccId: ccId,
                declarations: declarations,
                signedAt: signedAt,
                signedAtDisplay: signedAtDisplay,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                required String opId,
                required int assignmentId,
                required String ccId,
                required String declarations,
                required DateTime signedAt,
                required String signedAtDisplay,
              }) => AttestationsCompanion.insert(
                id: id,
                opId: opId,
                assignmentId: assignmentId,
                ccId: ccId,
                declarations: declarations,
                signedAt: signedAt,
                signedAtDisplay: signedAtDisplay,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$AttestationsTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $AttestationsTable,
      LocalAttestation,
      $$AttestationsTableFilterComposer,
      $$AttestationsTableOrderingComposer,
      $$AttestationsTableAnnotationComposer,
      $$AttestationsTableCreateCompanionBuilder,
      $$AttestationsTableUpdateCompanionBuilder,
      (
        LocalAttestation,
        BaseReferences<_$LocalStore, $AttestationsTable, LocalAttestation>,
      ),
      LocalAttestation,
      PrefetchHooks Function()
    >;
typedef $$CourseOptionsTableCreateCompanionBuilder =
    CourseOptionsCompanion Function({
      required String id,
      required int requirementId,
      required String starts,
      required String finishes,
      required String provider,
      required String location,
      required String durationLabel,
      required int seats,
      required String note,
      required bool recommended,
      required bool waitlistOnly,
      Value<int> rowid,
    });
typedef $$CourseOptionsTableUpdateCompanionBuilder =
    CourseOptionsCompanion Function({
      Value<String> id,
      Value<int> requirementId,
      Value<String> starts,
      Value<String> finishes,
      Value<String> provider,
      Value<String> location,
      Value<String> durationLabel,
      Value<int> seats,
      Value<String> note,
      Value<bool> recommended,
      Value<bool> waitlistOnly,
      Value<int> rowid,
    });

class $$CourseOptionsTableFilterComposer
    extends Composer<_$LocalStore, $CourseOptionsTable> {
  $$CourseOptionsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get starts => $composableBuilder(
    column: $table.starts,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get finishes => $composableBuilder(
    column: $table.finishes,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get provider => $composableBuilder(
    column: $table.provider,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get location => $composableBuilder(
    column: $table.location,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get durationLabel => $composableBuilder(
    column: $table.durationLabel,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get seats => $composableBuilder(
    column: $table.seats,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get recommended => $composableBuilder(
    column: $table.recommended,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get waitlistOnly => $composableBuilder(
    column: $table.waitlistOnly,
    builder: (column) => ColumnFilters(column),
  );
}

class $$CourseOptionsTableOrderingComposer
    extends Composer<_$LocalStore, $CourseOptionsTable> {
  $$CourseOptionsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get starts => $composableBuilder(
    column: $table.starts,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get finishes => $composableBuilder(
    column: $table.finishes,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get provider => $composableBuilder(
    column: $table.provider,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get location => $composableBuilder(
    column: $table.location,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get durationLabel => $composableBuilder(
    column: $table.durationLabel,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get seats => $composableBuilder(
    column: $table.seats,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get note => $composableBuilder(
    column: $table.note,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get recommended => $composableBuilder(
    column: $table.recommended,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get waitlistOnly => $composableBuilder(
    column: $table.waitlistOnly,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$CourseOptionsTableAnnotationComposer
    extends Composer<_$LocalStore, $CourseOptionsTable> {
  $$CourseOptionsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<int> get requirementId => $composableBuilder(
    column: $table.requirementId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get starts =>
      $composableBuilder(column: $table.starts, builder: (column) => column);

  GeneratedColumn<String> get finishes =>
      $composableBuilder(column: $table.finishes, builder: (column) => column);

  GeneratedColumn<String> get provider =>
      $composableBuilder(column: $table.provider, builder: (column) => column);

  GeneratedColumn<String> get location =>
      $composableBuilder(column: $table.location, builder: (column) => column);

  GeneratedColumn<String> get durationLabel => $composableBuilder(
    column: $table.durationLabel,
    builder: (column) => column,
  );

  GeneratedColumn<int> get seats =>
      $composableBuilder(column: $table.seats, builder: (column) => column);

  GeneratedColumn<String> get note =>
      $composableBuilder(column: $table.note, builder: (column) => column);

  GeneratedColumn<bool> get recommended => $composableBuilder(
    column: $table.recommended,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get waitlistOnly => $composableBuilder(
    column: $table.waitlistOnly,
    builder: (column) => column,
  );
}

class $$CourseOptionsTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $CourseOptionsTable,
          LocalCourseOption,
          $$CourseOptionsTableFilterComposer,
          $$CourseOptionsTableOrderingComposer,
          $$CourseOptionsTableAnnotationComposer,
          $$CourseOptionsTableCreateCompanionBuilder,
          $$CourseOptionsTableUpdateCompanionBuilder,
          (
            LocalCourseOption,
            BaseReferences<
              _$LocalStore,
              $CourseOptionsTable,
              LocalCourseOption
            >,
          ),
          LocalCourseOption,
          PrefetchHooks Function()
        > {
  $$CourseOptionsTableTableManager(_$LocalStore db, $CourseOptionsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$CourseOptionsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$CourseOptionsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$CourseOptionsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<int> requirementId = const Value.absent(),
                Value<String> starts = const Value.absent(),
                Value<String> finishes = const Value.absent(),
                Value<String> provider = const Value.absent(),
                Value<String> location = const Value.absent(),
                Value<String> durationLabel = const Value.absent(),
                Value<int> seats = const Value.absent(),
                Value<String> note = const Value.absent(),
                Value<bool> recommended = const Value.absent(),
                Value<bool> waitlistOnly = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => CourseOptionsCompanion(
                id: id,
                requirementId: requirementId,
                starts: starts,
                finishes: finishes,
                provider: provider,
                location: location,
                durationLabel: durationLabel,
                seats: seats,
                note: note,
                recommended: recommended,
                waitlistOnly: waitlistOnly,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required int requirementId,
                required String starts,
                required String finishes,
                required String provider,
                required String location,
                required String durationLabel,
                required int seats,
                required String note,
                required bool recommended,
                required bool waitlistOnly,
                Value<int> rowid = const Value.absent(),
              }) => CourseOptionsCompanion.insert(
                id: id,
                requirementId: requirementId,
                starts: starts,
                finishes: finishes,
                provider: provider,
                location: location,
                durationLabel: durationLabel,
                seats: seats,
                note: note,
                recommended: recommended,
                waitlistOnly: waitlistOnly,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$CourseOptionsTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $CourseOptionsTable,
      LocalCourseOption,
      $$CourseOptionsTableFilterComposer,
      $$CourseOptionsTableOrderingComposer,
      $$CourseOptionsTableAnnotationComposer,
      $$CourseOptionsTableCreateCompanionBuilder,
      $$CourseOptionsTableUpdateCompanionBuilder,
      (
        LocalCourseOption,
        BaseReferences<_$LocalStore, $CourseOptionsTable, LocalCourseOption>,
      ),
      LocalCourseOption,
      PrefetchHooks Function()
    >;
typedef $$TeamMembersTableCreateCompanionBuilder =
    TeamMembersCompanion Function({
      required String sam,
      required String name,
      required String worstState,
      Value<String?> reason,
      required bool inHand,
      Value<DateTime?> nudgedAt,
      Value<int> rowid,
    });
typedef $$TeamMembersTableUpdateCompanionBuilder =
    TeamMembersCompanion Function({
      Value<String> sam,
      Value<String> name,
      Value<String> worstState,
      Value<String?> reason,
      Value<bool> inHand,
      Value<DateTime?> nudgedAt,
      Value<int> rowid,
    });

class $$TeamMembersTableFilterComposer
    extends Composer<_$LocalStore, $TeamMembersTable> {
  $$TeamMembersTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get sam => $composableBuilder(
    column: $table.sam,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get worstState => $composableBuilder(
    column: $table.worstState,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get reason => $composableBuilder(
    column: $table.reason,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get inHand => $composableBuilder(
    column: $table.inHand,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get nudgedAt => $composableBuilder(
    column: $table.nudgedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$TeamMembersTableOrderingComposer
    extends Composer<_$LocalStore, $TeamMembersTable> {
  $$TeamMembersTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get sam => $composableBuilder(
    column: $table.sam,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get name => $composableBuilder(
    column: $table.name,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get worstState => $composableBuilder(
    column: $table.worstState,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get reason => $composableBuilder(
    column: $table.reason,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get inHand => $composableBuilder(
    column: $table.inHand,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get nudgedAt => $composableBuilder(
    column: $table.nudgedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$TeamMembersTableAnnotationComposer
    extends Composer<_$LocalStore, $TeamMembersTable> {
  $$TeamMembersTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get sam =>
      $composableBuilder(column: $table.sam, builder: (column) => column);

  GeneratedColumn<String> get name =>
      $composableBuilder(column: $table.name, builder: (column) => column);

  GeneratedColumn<String> get worstState => $composableBuilder(
    column: $table.worstState,
    builder: (column) => column,
  );

  GeneratedColumn<String> get reason =>
      $composableBuilder(column: $table.reason, builder: (column) => column);

  GeneratedColumn<bool> get inHand =>
      $composableBuilder(column: $table.inHand, builder: (column) => column);

  GeneratedColumn<DateTime> get nudgedAt =>
      $composableBuilder(column: $table.nudgedAt, builder: (column) => column);
}

class $$TeamMembersTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $TeamMembersTable,
          LocalTeamMember,
          $$TeamMembersTableFilterComposer,
          $$TeamMembersTableOrderingComposer,
          $$TeamMembersTableAnnotationComposer,
          $$TeamMembersTableCreateCompanionBuilder,
          $$TeamMembersTableUpdateCompanionBuilder,
          (
            LocalTeamMember,
            BaseReferences<_$LocalStore, $TeamMembersTable, LocalTeamMember>,
          ),
          LocalTeamMember,
          PrefetchHooks Function()
        > {
  $$TeamMembersTableTableManager(_$LocalStore db, $TeamMembersTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$TeamMembersTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$TeamMembersTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$TeamMembersTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> sam = const Value.absent(),
                Value<String> name = const Value.absent(),
                Value<String> worstState = const Value.absent(),
                Value<String?> reason = const Value.absent(),
                Value<bool> inHand = const Value.absent(),
                Value<DateTime?> nudgedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TeamMembersCompanion(
                sam: sam,
                name: name,
                worstState: worstState,
                reason: reason,
                inHand: inHand,
                nudgedAt: nudgedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String sam,
                required String name,
                required String worstState,
                Value<String?> reason = const Value.absent(),
                required bool inHand,
                Value<DateTime?> nudgedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => TeamMembersCompanion.insert(
                sam: sam,
                name: name,
                worstState: worstState,
                reason: reason,
                inHand: inHand,
                nudgedAt: nudgedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$TeamMembersTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $TeamMembersTable,
      LocalTeamMember,
      $$TeamMembersTableFilterComposer,
      $$TeamMembersTableOrderingComposer,
      $$TeamMembersTableAnnotationComposer,
      $$TeamMembersTableCreateCompanionBuilder,
      $$TeamMembersTableUpdateCompanionBuilder,
      (
        LocalTeamMember,
        BaseReferences<_$LocalStore, $TeamMembersTable, LocalTeamMember>,
      ),
      LocalTeamMember,
      PrefetchHooks Function()
    >;
typedef $$SyncStatesTableCreateCompanionBuilder =
    SyncStatesCompanion Function({
      Value<int> id,
      Value<int> cursor,
      Value<int> referenceCursor,
      Value<String?> serverToday,
      Value<DateTime?> lastSyncedAt,
      Value<String?> standingCcId,
      Value<String?> standingPartnership,
      Value<String?> standingFrom,
      Value<String?> standingTo,
      Value<bool?> standingCurrent,
      Value<String?> standingRollUp,
      Value<String?> standingHeadline,
      Value<int?> standingReady,
      Value<int?> standingTotal,
      Value<bool> supervisor,
      Value<String?> teamCcId,
    });
typedef $$SyncStatesTableUpdateCompanionBuilder =
    SyncStatesCompanion Function({
      Value<int> id,
      Value<int> cursor,
      Value<int> referenceCursor,
      Value<String?> serverToday,
      Value<DateTime?> lastSyncedAt,
      Value<String?> standingCcId,
      Value<String?> standingPartnership,
      Value<String?> standingFrom,
      Value<String?> standingTo,
      Value<bool?> standingCurrent,
      Value<String?> standingRollUp,
      Value<String?> standingHeadline,
      Value<int?> standingReady,
      Value<int?> standingTotal,
      Value<bool> supervisor,
      Value<String?> teamCcId,
    });

class $$SyncStatesTableFilterComposer
    extends Composer<_$LocalStore, $SyncStatesTable> {
  $$SyncStatesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get cursor => $composableBuilder(
    column: $table.cursor,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get referenceCursor => $composableBuilder(
    column: $table.referenceCursor,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get serverToday => $composableBuilder(
    column: $table.serverToday,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get lastSyncedAt => $composableBuilder(
    column: $table.lastSyncedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get standingCcId => $composableBuilder(
    column: $table.standingCcId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get standingPartnership => $composableBuilder(
    column: $table.standingPartnership,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get standingFrom => $composableBuilder(
    column: $table.standingFrom,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get standingTo => $composableBuilder(
    column: $table.standingTo,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get standingCurrent => $composableBuilder(
    column: $table.standingCurrent,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get standingRollUp => $composableBuilder(
    column: $table.standingRollUp,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get standingHeadline => $composableBuilder(
    column: $table.standingHeadline,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get standingReady => $composableBuilder(
    column: $table.standingReady,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get standingTotal => $composableBuilder(
    column: $table.standingTotal,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get supervisor => $composableBuilder(
    column: $table.supervisor,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get teamCcId => $composableBuilder(
    column: $table.teamCcId,
    builder: (column) => ColumnFilters(column),
  );
}

class $$SyncStatesTableOrderingComposer
    extends Composer<_$LocalStore, $SyncStatesTable> {
  $$SyncStatesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<int> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get cursor => $composableBuilder(
    column: $table.cursor,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get referenceCursor => $composableBuilder(
    column: $table.referenceCursor,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get serverToday => $composableBuilder(
    column: $table.serverToday,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get lastSyncedAt => $composableBuilder(
    column: $table.lastSyncedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get standingCcId => $composableBuilder(
    column: $table.standingCcId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get standingPartnership => $composableBuilder(
    column: $table.standingPartnership,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get standingFrom => $composableBuilder(
    column: $table.standingFrom,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get standingTo => $composableBuilder(
    column: $table.standingTo,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get standingCurrent => $composableBuilder(
    column: $table.standingCurrent,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get standingRollUp => $composableBuilder(
    column: $table.standingRollUp,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get standingHeadline => $composableBuilder(
    column: $table.standingHeadline,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get standingReady => $composableBuilder(
    column: $table.standingReady,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get standingTotal => $composableBuilder(
    column: $table.standingTotal,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get supervisor => $composableBuilder(
    column: $table.supervisor,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get teamCcId => $composableBuilder(
    column: $table.teamCcId,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$SyncStatesTableAnnotationComposer
    extends Composer<_$LocalStore, $SyncStatesTable> {
  $$SyncStatesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<int> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<int> get cursor =>
      $composableBuilder(column: $table.cursor, builder: (column) => column);

  GeneratedColumn<int> get referenceCursor => $composableBuilder(
    column: $table.referenceCursor,
    builder: (column) => column,
  );

  GeneratedColumn<String> get serverToday => $composableBuilder(
    column: $table.serverToday,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get lastSyncedAt => $composableBuilder(
    column: $table.lastSyncedAt,
    builder: (column) => column,
  );

  GeneratedColumn<String> get standingCcId => $composableBuilder(
    column: $table.standingCcId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get standingPartnership => $composableBuilder(
    column: $table.standingPartnership,
    builder: (column) => column,
  );

  GeneratedColumn<String> get standingFrom => $composableBuilder(
    column: $table.standingFrom,
    builder: (column) => column,
  );

  GeneratedColumn<String> get standingTo => $composableBuilder(
    column: $table.standingTo,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get standingCurrent => $composableBuilder(
    column: $table.standingCurrent,
    builder: (column) => column,
  );

  GeneratedColumn<String> get standingRollUp => $composableBuilder(
    column: $table.standingRollUp,
    builder: (column) => column,
  );

  GeneratedColumn<String> get standingHeadline => $composableBuilder(
    column: $table.standingHeadline,
    builder: (column) => column,
  );

  GeneratedColumn<int> get standingReady => $composableBuilder(
    column: $table.standingReady,
    builder: (column) => column,
  );

  GeneratedColumn<int> get standingTotal => $composableBuilder(
    column: $table.standingTotal,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get supervisor => $composableBuilder(
    column: $table.supervisor,
    builder: (column) => column,
  );

  GeneratedColumn<String> get teamCcId =>
      $composableBuilder(column: $table.teamCcId, builder: (column) => column);
}

class $$SyncStatesTableTableManager
    extends
        RootTableManager<
          _$LocalStore,
          $SyncStatesTable,
          LocalSyncState,
          $$SyncStatesTableFilterComposer,
          $$SyncStatesTableOrderingComposer,
          $$SyncStatesTableAnnotationComposer,
          $$SyncStatesTableCreateCompanionBuilder,
          $$SyncStatesTableUpdateCompanionBuilder,
          (
            LocalSyncState,
            BaseReferences<_$LocalStore, $SyncStatesTable, LocalSyncState>,
          ),
          LocalSyncState,
          PrefetchHooks Function()
        > {
  $$SyncStatesTableTableManager(_$LocalStore db, $SyncStatesTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$SyncStatesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$SyncStatesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$SyncStatesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int> cursor = const Value.absent(),
                Value<int> referenceCursor = const Value.absent(),
                Value<String?> serverToday = const Value.absent(),
                Value<DateTime?> lastSyncedAt = const Value.absent(),
                Value<String?> standingCcId = const Value.absent(),
                Value<String?> standingPartnership = const Value.absent(),
                Value<String?> standingFrom = const Value.absent(),
                Value<String?> standingTo = const Value.absent(),
                Value<bool?> standingCurrent = const Value.absent(),
                Value<String?> standingRollUp = const Value.absent(),
                Value<String?> standingHeadline = const Value.absent(),
                Value<int?> standingReady = const Value.absent(),
                Value<int?> standingTotal = const Value.absent(),
                Value<bool> supervisor = const Value.absent(),
                Value<String?> teamCcId = const Value.absent(),
              }) => SyncStatesCompanion(
                id: id,
                cursor: cursor,
                referenceCursor: referenceCursor,
                serverToday: serverToday,
                lastSyncedAt: lastSyncedAt,
                standingCcId: standingCcId,
                standingPartnership: standingPartnership,
                standingFrom: standingFrom,
                standingTo: standingTo,
                standingCurrent: standingCurrent,
                standingRollUp: standingRollUp,
                standingHeadline: standingHeadline,
                standingReady: standingReady,
                standingTotal: standingTotal,
                supervisor: supervisor,
                teamCcId: teamCcId,
              ),
          createCompanionCallback:
              ({
                Value<int> id = const Value.absent(),
                Value<int> cursor = const Value.absent(),
                Value<int> referenceCursor = const Value.absent(),
                Value<String?> serverToday = const Value.absent(),
                Value<DateTime?> lastSyncedAt = const Value.absent(),
                Value<String?> standingCcId = const Value.absent(),
                Value<String?> standingPartnership = const Value.absent(),
                Value<String?> standingFrom = const Value.absent(),
                Value<String?> standingTo = const Value.absent(),
                Value<bool?> standingCurrent = const Value.absent(),
                Value<String?> standingRollUp = const Value.absent(),
                Value<String?> standingHeadline = const Value.absent(),
                Value<int?> standingReady = const Value.absent(),
                Value<int?> standingTotal = const Value.absent(),
                Value<bool> supervisor = const Value.absent(),
                Value<String?> teamCcId = const Value.absent(),
              }) => SyncStatesCompanion.insert(
                id: id,
                cursor: cursor,
                referenceCursor: referenceCursor,
                serverToday: serverToday,
                lastSyncedAt: lastSyncedAt,
                standingCcId: standingCcId,
                standingPartnership: standingPartnership,
                standingFrom: standingFrom,
                standingTo: standingTo,
                standingCurrent: standingCurrent,
                standingRollUp: standingRollUp,
                standingHeadline: standingHeadline,
                standingReady: standingReady,
                standingTotal: standingTotal,
                supervisor: supervisor,
                teamCcId: teamCcId,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$SyncStatesTableProcessedTableManager =
    ProcessedTableManager<
      _$LocalStore,
      $SyncStatesTable,
      LocalSyncState,
      $$SyncStatesTableFilterComposer,
      $$SyncStatesTableOrderingComposer,
      $$SyncStatesTableAnnotationComposer,
      $$SyncStatesTableCreateCompanionBuilder,
      $$SyncStatesTableUpdateCompanionBuilder,
      (
        LocalSyncState,
        BaseReferences<_$LocalStore, $SyncStatesTable, LocalSyncState>,
      ),
      LocalSyncState,
      PrefetchHooks Function()
    >;

class $LocalStoreManager {
  final _$LocalStore _db;
  $LocalStoreManager(this._db);
  $$PeopleTableTableManager get people =>
      $$PeopleTableTableManager(_db, _db.people);
  $$HoldingsTableTableManager get holdings =>
      $$HoldingsTableTableManager(_db, _db.holdings);
  $$AssignmentsTableTableManager get assignments =>
      $$AssignmentsTableTableManager(_db, _db.assignments);
  $$LeaveRecordsTableTableManager get leaveRecords =>
      $$LeaveRecordsTableTableManager(_db, _db.leaveRecords);
  $$NotificationsTableTableManager get notifications =>
      $$NotificationsTableTableManager(_db, _db.notifications);
  $$SubmissionsTableTableManager get submissions =>
      $$SubmissionsTableTableManager(_db, _db.submissions);
  $$RequirementsTableTableManager get requirements =>
      $$RequirementsTableTableManager(_db, _db.requirements);
  $$CrewChangesTableTableManager get crewChanges =>
      $$CrewChangesTableTableManager(_db, _db.crewChanges);
  $$StandingCellsTableTableManager get standingCells =>
      $$StandingCellsTableTableManager(_db, _db.standingCells);
  $$OutboxTableTableManager get outbox =>
      $$OutboxTableTableManager(_db, _db.outbox);
  $$CrewIntentsTableTableManager get crewIntents =>
      $$CrewIntentsTableTableManager(_db, _db.crewIntents);
  $$CrewStatementsTableTableManager get crewStatements =>
      $$CrewStatementsTableTableManager(_db, _db.crewStatements);
  $$AttestationsTableTableManager get attestations =>
      $$AttestationsTableTableManager(_db, _db.attestations);
  $$CourseOptionsTableTableManager get courseOptions =>
      $$CourseOptionsTableTableManager(_db, _db.courseOptions);
  $$TeamMembersTableTableManager get teamMembers =>
      $$TeamMembersTableTableManager(_db, _db.teamMembers);
  $$SyncStatesTableTableManager get syncStates =>
      $$SyncStatesTableTableManager(_db, _db.syncStates);
}
