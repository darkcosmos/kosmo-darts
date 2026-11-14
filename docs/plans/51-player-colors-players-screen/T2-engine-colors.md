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
  `isAcceptable(color, others)` (PC12: PC11, PC13 and the distance to every other player's color; the single check
  used by `defaultFor`, `choicesFor` and the generator, D17). `PlayerColor.fromStored(argb)` forces alpha `FF`
  (D16).
- `.../engine/player/Oklab.kt` (new, internal): sRGB to OKLab and OKLCH to sRGB with gamut check.
- `.../engine/player/ColorChoices.kt` (new): `defaultFor(taken)` (PC2), `choicesFor(index, colors, random)` (PC14,
  PC15).
- Tests: `PlayerColorRulesTest`, `OklabTest`, `ColorChoicesTest` in `game-engine/src/test`;
  `app/src/test/.../ui/theme/PaletteContrastTest.kt` (new).

## Depends on
T1.

## Rules
PC2, PC10 to PC15, PC17.

## Tests
- WCAG: white/black 21:1, a color with itself 1:1, symmetric. OKLab: reference values for white, black, pure red.
- Every palette color: PC11 and PC13 hold; `textOn` gives white for blue and teal, dark for orange and lilac (as the
  design system tokens); the palette pairs satisfy PC12.
- `defaultFor`: empty gives blue; with blue and teal taken gives orange; with another player holding a generated
  color within `MIN_COLOR_DISTANCE` of orange (blue taken), gives teal, not orange; when every free palette color is
  too close, gives a generated acceptable color (PC2).
- `choicesFor` with another player holding a generated color near a free palette color: that palette color is not
  offered (PC14).
- `choicesFor` with 1 to 4 colors taken, seeded `Random`: own color first, no color of another player, at least 4
  swatches, every generated one conforming to PC11 to PC13 and at least `MIN_COLOR_DISTANCE` from all others.
- A loop over 200 seeds with 4 players: never a non-conforming swatch, and `MIN_CHOICES` reached for every seed
  (proves enough conforming colors exist; review U8). A fixed seed with 4 taken colors reaches `MIN_CHOICES`.
- `fromStored` with alpha `00` gives the opaque color; no exception.
- A `Random` that always returns the same value: fewer swatches, never a duplicate or bad one.
- `PlayerColor` rejects a non-opaque value.
- App JVM test `PaletteContrastTest` (review A2): `KosmoColors.Default` background, surface and `player1` to
  `player4` equal the engine constants `BACKGROUND`, `SURFACE` and `PlayerColor.PALETTE`.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest`, `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: rules PC10 to PC15 specified with their thresholds and tests.

## Implementation notes
- API: `ColorChoices.defaultFor(taken, random = Random.Default)` and `ColorChoices.choicesFor(index, colors, random)`
  (`colors` = all players' colors, `index` = the player); `PlayerColorRules` is an `object`.
- Generator draws OKLCH (L 0.45-0.95, C 0.05-0.22, any hue), rejects out-of-gamut, then `isAcceptable` (PC12).
- `PaletteContrastTest` already existed: the engine-constant test was added to it.
- Mid-luminance colors failing PC13 text contrast (4.5) are rejected by `isAcceptable`.
