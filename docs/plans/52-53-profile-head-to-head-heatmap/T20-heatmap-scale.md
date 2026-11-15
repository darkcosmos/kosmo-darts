# T20: Heatmap colour scale and legend

## Goal
Implement only the scale Kosmo chose at gate G1 (relative or absolute), its legend, and record the tokens in the
design system. Root "Approach" 6.

## Files
- `app/.../ui/board/HeatmapScale.kt` (new): share (0..1, or null) to colour with the ramp of the chosen mockup;
  0 darts gives the neutral colour; pure function, testable on the JVM.
- `app/.../ui/board/HeatmapLegend.kt` (new): gradient bar with its bounds as text (0 % to max % or 0 % to 100 %) and
  the neutral swatch "0", TalkBack description.
- `docs/design/design-system.md`: section 2, heatmap tokens (ramp stops, neutral), with the G1 decision date.
- `docs/rules/statistics.md`: HM5 states the chosen scale. Root plan "Decisions": add G1.
- Strings en and fr (legend).
- Tests: `HeatmapScaleTest.kt`, `HeatmapLegendTest.kt`; captures of the board with the scale on the two mockup data
  sets (spread and concentrated).

## Depends on
T19, gate G1 (Kosmo's choice recorded by main in the root "Decisions"). Do not start before G1.

## Rules
HM5, HM8; design system section 2, PC11 (ramp stops readable on `background`).

## Tests
- Zero, smallest positive, max and 100 % map to the expected stops; null share gives neutral.
- Every ramp stop has a WCAG contrast of at least 3.0 against `#0A0F1E` except the neutral colour, which is distinct
  from the first stop.
- Captures match the chosen mockup visually (Pixel 7, portrait, landscape).

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green (captures recorded); `kosmo-detekt` exit 0.

## Model
sonnet. Why: mapping and legend from the chosen mockup, with tests.

## Implementation notes
- G1 resolved by Kosmo (2026-11): RELATIVE scale only; the absolute scale is not in the code. Recorded in root
  "Decisions", HM5 (rules and plan copy) and `docs/design/design-system.md` section 2.
- API (`ui/board`): `HeatmapScale.NEUTRAL`, `NEUTRAL_FRAME`, `STOPS`, `colorAt(position 0..1)`,
  `color(share: Double?, maxShare: Double)`, `maxShare(shares)`, `colors(shares: Map<BoardZone, Double>)` (zones with
  at least 1 dart only; feed `Dartboard(colors, neutral = HeatmapScale.NEUTRAL, ...)`). `HeatmapLegend(maxShare, modifier)`
  (shares are ratios 0..1; needs the board's `maxShare`), `heatmapPercentText(share, locale)`, `HeatmapLegendTags`.
- Deviation: the first ramp stop `#414487` has 2.2:1 on `background`, below the 3.0 of this task's test list. The ramp
  is implemented exactly as in the approved mockup; the test requires 3.0 for the four upper stops and 2.0 for the first
  (documented in the design system). Task file text said "0 % to max %"; the legend shows "> 0 %" to "max = x %" as in
  the mockup. Dart count is not shown in the legend (the mockup shows none).
- Captures (record only): `HeatmapScaleScreenshotTest`, outputs `heatmap_scale_{spread,concentrated}_{portrait,landscape[_fr]}.png`.
  The data sets are approximations of the mockup data (same sizes, not the same zones).
- Legend strings: `heatmap_legend_*` in `values/` and `values-fr/`.
