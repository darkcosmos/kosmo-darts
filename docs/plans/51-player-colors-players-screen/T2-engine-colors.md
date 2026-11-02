# T2: Engine color rules

## Goal
Add player colors as ARGB values and every color rule to `:game-engine`, unused by the app yet. Root "Approach" 1
and 3.

## Files
- `game-engine/.../engine/player/PlayerColor.kt` (new): `@JvmInline value class PlayerColor(val argb: Int)`, opaque
  only (`require` alpha `FF`); `companion` with `BLUE`, `ORANGE`, `TEAL`, `LILAC`, `PALETTE` (that order).
- `.../engine/player/PlayerColorRules.kt` (new): constants `BACKGROUND`, `SURFACE`, `TEXT_LIGHT`, `TEXT_DARK`,
  `MIN_COLOR_CONTRAST = 3.0`, `MIN_TEXT_CONTRAST = 4.5`, `MIN_COLOR_DISTANCE = 0.12`, `MIN_CHOICES = 4`,
  `MAX_GENERATION_ATTEMPTS = 200`; `contrastRatio(a, b)`, `distance(a, b)`, `isReadable(c)`, `textOn(c)`,
  `isConforming(c, taken)`.
- `.../engine/player/Oklab.kt` (new, internal): sRGB to OKLab and OKLCH to sRGB with gamut check.
- `.../engine/player/ColorChoices.kt` (new): `defaultFor(taken)` (PC2), `choicesFor(index, colors, random)` (PC14,
  PC15).
- Tests: `PlayerColorRulesTest`, `OklabTest`, `ColorChoicesTest` in `game-engine/src/test`.

## Depends on
T1.

## Rules
PC2, PC10 to PC15, PC17.

## Tests
- WCAG: white/black 21:1, a color with itself 1:1, symmetric. OKLab: reference values for white, black, pure red.
- Every palette color: PC11 and PC13 hold; `textOn` gives white for blue and teal, dark for orange and lilac (as the
  design system tokens); the palette pairs satisfy PC12.
- `defaultFor`: empty gives blue; with blue and teal taken gives orange.
- `choicesFor` with 1 to 4 colors taken, seeded `Random`: own color first, no color of another player, at least 4
  swatches, every generated one conforming to PC11 to PC13 and at least `MIN_COLOR_DISTANCE` from all others.
- A loop over 200 seeds with 4 players: never a non-conforming swatch.
- A `Random` that always returns the same value: fewer swatches, never a duplicate or bad one.
- `PlayerColor` rejects a non-opaque value.

## Done when
`./gradlew :game-engine:test`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: rules PC10 to PC15 specified with their thresholds and tests.

## Implementation notes
