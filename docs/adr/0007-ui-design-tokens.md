# ADR 0007: UI design tokens, fixed sizes, layout policy and embedded font

- Status: Proposed (design review of issue #49); accepted when its PR is merged
- Date: 2026-10-22
- Values: `docs/design/design-system.md` (authoritative for every color, size and rule); this ADR records how the app
  applies them.

## Context

Issue #49 applies the "Retransmission TV / Plein écran" design system to every screen. The app needs one place for
the tokens, text sizes that ignore the Android font size setting for a few styles, a layout that keeps the score
readable from the throwing line on any screen without clipping anything, and the Saira display font, offline.

## Decisions

1. **Tokens through composition locals, mapped onto Material 3.** `KosmoColors` (every color token, the four player
   colors and their text colors) and `KosmoTypography` are provided by `KosmoDartsTheme` through
   `CompositionLocal`s and read with `KosmoTheme.colors` / `KosmoTheme.typography`. The Material 3 color scheme is
   mapped from the tokens (background, surfaces, primary = `accent`, error = `danger`) so the remaining Material
   components (dialogs, text fields) follow. No dynamic color. `PlayerPalette` maps an engine `ColorSlot` to its
   tokens; the engine knows slots only (`docs/rules/players.md`).
   - Alternative rejected: only the Material color scheme. It has no room for the player colors and `accentDim`, and
     its role names do not match the design system.
2. **Fixed sizes with `Density.fixedSp`.** The score, keypad, dart, visit box and tile score styles are built for
   the current density with `dp.toSp()`, so the conversion back to pixels cancels the font scale, Android 14's
   non-linear scaling included. Every other style follows the setting.
3. **Layout policy outside composables.** `GameLayoutPolicy` and `ScreenLayoutPolicy` (`ui/layout`) are pure
   functions of plain numbers (available width and height in dp after the insets, effective text scale, player
   count): they choose the key and action bar heights, margins, gaps, tile variant, the score size and the zone's
   least height, applying the degradation steps of the design system in order. They have no Compose type and are
   tested on the JVM. On screen, `FillColumn` measures the real heights of the other parts and gives the zone the
   rest (never less than the policy's least height), and scrolls when the parts do not fit: the estimates choose
   sizes, they never decide what gets clipped. The score size is then fitted to its area for the widest score,
   "888", so it never changes with the digits shown.
4. **Embedded variable font.** `docs/design/font/Saira[wdth,wght].ttf` is copied unchanged to
   `app/src/main/res/font/saira_variable.ttf` and used through `FontVariation` (width 75, weights 700 and 800;
   minSdk 30 is above the API 26 the axes need). Its SIL OFL 1.1 license is shipped as
   `app/src/main/assets/licenses/saira-OFL.txt` and credited in the settings. The font is never downloaded: no
   downloadable fonts, no Google Play services, no network.

## Consequences

- Composables read tokens and the policy's numbers; they hold no layout decision beyond applying them.
- A new color must be added to `KosmoColors` and to the design system together; `PaletteContrastTest` checks the
  contrast pairs.
- Robolectric honours the font's width axis (`SairaWidthSpikeTest`) but not the line height of the styles, so the
  captures show the score somewhat smaller than the phone does.
