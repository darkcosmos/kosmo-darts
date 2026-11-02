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
  of %s", "selected"; generated swatches read "Color %1$d of %2$d" (review U3).
- Layout (U1, U2): the swatches wrap with `FlowRow` (no horizontal scroll), so up to 7 fit at 200% font; when the
  row opens, it is brought into view (`bringIntoViewRequester`), also under the 4th player in landscape. The check
  mark is drawn in the PC13 text color of the swatch; the selected swatch also has a 2dp `onBackground` ring and
  TalkBack "selected" state, so selection is not carried by the check alone.
- Random (A4): `SetupState` functions take the `Random` as a parameter, passed by the reducer from its injected
  source; no `Random` is created in a composable.
- Tests: `SetupStateColorTest` (JVM), `SetupColorFlowTest` (Compose), `SetupColorScreenshotTest` (Roborazzi).

## Depends on
T4.

## Rules
PC1 to PC4, PC9, PC12 to PC16; M13.

## Tests
- Toggle opens the row for one player, closes it on a second tap; opening another player's row moves it.
- Picking applies the color and closes the row; others unchanged; add, remove, shuffle keep PC2 to PC4.
- A color of another player is absent from the row (the lightened refusal test, Q3).
- Swatches are the same on repeated reads while the row is open; closing discards them, reopening draws again
  (review T2). Removing the player whose row is open closes it; removing another player keeps it open.
- Adding a player while another holds a generated color near a free palette color gives an acceptable color (PC2,
  review T1).
- A generated color survives (review T3), X01 and Cricket: undo, restart, start then rematch, and process-death
  resume (save written, state rebuilt from the saved game) then rematch.
- TalkBack: swatch descriptions and "selected"; touch targets at least 48dp.
- Captures, Pixel 7, portrait, landscape and 200% font, seeded random: default palette setup; row open with
  generated colors in a 4-player setup; row open under the 4th player in landscape at 200% font.

## Done when
`./gradlew :app:testDebugUnitTest` (records the captures under `app/build/outputs/roborazzi`), `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen and state holder from specified rules, JVM tests listed.

## Implementation notes
- Deviations: `ColorSwatchRow` lives in `ui/components/` (not `ui/setup/`) so `PlayerNameRow` and T10 can use it; tags via
  `swatchTag(rowTag, color)`. `withSwatchesToggled` / `withColorPicked` are extension functions in `SetupState.kt`
  (detekt TooManyFunctions); `SwatchRow(index, colors)` is there too. `SetupState.withPlayerAdded(random)` takes the Random.
- Behavior: adding a player or shuffling closes the open row; removing the open player closes it, removing an earlier
  one shifts its index; a pick of a color not offered or held by another player is ignored.
- Events `ColorSwatchTapped(index)` and `ColorPicked(color)` go through `editSetup` with the reducer's Random.
- Strings: `color_blue/orange/teal/lilac`, `color_generated` ("Color %1$d of %2$d"), `color_selected`, `setup_change_color`.
- No "restart" event exists in the app, so the T3 survival test covers start, undo, rematch, resume then rematch (X01) and
  start, resume then rematch (Cricket) in `SetupStateColorTest`.
- Captures: `app/build/outputs/roborazzi/setup_colors_*.png` (default, open, open at 200%, 4th player landscape 200%).
- Checks: `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.
