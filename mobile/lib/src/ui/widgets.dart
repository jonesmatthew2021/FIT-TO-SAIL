/// The Nocturne component kit, as Flutter widgets.
///
/// Pure presentation: every widget here takes what it renders, holds no stream and knows nothing
/// about [AppState]. That is what lets a widget test hand one a record and assert on the result
/// with no database behind it — see the `*Screen` / `*View` split in `screens.dart`.
///
/// The classes mirror Nocturne's own (`.btn`, `.tag`, `.card`, `.field`) rather than Material's,
/// because the design system is the contract. Where Material has an equivalent it is deliberately
/// unused: `FilledButton` is a solid pill and Nocturne's primary is an accent outline, and the two
/// are not a theme apart — they are opposite instructions about how much an action should shout.
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:phosphor_icons/phosphor_icons.dart';

import 'nocturne.dart';

// ---------------------------------------------------------------------------
// Interaction
// ---------------------------------------------------------------------------

/// Hover, pressed and focus states, themed rather than borrowed from Material.
///
/// Nocturne asks for a tint one step along the accent ramp on hover, a stronger one when pressed,
/// and a 2px accent focus ring. The ring is painted *inside* the bounds rather than at the CSS's
/// 2px offset: an outset ring would have to inflate the widget, and shifting a row's geometry the
/// moment a keyboard arrives is a worse bug than a ring 2px in from where the mock draws it.
class Pressable extends StatefulWidget {
  const Pressable({
    super.key,
    required this.child,
    this.onTap,
    this.tint,
    this.borderRadius = Nocturne.borderMd,
    this.semanticLabel,
    this.excludeSemantics = false,
  });

  final Widget child;
  final VoidCallback? onTap;

  /// The colour the hover and pressed tints are mixed from. Defaults to the accent.
  final Color? tint;

  final BorderRadius borderRadius;
  final String? semanticLabel;
  final bool excludeSemantics;

  @override
  State<Pressable> createState() => _PressableState();
}

class _PressableState extends State<Pressable> {
  bool _focused = false;

  @override
  Widget build(BuildContext context) {
    final tint = widget.tint ?? Nocturne.accent;

    final ink = Material(
      type: MaterialType.transparency,
      child: InkWell(
        onTap: widget.onTap,
        borderRadius: widget.borderRadius,
        onFocusChange: (value) => setState(() => _focused = value),
        // Material's default focus overlay reads as a selection on a dark ground; the ring below
        // is the focus signal instead.
        focusColor: Colors.transparent,
        hoverColor: tint.withValues(alpha: 0.10),
        highlightColor: tint.withValues(alpha: 0.08),
        splashColor: tint.withValues(alpha: 0.14),
        child: widget.child,
      ),
    );

    final ringed = _focused
        ? DecoratedBox(
            position: DecorationPosition.foreground,
            decoration: BoxDecoration(
              border: Border.all(color: Nocturne.accent, width: 2),
              borderRadius: widget.borderRadius,
            ),
            child: ink,
          )
        : ink;

    if (widget.semanticLabel == null) return ringed;
    return Semantics(
      label: widget.semanticLabel,
      button: widget.onTap != null,
      excludeSemantics: widget.excludeSemantics,
      child: ringed,
    );
  }
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

enum NButtonVariant {
  /// A 1px accent outline on transparent. **Never a fill** — the single largest visual change
  /// from the shipped build.
  primary,

  /// A 1px hairline outline, text in the foreground colour.
  secondary,

  /// Text and icon only, in the accent.
  ghost,
}

/// Nocturne's `.btn`.
///
/// [minHeight] defaults to the 44pt floor every tap target on these screens has to clear; the
/// main action on a screen takes 48–52.
class NButton extends StatelessWidget {
  const NButton({
    super.key,
    required this.label,
    this.onPressed,
    this.icon,
    this.variant = NButtonVariant.secondary,
    this.minHeight = 44,
    this.block = false,
    this.alignStart = false,
    this.fontSize = 14,
    this.iconSize = 16,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final NButtonVariant variant;
  final double minHeight;

  /// Fills the available width. Nocturne's `.btn-block`.
  final bool block;

  /// Icon-then-label, left aligned — MOB-5's three 52px answers.
  final bool alignStart;

  final double fontSize;
  final double iconSize;

  @override
  Widget build(BuildContext context) {
    final enabled = onPressed != null;
    final (Color foreground, BorderSide border) = switch (variant) {
      NButtonVariant.primary => (Nocturne.accent, const BorderSide(color: Nocturne.accent)),
      NButtonVariant.secondary => (Nocturne.text, const BorderSide(color: Nocturne.divider)),
      NButtonVariant.ghost => (Nocturne.accent, BorderSide.none),
    };

    final content = Row(
      mainAxisSize: block ? MainAxisSize.max : MainAxisSize.min,
      mainAxisAlignment: alignStart ? MainAxisAlignment.start : MainAxisAlignment.center,
      children: [
        if (icon != null) ...[
          Icon(icon, size: iconSize, color: foreground),
          SizedBox(width: alignStart ? 12 : 6),
        ],
        Flexible(
          child: Text(
            label,
            style: NoctType.button.copyWith(fontSize: fontSize, color: foreground),
            overflow: TextOverflow.ellipsis,
          ),
        ),
      ],
    );

    return Opacity(
      opacity: enabled ? 1 : 0.45,
      child: Pressable(
        onTap: onPressed,
        tint: variant == NButtonVariant.secondary ? Nocturne.text : Nocturne.accent,
        semanticLabel: label,
        excludeSemantics: true,
        child: Container(
          constraints: BoxConstraints(minHeight: minHeight),
          width: block ? double.infinity : null,
          padding: EdgeInsets.symmetric(
            horizontal: variant == NButtonVariant.ghost ? Nocturne.space3 : 14,
            vertical: Nocturne.space2,
          ),
          decoration: BoxDecoration(
            border: Border.fromBorderSide(border),
            borderRadius: Nocturne.borderMd,
          ),
          alignment: alignStart ? Alignment.centerLeft : Alignment.center,
          child: content,
        ),
      ),
    );
  }
}

/// Nocturne's `.btn-icon` — a square, borderless action. 34×34 in the nav bars, which clears 44pt
/// because [Pressable] sits inside a row that is at least that tall.
class NIconButton extends StatelessWidget {
  const NIconButton({
    super.key,
    required this.icon,
    required this.semanticLabel,
    this.onPressed,
    this.size = 34,
    this.iconSize = 17,
    this.colour = Nocturne.text,
  });

  final IconData icon;
  final String semanticLabel;
  final VoidCallback? onPressed;
  final double size;
  final double iconSize;
  final Color colour;

  @override
  Widget build(BuildContext context) {
    return Opacity(
      opacity: onPressed == null ? 0.45 : 1,
      child: Pressable(
        onTap: onPressed,
        semanticLabel: semanticLabel,
        excludeSemantics: true,
        child: SizedBox(
          width: size,
          height: size,
          child: Icon(icon, size: iconSize, color: colour),
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Tags
// ---------------------------------------------------------------------------

/// Nocturne's `.tag`, carrying one of the six state tones.
///
/// The label is not optional and never abbreviated to a dot. Colour is reinforcement here, never
/// the carrier — a colour-blind reader and a sunlit deck are the same problem.
class NTag extends StatelessWidget {
  const NTag({super.key, required this.label, this.tone = Tone.muted});

  final String label;
  final Tone tone;

  @override
  Widget build(BuildContext context) {
    final colours = toneColours(tone);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
      decoration: BoxDecoration(color: colours.fill, borderRadius: Nocturne.borderChip),
      child: Text(label, style: NoctType.tag.copyWith(color: colours.text)),
    );
  }
}

/// Nocturne's `.tag-outline` — an accent hairline with no fill, for a fact rather than a state.
class NOutlineTag extends StatelessWidget {
  const NOutlineTag({super.key, required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
      decoration: BoxDecoration(
        border: Border.all(color: Nocturne.accent),
        borderRadius: Nocturne.borderChip,
      ),
      child: Text(label, style: NoctType.tag.copyWith(fontSize: 10.5, color: Nocturne.accent300)),
    );
  }
}

// ---------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------

/// A surface-filled card.
///
/// [leftMark] is Nocturne's `inset 2px 0 0 <colour>` — a 2px edge, **not** a tinted card. Tinting
/// a whole card to mean "urgent" is the flood rule 1 forbids, and it stops working the moment two
/// cards are urgent at once.
class NCard extends StatelessWidget {
  const NCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.symmetric(horizontal: 15, vertical: 14),
    this.elevated = false,
    this.leftMark,
    this.onTap,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  /// `--shadow-sm`: a hairline edge, no blur. Elevation on a dark ground is an edge.
  final bool elevated;

  final Color? leftMark;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final decorated = Container(
      padding: padding,
      decoration: BoxDecoration(
        color: Nocturne.surface,
        borderRadius: Nocturne.borderMd,
        border: Border(
          left: leftMark != null
              ? BorderSide(color: leftMark!, width: 2)
              : (elevated ? Nocturne.shadowSmEdge : BorderSide.none),
          top: elevated ? Nocturne.shadowSmEdge : BorderSide.none,
          right: elevated ? Nocturne.shadowSmEdge : BorderSide.none,
          bottom: elevated ? Nocturne.shadowSmEdge : BorderSide.none,
        ),
      ),
      child: child,
    );

    if (onTap == null) return decorated;
    return Pressable(onTap: onTap, child: decorated);
  }
}

/// The accent information band: a 1px `accent-800` edge on an `accent-900` tint with a 5px dot.
///
/// Used for the one sentence on a screen that explains what the system will do next. It is the
/// largest area the accent is allowed to touch, and only at the 900 step.
class InfoBand extends StatelessWidget {
  const InfoBand({super.key, required this.text, this.emphasis = const <String>[]});

  final String text;

  /// Substrings of [text] lifted to `--color-text`. The mock brightens the requirement code and
  /// nothing else; passing the substring rather than pre-split spans keeps the copy readable in
  /// the call site, which is where anyone checking it against the handoff will look.
  final List<String> emphasis;

  @override
  Widget build(BuildContext context) {
    const base = TextStyle(
      fontFamily: Nocturne.fontFamily,
      fontSize: 12.5,
      height: 1.45,
      color: Nocturne.accent200,
    );

    return Container(
      padding: const EdgeInsets.fromLTRB(13, 11, 13, 11),
      decoration: BoxDecoration(
        color: Nocturne.accent900,
        border: Border.all(color: Nocturne.accent800),
        borderRadius: Nocturne.borderMd,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 5,
            height: 5,
            margin: const EdgeInsets.only(top: 7, right: 10),
            decoration: const BoxDecoration(color: Nocturne.accent, shape: BoxShape.circle),
          ),
          Expanded(
            child: Text.rich(TextSpan(children: _spans(base)), style: base),
          ),
        ],
      ),
    );
  }

  List<InlineSpan> _spans(TextStyle base) {
    if (emphasis.isEmpty) return [TextSpan(text: text)];

    var rest = text;
    final spans = <InlineSpan>[];
    while (rest.isNotEmpty) {
      var earliest = -1;
      var match = '';
      for (final needle in emphasis) {
        final at = rest.indexOf(needle);
        if (at >= 0 && (earliest < 0 || at < earliest)) {
          earliest = at;
          match = needle;
        }
      }
      if (earliest < 0) {
        spans.add(TextSpan(text: rest));
        break;
      }
      if (earliest > 0) spans.add(TextSpan(text: rest.substring(0, earliest)));
      spans.add(
        TextSpan(
          text: match,
          style: base.copyWith(color: Nocturne.text),
        ),
      );
      rest = rest.substring(earliest + match.length);
    }
    return spans;
  }
}

// ---------------------------------------------------------------------------
// Structure
// ---------------------------------------------------------------------------

/// A 10px uppercase section label, optionally with a right-aligned count.
class SectionLabel extends StatelessWidget {
  const SectionLabel(this.title, {super.key, this.trailing, this.padding});

  final String title;
  final String? trailing;
  final EdgeInsetsGeometry? padding;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: padding ?? const EdgeInsets.fromLTRB(Nocturne.gutter, 20, Nocturne.gutter, 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.baseline,
        textBaseline: TextBaseline.alphabetic,
        children: [
          Text(title.toUpperCase(), style: NoctType.sectionLabel),
          if (trailing != null) ...[
            const Spacer(),
            Text(trailing!, style: NoctType.sectionLabel.copyWith(fontSize: 11, letterSpacing: 0)),
          ],
        ],
      ),
    );
  }
}

/// A flush list row: full-bleed, gutter-padded, separated by a 1px hairline above rather than
/// carded individually. The restyle's central move — six cards in a column is six competing
/// surfaces, where six rows on one ground is a list.
class FlushRow extends StatelessWidget {
  const FlushRow({
    super.key,
    required this.child,
    this.onTap,
    this.leftMark,
    this.verticalPadding = 12,
    this.topDivider = true,
    this.bottomDivider = false,
  });

  final Widget child;
  final VoidCallback? onTap;
  final Color? leftMark;
  final double verticalPadding;
  final bool topDivider;
  final bool bottomDivider;

  @override
  Widget build(BuildContext context) {
    final row = Container(
      decoration: BoxDecoration(
        border: Border(
          top: topDivider ? const BorderSide(color: Nocturne.neutral900) : BorderSide.none,
          bottom: bottomDivider ? const BorderSide(color: Nocturne.neutral900) : BorderSide.none,
          left: leftMark != null ? BorderSide(color: leftMark!, width: 2) : BorderSide.none,
        ),
      ),
      padding: EdgeInsets.fromLTRB(
        leftMark != null ? Nocturne.gutter - 2 : Nocturne.gutter,
        verticalPadding,
        Nocturne.gutter,
        verticalPadding,
      ),
      constraints: const BoxConstraints(minHeight: 44),
      child: child,
    );

    if (onTap == null) return row;
    return Pressable(onTap: onTap, borderRadius: BorderRadius.zero, child: row);
  }
}

/// A `label / value` pair on the 110px definition grid the detail screens are built from.
class DefinitionRow extends StatelessWidget {
  const DefinitionRow({super.key, required this.label, required this.value, this.valueSpan});

  final String label;
  final String value;

  /// Overrides [value] when the value carries mixed styling — a mono code, or a date with its
  /// "in 18 days" in the warning tone.
  final InlineSpan? valueSpan;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 110,
            child: Text(
              label,
              style: const TextStyle(
                fontFamily: Nocturne.fontFamily,
                fontSize: 14,
                height: 1.35,
                color: Nocturne.neutral500,
              ),
            ),
          ),
          Expanded(
            child: valueSpan == null
                ? Text(
                    value,
                    style: const TextStyle(
                      fontFamily: Nocturne.fontFamily,
                      fontSize: 14,
                      height: 1.35,
                      color: Nocturne.text,
                    ),
                  )
                : Text.rich(
                    valueSpan!,
                    style: const TextStyle(
                      fontFamily: Nocturne.fontFamily,
                      fontSize: 14,
                      height: 1.35,
                      color: Nocturne.text,
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}

/// Rule 4's carrier: a business key, monospaced, at the size of the text around it.
TextSpan monoSpan(String value, {double fontSize = 12, Color? colour}) => TextSpan(
  text: value,
  style: NoctType.mono.copyWith(fontSize: fontSize, color: colour),
);

class Mono extends StatelessWidget {
  const Mono(this.value, {super.key, this.fontSize = 12, this.colour});

  final String value;
  final double fontSize;
  final Color? colour;

  @override
  Widget build(BuildContext context) => Text(
    value,
    style: NoctType.mono.copyWith(fontSize: fontSize, color: colour),
  );
}

/// A pushed screen's bar: a 34×34 back or close button, a centred title, and a 1px rule under it.
class NocturneNavBar extends StatelessWidget implements PreferredSizeWidget {
  const NocturneNavBar({
    super.key,
    required this.title,
    this.onBack,
    this.closeIcon = false,
    this.titleSpan,
    this.trailing,
  });

  final String title;

  /// Overrides [title] where it is a business key and has to be monospaced.
  final InlineSpan? titleSpan;

  final VoidCallback? onBack;

  /// MOB-6 and MOB-7 are modal, and a modal closes rather than goes back.
  final bool closeIcon;

  final Widget? trailing;

  @override
  Size get preferredSize => const Size.fromHeight(57);

  @override
  Widget build(BuildContext context) {
    final back = onBack ?? () => Navigator.of(context).maybePop();
    return Container(
      decoration: const BoxDecoration(
        color: Nocturne.bg,
        border: Border(bottom: BorderSide(color: Nocturne.neutral900)),
      ),
      padding: const EdgeInsets.fromLTRB(Nocturne.gutter, 14, Nocturne.gutter, 14),
      child: SafeArea(
        bottom: false,
        child: Row(
          children: [
            NIconButton(
              icon: closeIcon ? PhosphorIconsRegular.x : PhosphorIconsRegular.caretLeft,
              iconSize: closeIcon ? 16 : 17,
              semanticLabel: closeIcon ? 'Close' : 'Back',
              onPressed: back,
            ),
            Expanded(
              child: Padding(
                // Balances the 34px leading button so the title is optically centred, exactly as
                // the mock's `padding-right: 34px` does.
                padding: EdgeInsets.only(left: Nocturne.space2, right: trailing == null ? 34 : 0),
                child: titleSpan == null
                    ? Text(title, textAlign: TextAlign.center, style: NoctType.navTitle)
                    : Text.rich(titleSpan!, textAlign: TextAlign.center, style: NoctType.navTitle),
              ),
            ),
            ?trailing,
          ],
        ),
      ),
    );
  }
}

/// The empty state a list falls back to. Kept plain: a screen with nothing on it should read as
/// an answer, not as a failure to load.
class EmptyState extends StatelessWidget {
  const EmptyState({super.key, required this.icon, required this.title, required this.message});

  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 40),
      children: [
        const SizedBox(height: 80),
        Icon(icon, size: 44, color: Nocturne.neutral700),
        const SizedBox(height: 16),
        Center(child: Text(title, style: NoctType.cardTitle)),
        const SizedBox(height: 8),
        Text(message, textAlign: TextAlign.center, style: NoctType.cardBody),
      ],
    );
  }
}

// ---------------------------------------------------------------------------
// Urgency
// ---------------------------------------------------------------------------

/// The urgency line on an asked-of-you card: a countdown in the deadline's own tone, and a 3px
/// track whose fill is how much of the warning window has already gone.
class UrgencyBar extends StatelessWidget {
  const UrgencyBar({super.key, required this.label, required this.fraction, required this.colour});

  final String label;

  /// Elapsed share of the lead window, 0–1.
  final double fraction;

  final Color colour;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Text(
          label.toUpperCase(),
          style: TextStyle(
            fontFamily: Nocturne.fontFamily,
            fontSize: 11,
            height: 1.3,
            letterSpacing: 0.04 * 11,
            color: colour == Nocturne.markGreen ? Nocturne.goodText : toneTextFor(colour),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: ClipRRect(
            borderRadius: BorderRadius.circular(2),
            child: Stack(
              children: [
                Container(height: 3, color: Nocturne.neutral800),
                FractionallySizedBox(
                  widthFactor: fraction.clamp(0.0, 1.0),
                  child: Container(height: 3, color: colour),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

/// The readable text colour that pairs with an urgency mark. The marks carry more chroma than a
/// tag fill can, so their labels come from the tag tones rather than from the mark itself.
Color toneTextFor(Color mark) => switch (mark) {
  Nocturne.markRed => Nocturne.criticalText,
  Nocturne.markAmber => Nocturne.warningText,
  _ => Nocturne.goodText,
};

/// MOB-0's readiness ring: an 88px conic sweep with a 70px surface disc punched out of it.
///
/// The numbers are counted from the server's own evaluated cells and nothing else (AUTH-1) — this
/// widget is handed `ready` and `total` and draws them.
class ReadinessRing extends StatelessWidget {
  const ReadinessRing({super.key, required this.ready, required this.total, this.size = 88});

  final int ready;
  final int total;
  final double size;

  @override
  Widget build(BuildContext context) {
    final fraction = total <= 0 ? 1.0 : (ready / total).clamp(0.0, 1.0);
    final inner = size * (70 / 88);

    return Semantics(
      label: '$ready of $total requirements ready',
      excludeSemantics: true,
      child: SizedBox(
        width: size,
        height: size,
        child: CustomPaint(
          painter: _RingPainter(fraction),
          child: Center(
            child: Container(
              width: inner,
              height: inner,
              decoration: const BoxDecoration(color: Nocturne.surface, shape: BoxShape.circle),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text.rich(
                    TextSpan(
                      children: [
                        TextSpan(text: '$ready'),
                        TextSpan(
                          text: '/$total',
                          style: const TextStyle(fontSize: 14, color: Nocturne.neutral600),
                        ),
                      ],
                    ),
                    style: const TextStyle(
                      fontFamily: Nocturne.fontFamily,
                      fontWeight: FontWeight.w500,
                      fontSize: 21,
                      height: 1,
                      color: Nocturne.text,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'READY',
                    style: NoctType.sectionLabel.copyWith(fontSize: 9, letterSpacing: 0.9),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _RingPainter extends CustomPainter {
  const _RingPainter(this.fraction);

  final double fraction;

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    // A sweep from twelve o'clock, which is where the CSS conic gradient starts.
    final paint = Paint()
      ..shader = SweepGradient(
        startAngle: -1.5707963267948966,
        endAngle: 4.71238898038469,
        colors: const [Nocturne.accent, Nocturne.accent, Nocturne.neutral800, Nocturne.neutral800],
        stops: [0, fraction, fraction, 1],
        transform: const GradientRotation(-1.5707963267948966),
      ).createShader(rect);
    canvas.drawOval(rect, paint);
  }

  @override
  bool shouldRepaint(_RingPainter oldDelegate) => oldDelegate.fraction != fraction;
}

/// MOB-11's summary strip: one 8×30 bar per crew member, worst state per bar.
class BarStrip extends StatelessWidget {
  const BarStrip({super.key, required this.colours});

  final List<Color> colours;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: '${colours.length} crew, one bar each',
      excludeSemantics: true,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          for (final (index, colour) in colours.indexed) ...[
            if (index > 0) const SizedBox(width: 3),
            Container(
              width: 8,
              height: 30,
              decoration: BoxDecoration(color: colour, borderRadius: BorderRadius.circular(2)),
            ),
          ],
        ],
      ),
    );
  }
}

// ---------------------------------------------------------------------------
// Forms
// ---------------------------------------------------------------------------

/// Nocturne's `.field` — a 12px label over its control.
class NField extends StatelessWidget {
  const NField({super.key, required this.label, required this.child});

  final String label;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(bottom: 5),
          child: Text(label, style: NoctType.fieldLabel),
        ),
        child,
      ],
    );
  }
}

/// Nocturne's `.input`, at the 44px minimum every control on these screens holds to.
class NTextInput extends StatelessWidget {
  const NTextInput({
    super.key,
    this.controller,
    this.hintText,
    this.keyboardType,
    this.maxLines = 1,
    this.onChanged,
    this.textCapitalization = TextCapitalization.sentences,
  });

  final TextEditingController? controller;

  /// The parser could not fill this one. The mock's wording — "Not read — add it" — is the point:
  /// an empty field with no explanation reads as a bug rather than as a question.
  final String? hintText;

  final TextInputType? keyboardType;
  final int maxLines;
  final ValueChanged<String>? onChanged;
  final TextCapitalization textCapitalization;

  @override
  Widget build(BuildContext context) {
    return TextField(
      controller: controller,
      onChanged: onChanged,
      keyboardType: keyboardType,
      maxLines: maxLines,
      textCapitalization: textCapitalization,
      style: NoctType.input,
      cursorColor: Nocturne.accent,
      decoration: InputDecoration(
        isDense: true,
        hintText: hintText,
        hintStyle: NoctType.input.copyWith(color: Nocturne.neutral600),
        filled: true,
        fillColor: Nocturne.surface,
        constraints: const BoxConstraints(minHeight: 44),
        contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 12),
        border: const OutlineInputBorder(
          borderRadius: Nocturne.borderMd,
          borderSide: BorderSide(color: Nocturne.divider),
        ),
        enabledBorder: const OutlineInputBorder(
          borderRadius: Nocturne.borderMd,
          borderSide: BorderSide(color: Nocturne.divider),
        ),
        focusedBorder: const OutlineInputBorder(
          borderRadius: Nocturne.borderMd,
          borderSide: BorderSide(color: Nocturne.accent, width: 2),
        ),
      ),
    );
  }
}

/// A single-select drawn as Nocturne draws it: a filled accent `check-circle` when chosen, an
/// outline `circle` when not, and the 2px accent left mark on the chosen row.
///
/// Radio semantics, deliberately: these are mutually exclusive answers, and a screen reader that
/// announced them as checkboxes would imply a crew member could pick two reasons.
class ChoiceRow extends StatelessWidget {
  const ChoiceRow({
    super.key,
    required this.label,
    required this.selected,
    required this.onTap,
    this.supporting,
    this.unselectedMark,
    this.unselectedSupportingColour,
  });

  final String label;
  final String? supporting;
  final bool selected;
  final VoidCallback? onTap;

  /// MOB-9's outstanding declaration takes an amber mark rather than none, because it is the one
  /// thing left to do rather than merely an option not taken.
  final Color? unselectedMark;

  final Color? unselectedSupportingColour;

  @override
  Widget build(BuildContext context) {
    final mark = selected ? Nocturne.accent : unselectedMark;
    final glyphColour = selected ? Nocturne.accent : (unselectedMark ?? Nocturne.neutral600);

    return Semantics(
      inMutuallyExclusiveGroup: true,
      checked: selected,
      label: supporting == null ? label : '$label. $supporting',
      excludeSemantics: true,
      child: NCard(
        onTap: onTap,
        leftMark: mark,
        padding: EdgeInsets.fromLTRB(mark != null ? 12 : 14, 12, 14, 12),
        child: Row(
          crossAxisAlignment: supporting == null
              ? CrossAxisAlignment.center
              : CrossAxisAlignment.start,
          children: [
            Icon(
              selected ? PhosphorIconsFill.checkCircle : PhosphorIconsRegular.circle,
              size: supporting == null ? 19 : 20,
              color: glyphColour,
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    label,
                    style: TextStyle(
                      fontFamily: Nocturne.fontFamily,
                      fontSize: 14,
                      height: 1.35,
                      color: selected ? Nocturne.text : Nocturne.neutral300,
                    ),
                  ),
                  if (supporting != null) ...[
                    const SizedBox(height: 3),
                    Text(
                      supporting!,
                      style: NoctType.listSecondary.copyWith(
                        color: unselectedSupportingColour ?? Nocturne.neutral500,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// MOB-5's "what happens" rail: dots joined by a 1px line, the first one live.
class StepTimeline extends StatelessWidget {
  const StepTimeline({super.key, required this.steps});

  final List<({String title, String detail})> steps;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final (index, step) in steps.indexed)
          IntrinsicHeight(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                SizedBox(
                  width: 9,
                  child: Column(
                    children: [
                      Container(
                        width: 9,
                        height: 9,
                        margin: const EdgeInsets.only(top: 5),
                        decoration: BoxDecoration(
                          color: index == 0 ? Nocturne.accent : Nocturne.neutral700,
                          shape: BoxShape.circle,
                          boxShadow: index == 0
                              ? const [BoxShadow(color: Nocturne.accent900, spreadRadius: 4)]
                              : null,
                        ),
                      ),
                      if (index < steps.length - 1)
                        const Expanded(
                          child: VerticalDivider(
                            width: 9,
                            thickness: 1,
                            color: Nocturne.neutral800,
                          ),
                        ),
                    ],
                  ),
                ),
                Expanded(
                  child: Padding(
                    padding: EdgeInsets.fromLTRB(20, 0, 0, index < steps.length - 1 ? 16 : 0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          step.title,
                          style: TextStyle(
                            fontFamily: Nocturne.fontFamily,
                            fontSize: 13.5,
                            height: 1.35,
                            color: index == 0 ? Nocturne.text : Nocturne.neutral300,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(step.detail, style: NoctType.meta),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }
}

/// A 58×58 rounded tile — MOB-6's share targets, and the one Nocturne surface the accent fills
/// (at its 900 step, behind an accent glyph).
class SourceTile extends StatelessWidget {
  const SourceTile({
    super.key,
    required this.icon,
    required this.label,
    required this.onTap,
    this.accented = false,
    this.caption,
  });

  final IconData icon;
  final String label;
  final String? caption;
  final VoidCallback onTap;
  final bool accented;

  @override
  Widget build(BuildContext context) {
    return Pressable(
      onTap: onTap,
      semanticLabel: caption == null ? label : '$label. $caption',
      excludeSemantics: true,
      borderRadius: BorderRadius.circular(16),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 58,
              height: 58,
              decoration: BoxDecoration(
                color: accented ? Nocturne.accent900 : Nocturne.neutral800,
                border: accented ? Border.all(color: Nocturne.accent700) : null,
                borderRadius: BorderRadius.circular(16),
              ),
              child: Icon(
                icon,
                size: accented ? 26 : 24,
                color: accented ? Nocturne.accent : Nocturne.neutral500,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              label,
              textAlign: TextAlign.center,
              style: NoctType.tabLabel.copyWith(
                color: accented ? Nocturne.text : Nocturne.neutral600,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// A dashed 1px outline — the signature block's, and the design system's way of drawing a place
/// where something is *going* to be rather than a surface that already holds something.
///
/// Hand-painted because Flutter's `BorderStyle` offers only `none` and `solid`; a `BoxDecoration`
/// asking for a dash silently draws a solid line, which reads as an ordinary empty card.
class DashedBorder extends StatelessWidget {
  const DashedBorder({
    super.key,
    required this.child,
    this.colour = Nocturne.neutral700,
    this.radius = Nocturne.radiusMd,
    this.dash = 4,
    this.gap = 3,
  });

  final Widget child;
  final Color colour;
  final double radius;
  final double dash;
  final double gap;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      painter: _DashedBorderPainter(colour: colour, radius: radius, dash: dash, gap: gap),
      child: child,
    );
  }
}

class _DashedBorderPainter extends CustomPainter {
  const _DashedBorderPainter({
    required this.colour,
    required this.radius,
    required this.dash,
    required this.gap,
  });

  final Color colour;
  final double radius;
  final double dash;
  final double gap;

  @override
  void paint(Canvas canvas, Size size) {
    final path = Path()
      ..addRRect(RRect.fromRectAndRadius(Offset.zero & size, Radius.circular(radius)));
    final paint = Paint()
      ..color = colour
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;

    for (final metric in path.computeMetrics()) {
      var distance = 0.0;
      while (distance < metric.length) {
        final end = distance + dash;
        canvas.drawPath(metric.extractPath(distance, end.clamp(0, metric.length)), paint);
        distance = end + gap;
      }
    }
  }

  @override
  bool shouldRepaint(_DashedBorderPainter oldDelegate) =>
      oldDelegate.colour != colour ||
      oldDelegate.radius != radius ||
      oldDelegate.dash != dash ||
      oldDelegate.gap != gap;
}

/// The bottom-sheet grabber. 36×4, `neutral-700`, centred.
class SheetGrabber extends StatelessWidget {
  const SheetGrabber({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Container(
        width: 36,
        height: 4,
        decoration: BoxDecoration(
          color: Nocturne.neutral700,
          borderRadius: BorderRadius.circular(2),
        ),
      ),
    );
  }
}

/// Presents [child] as a Nocturne bottom sheet: 20px top radius, `--shadow-lg`, and the grabber.
Future<T?> showNocturneSheet<T>({required BuildContext context, required WidgetBuilder builder}) {
  return showModalBottomSheet<T>(
    context: context,
    backgroundColor: Colors.transparent,
    barrierColor: const Color(0x99000000),
    isScrollControlled: true,
    builder: (sheetContext) => Container(
      decoration: const BoxDecoration(
        color: Nocturne.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        border: Border(
          top: Nocturne.shadowLgEdge,
          left: Nocturne.shadowLgEdge,
          right: Nocturne.shadowLgEdge,
        ),
        boxShadow: Nocturne.shadowLg,
      ),
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SheetGrabber(),
            const SizedBox(height: 12),
            Flexible(child: builder(sheetContext)),
            const SizedBox(height: 24),
          ],
        ),
      ),
    ),
  );
}

/// Copy a business key to the clipboard on a long press. Not in the mock; added because a crew
/// member quoting a record id down a satellite phone is the reason rule 4 exists at all.
Future<void> copyKey(BuildContext context, String value) async {
  await Clipboard.setData(ClipboardData(text: value));
  if (!context.mounted) return;
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Copied $value')));
}
