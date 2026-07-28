/// Nocturne, expressed as Dart.
///
/// This is the same token sheet the admin SPA carries in `admin-web/src/styles.css`, ported
/// value-for-value so the two clients cannot drift apart: a crew member and the coordinator
/// looking at the same swing are looking at the same colours. The ramps were generated in OKLCH
/// on one shared lightness scale, which is the property that makes `neutral700` and `accent700`
/// carry the same visual weight — so pick a *step*, never a hex.
///
/// Four rules the whole file exists to enforce. The first two are the design system's; the last
/// two are this application's and predate it.
///
///  1. **The accent is a line, never a flood.** A 1px border, a 2px left mark, a 5px dot, or a
///     900-step tint. The primary button is an accent *outline* — the single biggest visual
///     departure from the shipped build, which filled it solid blue.
///  2. **Nothing is bolder than 500.** Hierarchy is size and space. There is no 600 in the
///     bundled font, so a `FontWeight.w600` here would silently synthesise or fall back.
///  3. **Colour is never the only carrier of meaning.** Every tag renders its text label. A
///     sunlit deck and a colour-blind reader are the same problem.
///  4. **Monospace is semantic.** Requirement codes, record ids, matrix versions and slot refs
///     are monospaced and nothing else is. It is how a value you can quote is told from prose.
library;

import 'package:flutter/material.dart';

/// The token sheet. Every colour, space, radius and shadow on these screens comes from here.
abstract final class Nocturne {
  // — Ground and text —

  static const bg = Color(0xFF161826);
  static const surface = Color(0xFF232532);
  static const text = Color(0xFFE9E9ED);
  static const accent = Color(0xFF9184D9);

  /// `color-mix(in srgb, #e9e9ed 16%, transparent)` — the system's hairline.
  static const divider = Color(0x29E9E9ED);

  // — Neutral ramp —

  static const neutral100 = Color(0xFFF3F5FE);
  static const neutral200 = Color(0xFFE4E7F5);
  static const neutral300 = Color(0xFFCFD3E5);
  static const neutral400 = Color(0xFFB2B6CA);
  static const neutral500 = Color(0xFF9397AB);
  static const neutral600 = Color(0xFF75798C);
  static const neutral700 = Color(0xFF595D6C);
  static const neutral800 = Color(0xFF3F424D);
  static const neutral900 = Color(0xFF292B31);

  // — Accent ramp —

  static const accent100 = Color(0xFFF5F4FF);
  static const accent200 = Color(0xFFE7E5FE);
  static const accent300 = Color(0xFFD2CEFD);
  static const accent400 = Color(0xFFB5ABFC);
  static const accent500 = Color(0xFF968AE0);
  static const accent600 = Color(0xFF796CBF);
  static const accent700 = Color(0xFF5D5294);
  static const accent800 = Color(0xFF423A6A);
  static const accent900 = Color(0xFF2B2741);

  // — State tones —
  //
  // Nocturne is a mono palette; compliance states are not. These six tones were derived onto the
  // ramps' own lightness steps, so a warning tag and a neutral tag sit at the same visual weight
  // and only the hue differs. `caution` is the quietest warm tone on purpose: unknown, review and
  // pending are an *absence* of information rather than a verdict, and a row nobody has
  // established anything about must not read as a failure.

  static const criticalFill = Color(0xFF4B2030);
  static const criticalText = Color(0xFFF2BFCA);
  static const criticalLine = Color(0xFF6B2F42);
  static const warningFill = Color(0xFF4C3418);
  static const warningText = Color(0xFFF0CFA2);
  static const cautionFill = Color(0xFF3E3F22);
  static const cautionText = Color(0xFFDFE0AB);
  static const goodFill = Color(0xFF1F3B31);
  static const goodText = Color(0xFFB6DED0);
  static const neutralFill = accent800;
  static const neutralText = accent100;
  static const mutedFill = neutral900;
  static const mutedText = neutral500;

  /// The three marks urgency is drawn with — a 2px left edge, a progress fill, a team bar.
  ///
  /// Distinct from the tag tones above, and deliberately so: a tag is a fill behind text and has
  /// to stay legible, whereas these are strokes on the ground and can carry more chroma.
  static const markGreen = Color(0xFF3F7F68);
  static const markAmber = Color(0xFFD79553);
  static const markRed = Color(0xFFB8546E);

  // — Geometry —
  //
  // Density 0.70×, baked into the scale. Use the token, not the number it happens to hold.

  static const space1 = 2.8;
  static const space2 = 5.6;
  static const space3 = 8.4;
  static const space4 = 11.2;
  static const space6 = 16.8;
  static const space8 = 22.4;

  static const radiusSm = 4.0;
  static const radiusMd = 8.0;
  static const radiusLg = 14.0;

  /// `radius-md × 0.75`, the tag radius.
  static const radiusChip = 6.0;

  /// The screen gutter. Every full-width row bleeds to the edge and pads to this.
  static const gutter = 18.0;

  static const borderMd = BorderRadius.all(Radius.circular(radiusMd));
  static const borderChip = BorderRadius.all(Radius.circular(radiusChip));

  // — Type —

  static const fontFamily = 'Inter';

  /// System monospace, per platform.
  ///
  /// Spelled as a primary plus a fallback list rather than a single name because a family that
  /// does not exist on the device resolves to the *proportional* default — which defeats rule 4
  /// silently, on one platform, and looks fine on the other.
  static const monoFamily = 'Menlo';
  static const monoFamilyFallback = <String>['SF Mono', 'Roboto Mono', 'monospace'];

  // — Elevation —
  //
  // On a dark ground elevation is a hairline edge plus ambient darkness, never a soft glow. The
  // CSS spells the hairline as a zero-blur box-shadow; here it is a real border, which is the
  // same pixels and additionally insets the content by 1px the way the mock's padding assumes.

  static const shadowSmEdge = BorderSide(color: neutral800);
  static const shadowMdEdge = BorderSide(color: neutral700);
  static const shadowLgEdge = BorderSide(color: neutral500);

  static const shadowMd = <BoxShadow>[
    BoxShadow(color: Color(0x8C000000), blurRadius: 18, offset: Offset(0, 6)),
  ];
  static const shadowLg = <BoxShadow>[
    BoxShadow(color: Color(0xA6000000), blurRadius: 40, offset: Offset(0, 16)),
  ];
}

/// The six tones a state can take. The mapping from a wire state to one of these lives in
/// `domain/states.dart`; this is only what each tone looks like.
enum Tone { critical, warning, caution, neutral, good, muted }

/// A tone's fill and text pair. Both are always used together — a fill without its paired text
/// colour is how a tag ends up unreadable at one size and fine at another.
({Color fill, Color text}) toneColours(Tone tone) => switch (tone) {
  Tone.critical => (fill: Nocturne.criticalFill, text: Nocturne.criticalText),
  Tone.warning => (fill: Nocturne.warningFill, text: Nocturne.warningText),
  Tone.caution => (fill: Nocturne.cautionFill, text: Nocturne.cautionText),
  Tone.neutral => (fill: Nocturne.neutralFill, text: Nocturne.neutralText),
  Tone.good => (fill: Nocturne.goodFill, text: Nocturne.goodText),
  Tone.muted => (fill: Nocturne.mutedFill, text: Nocturne.mutedText),
};

/// The named type styles, at the sizes the handoff specifies for a 392-wide viewport.
///
/// They are plain [TextStyle]s rather than a [TextTheme] because the design names them by role
/// ("list item secondary", "section label") and Material's slots name them by size. Mapping one
/// onto the other would mean looking up `bodySmall` to find out what a section label is.
abstract final class NoctType {
  static const _heading = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontWeight: FontWeight.w500,
    height: 1.2,
    letterSpacing: -0.015 * 20,
  );

  /// The person's name on a root screen.
  static const screenName = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontWeight: FontWeight.w500,
    fontSize: 22,
    height: 1.2,
    color: Nocturne.text,
  );

  /// A pushed screen's centred title.
  static const navTitle = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontWeight: FontWeight.w500,
    fontSize: 16,
    color: Nocturne.text,
  );

  static final cardTitle = _heading.copyWith(fontSize: 17, color: Nocturne.text);
  static final cardTitleSm = _heading.copyWith(fontSize: 16, color: Nocturne.text);

  /// The kicker above a screen name — `CHIEF OFFICER · UNI CC24`.
  static const kicker = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontSize: 11,
    height: 1.3,
    letterSpacing: 0.12 * 11,
    color: Nocturne.neutral600,
  );

  static const sectionLabel = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontSize: 10,
    height: 1.4,
    letterSpacing: 0.12 * 10,
    color: Nocturne.neutral600,
  );

  static const listPrimary = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontSize: 15,
    height: 1.3,
    color: Nocturne.text,
  );

  static const listPrimarySm = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontSize: 14.5,
    height: 1.3,
    color: Nocturne.text,
  );

  static const listSecondary = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontSize: 11.5,
    height: 1.35,
    color: Nocturne.neutral500,
  );

  /// Body copy inside a card — the sentence under a title.
  static const cardBody = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontSize: 12.5,
    height: 1.45,
    color: Nocturne.neutral500,
  );

  static const bodyText = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontSize: 13,
    height: 1.5,
    color: Nocturne.neutral300,
  );

  /// The closing reassurance lines, and other tertiary meta.
  static const meta = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontSize: 11.5,
    height: 1.45,
    color: Nocturne.neutral600,
  );

  static const tabLabel = TextStyle(fontFamily: Nocturne.fontFamily, fontSize: 10.5, height: 1.2);

  static const tag = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontSize: 11,
    height: 1.3,
    letterSpacing: 0.02 * 11,
  );

  static const button = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontWeight: FontWeight.w500,
    fontSize: 14,
    height: 1.2,
  );

  static const fieldLabel = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontSize: 12,
    height: 1.3,
    color: Nocturne.neutral400,
  );

  static const input = TextStyle(
    fontFamily: Nocturne.fontFamily,
    fontSize: 14,
    height: 1.3,
    color: Nocturne.text,
  );

  /// Rule 4. Every business key, and nothing else.
  static const mono = TextStyle(
    fontFamily: Nocturne.monoFamily,
    fontFamilyFallback: Nocturne.monoFamilyFallback,
    fontSize: 12,
    height: 1.35,
  );
}

/// The application theme.
///
/// There is exactly one, and it is dark. Nocturne has no light mode, and offering a half-ported
/// one would put the crew app on a ground the console does not have — so the app pins
/// [Brightness.dark] rather than following the platform.
ThemeData nocturneTheme() {
  const scheme = ColorScheme.dark(
    primary: Nocturne.accent,
    onPrimary: Nocturne.bg,
    secondary: Nocturne.accent300,
    onSecondary: Nocturne.bg,
    surface: Nocturne.surface,
    onSurface: Nocturne.text,
    surfaceContainerHighest: Nocturne.neutral900,
    onSurfaceVariant: Nocturne.neutral500,
    outline: Nocturne.neutral700,
    outlineVariant: Nocturne.neutral800,
    error: Nocturne.criticalText,
    onError: Nocturne.bg,
  );

  return ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    colorScheme: scheme,
    scaffoldBackgroundColor: Nocturne.bg,
    canvasColor: Nocturne.bg,
    fontFamily: Nocturne.fontFamily,
    splashFactory: InkSparkle.splashFactory,
    // Nocturne's focus ring is a 2px accent outline at 2px offset, everywhere. Material's default
    // is a filled overlay, which on a dark ground reads as a selection rather than a focus.
    focusColor: const Color(0x00000000),
    dividerTheme: const DividerThemeData(color: Nocturne.neutral900, thickness: 1, space: 1),
    textSelectionTheme: const TextSelectionThemeData(
      cursorColor: Nocturne.accent,
      selectionColor: Color(0x4D9184D9),
      selectionHandleColor: Nocturne.accent,
    ),
    progressIndicatorTheme: const ProgressIndicatorThemeData(
      color: Nocturne.accent,
      linearTrackColor: Nocturne.neutral800,
      circularTrackColor: Nocturne.neutral800,
    ),
    snackBarTheme: const SnackBarThemeData(
      backgroundColor: Nocturne.surface,
      contentTextStyle: TextStyle(
        fontFamily: Nocturne.fontFamily,
        fontSize: 13,
        color: Nocturne.text,
      ),
      behavior: SnackBarBehavior.floating,
    ),
    textTheme: const TextTheme(
      bodyLarge: TextStyle(fontSize: 15, height: 1.55, color: Nocturne.text),
      bodyMedium: TextStyle(fontSize: 14, height: 1.5, color: Nocturne.text),
      bodySmall: NoctType.listSecondary,
      titleLarge: NoctType.screenName,
      titleMedium: NoctType.navTitle,
      labelSmall: NoctType.sectionLabel,
    ),
  );
}
