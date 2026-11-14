# T14: Trend curve component

## Goal
The trend curve drawn with Compose Canvas, no chart library, in the X01 and Cricket sections. Root "Rules", PS5.

## Files
- `app/.../ui/profile/TrendCurve.kt` (new): line of points scaled to min and max with a small margin, axis labels
  min and max, `accent` line on `surface`; fewer than 2 points shows the text instead.
- `app/.../ui/profile/TrendDescription.kt` (new): the TalkBack text (count, first, last, lowest, highest), testable
  without a device.
- `app/.../ui/profile/X01Section.kt`, `CricketSection.kt`: replace the placeholder.
- Strings en and fr.
- Tests: `TrendDescriptionTest.kt`, `TrendCurveTest.kt` (semantics); captures added to `ProfileScreenshotTest.kt`.

## Depends on
T13.

## Rules
PS5, PS8; design system section 2 (colours) and 8 (accessibility).

## Tests
- 0, 1, 2 and 1,000 points; equal values (flat line, no division by zero).
- Description text in English and French.
- Captures: 10 points X01, 30 points Cricket, 1 point (text), portrait and landscape.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green (captures recorded); `kosmo-detekt` exit 0.

## Model
sonnet. Why: Canvas component specified by PS5, tests and captures.

## Implementation notes
