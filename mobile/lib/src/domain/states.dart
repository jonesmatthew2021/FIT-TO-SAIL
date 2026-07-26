/// Presentation of the engine's Appendix A enumerations — **labels and colours only**.
///
/// AUTH-1: no compliance logic lives in a client. Cell states, roll-ups and expiry impacts are
/// the server's answers, arriving pre-computed in the sync payload; this file decides what
/// colour "expiring" is and nothing else. The moment something here starts deciding what a
/// state *is*, the rule is broken — and on mobile it would be broken offline, where nobody can
/// see it happen.
library;

import 'package:flutter/material.dart';

/// §5.1 cell states, ordered by how much attention they want.
const cellStateOrder = <String>[
  'gap',
  'review',
  'unknown',
  'expiring',
  'quota_only',
  'recommended',
  'ok',
  'na',
];

/// Where an unrecognised state sorts: first, so a state this build has never heard of surfaces
/// rather than hiding at the bottom of a list.
int cellStateRank(String state) {
  final index = cellStateOrder.indexOf(state);
  return index < 0 ? -1 : index;
}

String cellStateLabel(String state) => switch (state) {
      'ok' => 'OK',
      'gap' => 'Gap',
      'expiring' => 'Expiring',
      'unknown' => 'Unknown',
      'review' => 'Review',
      'quota_only' => 'Quota only',
      'recommended' => 'Recommended',
      'na' => 'N/A',
      // Shown verbatim rather than mapped to something reassuring: a state the app does not
      // know about must not render as "OK".
      _ => state,
    };

/// True for the states a crew member should act on. Drives the "needs attention" grouping.
bool needsAttention(String state) =>
    state == 'gap' || state == 'expiring' || state == 'unknown' || state == 'review';

/// Appendix A `qualification_holding.status`.
String holdingStatusLabel(String status) => switch (status) {
      'held_expiry' => 'Held, expires',
      'held_perpetual' => 'Held',
      'not_held' => 'Not held',
      'unknown' => 'Unknown',
      _ => status,
    };

/// Appendix A `evidence_document.verification_status` — MOB-4's visible queue state.
String submissionStatusLabel(String status) => switch (status) {
      'pending_extraction' => 'Processing',
      'pending_review' => 'Awaiting review',
      'auto_accepted' => 'Accepted',
      'verified' => 'Verified',
      'rejected' => 'Rejected',
      _ => status,
    };

String notificationKindLabel(String kind) => switch (kind) {
      'expiry_warning' => 'Expiry',
      'assignment_added' => 'Assignment',
      'assignment_removed' => 'Assignment',
      'assignment_changed' => 'Assignment',
      'requirement_added' => 'Requirement',
      'evidence_received' => 'Submission',
      'evidence_verified' => 'Submission',
      'evidence_rejected' => 'Submission',
      _ => 'Notice',
    };

IconData notificationKindIcon(String kind) => switch (kind) {
      'expiry_warning' => Icons.schedule,
      'assignment_added' || 'assignment_removed' || 'assignment_changed' => Icons.event,
      'requirement_added' => Icons.rule,
      'evidence_received' || 'evidence_verified' || 'evidence_rejected' => Icons.description,
      _ => Icons.notifications,
    };

/// The three colours a state chip can take.
///
/// Colour is never the only carrier of meaning — every chip renders its text label too, so the
/// screen still works for a colour-blind reader and in bright sunlight on a deck.
({Color background, Color foreground}) cellStateColours(String state, Brightness brightness) {
  final dark = brightness == Brightness.dark;
  return switch (state) {
    'gap' || 'review' => dark
        ? (background: const Color(0xFF5A1F1F), foreground: const Color(0xFFFFD9D6))
        : (background: const Color(0xFFFBE4E2), foreground: const Color(0xFF8C1D18)),
    'expiring' || 'unknown' => dark
        ? (background: const Color(0xFF553D14), foreground: const Color(0xFFFFE0A6))
        : (background: const Color(0xFFFDF0D5), foreground: const Color(0xFF7A5300)),
    'ok' => dark
        ? (background: const Color(0xFF1E4429), foreground: const Color(0xFFB6F0C4))
        : (background: const Color(0xFFE3F5E8), foreground: const Color(0xFF1B5E33)),
    _ => dark
        ? (background: const Color(0xFF2E3440), foreground: const Color(0xFFC7CEDB))
        : (background: const Color(0xFFEDEFF3), foreground: const Color(0xFF44506A)),
  };
}

/// Requirement category codes (Appendix A: QL · VS · PS · MS · CS · HR · PT · VI · PI).
///
/// Shown raw, deliberately. Nothing in the spec says what they expand to, and inventing an
/// expansion would put a confident wrong label in front of people who know the right one. Same
/// position as the admin SPA takes; it is a question for the client.
String categoryLabel(String category) => category;
