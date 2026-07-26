/// The crew self-service screens: MOB-1 certifications, MOB-2 roster, MOB-3 notifications.
///
/// All three read the local store and nothing else. There is no loading spinner waiting on a
/// network call anywhere in this file, because there is no network call anywhere in this file —
/// that is what offline-first means in practice, and it is why the app is usable in a dead spot.
library;

import 'package:flutter/material.dart';

import '../data/local_store.dart';
import '../domain/calendar.dart';
import '../domain/states.dart';
import 'app_state.dart';

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

        final screens = [
          CertificationsScreen(state: widget.state),
          RosterScreen(state: widget.state),
          NotificationsScreen(state: widget.state),
        ];

        return Scaffold(
          appBar: AppBar(
            title: Text(widget.state.person?.name ?? 'CREWCOMP'),
            bottom: PreferredSize(
              preferredSize: const Size.fromHeight(24),
              child: _SyncBanner(state: widget.state),
            ),
            actions: [
              IconButton(
                onPressed: widget.state.syncing ? null : widget.state.sync,
                icon: widget.state.syncing
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.sync),
                tooltip: 'Sync now',
              ),
            ],
          ),
          body: RefreshIndicator(onRefresh: widget.state.sync, child: screens[_tab]),
          bottomNavigationBar: NavigationBar(
            selectedIndex: _tab,
            onDestinationSelected: (index) => setState(() => _tab = index),
            destinations: [
              const NavigationDestination(
                icon: Icon(Icons.verified_outlined),
                selectedIcon: Icon(Icons.verified),
                label: 'Certifications',
              ),
              const NavigationDestination(
                icon: Icon(Icons.calendar_month_outlined),
                selectedIcon: Icon(Icons.calendar_month),
                label: 'Roster',
              ),
              NavigationDestination(
                icon: _UnreadBadge(
                  state: widget.state,
                  child: const Icon(Icons.notifications_outlined),
                ),
                selectedIcon: const Icon(Icons.notifications),
                label: 'Alerts',
              ),
            ],
          ),
        );
      },
    );
  }
}

/// §7.6 staleness indicator: the app always says when it last heard from the server.
///
/// Not decoration. Every number on these screens is as old as this line says it is, and a crew
/// member deciding whether their ticket is current deserves to know they are reading a
/// three-week-old answer.
class _SyncBanner extends StatelessWidget {
  const _SyncBanner({required this.state});

  final AppState state;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final error = state.lastError;
    final last = state.lastSyncedAt;

    final String message;
    final Color colour;
    if (state.syncing) {
      message = 'Syncing…';
      colour = theme.colorScheme.onSurfaceVariant;
    } else if (last == null) {
      message = 'Never synced — showing nothing yet';
      colour = theme.colorScheme.error;
    } else if (error != null) {
      message = 'Offline · last synced ${_ago(last)}';
      colour = theme.colorScheme.onSurfaceVariant;
    } else {
      message = 'Last synced ${_ago(last)}';
      colour = theme.colorScheme.onSurfaceVariant;
    }

    return Padding(
      padding: const EdgeInsets.only(left: 16, right: 16, bottom: 6),
      child: Align(
        alignment: Alignment.centerLeft,
        child: Text(message, style: theme.textTheme.bodySmall?.copyWith(color: colour)),
      ),
    );
  }

  /// Elapsed wall-clock time, which is a different thing from a business date and is allowed to
  /// come from the device: it answers "how stale is this?", not "what day is it?".
  static String _ago(DateTime when) {
    final delta = DateTime.now().toUtc().difference(when);
    if (delta.inMinutes < 1) return 'just now';
    if (delta.inMinutes < 60) return '${delta.inMinutes} min ago';
    if (delta.inHours < 24) return '${delta.inHours} h ago';
    return '${delta.inDays} days ago';
  }
}

/// SEC-12: past the offline-validity window the cached data stops being shown at all.
class _LockedScreen extends StatelessWidget {
  const _LockedScreen({required this.state});

  final AppState state;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.lock_outline, size: 48),
              const SizedBox(height: 16),
              Text('Offline too long', style: Theme.of(context).textTheme.titleLarge),
              const SizedBox(height: 8),
              const Text(
                'This device has not reached CREWCOMP in over 30 days, so the cached copy of '
                'your records has been locked. Connect to sign in again.',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              FilledButton(onPressed: state.sync, child: const Text('Try now')),
            ],
          ),
        ),
      ),
    );
  }
}

class _UnreadBadge extends StatelessWidget {
  const _UnreadBadge({required this.state, required this.child});

  final AppState state;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<int>(
      stream: state.watchUnreadCount(),
      builder: (context, snapshot) {
        final count = snapshot.data ?? 0;
        if (count == 0) return child;
        return Badge(label: Text('$count'), child: child);
      },
    );
  }
}

// ---------------------------------------------------------------------------
// MOB-1 — My certifications
// ---------------------------------------------------------------------------

/// Stream plumbing. Everything visual is in [CertificationsView].
///
/// The split is not ceremony: a presentational widget that is a pure function of its data can be
/// tested by handing it data, whereas one that owns a database stream drags drift's timers into
/// the widget-test fake-async zone and hangs there.
class CertificationsScreen extends StatelessWidget {
  const CertificationsScreen({super.key, required this.state});

  final AppState state;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<List<CertificationRow>>(
      stream: state.watchCertifications(),
      builder: (context, snapshot) =>
          CertificationsView(rows: snapshot.data, sync: state.syncState, today: state.serverToday),
    );
  }
}

class CertificationsView extends StatelessWidget {
  const CertificationsView({super.key, this.rows, this.sync, this.today});

  final List<CertificationRow>? rows;
  final LocalSyncState? sync;
  final String? today;

  @override
  Widget build(BuildContext context) {
    {
      {
        final rows = this.rows;
        if (rows == null) return const SizedBox.shrink();

        final sync = this.sync;
        if (rows.isEmpty) {
          return _Empty(
            icon: Icons.sailing_outlined,
            title: sync?.standingCcId == null ? 'No upcoming swing' : 'Nothing to show yet',
            // A person with no assignment has no swing to be evaluated against, so there is no
            // roll-up to show. Saying "compliant" here would be inventing an answer.
            message: sync?.standingCcId == null
                ? 'Your compliance is assessed against a swing. You are not currently assigned '
                      'to one, so there is nothing to report.'
                : 'Pull down to sync.',
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
            if (sync != null) _StandingHeader(sync: sync),
            if (attention.isNotEmpty) const _SectionHeader('Needs attention'),
            ...attention.map((row) => _CertificationTile(row: row, today: today)),
            if (rest.isNotEmpty) const _SectionHeader('Everything else'),
            ...rest.map((row) => _CertificationTile(row: row, today: today)),
          ],
        );
      }
    }
  }
}

class _StandingHeader extends StatelessWidget {
  const _StandingHeader({required this.sync});

  final LocalSyncState sync;

  @override
  Widget build(BuildContext context) {
    final ccId = sync.standingCcId;
    if (ccId == null) return const SizedBox.shrink();

    final rollUp = sync.standingRollUp ?? 'unknown';
    final colours = cellStateColours(rollUp, Theme.of(context).brightness);

    return Card(
      margin: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Padding(
        padding: const EdgeInsets.all(16),
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
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  if (sync.standingFrom != null && sync.standingTo != null)
                    Text(
                      '${formatDateRange(sync.standingFrom!, sync.standingTo!)}'
                      '${sync.standingCurrent == true ? ' · in progress' : ' · upcoming'}',
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                ],
              ),
            ),
            _Chip(label: cellStateLabel(rollUp), colours: colours),
          ],
        ),
      ),
    );
  }
}

class _CertificationTile extends StatelessWidget {
  const _CertificationTile({required this.row, required this.today});

  final CertificationRow row;
  final String? today;

  @override
  Widget build(BuildContext context) {
    final colours = cellStateColours(row.cell.state, Theme.of(context).brightness);
    final expiry = row.cell.expiry ?? row.holding?.expiry;

    return ListTile(
      title: Text(row.title),
      subtitle: Text(
        [
          '${categoryLabel(row.category)} · ${row.code}',
          if (row.holding != null) holdingStatusLabel(row.holding!.status),
          // Counted from the *server's* today, carried in the sync payload — never from the
          // device clock (NFR-5, O-11).
          if (expiry != null && today != null) 'expires ${relativeDays(today!, expiry)}',
          if (expiry != null && today == null) 'expires ${formatDate(expiry)}',
        ].join(' · '),
      ),
      trailing: _Chip(label: cellStateLabel(row.cell.state), colours: colours),
      isThreeLine: false,
    );
  }
}

// ---------------------------------------------------------------------------
// MOB-2 — My roster
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
  });

  final List<LocalAssignment> assignments;
  final List<LocalLeave> leave;
  final String? today;

  @override
  Widget build(BuildContext context) {
    {
      {
        {
          // Local copy so the null check promotes: a field cannot be promoted, and "is today
          // within this swing" is the whole difference between the current row and the rest.
          final today = this.today;
          if (assignments.isEmpty && leave.isEmpty) {
            return const _Empty(
              icon: Icons.calendar_today_outlined,
              title: 'Nothing scheduled',
              message: 'Assignments and leave will appear here once a coordinator records them.',
            );
          }

          return ListView(
            padding: const EdgeInsets.only(bottom: 24),
            children: [
              if (assignments.isNotEmpty) const _SectionHeader('Swings'),
              ...assignments.map(
                (assignment) => ListTile(
                  leading: Icon(
                    today != null && isWithin(today, assignment.fromDate, assignment.toDate)
                        ? Icons.sailing
                        : Icons.sailing_outlined,
                  ),
                  title: Text('${assignment.partnershipAbbrev} ${assignment.ccId}'),
                  subtitle: Text(
                    'Slot ${assignment.slotRef} · '
                    '${formatDateRange(assignment.fromDate, assignment.toDate)}',
                  ),
                  trailing: today != null && isWithin(today, assignment.fromDate, assignment.toDate)
                      ? const _Chip(
                          label: 'Current',
                          colours: (background: Color(0xFFE3F5E8), foreground: Color(0xFF1B5E33)),
                        )
                      : null,
                ),
              ),
              if (leave.isNotEmpty) const _SectionHeader('Leave'),
              ...leave.map(
                (record) => ListTile(
                  leading: const Icon(Icons.beach_access_outlined),
                  title: Text(record.kind.replaceAll('_', ' ')),
                  subtitle: Text(formatDateRange(record.fromDate, record.toDate)),
                  // O-8 is unresolved: leave may become a mirror of an HR system, so the app
                  // shows it read-only and offers no way to request or amend it.
                  trailing: Text(record.status),
                ),
              ),
            ],
          );
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------
// MOB-3 — Notifications
// ---------------------------------------------------------------------------

class NotificationsScreen extends StatelessWidget {
  const NotificationsScreen({super.key, required this.state});

  final AppState state;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<List<LocalNotification>>(
      stream: state.watchNotifications(),
      builder: (context, snapshot) =>
          NotificationsView(notifications: snapshot.data, onMarkRead: state.markRead),
    );
  }
}

class NotificationsView extends StatelessWidget {
  const NotificationsView({super.key, this.notifications, required this.onMarkRead});

  final List<LocalNotification>? notifications;
  final void Function(int notificationId) onMarkRead;

  @override
  Widget build(BuildContext context) {
    {
      {
        final notifications = this.notifications;
        if (notifications == null) return const SizedBox.shrink();
        if (notifications.isEmpty) {
          return const _Empty(
            icon: Icons.notifications_none,
            title: 'No alerts',
            message: 'Expiry warnings and assignment changes will appear here.',
          );
        }

        return ListView.separated(
          itemCount: notifications.length,
          separatorBuilder: (_, _) => const Divider(height: 1),
          itemBuilder: (context, index) {
            final notification = notifications[index];
            return ListTile(
              leading: Icon(notificationKindIcon(notification.kind)),
              title: Text(
                notification.title,
                style: TextStyle(
                  fontWeight: notification.readAt == null ? FontWeight.w600 : FontWeight.normal,
                ),
              ),
              // SEC-13: the title above is all a push payload carries. The body is here, from
              // the synced record, which is the "details fetched in-app" the rule asks for.
              subtitle: notification.body == null ? null : Text(notification.body!),
              trailing: notification.readAt == null ? const Icon(Icons.circle, size: 10) : null,
              onTap: notification.readAt == null ? () => onMarkRead(notification.id) : null,
            );
          },
        );
      }
    }
  }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

class _Chip extends StatelessWidget {
  const _Chip({required this.label, required this.colours});

  final String label;
  final ({Color background, Color foreground}) colours;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: colours.background, borderRadius: BorderRadius.circular(12)),
      // Always the text as well as the colour: readable to a colour-blind reader, and on a
      // sunlit deck.
      child: Text(
        label,
        style: TextStyle(color: colours.foreground, fontSize: 12, fontWeight: FontWeight.w600),
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader(this.title);

  final String title;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 20, 16, 8),
      child: Text(
        title.toUpperCase(),
        style: Theme.of(context).textTheme.labelSmall?.copyWith(letterSpacing: 0.8),
      ),
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty({required this.icon, required this.title, required this.message});

  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return ListView(
      children: [
        const SizedBox(height: 80),
        Icon(icon, size: 48, color: Theme.of(context).colorScheme.outline),
        const SizedBox(height: 16),
        Center(child: Text(title, style: Theme.of(context).textTheme.titleMedium)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 40, vertical: 8),
          child: Text(message, textAlign: TextAlign.center),
        ),
      ],
    );
  }
}
