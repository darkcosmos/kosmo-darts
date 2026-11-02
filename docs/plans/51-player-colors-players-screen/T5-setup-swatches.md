# T5: Setup swatch row

## Goal
In the X01 and Cricket setup, each player can change his color through a row of swatches, inside the screen. Root
"Screens and states", Setup.

## Files
- `app/.../ui/setup/SetupState.kt`: `openSwatches: SwatchRow?` (player index and offered colors), `withSwatchesToggled
  (index, random)`, `withColorPicked(color)`; drawn with `ColorChoices.choicesFor`.
- `app/.../ui/app/AppState.kt`, `AppReducer.kt` or `SetupTransitions.kt`: events `ColorSwatchTapped(index)`,
  `ColorPicked(color)`; the reducer's `Random` is injected (as for shuffle).
- `app/.../ui/components/PlayerNameRow.kt`: the color strip becomes a 48dp tappable swatch.
- `app/.../ui/setup/ColorSwatchRow.kt` (new), `SetupTags.kt`.
- `values/strings.xml`, `values-fr/strings.xml`: swatch descriptions (palette names, "Color %d"), "Change the color
  of %s", "selected".
- Tests: `SetupStateColorTest` (JVM), `SetupColorFlowTest` (Compose), `SetupColorScreenshotTest` (Roborazzi).

## Depends on
T4.

## Rules
PC1 to PC4, PC9, PC14 to PC16; M13.

## Tests
- Toggle opens the row for one player, closes it on a second tap; opening another player's row moves it.
- Picking applies the color and closes the row; others unchanged; add, remove, shuffle keep PC2 to PC4.
- A color of another player is absent from the row (the lightened refusal test, Q3).
- Start then rematch keeps a generated color.
- TalkBack: swatch descriptions and "selected"; touch targets at least 48dp.
- Captures, Pixel 7, portrait, landscape and 200% font, seeded random: default palette setup; row open with
  generated colors in a 4-player setup.

## Done when
`./gradlew :app:testDebugUnitTest` (records the captures under `app/build/outputs/roborazzi`), `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen and state holder from specified rules, JVM tests listed.

## Implementation notes
