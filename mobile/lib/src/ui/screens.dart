/// The crew app's root screens: MOB-0 home, MOB-1 certifications, MOB-3 roster, MOB-4 alerts.
///
/// All four read the local store and nothing else. There is no loading spinner waiting on a
/// network call anywhere in this file, because there is no network call anywhere in this file —
/// that is what offline-first means in practice, and it is why the app is usable in a dead spot.
///
/// Each screen is a `*Screen` wrapper that owns the streams and a `*View` that is a pure function
/// of its data. The split is not ceremony: a presentational widget that takes rows can be tested
/// by handing it rows, whereas one that owns a drift stream drags the database's timers into the
/// widget-test fake-async zone and hangs there.
library;

import 'package:flutter/material.dart';
import 'package:phosphor_icons/phosphor_icons.dart';

import '../data/local_store.dart';
import '../domain/calendar.dart';
import '../domain/intents.dart';
import '../domain/offers.dart';
import '../domain/states.dart';
import '../domain/urgency.dart';
import 'app_state.dart';
import 'detail_screens.dart';
import 'evidence_screens.dart';
import 'nocturne.dart';
import 'team_screen.dart';
import 'widgets.dart';

class CrewHome extends StatefulWidget {
  const CrewHome({super.key, required this.state});

  final AppState state;

  @override
  State<CrewHome> createState() => _CrewHomeState();
}

class _CrewHomeState extends State<CrewHome> {
  int _tab = 0;

  @override
  void initState() {
    super.initState();
    // Fire-and-forget: the screens already have whatever the last sync left behind.
    widget.state.sync();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: widget.state,
      builder: (context, _) {
        if (widget.state.locked) return _LockedScreen(state: widget.state);

        // MOB-11: a supervisor's second tab is their watch, not their own certificates. Their own
        // are still one tap away from Home, which is where the ring already summarises them.
        final supervising = widget.state.supervisor;
        final screens = [
          HomeScreen(state: widget.state, onTab: (index) => setState(() => _tab = index)),
          if (supervising)
            TeamScreen(state: widget.state)
          else
            CertificationsScreen(state: widget.state),
          RosterScreen(state: widget.state),
          NotificationsScreen(
            state: widget.state,
            onOpenTab: (index) => setState(() => _tab = index),
          ),
        ];

        return Scaffold(
          backgroundColor: Nocturne.bg,
          body: SafeArea(bottom: false, child: screens[_tab]),
          bottomNavigationBar: CrewTabBar(
            index: _tab,
            supervising: supervising,
            unread: widget.state.watchUnreadCount(),
            onSelected: (index) => setState(() => _tab = index),
          ),
        );
      },
    );
  }
}

/// The four-tab bar: a filled glyph in a 48×28 accent pill for the active tab, regular weight and
/// `neutral-500` for the rest.
class CrewTabBar extends StatelessWidget {
  const CrewTabBar({
    super.key,
    required this.index,
    required this.onSelected,
    this.supervising = false,
    this.unread,
  });

  final int index;
  final ValueChanged<int> onSelected;
  final bool supervising;
  final Stream<int>? unread;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        color: Nocturne.bg,
        border: Border(top: BorderSide(color: Nocturne.neutral900)),
      ),
      padding: const EdgeInsets.fromLTRB(10, 8, 10, 0),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Row(
            children: [
              _tab(0, 'Home', PhosphorIconsRegular.house, PhosphorIconsFill.house),
              if (supervising)
                _tab(1, 'Team', PhosphorIconsRegular.usersThree, PhosphorIconsFill.usersThree)
              else
                _tab(
                  1,
                  'Certifications',
                  PhosphorIconsRegular.sealCheck,
                  PhosphorIconsFill.sealCheck,
                ),
              _tab(2, 'Roster', PhosphorIconsRegular.calendarDots, PhosphorIconsFill.calendarDots),
              _tab(3, 'Alerts', PhosphorIconsRegular.bell, PhosphorIconsFill.bell, badge: true),
            ],
          ),
        ),
      ),
    );
  }

  Widget _tab(int at, String label, IconData icon, IconData active, {bool badge = false}) {
    final selected = index == at;
    final glyph = Icon(
      selected ? active : icon,
      size: 19,
      color: selected ? Nocturne.accent : Nocturne.neutral500,
    );

    return Expanded(
      child: Semantics(
        selected: selected,
        button: true,
        label: label,
        excludeSemantics: true,
        child: Pressable(
          onTap: () => onSelected(at),
          borderRadius: BorderRadius.circular(14),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 44),
            child: Column(
              // `min`, and it matters more than it looks. A `Scaffold` hands its
              // `bottomNavigationBar` loose constraints up to the full screen height, so a Column
              // left at `MainAxisSize.max` grows to fill all of it: the tab bar becomes the whole
              // screen, the body is squeezed to nothing, and the symptom is a blank app with its
              // tabs floating in the middle — with no exception anywhere to say so.
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Container(
                  width: 48,
                  height: 28,
                  alignment: Alignment.center,
                  decoration: selected
                      ? BoxDecoration(
                          color: Nocturne.accent900,
                          borderRadius: BorderRadius.circular(14),
                        )
                      : null,
                  child: badge && unread != null
                      ? _UnreadBadge(unread: unread!, child: glyph)
                      : glyph,
                ),
                const SizedBox(height: 4),
                Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: NoctType.tabLabel.copyWith(
                    color: selected ? Nocturne.text : Nocturne.neutral500,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _UnreadBadge extends StatelessWidget {
  const _UnreadBadge({required this.unread, required this.child});

  final Stream<int> unread;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<int>(
      stream: unread,
      builder: (context, snapshot) {
        final count = snapshot.data ?? 0;
        if (count == 0) return child;
        return Badge(
          backgroundColor: Nocturne.accent,
          textColor: Nocturne.bg,
          label: Text('$count'),
          child: child,
        );
      },
    );
  }
}

// ---------------------------------------------------------------------------
// Header furniture
// ---------------------------------------------------------------------------

/// A root screen's header: the person's name, centred, with the sync action beside it.
class CrewHeader extends StatelessWidget {
  const CrewHeader({super.key, required this.name, required this.syncing, this.onSync});

  final String name;
  final bool syncing;
  final VoidCallback? onSync;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 14, Nocturne.gutter, 4),
      child: Row(
        children: [
          Expanded(
            child: Padding(
              // Balances the 34px action so the name is optically centred.
              padding: const EdgeInsets.only(left: 26),
              child: Text(
                name,
                textAlign: TextAlign.center,
                overflow: TextOverflow.ellipsis,
                style: NoctType.screenName.copyWith(fontSize: 18, letterSpacing: -0.18),
              ),
            ),
          ),
          SyncButton(syncing: syncing, onSync: onSync),
        ],
      ),
    );
  }
}

class SyncButton extends StatelessWidget {
  const SyncButton({super.key, required this.syncing, this.onSync});

  final bool syncing;
  final VoidCallback? onSync;

  @override
  Widget build(BuildContext context) {
    if (syncing) {
      return const SizedBox(
        width: 34,
        height: 34,
        child: Center(
          child: SizedBox(
            width: 16,
            height: 16,
            child: CircularProgressIndicator(strokeWidth: 2, color: Nocturne.accent),
          ),
        ),
      );
    }
    return NIconButton(
      icon: PhosphorIconsRegular.arrowsClockwise,
      semanticLabel: 'Sync now',
      onPressed: onSync,
    );
  }
}

/// §7.6's staleness line: the app always says when it last heard from the server.
///
/// Not decoration, and not removable. Every number on these screens is as old as this line says
/// it is, and a crew member deciding whether their ticket is current deserves to know they are
/// reading a three-week-old answer. When the last attempt failed it keeps the data and offers a
/// retry inline rather than replacing the screen with an error.
class SyncLine extends StatelessWidget {
  const SyncLine({
    super.key,
    required this.syncing,
    required this.lastSyncedAt,
    required this.error,
    this.onRetry,
    this.padding = const EdgeInsets.fromLTRB(Nocturne.gutter, 0, Nocturne.gutter, 12),
  });

  final bool syncing;
  final DateTime? lastSyncedAt;
  final String? error;
  final VoidCallback? onRetry;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    final last = lastSyncedAt;

    final String message;
    Color colour = Nocturne.neutral600;
    var retry = false;
    if (syncing) {
      message = 'Syncing…';
    } else if (last == null) {
      message = 'Never synced — showing nothing yet';
      colour = Nocturne.warningText;
      retry = true;
    } else if (error != null) {
      message = "Couldn't sync · last synced ${ago(last)}";
      colour = Nocturne.warningText;
      retry = true;
    } else {
      message = 'Last synced ${ago(last)}';
    }

    return Padding(
      padding: padding,
      child: Row(
        children: [
          Flexible(
            child: Text(message, style: NoctType.meta.copyWith(color: colour)),
          ),
          if (retry && onRetry != null) ...[
            Text(' · ', style: NoctType.meta.copyWith(color: colour)),
            Pressable(
              onTap: onRetry,
              borderRadius: BorderRadius.circular(Nocturne.radiusSm),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 4),
                child: Text('Retry', style: NoctType.meta.copyWith(color: Nocturne.accent300)),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// Elapsed wall-clock time, which is a different thing from a business date and is allowed to
/// come from the device: it answers "how stale is this?", not "what day is it?".
String ago(DateTime when) {
  final delta = DateTime.now().toUtc().difference(when);
  if (delta.inMinutes < 1) return 'just now';
  if (delta.inMinutes < 60) return '${delta.inMinutes} min ago';
  if (delta.inHours < 24) return '${delta.inHours} h ago';
  if (delta.inDays == 1) return '1 day ago';
  return '${delta.inDays} days ago';
}

/// SEC-12: past the offline-validity window the cached data stops being shown at all.
class _LockedScreen extends StatelessWidget {
  const _LockedScreen({required this.state});

  final AppState state;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Nocturne.bg,
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(PhosphorIconsRegular.lockKey, size: 44, color: Nocturne.neutral600),
              const SizedBox(height: 16),
              Text('Offline too long', style: NoctType.cardTitle),
              const SizedBox(height: 8),
              const Text(
                'This device has not reached CREWCOMP in over 30 days, so the cached copy of '
                'your records has been locked. Connect to sign in again.',
                textAlign: TextAlign.center,
                style: NoctType.cardBody,
              ),
              const SizedBox(height: 24),
              NButton(label: 'Try now', variant: NButtonVariant.primary, onPressed: state.sync),
            ],
          ),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// MOB-0 — Home
// ---------------------------------------------------------------------------

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key, required this.state, required this.onTab});

  final AppState state;

  /// Home's rows lead somewhere; where a destination is another root tab, it switches rather
  /// than pushing a second copy of a screen the tab bar already owns.
  final ValueChanged<int> onTab;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<List<CertificationRow>>(
      stream: state.watchCertifications(),
      builder: (context, rowSnapshot) {
        // Both halves of the one-tap record: what this device sent, and what the office holds.
        // Nested rather than combined into one stream so the merge stays a pure function the view
        // tests can call directly — the `*Screen` / `*View` split this file keeps to throughout.
        return StreamBuilder<List<LocalCrewIntent>>(
          stream: state.watchIntents(),
          builder: (context, intentSnapshot) => StreamBuilder<List<LocalCrewStatement>>(
            stream: state.watchStatements(),
            builder: (context, statementSnapshot) => HomeView(
              rows: rowSnapshot.data,
              answers: answersFrom(
                intentSnapshot.data ?? const <LocalCrewIntent>[],
                statementSnapshot.data ?? const <LocalCrewStatement>[],
              ),
              person: state.person,
              sync: state.syncState,
              credits: state.credits,
              today: state.serverToday,
              syncing: state.syncing,
              error: state.lastError,
              onSync: state.syncing ? null : state.sync,
              onOpenRequirement: (row) => openRequirement(context, state, row.cell.requirementId),
              onSendCertificate: (row) => showSendCertificateSheet(
                context: context,
                state: state,
                requirementId: row.cell.requirementId,
                requirementLabel: '${row.code} ${row.title}',
              ),
              // Confirmed first (issue #16): the statement silences the crew member's own
              // reminders on their word alone, and this button sits 8px from "I have it".
              onCourseBooked: (row) async {
                final confirmed = await confirmCourseBooked(
                  context: context,
                  requirementTitle: row.title,
                );
                if (!confirmed) return;
                await state.answer(
                  kind: IntentKind.courseBooked,
                  summary: 'Course booked for ${row.title}',
                  requirementId: row.cell.requirementId,
                );
              },
              onAsk: (row) => state.answer(
                kind: IntentKind.helpNeeded,
                summary: 'Asked for help with ${row.title}',
                requirementId: row.cell.requirementId,
              ),
            ),
          ),
        );
      },
    );
  }
}

class HomeView extends StatelessWidget {
  const HomeView({
    super.key,
    required this.rows,
    required this.answers,
    required this.person,
    required this.sync,
    required this.today,
    this.credits,
    this.syncing = false,
    this.error,
    this.onSync,
    this.onOpenRequirement,
    this.onSendCertificate,
    this.onCourseBooked,
    this.onAsk,
  });

  final List<CertificationRow>? rows;
  final List<Answer> answers;
  final LocalPerson? person;
  final LocalSyncState? sync;
  final String? today;
  final Credits? credits;
  final bool syncing;
  final String? error;
  final VoidCallback? onSync;
  final void Function(CertificationRow row)? onOpenRequirement;
  final void Function(CertificationRow row)? onSendCertificate;
  final void Function(CertificationRow row)? onCourseBooked;
  final void Function(CertificationRow row)? onAsk;

  @override
  Widget build(BuildContext context) {
    final rows = this.rows;
    if (rows == null) return const SizedBox.shrink();

    final sync = this.sync;
    final today = this.today;
    final swingTo = sync?.standingTo;
    // The engine's own count wherever the payload carries it; the device's mirror only for the
    // one sync-less window after an app upgrade (issue #12).
    final readiness = sync?.standingReady != null && sync?.standingTotal != null
        ? Readiness(ready: sync!.standingReady!, total: sync.standingTotal!)
        : readinessFrom(rows);

    // Worst first, and within a state by how close the deadline is — the same order §5.4 puts a
    // gap report in, refined by the one thing the device is allowed to count.
    final asks = rows.where((row) => needsAttention(row.cell.state)).toList()
      ..sort((a, b) {
        final byState = cellStateRank(a.cell.state).compareTo(cellStateRank(b.cell.state));
        if (byState != 0) return byState;
        final aExpiry = a.expiry;
        final bExpiry = b.expiry;
        if (aExpiry != null && bExpiry != null) return aExpiry.compareTo(bExpiry);
        return a.code.compareTo(b.code);
      });

    // Recommended and quota-only: worth doing, nothing overdue about them. The design keeps them
    // visible but visually quiet, below the things with a date on them.
    final soft =
        rows
            .where((row) => row.cell.state == 'recommended' || row.cell.state == 'quota_only')
            .toList()
          ..sort((a, b) => a.code.compareTo(b.code));

    return ListView(
      padding: const EdgeInsets.only(bottom: 28),
      children: [
        _HomeHeader(person: person, sync: sync, syncing: syncing, onSync: onSync),

        // On Home the staleness line only appears when it is telling the crew member something
        // they need: that the last attempt failed, or that nothing has ever arrived. The mock has
        // no line here, and a permanent "synced 2 min ago" under a readiness ring reads as
        // reassurance about the wrong thing.
        if (error != null || sync?.lastSyncedAt == null)
          SyncLine(
            syncing: syncing,
            lastSyncedAt: sync?.lastSyncedAt,
            error: error,
            onRetry: onSync,
            padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 0, Nocturne.gutter, 10),
          ),

        Padding(
          padding: const EdgeInsets.symmetric(horizontal: Nocturne.gutter),
          child: NCard(
            elevated: true,
            padding: const EdgeInsets.all(18),
            child: Row(
              children: [
                ReadinessRing(ready: readiness.ready, total: readiness.total),
                const SizedBox(width: 18),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        _headline(sync),
                        style: NoctType.cardTitle.copyWith(height: 1.25),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        _subhead(asks, today: today, swingTo: swingTo, readiness: readiness),
                        style: NoctType.cardBody,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),

        if (credits != null) ...[
          const SizedBox(height: 12),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: Nocturne.gutter),
            child: _CreditTiles(credits: credits!),
          ),
        ],

        if (asks.isEmpty && soft.isEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 22, Nocturne.gutter, 0),
            child: Text(_closing(swingTo), style: NoctType.meta),
          )
        else ...[
          SectionLabel(
            'Asked of you',
            trailing: _count(asks.length + soft.length),
            padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 22, Nocturne.gutter, 8),
          ),
          for (final (index, row) in asks.indexed) ...[
            if (index > 0) const SizedBox(height: 10),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: Nocturne.gutter),
              child: _AskCard(
                row: row,
                today: today,
                swingTo: swingTo,
                answers: answers.where((a) => a.requirementId == row.cell.requirementId).toList(),
                onOpen: onOpenRequirement == null ? null : () => onOpenRequirement!(row),
                onHaveIt: onSendCertificate == null ? null : () => onSendCertificate!(row),
                onCourseBooked: onCourseBooked == null ? null : () => onCourseBooked!(row),
              ),
            ),
          ],
          for (final row in soft) ...[
            const SizedBox(height: 10),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: Nocturne.gutter),
              child: _SoftCard(
                row: row,
                // `stands` and not merely "exists": an answer the office dismissed puts the
                // ask back, exactly as a dismissal restarts the server's own expiry chasing.
                asked: answers.any(
                  (a) =>
                      a.requirementId == row.cell.requirementId &&
                      a.kind == IntentKind.helpNeeded &&
                      a.stands,
                ),
                onOpen: onOpenRequirement == null ? null : () => onOpenRequirement!(row),
                onAsk: onAsk == null ? null : () => onAsk!(row),
              ),
            ),
          ],
          Padding(
            padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 16, Nocturne.gutter, 0),
            child: Text(_closing(swingTo), style: NoctType.meta),
          ),
        ],
      ],
    );
  }

  static String _count(int items) => items == 1 ? '1 thing' : '$items things';

  /// The one sentence at the top of the screen — the **server's**, rendered as received.
  ///
  /// The device used to map it from the roll-up itself, and that mapping was where the app told
  /// its one lie: `pending` and `expiring` both read "You can sail this swing." (issue #12).
  /// A compliance sentence is the engine's to phrase; the only lines composed here are the two
  /// non-verdicts — no swing to check, and a replica that predates the field.
  static String _headline(LocalSyncState? sync) {
    if (sync?.standingCcId == null) return 'No swing to check against.';
    return sync?.standingHeadline ?? 'Sync to update your standing.';
  }

  static String _subhead(
    List<CertificationRow> asks, {
    required String? today,
    required String? swingTo,
    required Readiness readiness,
  }) {
    if (asks.isEmpty) {
      if (readiness.total == 0) return 'Nothing is required of you against this swing yet.';
      // Not everything settled is *granted*: a pending exemption leaves the ask list empty while
      // the office still holds the decision, and saying "accepted and current" over that was the
      // before/after contradiction the review called out (issue #12).
      final waiting = readiness.total - readiness.ready - asks.length;
      if (waiting > 0) {
        return waiting == 1
            ? 'One request is with the office. Everything else is accepted and current.'
            : '$waiting requests are with the office. Everything else is accepted and current.';
      }
      return 'Everything asked of you is accepted and current.';
    }

    final lapsing = asks
        .where(
          (row) => row.expiry != null && swingTo != null && daysBetween(row.expiry!, swingTo) >= 0,
        )
        .length;
    final rest = asks.length - lapsing;

    final parts = <String>[
      if (lapsing == 1) 'One certificate lapses before ${formatDate(swingTo!)}',
      if (lapsing > 1) '$lapsing certificates lapse before ${formatDate(swingTo!)}',
      if (rest == 1 && lapsing == 0) 'One thing is outstanding',
      if (rest >= 1 && lapsing > 0)
        '$rest other ${rest == 1 ? 'thing needs' : 'things need'} an answer',
      if (rest > 1 && lapsing == 0) '$rest things are outstanding',
    ];
    if (readiness.ready > 0) {
      parts.add('Everything else is accepted and current');
    }
    return '${parts.join('. ')}.';
  }

  static String _closing(String? swingTo) {
    final until = swingTo == null ? '' : ' before ${formatDate(swingTo)}';
    return 'Nothing else is asked of you$until. '
        'The office sees each answer the moment you tap it.';
  }
}

class _HomeHeader extends StatelessWidget {
  const _HomeHeader({required this.person, required this.sync, required this.syncing, this.onSync});

  final LocalPerson? person;
  final LocalSyncState? sync;
  final bool syncing;
  final VoidCallback? onSync;

  @override
  Widget build(BuildContext context) {
    final kicker = [
      if (person?.positionName != null) person!.positionName.toUpperCase(),
      if (sync?.standingCcId != null)
        '${sync!.standingPartnership ?? ''} ${sync!.standingCcId}'.trim().toUpperCase(),
    ].join(' · ');

    return Padding(
      padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 16, Nocturne.gutter, 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (kicker.isNotEmpty) Text(kicker, style: NoctType.kicker),
                const SizedBox(height: 3),
                Text(person?.name ?? 'CREWCOMP', style: NoctType.screenName),
              ],
            ),
          ),
          const SizedBox(width: 10),
          SyncButton(syncing: syncing, onSync: onSync),
        ],
      ),
    );
  }
}

/// The motivational half of Home: what this person has done *well*, stated as fact.
class _CreditTiles extends StatelessWidget {
  const _CreditTiles({required this.credits});

  final Credits credits;

  @override
  Widget build(BuildContext context) {
    // IntrinsicHeight, not `CrossAxisAlignment.stretch`: the tiles must be the same height as
    // each other, and stretch inside a scroll view asks the Row to be as tall as infinity.
    return IntrinsicHeight(
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Expanded(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(
                color: Nocturne.accent900,
                border: Border.all(color: Nocturne.accent800),
                borderRadius: Nocturne.borderMd,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    credits.streakLabel,
                    style: NoctType.cardTitleSm.copyWith(color: Nocturne.accent200),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    credits.streakCaption,
                    style: NoctType.listSecondary.copyWith(
                      fontSize: 11,
                      color: Nocturne.neutral400,
                    ),
                  ),
                ],
              ),
            ),
          ),
          if (credits.secondaryLabel != null) ...[
            const SizedBox(width: 8),
            Expanded(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                decoration: const BoxDecoration(
                  color: Nocturne.surface,
                  borderRadius: Nocturne.borderMd,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(credits.secondaryLabel!, style: NoctType.cardTitleSm),
                    const SizedBox(height: 2),
                    Text(
                      credits.secondaryCaption ?? '',
                      style: NoctType.listSecondary.copyWith(fontSize: 11),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// One thing asked of the crew member, with its urgency and the two answers that resolve it.
class _AskCard extends StatelessWidget {
  const _AskCard({
    required this.row,
    required this.today,
    required this.swingTo,
    required this.answers,
    this.onOpen,
    this.onHaveIt,
    this.onCourseBooked,
  });

  final CertificationRow row;
  final String? today;
  final String? swingTo;
  final List<Answer> answers;
  final VoidCallback? onOpen;
  final VoidCallback? onHaveIt;
  final VoidCallback? onCourseBooked;

  @override
  Widget build(BuildContext context) {
    final expiry = row.expiry;
    final urgency = urgencyFor(
      state: row.cell.state,
      expiry: expiry,
      today: today,
      swingTo: swingTo,
    );
    final mark = urgencyMark(urgency);
    // Two different questions, and conflating them was a bug: *is there an answer to report* —
    // yes, even a dismissed one, because that is the thing the crew member most needs to read —
    // and *is the ask still outstanding*, which a dismissal makes true again. Keeping them apart
    // is what lets the card say "the office couldn't act on this" and offer the button in the
    // same breath.
    final booked = answers.where((a) => a.kind == IntentKind.courseBooked).firstOrNull;
    final stillBooked = booked?.stands ?? false;

    return NCard(
      leftMark: mark,
      onTap: onOpen,
      padding: const EdgeInsets.fromLTRB(13, 14, 15, 14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (expiry != null && today != null)
            UrgencyBar(
              label: countdownLabel(today!, expiry),
              fraction: leadElapsed(daysBetween(today!, expiry)),
              colour: mark,
            )
          else
            Row(
              children: [
                NTag(label: cellStateLabel(row.cell.state), tone: cellStateTone(row.cell.state)),
              ],
            ),
          const SizedBox(height: 8),
          Text(askTitle(row.cell.state, row.title), style: NoctType.cardTitle),
          const SizedBox(height: 3),
          Text(_body(), style: NoctType.cardBody),
          if (booked != null) ...[const SizedBox(height: 8), AnswerLine(answer: booked)],
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(
                child: NButton(
                  label: 'I have it',
                  icon: PhosphorIconsRegular.certificate,
                  variant: NButtonVariant.primary,
                  fontSize: 13,
                  onPressed: onHaveIt,
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: NButton(
                  label: stillBooked ? 'Told them' : 'Course booked',
                  icon: PhosphorIconsRegular.calendarCheck,
                  fontSize: 13,
                  onPressed: stillBooked ? null : onCourseBooked,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  String _body() {
    final expiry = row.expiry;
    if (expiry == null) {
      return '${row.code} · ${cellStateLabel(row.cell.state)}. Tap for what is required.';
    }

    final swingTo = this.swingTo;
    if (swingTo != null) {
      final margin = daysBetween(expiry, swingTo);
      if (margin >= 0) {
        return 'Expires ${formatDate(expiry)}, '
            '${margin == 0 ? 'the day' : '$margin ${margin == 1 ? 'day' : 'days'}'} '
            'before your swing ends.';
      }
    }
    return 'Expires ${formatDate(expiry)}.';
  }
}

/// A recommendation, with no deadline attached to it and nothing shouting.
class _SoftCard extends StatelessWidget {
  const _SoftCard({required this.row, required this.asked, this.onOpen, this.onAsk});

  final CertificationRow row;
  final bool asked;
  final VoidCallback? onOpen;
  final VoidCallback? onAsk;

  @override
  Widget build(BuildContext context) {
    final reason = row.cell.state == 'quota_only'
        ? 'Counts toward a quota for your position · no deadline'
        : 'Recommended for your position · no deadline';

    return NCard(
      onTap: onOpen,
      padding: const EdgeInsets.fromLTRB(15, 13, 15, 13),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(row.title, style: NoctType.listPrimarySm),
                const SizedBox(height: 2),
                Text(reason, style: NoctType.listSecondary),
              ],
            ),
          ),
          const SizedBox(width: 12),
          NButton(
            label: asked ? 'Asked' : 'Ask',
            variant: NButtonVariant.ghost,
            fontSize: 12.5,
            onPressed: asked ? null : onAsk,
          ),
        ],
      ),
    );
  }
}

/// What became of a one-tap answer, on the row that raised it.
///
/// Five states, and the two that come from the office are the reason this exists: an answer that
/// only ever reported *sent* is a message dropped down a well.
class AnswerLine extends StatelessWidget {
  const AnswerLine({super.key, required this.answer, this.onRetry, this.onDismiss});

  final Answer answer;
  final VoidCallback? onRetry;
  final VoidCallback? onDismiss;

  @override
  Widget build(BuildContext context) {
    final failed = answer.failed;
    final colour = switch (answer.state) {
      AnswerState.actioned => Nocturne.goodText,
      AnswerState.sent => Nocturne.goodText,
      AnswerState.failed => Nocturne.criticalText,
      // Not critical. The office looked and could not act — that is an answer the crew member has
      // to do something about, not a fault, and colouring it like a failure would read as one.
      AnswerState.dismissed => Nocturne.warningText,
      AnswerState.queued => Nocturne.neutral500,
    };
    final icon = switch (answer.state) {
      AnswerState.actioned => PhosphorIconsRegular.checkCircle,
      AnswerState.sent => PhosphorIconsRegular.checkCircle,
      AnswerState.failed => PhosphorIconsRegular.warningCircle,
      AnswerState.dismissed => PhosphorIconsRegular.info,
      AnswerState.queued => PhosphorIconsRegular.clockCountdown,
    };

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 15, color: colour),
        const SizedBox(width: 8),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                '${answer.summary} · ${answerStateLabel(answer.state)}',
                style: NoctType.listSecondary.copyWith(color: colour),
              ),
              // Whoever's words they are. For a failure, the server's — "Unsupported operation
              // type 'course.seat_request'" is not crew-facing prose, but hiding it would leave a
              // failure with no cause at all, and it is exactly the string that names the backend
              // work outstanding. For a decision, the coordinator's own note, written knowing the
              // crew member reads it (ADM-11's form says so above the field).
              if (answer.detail != null) Text(answer.detail!, style: NoctType.meta),
              if (failed && (onRetry != null || onDismiss != null))
                Row(
                  children: [
                    if (onRetry != null)
                      NButton(
                        label: 'Retry',
                        variant: NButtonVariant.ghost,
                        fontSize: 12,
                        minHeight: 36,
                        onPressed: onRetry,
                      ),
                    if (onDismiss != null)
                      NButton(
                        label: 'Dismiss',
                        variant: NButtonVariant.ghost,
                        fontSize: 12,
                        minHeight: 36,
                        onPressed: onDismiss,
                      ),
                  ],
                ),
            ],
          ),
        ),
      ],
    );
  }
}

// ---------------------------------------------------------------------------
// MOB-1 — My certifications
// ---------------------------------------------------------------------------

class CertificationsScreen extends StatelessWidget {
  const CertificationsScreen({super.key, required this.state});

  final AppState state;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<List<CertificationRow>>(
      stream: state.watchCertifications(),
      builder: (context, snapshot) => CertificationsView(
        rows: snapshot.data,
        sync: state.syncState,
        today: state.serverToday,
        person: state.person,
        syncing: state.syncing,
        error: state.lastError,
        onSync: state.syncing ? null : state.sync,
        onOpen: (row) => openRequirement(context, state, row.cell.requirementId),
      ),
    );
  }
}

class CertificationsView extends StatelessWidget {
  const CertificationsView({
    super.key,
    this.rows,
    this.sync,
    this.today,
    this.person,
    this.syncing = false,
    this.error,
    this.onSync,
    this.onOpen,
  });

  final List<CertificationRow>? rows;
  final LocalSyncState? sync;
  final String? today;
  final LocalPerson? person;
  final bool syncing;
  final String? error;
  final VoidCallback? onSync;

  /// Navigation is the wrapper's, not the view's: a pure widget that pushed a route would drag
  /// a Navigator into every test that renders a list.
  final void Function(CertificationRow row)? onOpen;

  @override
  Widget build(BuildContext context) {
    final rows = this.rows;
    if (rows == null) return const SizedBox.shrink();

    final sync = this.sync;
    final header = Column(
      children: [
        CrewHeader(name: person?.name ?? 'CREWCOMP', syncing: syncing, onSync: onSync),
        SyncLine(syncing: syncing, lastSyncedAt: sync?.lastSyncedAt, error: error, onRetry: onSync),
      ],
    );

    if (rows.isEmpty) {
      return Column(
        children: [
          header,
          Expanded(
            child: EmptyState(
              icon: PhosphorIconsRegular.sailboat,
              title: sync?.standingCcId == null ? 'No upcoming swing' : 'Nothing to show yet',
              // A person with no assignment has no swing to be evaluated against, so there is no
              // roll-up to show. Saying "compliant" here would be inventing an answer.
              message: sync?.standingCcId == null
                  ? 'Your compliance is assessed against a swing. You are not currently assigned '
                        'to one, so there is nothing to report.'
                  : 'Pull down to sync.',
            ),
          ),
        ],
      );
    }

    // The engine's worklist order (§5.4): worst first, so the thing to act on is at the top.
    final sorted = [...rows]
      ..sort((a, b) {
        final byState = cellStateRank(a.cell.state).compareTo(cellStateRank(b.cell.state));
        return byState != 0 ? byState : a.code.compareTo(b.code);
      });

    final attention = sorted.where((r) => needsAttention(r.cell.state)).toList();
    final rest = sorted.where((r) => !needsAttention(r.cell.state)).toList();

    return ListView(
      padding: const EdgeInsets.only(bottom: 24),
      children: [
        header,
        if (sync != null) _StandingCard(sync: sync),
        if (attention.isNotEmpty)
          const SectionLabel(
            'Needs attention',
            padding: EdgeInsets.fromLTRB(Nocturne.gutter, 0, Nocturne.gutter, 8),
          ),
        ...attention.map(
          (row) => _CertificationRow(
            row: row,
            today: today,
            marked: true,
            onTap: onOpen == null ? null : () => onOpen!(row),
          ),
        ),
        if (rest.isNotEmpty) const SectionLabel('Everything else'),
        ...rest.map(
          (row) => _CertificationRow(
            row: row,
            today: today,
            onTap: onOpen == null ? null : () => onOpen!(row),
          ),
        ),
      ],
    );
  }
}

class _StandingCard extends StatelessWidget {
  const _StandingCard({required this.sync});

  final LocalSyncState sync;

  @override
  Widget build(BuildContext context) {
    final ccId = sync.standingCcId;
    if (ccId == null) return const SizedBox.shrink();

    final rollUp = sync.standingRollUp ?? 'unknown';

    return Padding(
      padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 0, Nocturne.gutter, 16),
      child: NCard(
        elevated: true,
        padding: const EdgeInsets.fromLTRB(15, 13, 15, 13),
        child: Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    // Named explicitly: a roll-up is only meaningful against a swing (§5.2), so
                    // the screen says which one rather than letting the reader assume.
                    '${sync.standingPartnership ?? ''} $ccId'.trim(),
                    style: NoctType.cardTitle,
                  ),
                  if (sync.standingFrom != null && sync.standingTo != null) ...[
                    const SizedBox(height: 2),
                    Text(
                      '${formatDateRange(sync.standingFrom!, sync.standingTo!)}'
                      '${sync.standingCurrent == true ? ' · in progress' : ' · upcoming'}',
                      style: NoctType.listSecondary.copyWith(fontSize: 12),
                    ),
                  ],
                ],
              ),
            ),
            const SizedBox(width: 12),
            NTag(label: cellStateLabel(rollUp), tone: cellStateTone(rollUp)),
          ],
        ),
      ),
    );
  }
}

/// A flush list row, not a card. Six cards in a column is six competing surfaces; six rows on one
/// ground is a list, and the only thing that stands out is the one carrying the accent mark.
class _CertificationRow extends StatelessWidget {
  const _CertificationRow({
    required this.row,
    required this.today,
    this.marked = false,
    this.onTap,
  });

  final CertificationRow row;
  final String? today;
  final bool marked;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final expiry = row.expiry;

    return FlushRow(
      onTap: onTap,
      leftMark: marked ? Nocturne.accent : null,
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(row.title, style: NoctType.listPrimary),
                const SizedBox(height: 3),
                Text.rich(
                  TextSpan(
                    children: [
                      monoSpan(row.code, fontSize: 11.5, colour: Nocturne.neutral500),
                      TextSpan(
                        text: [
                          '',
                          if (row.holding != null) holdingStatusLabel(row.holding!.status),
                          // "Held, expires · in 18 days" reads as one clause with the status
                          // above it; with no holding on record the verb has to come back or the
                          // line says "MS-02 · in 10 days" and never says of what.
                          if (expiry != null && today != null)
                            '${row.holding == null ? 'expires ' : ''}${relativeDays(today!, expiry)}',
                          if (expiry != null && today == null)
                            '${row.holding == null ? 'expires ' : ''}${formatDate(expiry)}',
                        ].join(' · '),
                      ),
                    ],
                  ),
                  style: NoctType.listSecondary,
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),
          NTag(label: cellStateLabel(row.cell.state), tone: cellStateTone(row.cell.state)),
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// MOB-3 — My roster
// ---------------------------------------------------------------------------

class RosterScreen extends StatelessWidget {
  const RosterScreen({super.key, required this.state});

  final AppState state;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<List<LocalAssignment>>(
      stream: state.watchAssignments(),
      builder: (context, assignmentSnapshot) {
        return StreamBuilder<List<LocalLeave>>(
          stream: state.watchLeave(),
          builder: (context, leaveSnapshot) => RosterView(
            assignments: assignmentSnapshot.data ?? const <LocalAssignment>[],
            leave: leaveSnapshot.data ?? const <LocalLeave>[],
            today: state.serverToday,
            person: state.person,
            sync: state.syncState,
            syncing: state.syncing,
            error: state.lastError,
            onSync: state.syncing ? null : state.sync,
            onOpenSwing: (assignment) => Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => SwingDetailScreen(state: state, assignment: assignment),
              ),
            ),
          ),
        );
      },
    );
  }
}

class RosterView extends StatelessWidget {
  const RosterView({
    super.key,
    required this.assignments,
    required this.leave,
    required this.today,
    this.person,
    this.sync,
    this.syncing = false,
    this.error,
    this.onSync,
    this.onOpenSwing,
  });

  final List<LocalAssignment> assignments;
  final List<LocalLeave> leave;
  final String? today;
  final LocalPerson? person;
  final LocalSyncState? sync;
  final bool syncing;
  final String? error;
  final VoidCallback? onSync;
  final void Function(LocalAssignment assignment)? onOpenSwing;

  @override
  Widget build(BuildContext context) {
    // Local copy so the null check promotes: a field cannot be promoted, and "is today within
    // this swing" is the whole difference between the current row and the rest.
    final today = this.today;

    final header = Column(
      children: [
        CrewHeader(name: person?.name ?? 'CREWCOMP', syncing: syncing, onSync: onSync),
        SyncLine(
          syncing: syncing,
          lastSyncedAt: sync?.lastSyncedAt,
          error: error,
          onRetry: onSync,
          padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 0, Nocturne.gutter, 16),
        ),
      ],
    );

    if (assignments.isEmpty && leave.isEmpty) {
      return Column(
        children: [
          header,
          const Expanded(
            child: EmptyState(
              icon: PhosphorIconsRegular.calendarDots,
              title: 'Nothing scheduled',
              message: 'Assignments and leave will appear here once a coordinator records them.',
            ),
          ),
        ],
      );
    }

    return ListView(
      padding: const EdgeInsets.only(bottom: 24),
      children: [
        header,
        if (assignments.isNotEmpty)
          const SectionLabel(
            'Swings',
            padding: EdgeInsets.fromLTRB(Nocturne.gutter, 0, Nocturne.gutter, 8),
          ),
        ...assignments.map((assignment) {
          final current = today != null && isWithin(today, assignment.fromDate, assignment.toDate);
          return FlushRow(
            onTap: onOpenSwing == null ? null : () => onOpenSwing!(assignment),
            verticalPadding: 14,
            child: Row(
              children: [
                Icon(
                  current ? PhosphorIconsFill.sailboat : PhosphorIconsRegular.sailboat,
                  size: 22,
                  color: Nocturne.accent,
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${assignment.partnershipAbbrev} ${assignment.ccId}',
                        style: NoctType.cardTitleSm,
                      ),
                      const SizedBox(height: 2),
                      Text(
                        'Slot ${assignment.slotRef} · '
                        '${formatDateRange(assignment.fromDate, assignment.toDate)}',
                        style: NoctType.listSecondary,
                      ),
                    ],
                  ),
                ),
                if (current) ...[
                  const SizedBox(width: 12),
                  const NTag(label: 'Current', tone: Tone.good),
                ],
              ],
            ),
          );
        }),
        if (leave.isNotEmpty) const SectionLabel('Leave'),
        ...leave.map(
          (record) => FlushRow(
            verticalPadding: 14,
            child: Row(
              children: [
                const Icon(PhosphorIconsRegular.umbrella, size: 22, color: Nocturne.neutral500),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(leaveKindLabel(record.kind), style: NoctType.listPrimary),
                      const SizedBox(height: 2),
                      Text(
                        formatDateRange(record.fromDate, record.toDate),
                        style: NoctType.listSecondary,
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                // O-8 is unresolved: leave may become a mirror of an HR system, so the app shows
                // it read-only and offers no way to request or amend it. Plain meta rather than a
                // tag, because "recorded" is not an actionable state and a tag would imply it is.
                Text(
                  record.status,
                  style: NoctType.listSecondary.copyWith(color: Nocturne.neutral600),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

/// `annual_leave` → `Annual leave`. Sentence case, not lower case: the shipped build renders the
/// wire value with its underscores swapped for spaces, which puts "annual leave" on screen.
String leaveKindLabel(String kind) {
  final words = kind.replaceAll('_', ' ').trim();
  if (words.isEmpty) return kind;
  return words[0].toUpperCase() + words.substring(1);
}

// ---------------------------------------------------------------------------
// MOB-4 — Alerts
// ---------------------------------------------------------------------------

class NotificationsScreen extends StatelessWidget {
  const NotificationsScreen({super.key, required this.state, this.onOpenTab});

  final AppState state;

  /// Where an alert whose deep link names a root screen should send the crew member. Switching
  /// the tab rather than pushing keeps one copy of each root screen in the tree.
  final ValueChanged<int>? onOpenTab;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<List<LocalNotification>>(
      stream: state.watchNotifications(),
      builder: (context, snapshot) => NotificationsView(
        notifications: snapshot.data,
        onMarkRead: state.markRead,
        person: state.person,
        sync: state.syncState,
        syncing: state.syncing,
        error: state.lastError,
        onSync: state.syncing ? null : state.sync,
        onAct: (notification) => _act(context, notification),
      ),
    );
  }

  /// MOB-4's inline action. An alert that implies something to do takes the crew member to the
  /// place they do it, rather than to a list they then have to search.
  void _act(BuildContext context, LocalNotification notification) {
    state.markRead(notification.id);

    // The deep link is the server's own answer to "where does this go", and the only field
    // besides the title a push payload is allowed to carry (SEC-13). Two crew-facing forms exist:
    // `crewcomp://certifications` and `crewcomp://certifications/<requirementId>`. Anything else
    // is a back-office link that reached the wrong recipient, and doing nothing is the right
    // response to it.
    final link = notification.deepLink ?? '';
    final match = RegExp(r'^crewcomp://certifications/(\d+)$').firstMatch(link);
    if (match != null) {
      openRequirement(context, state, int.parse(match.group(1)!));
    } else if (link == 'crewcomp://certifications') {
      onOpenTab?.call(1);
    }
  }
}

class NotificationsView extends StatelessWidget {
  const NotificationsView({
    super.key,
    this.notifications,
    required this.onMarkRead,
    this.person,
    this.sync,
    this.syncing = false,
    this.error,
    this.onSync,
    this.onAct,
  });

  final List<LocalNotification>? notifications;
  final void Function(int notificationId) onMarkRead;
  final LocalPerson? person;
  final LocalSyncState? sync;
  final bool syncing;
  final String? error;
  final VoidCallback? onSync;
  final void Function(LocalNotification notification)? onAct;

  @override
  Widget build(BuildContext context) {
    final notifications = this.notifications;
    if (notifications == null) return const SizedBox.shrink();

    final header = Column(
      children: [
        CrewHeader(name: person?.name ?? 'CREWCOMP', syncing: syncing, onSync: onSync),
        SyncLine(
          syncing: syncing,
          lastSyncedAt: sync?.lastSyncedAt,
          error: error,
          onRetry: onSync,
          padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 0, Nocturne.gutter, 16),
        ),
      ],
    );

    if (notifications.isEmpty) {
      return Column(
        children: [
          header,
          const Expanded(
            child: EmptyState(
              icon: PhosphorIconsRegular.bell,
              title: 'No alerts',
              message: 'Expiry warnings and assignment changes will appear here.',
            ),
          ),
        ],
      );
    }

    return ListView(
      padding: const EdgeInsets.only(bottom: 24),
      children: [
        header,
        for (final (index, notification) in notifications.indexed)
          _AlertRow(
            notification: notification,
            last: index == notifications.length - 1,
            onMarkRead: () => onMarkRead(notification.id),
            onAct: onAct == null ? null : () => onAct!(notification),
          ),
      ],
    );
  }
}

class _AlertRow extends StatelessWidget {
  const _AlertRow({
    required this.notification,
    required this.last,
    required this.onMarkRead,
    this.onAct,
  });

  final LocalNotification notification;
  final bool last;
  final VoidCallback onMarkRead;
  final VoidCallback? onAct;

  @override
  Widget build(BuildContext context) {
    final unread = notification.readAt == null;
    final action = notificationActionLabel(notification.kind);

    return FlushRow(
      onTap: unread ? onMarkRead : null,
      bottomDivider: last,
      verticalPadding: 16,
      // The accent mark means "this one wants something from you", not "unread". An unread
      // acknowledgement and an expiring certificate are not the same weight of thing.
      leftMark: action != null ? Nocturne.accent : null,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 24,
            child: Icon(
              notificationKindIcon(notification.kind),
              size: 22,
              color: notificationKindColour(notification.kind),
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  notification.title,
                  style: NoctType.listPrimary.copyWith(
                    color: unread ? Nocturne.text : Nocturne.neutral300,
                  ),
                ),
                // SEC-13: the title above is all a push payload carries. The body is here, from
                // the synced record, which is the "details fetched in-app" the rule asks for.
                if (notification.body != null) ...[
                  const SizedBox(height: 4),
                  Text(humaniseDates(notification.body!), style: NoctType.cardBody),
                ],
                const SizedBox(height: 8),
                Row(
                  children: [
                    Text(ago(notification.createdAt), style: NoctType.meta),
                    if (action != null && onAct != null) ...[
                      Text(' · ', style: NoctType.meta),
                      Pressable(
                        onTap: onAct,
                        borderRadius: BorderRadius.circular(Nocturne.radiusSm),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 4),
                          child: Text(
                            action,
                            style: NoctType.meta.copyWith(color: Nocturne.accent300),
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
              ],
            ),
          ),
          if (unread)
            const Padding(
              padding: EdgeInsets.only(left: 10, top: 6),
              child: Icon(PhosphorIconsFill.circle, size: 8, color: Nocturne.accent),
            ),
        ],
      ),
    );
  }
}
