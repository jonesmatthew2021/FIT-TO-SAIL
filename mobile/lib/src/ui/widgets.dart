/// Presentation pieces shared by the list screens and the detail screens.
///
/// Pure widgets: they take what they render and hold no state, no stream and no reference to
/// [AppState]. That is what keeps them usable from a widget test with no database behind it.
library;

import 'package:flutter/material.dart';

/// A state pill.
///
/// Colour is never the only carrier of meaning — the label is always rendered too, so the screen
/// still works for a colour-blind reader and on a sunlit deck.
class StateChip extends StatelessWidget {
  const StateChip({super.key, required this.label, required this.colours});

  final String label;
  final ({Color background, Color foreground}) colours;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(color: colours.background, borderRadius: BorderRadius.circular(12)),
      child: Text(
        label,
        style: TextStyle(color: colours.foreground, fontSize: 12, fontWeight: FontWeight.w600),
      ),
    );
  }
}

class SectionHeader extends StatelessWidget {
  const SectionHeader(this.title, {super.key});

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

class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
  });

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

/// One `label: value` line. The detail screens are mostly these.
class DetailRow extends StatelessWidget {
  const DetailRow({super.key, required this.label, required this.value, this.emphasis = false});

  final String label;
  final String value;
  final bool emphasis;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 132,
            child: Text(
              label,
              style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.outline),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: emphasis
                  ? theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600)
                  : theme.textTheme.bodyMedium,
            ),
          ),
        ],
      ),
    );
  }
}
