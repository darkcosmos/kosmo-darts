# T16: Heatmap scale mockups (SVG)

## Goal
Two static SVG mockups so that Kosmo picks the colour scale (owner P9 C) before any heatmap code. Root "Approach" 6,
gate G1. No Kotlin code.

## Files
- `docs/design/mockups/heatmap/heatmap-relative.svg` (new): scale relative to the most hit zone (legend 0 to max %).
- `docs/design/mockups/heatmap/heatmap-absolute.svg` (new): absolute scale (legend 0 to 100 %).
- `docs/design/mockups/heatmap/README.md` (new, short, French): what each file shows, the sample data, the ramp
  values, and the question for Kosmo.
- Each SVG at Pixel 7 portrait width (412 x about 900 px viewBox), background `#0A0F1E`, shows the same two boards:
  "répartie" (darts spread over many zones) and "concentrée" (most darts in T20, S20, S5, S1), each with the misses
  line under it ("Ratés : 12 · 8 %"), the legend, and one zone at 0 in the neutral colour.

## Depends on
T1.

## Rules
HM1, HM2, HM5; design system section 2 (tokens) and PC11 contrast idea (zones readable on `#0A0F1E`).

## Tests
None (documents). Self-check: both files use the same ramp and the same data; the zone-0 colour differs from the
lowest ramp colour; the files render in a browser (open them with any SVG viewer available, or validate the XML with
`xmllint --noout`). A generator script, if used, stays in the scratchpad and is not committed.

## Done when
Both SVGs and the README committed; `./gradlew build` green; `kosmo-detekt` exit 0. Then main stops for gate G1.

## Model
sonnet. Why: one design choice (the ramp, from the design system tokens) and hand-computed geometry.

## Implementation notes
