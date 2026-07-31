/// MOB-11 — a supervisor's watch.
///
/// One privacy rule runs the whole screen, and it is not a rendering choice: **a supervisor sees
/// requirement status and one line of reason, never a document, never medical detail, never why
/// a certificate lapsed.** The control is the shape of the endpoint, not the discretion of this
/// widget — [TeamMember] has nowhere to put a document because the payload must never contain
/// one. A screen that received the detail and chose not to draw it would leak it to anyone who
/// read the device's database, which is exactly the threat SEC-12 encrypts against.
///
/// The tab replaces Certifications for a supervisor; their own record is still one tap from Home,
/// where the readiness ring already summarises it.
library;

import 'package:flutter/material.dart';
import 'package:phosphor_icons/phosphor_icons.dart';

import '../data/local_store.dart';
import '../domain/intents.dart';
import '../domain/offers.dart';
import '../domain/states.dart';
import 'app_state.dart';
import 'nocturne.dart';
import 'screens.dart';
import 'widgets.dart';

class TeamScreen extends StatelessWidget {
  const TeamScreen({super.key, required this.state});

  final AppState state;

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<List<LocalCrewIntent>>(
      stream: state.watchIntents(),
      builder: (context, snapshot) => RefreshIndicator(
        color: Nocturne.accent,
        backgroundColor: Nocturne.surface,
        onRefresh: state.sync,
        child: TeamView(
        members: state.team,
        ccId: state.teamCcId,
        nudges: (snapshot.data ?? const <LocalCrewIntent>[])
            .where((intent) => intent.kind == IntentKind.nudge)
            .toList(),
        sync: state.syncState,
        syncing: state.syncing,
        error: state.lastError,
        sessionExpired: state.sessionExpired,
        onSync: state.syncing ? null : state.sync,
        onNudge: (member) => state.answer(
          kind: IntentKind.nudge,
          summary: 'Nudged ${member.name}',
          // Both, and they are not redundant. `subjectRef` is how this screen recognises its own
          // outbox entry; `targetSam` is the wire field, named for what it is — the one field on
          // the whole queue that points at somebody other than the sender, and the one the server
          // checks against the sender's own watch rather than trusting.
          subjectRef: member.sam,
          payload: {'targetSam': member.sam},
        ),
        ),
      ),
    );
  }
}

class TeamView extends StatelessWidget {
  const TeamView({
    super.key,
    required this.members,
    this.ccId,
    this.nudges = const <LocalCrewIntent>[],
    this.sync,
    this.syncing = false,
    this.error,
    this.sessionExpired = false,
    this.onSync,
    this.onNudge,
  });

  final List<TeamMember> members;

  /// The swing this watch is over, or null when the supervisor is rostered nowhere.
  ///
  /// The distinction this carries is the whole reason it exists: an empty [members] with a swing
  /// means everyone is clear, and an empty one *without* means the app has nothing to show. A
  /// screen that drew them the same way would tell a supervisor their watch was fine when it had
  /// simply not been loaded.
  final String? ccId;

  final List<LocalCrewIntent> nudges;
  final LocalSyncState? sync;
  final bool syncing;
  final String? error;
  final bool sessionExpired;
  final VoidCallback? onSync;
  final void Function(TeamMember member)? onNudge;

  @override
  Widget build(BuildContext context) {
    final header = _TeamHeader(sync: sync, syncing: syncing, onSync: onSync);

    if (members.isEmpty) {
      return Column(
        children: [
          header,
          SyncLine(
            syncing: syncing,
            lastSyncedAt: sync?.lastSyncedAt,
            error: error,
            sessionExpired: sessionExpired,
            onRetry: onSync,
          ),
          Expanded(
            // Two different silences, said differently. A supervisor reading "no watch to show"
            // as "everyone is fine" is the worst outcome this tab has, so the case where there is
            // nothing to *be* fine about says so in as many words.
            child: ccId == null
                ? const EmptyState(
                    icon: PhosphorIconsRegular.usersThree,
                    title: 'No swing under way',
                    message:
                        'Your watch appears here once you are rostered onto a swing. '
                        'Your own record is on Home.',
                  )
                : EmptyState(
                    icon: PhosphorIconsRegular.usersThree,
                    title: 'Nobody else on $ccId',
                    message: 'You are the only person rostered onto this swing.',
                  ),
          ),
        ],
      );
    }

    final needs = members.where((member) => needsAttention(member.worstState)).toList()
      ..sort((a, b) => cellStateRank(a.worstState).compareTo(cellStateRank(b.worstState)));
    final clear = members.where((member) => !needsAttention(member.worstState)).toList()
      ..sort((a, b) => a.name.compareTo(b.name));

    // "Nudged" disarms the button for a day, matching the server's per-day dedupe (#21) — the
    // device's 30-day intent retention used to freeze it for a month while the server would
    // happily have delivered a second nudge after midnight. Wall-clock elapsed time, which is
    // allowed to come from the device (it answers "how recently", not "what day is it").
    final nudgedToday = <String?>{
      for (final intent in nudges)
        if (DateTime.now().toUtc().difference(intent.queuedAt.toUtc()) < const Duration(hours: 24))
          intent.subjectRef,
    };
    bool nudgedRecently(TeamMember member) =>
        nudgedToday.contains(member.sam) ||
        (member.nudgedAt != null &&
            DateTime.now().toUtc().difference(member.nudgedAt!.toUtc()) <
                const Duration(hours: 24));

    return ListView(
      padding: const EdgeInsets.only(bottom: 24),
      children: [
        header,
        SyncLine(
          syncing: syncing,
          lastSyncedAt: sync?.lastSyncedAt,
          error: error,
          sessionExpired: sessionExpired,
          onRetry: onSync,
        ),

        Padding(
          padding: const EdgeInsets.symmetric(horizontal: Nocturne.gutter),
          child: NCard(
            elevated: true,
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${clear.length} of ${members.length} sail clean',
                        style: NoctType.cardTitleSm,
                      ),
                      const SizedBox(height: 3),
                      Text(
                        _summary(needs.length, sync?.standingTo),
                        style: NoctType.cardBody.copyWith(fontSize: 12),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 14),
                BarStrip(
                  colours: [
                    for (final member in [...needs, ...clear]) _barColour(member.worstState),
                  ],
                ),
              ],
            ),
          ),
        ),

        if (needs.isNotEmpty) ...[
          const SectionLabel('Needs something'),
          for (final member in needs)
            _TeamRow(
              member: member,
              nudged: nudgedRecently(member),
              onNudge: onNudge == null ? null : () => onNudge!(member),
            ),
        ],

        if (clear.isNotEmpty) ...[
          const SectionLabel('Clear'),
          for (final (index, member) in clear.indexed)
            _ClearRow(member: member, last: index == clear.length - 1),
        ],

        Padding(
          padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 14, Nocturne.gutter, 0),
          child: Text(
            'You see requirement status only — never medical detail or documents.',
            style: NoctType.meta,
          ),
        ),
      ],
    );
  }

  static String _summary(int outstanding, String? swingTo) {
    if (outstanding == 0) return 'Nothing outstanding across your watch.';
    final by = swingTo == null ? '' : ' before ${_short(swingTo)}';
    return outstanding == 1 ? 'One needs something$by' : '$outstanding need something$by';
  }

  static String _short(String date) {
    const months = [
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
      'Dec',
    ];
    if (date.length < 10) return date;
    return '${int.parse(date.substring(8, 10))} ${months[int.parse(date.substring(5, 7)) - 1]}';
  }

  /// One bar per crew member, in the same three marks the rest of the app uses for urgency.
  static Color _barColour(String state) => switch (cellStateTone(state)) {
    Tone.critical => Nocturne.markRed,
    Tone.warning || Tone.caution => Nocturne.markAmber,
    _ => Nocturne.markGreen,
  };
}

class _TeamHeader extends StatelessWidget {
  const _TeamHeader({required this.sync, required this.syncing, this.onSync});

  final LocalSyncState? sync;
  final bool syncing;
  final VoidCallback? onSync;

  @override
  Widget build(BuildContext context) {
    final ccId = sync?.standingCcId;
    final kicker = ccId == null
        ? 'YOUR WATCH'
        : 'YOUR WATCH · ${'${sync!.standingPartnership ?? ''} $ccId'.trim().toUpperCase()}';

    return Padding(
      padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 14, Nocturne.gutter, 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(kicker, style: NoctType.kicker),
                const SizedBox(height: 3),
                Text('Your team', style: NoctType.screenName.copyWith(fontSize: 20)),
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

class _TeamRow extends StatelessWidget {
  const _TeamRow({required this.member, required this.nudged, this.onNudge});

  final TeamMember member;
  final bool nudged;
  final VoidCallback? onNudge;

  @override
  Widget build(BuildContext context) {
    return FlushRow(
      leftMark: TeamView._barColour(member.worstState),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(member.name, style: NoctType.listPrimary),
                if (member.reason != null) ...[
                  const SizedBox(height: 3),
                  Text(member.reason!, style: NoctType.listSecondary),
                ],
                // The server's own record of the last nudge, rendered at last (#21): "no
                // movement since you nudged 9 days ago" is the fact a supervisor acts on.
                if (!nudged && member.nudgedAt != null) ...[
                  const SizedBox(height: 3),
                  Text('Nudged ${ago(member.nudgedAt!)}', style: NoctType.meta),
                ],
              ],
            ),
          ),
          const SizedBox(width: 12),
          // Something is already moving, so there is nothing useful to chase. Offering a nudge
          // here is how a supervisor annoys someone who has already done the thing.
          if (member.inHand)
            const NTag(label: 'In hand', tone: Tone.caution)
          else
            NButton(
              label: nudged ? 'Nudged' : 'Nudge',
              fontSize: 12.5,
              onPressed: nudged ? null : onNudge,
            ),
        ],
      ),
    );
  }
}

class _ClearRow extends StatelessWidget {
  const _ClearRow({required this.member, required this.last});

  final TeamMember member;
  final bool last;

  @override
  Widget build(BuildContext context) {
    return FlushRow(
      verticalPadding: 11,
      bottomDivider: last,
      child: Row(
        children: [
          Expanded(child: Text(member.name, style: NoctType.listPrimarySm)),
          if (member.reason != null) ...[
            Text(member.reason!, style: NoctType.meta),
            const SizedBox(width: 12),
          ],
          NTag(label: cellStateLabel(member.worstState), tone: cellStateTone(member.worstState)),
        ],
      ),
    );
  }
}
