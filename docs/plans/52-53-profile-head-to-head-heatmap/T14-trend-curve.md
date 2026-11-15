# T14: Trend curve component

## Goal
The trend curve drawn with Compose Canvas, no chart library, in the X01 and Cricket sections. Root "Rules", PS5.

## Files
- `app/.../ui/profile/TrendCurve.kt` (new): line of points scaled to min and max with a small margin, `accent` line
  on `surface`; shown as text: lowest and highest values and the first and last match dates, all from the state's
  `TrendSummary` (T8, T11), never computed in the composable; fewer than 2 points shows the text instead.
- `app/.../ui/profile/TrendDescription.kt` (new): the TalkBack text (count, first, last, lowest, highest, two dates)
  built from `TrendSummary`, testable without a device.
- `app/.../ui/profile/X01Section.kt`, `CricketSection.kt`: replace the placeholder.
- Strings en and fr.
- Tests: `TrendDescriptionTest.kt`, `TrendCurveTest.kt` (semantics); captures added to `ProfileScreenshotTest.kt`.

## Depends on
T13.

## Rules
PS5, PS8; design system section 2 (colours) and 8 (accessibility).

## Tests
- 0, 1, 2 and 1,000 points; equal values (flat line, no division by zero).
- Description text in English and French (JVM): values and dates of a given `TrendSummary`.
- Min, max and date labels shown on the screen equal the summary values.
- Captures: 10 points X01, 30 points Cricket, 1 point (text), portrait and landscape.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green (captures recorded); `kosmo-detekt` exit 0.

## Model
sonnet. Why: Canvas component specified by PS5, tests and captures.

## Implementation notes
- `TrendCurve.kt`: `TrendCurve(display)` Canvas (96 dp, `surface` background, `accent` line plus a dot on the last
  point), scaled between the summary's lowest and highest (flat line at mid height when equal); nothing drawn under 2
  points. Tag `ProfileTags.TREND_CURVE`; its TalkBack description is the whole `trendDescription`.
- `TrendDescription.kt`: `trendDescription`, `trendValueText`
  (average or MPR), `trendDateText`. `dateText` (medium locale format) now lives in `ProfileText.kt` and `sinceText`
  uses it. `TrendDisplay` (points, engine summary, description) sits in `ProfileScreen.kt` with `FigureBlock`. Strings `profile_trend_*` and plural `profile_trend_description` in values/ and values-fr/.
- Divergences: `trendBlock(points, summary, isCricket)` is shared by X01 and Cricket (both sections used the same
  placeholder); `FigureBlock` gained `trend: TrendDisplay?`; "Matches in the curve" now reads `summary.count`.
  Under 2 points the block shows the note "A curve needs at least 2 matches." (tag `TREND_TOO_FEW`); lowest, highest and
  the two dates still show from 1 point. Rows: matches, lowest, highest, first match, last match.
- `FigureRowView` is now a `FlowRow` (space between): a value that does not fit moves under its label instead of the
  label being cut mid-word (seen with a long French date at 200 % font in landscape).
- Tests: `TrendDescriptionTest` (5), `TrendCurveTest` (8, labels come from a summary that differs from the points),
  captures `trend_*` in `ProfileScreenshotTest` (X01 10 points, Cricket 30 points, 1 point; portrait EN, landscape FR,
  200 % font). Test helper `TrendTestData` (`withTrend` replaces the trend of a state).
