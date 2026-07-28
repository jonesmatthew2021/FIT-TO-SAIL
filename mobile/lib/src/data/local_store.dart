import 'package:drift/drift.dart';

part 'local_store.g.dart';

/// The on-device replica of the crew member's scoped dataset (MOB-5).
///
/// The tables mirror the sync payload one-for-one, on purpose: applying a delta is an upsert per
/// changed row and a delete per tombstone, with no translation layer in between to get wrong.
/// Server ids are the primary keys, because the server is authoritative for every row here —
/// the app originates only evidence submissions, read-marks and one-tap statements, and all three
/// go through [Outbox].
///
/// Two things this schema deliberately does *not* do:
///
///  * It stores no computed compliance state of its own. [StandingCells] holds what the server
///    evaluated, verbatim (AUTH-1).
///  * It stores dates as `YYYY-MM-DD` text, never as a `DateTime`. See `domain/calendar.dart`.
@DataClassName('LocalPerson')
class People extends Table {
  IntColumn get id => integer()();
  TextColumn get sam => text()();
  TextColumn get name => text()();
  IntColumn get positionId => integer()();
  TextColumn get positionName => text()();
  TextColumn get tier => text().nullable()();
  TextColumn get partnershipAbbrev => text()();
  TextColumn get status => text()();
  TextColumn get email => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

@DataClassName('LocalHolding')
class Holdings extends Table {
  IntColumn get id => integer()();
  IntColumn get requirementId => integer()();
  TextColumn get status => text()();
  TextColumn get expiry => text().nullable()();
  TextColumn get issueDate => text().nullable()();
  TextColumn get note => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

@DataClassName('LocalAssignment')
class Assignments extends Table {
  IntColumn get id => integer()();
  IntColumn get crewChangeId => integer()();
  TextColumn get ccId => text()();
  TextColumn get partnershipAbbrev => text()();
  IntColumn get slotRef => integer()();
  TextColumn get fromDate => text()();
  TextColumn get toDate => text()();

  @override
  Set<Column> get primaryKey => {id};
}

@DataClassName('LocalLeave')
class LeaveRecords extends Table {
  IntColumn get id => integer()();
  TextColumn get kind => text()();
  TextColumn get fromDate => text()();
  TextColumn get toDate => text()();
  TextColumn get status => text()();

  @override
  Set<Column> get primaryKey => {id};
}

@DataClassName('LocalNotification')
class Notifications extends Table {
  IntColumn get id => integer()();
  TextColumn get kind => text()();
  TextColumn get title => text()();
  TextColumn get body => text().nullable()();
  TextColumn get deepLink => text().nullable()();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get readAt => dateTime().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

@DataClassName('LocalSubmission')
class Submissions extends Table {
  /// The device-generated id: primary key here as well as on the server, because the device
  /// minted it before the server ever saw the submission (§7.6).
  TextColumn get publicId => text()();
  IntColumn get requirementHintId => integer().nullable()();
  TextColumn get source => text()();
  TextColumn get contentType => text().nullable()();
  IntColumn get declaredSize => integer().nullable()();
  IntColumn get uploadOffset => integer().withDefault(const Constant(0))();
  BoolColumn get uploadComplete => boolean().withDefault(const Constant(false))();
  TextColumn get verificationStatus => text()();
  TextColumn get rejectionReason => text().nullable()();
  DateTimeColumn get submittedAt => dateTime()();

  /// Local path of the captured file, if it is still on the device awaiting upload.
  TextColumn get localPath => text().nullable()();

  @override
  Set<Column> get primaryKey => {publicId};
}

@DataClassName('LocalRequirement')
class Requirements extends Table {
  IntColumn get id => integer()();
  TextColumn get code => text()();
  TextColumn get category => text()();
  TextColumn get title => text()();
  TextColumn get status => text()();
  TextColumn get issuingAuthority => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

@DataClassName('LocalCrewChange')
class CrewChanges extends Table {
  IntColumn get id => integer()();
  TextColumn get ccId => text()();
  TextColumn get fromDate => text()();
  TextColumn get toDate => text()();
  TextColumn get cutoff => text()();

  @override
  Set<Column> get primaryKey => {id};
}

/// The server's §5.2 evaluation, stored as received.
///
/// Replaced wholesale on every sync rather than merged: a partial roll-up assembled from two
/// evaluations would be a compliance answer nobody computed.
@DataClassName('LocalStandingCell')
class StandingCells extends Table {
  IntColumn get requirementId => integer()();
  TextColumn get level => text()();
  TextColumn get state => text()();
  TextColumn get expiry => text().nullable()();
  TextColumn get notes => text().nullable()();
  TextColumn get registerRecordId => text().nullable()();

  @override
  Set<Column> get primaryKey => {requirementId};
}

/// The durable outbound queue (§7.6).
///
/// Rows survive app restarts, carry their own retry state, and are only ever deleted once the
/// server has reported `applied` or `rejected` for them. A `rejected` entry is dropped rather
/// than retried: it will never succeed, and retrying it forever is how a queue wedges.
@DataClassName('OutboxEntry')
class Outbox extends Table {
  /// Client-generated, and the idempotency key the server echoes back.
  TextColumn get opId => text()();
  TextColumn get type => text()();

  /// The operation's JSON payload, ready to post.
  TextColumn get payload => text()();
  DateTimeColumn get queuedAt => dateTime()();
  IntColumn get attempts => integer().withDefault(const Constant(0))();
  DateTimeColumn get nextAttemptAt => dateTime().nullable()();

  /// Set when the server refused it permanently — kept, briefly, so the UI can explain why.
  TextColumn get lastError => text().nullable()();

  @override
  Set<Column> get primaryKey => {opId};
}

/// The crew member's one-tap answers, and what became of each (MOB-0, MOB-5, MOB-8, MOB-9,
/// MOB-10, MOB-11 — the *design handoff's* numbering).
///
/// An intent is the device's record of something the crew member *said*: the course is booked,
/// they need help arranging it, they want that seat, they are asking for an exemption. Every one
/// rides the [Outbox] like an evidence submission does, and this table is the half the screens
/// read — the outbox holds the payload in flight, this holds what to draw on the row.
///
/// It exists because of one rule: a one-tap answer updates the row immediately, queues when
/// offline, and **shows a retry affordance rather than reverting silently** if it fails. The
/// outbox alone cannot do that. A rejected entry is deleted from it on purpose — a poison entry
/// retried forever is how a queue wedges — and if that were the only record, the crew member's
/// tap would disappear without a word and the row would revert on the next snapshot, which is
/// the one failure mode the one-tap design explicitly rules out. Here the verdict lands on the
/// intent instead, and stays.
///
/// Not server-owned, so `_applySnapshot` does not clear it.
@DataClassName('LocalCrewIntent')
class CrewIntents extends Table {
  /// Shared with the outbox entry that carries it, which is also the server's idempotency key.
  TextColumn get opId => text()();

  /// `requirement.progress`, `requirement.help`, `evidence.reading`, `course.seat_request`,
  /// `course.waitlist`, `register.exemption_request`, `attestation.sign_off`, `team.nudge`.
  TextColumn get kind => text()();

  /// What it is about, where that is a requirement. Lets a detail screen find its own intents.
  IntColumn get requirementId => integer().nullable()();

  /// The non-requirement subject: a course option id, a crew change id, a colleague's Sam #.
  TextColumn get subjectRef => text().nullable()();

  /// One line, already written, for the row that reports it. Composed at queue time because the
  /// screen that shows it may not be the screen that raised it.
  TextColumn get summary => text()();

  /// The operation's JSON body, kept here as well as on the outbox entry.
  ///
  /// Duplication with a purpose: a server rejection deletes the outbox entry, and without a copy
  /// the "Retry" the failed row offers would have nothing to send. Retrying re-posts under the
  /// *same* `opId`, so a request the server actually applied before losing the connection cannot
  /// be applied twice.
  TextColumn get payload => text()();

  DateTimeColumn get queuedAt => dateTime()();

  /// `queued` · `sent` · `failed`. Nothing else, and no state that means "probably".
  TextColumn get state => text().withDefault(const Constant('queued'))();

  /// Why it failed, in the server's words where there are any.
  TextColumn get detail => text().nullable()();

  @override
  Set<Column> get primaryKey => {opId};
}

/// Single-row sync bookkeeping. `id` is pinned to 0.
@DataClassName('LocalSyncState')
class SyncStates extends Table {
  IntColumn get id => integer().withDefault(const Constant(0))();
  IntColumn get cursor => integer().withDefault(const Constant(0))();
  IntColumn get referenceCursor => integer().withDefault(const Constant(0))();

  /// The server's business date as of the last sync — the app's only source of "today".
  TextColumn get serverToday => text().nullable()();
  DateTimeColumn get lastSyncedAt => dateTime().nullable()();

  /// Denormalised from the standing payload so the certifications screen can name the swing.
  TextColumn get standingCcId => text().nullable()();
  TextColumn get standingPartnership => text().nullable()();
  TextColumn get standingFrom => text().nullable()();
  TextColumn get standingTo => text().nullable()();
  BoolColumn get standingCurrent => boolean().nullable()();
  TextColumn get standingRollUp => text().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

@DriftDatabase(
  tables: [
    People,
    Holdings,
    Assignments,
    LeaveRecords,
    Notifications,
    Submissions,
    Requirements,
    CrewChanges,
    StandingCells,
    Outbox,
    CrewIntents,
    SyncStates,
  ],
)
class LocalStore extends _$LocalStore {
  LocalStore(super.executor);

  @override
  int get schemaVersion => 2;

  /// v1 → v2 adds [CrewIntents].
  ///
  /// Additive, and it has to be: an upgrade that dropped and re-created the database would take
  /// the outbox with it, and the outbox is the only copy of writes the server has never seen. A
  /// crew member who queued an evidence submission in a dead spot and then took an app update
  /// would lose it, with nothing anywhere saying so. Everything server-owned in here is a replica
  /// and could be rebuilt from a snapshot; the two device-owned tables cannot.
  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (m) => m.createAll(),
        onUpgrade: (m, from, to) async {
          if (from < 2) await m.createTable(crewIntents);
        },
      );

  /// Everything the crew member's device holds, gone.
  ///
  /// SEC-12 requires the local store to be wiped on logout and to be remotely revocable. This is
  /// the wipe; deleting the database file and its key is the stronger form and is what
  /// `DatabaseKeyStore.forget` plus file deletion does. Both exist because the first is instant
  /// and the second is thorough.
  Future<void> wipe() async {
    await transaction(() async {
      for (final table in allTables) {
        await delete(table).go();
      }
    });
  }
}
