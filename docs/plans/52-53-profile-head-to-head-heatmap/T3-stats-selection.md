# T3: Stats input, validation and match selection

## Goal
The validated engine input for statistics and the selection of matches by reset date, filters and period. Root
"Approach" 1, 2 and 2b.

## Files
- `game-engine/.../engine/stats/StatsMatch.kt` (new): `RawStatsMatch` (plain values mapped by the app: session id,
  startedAt, endedAt, encoded settings, players with profile id, position, rank, legs won, sets won; legs with stored
  winner; darts ordered by leg, visit, rank, with the voided flag). `StatsMatch` with an `internal` constructor and
  per-dart facts (X01 remaining before the dart; Cricket `DartYield` from T5), `isSolo`, `dartsOf(position)`
  excluding voided darts (ST1).
- `game-engine/.../engine/stats/StatsMatchValidator.kt` (new): `validate(raw): Valid(StatsMatch) | Invalid(reason)`,
  checks of root "Approach" 2b and ST10, one replay per leg with the existing `X01Game` / `CricketGame`; stops at the
  first rejected dart. Never throws.
- `game-engine/.../engine/stats/X01Remaining.kt` (new): remaining before each dart, bust visits restore the score.
- `game-engine/.../engine/stats/MatchSelection.kt` (new): `X01Filter(startScore: Int?, checkoutRule: CheckoutRule?)`,
  `CricketFilter(variant: CricketVariant?)`, `Period { SINCE_ALWAYS, LAST_30_DAYS, LAST_10_MATCHES }`, constants
  `PERIOD_DAYS = 30`, `LAST_MATCHES = 10`; `select(matches, profileId, resetAt, filter, period, now)`.
- Tests: `StatsMatchValidatorTest.kt`, `MatchSelectionTest.kt`, and a builder `StatsMatchFixtures.kt` in the test
  sources (valid matches and each corrupt variant below), reused by later tasks.

## Depends on
T5 (for `DartYield`).

## Rules
ST1, ST2, ST3, ST4, ST10; DJ2 (voided darts).

## Tests
- ST2: start equal to the reset date counts, one millisecond before does not.
- ST3: every X01 combination (All, 501, 301 x All, double, single); Cricket All, Standard, Cut-throat; a Cricket match
  never passes an X01 filter and the reverse.
- ST4: end exactly 30 x 24 h ago counts, older does not; last 10 with 9, 10 and 11 matches; last 10 is taken after
  filters and ST2 (a filtered-out recent match does not take a slot).
- Voided darts are absent from `dartsOf`.
- ST10, each gives `Invalid` with no exception: unknown segment or multiplier; a dart after the leg win; undecodable
  settings; a leg without darts; a rank, a player position, legs won or sets won out of range; a stored leg winner
  different from the replay. X01 (501, 301, double and single out) and Cricket (both variants).
- Valid 1- to 4-player X01 and Cricket matches give `Valid`, with the expected remaining and yields per dart.
- Validating a list with one corrupt match among good ones returns the good ones unchanged.

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: validation specified by ST10 over the existing engines, filters and periods as pure functions.

## Implementation notes
Deviations from the task text:
- `RawStatsMatch.settings` is the decoded `ArchivedSettings?` (`null` = does not decode), not an encoded string:
  `SettingsText.decode` lives in `:app`, so the app decodes and maps a failure to `null`. `RawStatsDart.multiplier` is
  `Multiplier?` (`null` = unknown value). Profile ids are `Long`, positions 0-based, ranks 1-based.
- T5 has no `PlayedDart`: Cricket yields come from `usefulMarks(config, darts, starter)` (`CricketYields.isValid`).
- `StatsDart` carries `remainingBefore` (X01, else null), `isBust` (X01, derived from the replay, not the stored flag)
  and `yield` (Cricket, else null), plus the stored `visit` and `rank`. Voided darts never enter `StatsMatch`.
- Extra ST10 checks: the stored thrower of each dart must equal the replayed thrower (`DART_THROWER`); duplicated leg
  ordinals, a dart of an unknown leg, a leg starter or winner out of range, a start score other than 501/301.
- Where: `StatsMatchValidator` (`validate`, `validateAll` -> `StatsMatches(matches, ignoredCount)`), replay per leg in
  `LegFacts.kt` and `X01Remaining.kt` (`x01Remaining`), `InvalidReason` enum for rejections.
- `MatchSelection.kt`: `select(...)` takes a `StatsFilter` (`AllModes`, `X01Filter`, `CricketFilter`; `null` = All),
  keeps only matches where `profileId` plays, keeps the input order, "Last 10" ties break by `startedAt` desc.
- Fixtures: `StatsMatchFixtures.x01Raw/cricketRaw` (player 0 wins every leg, profile id = position + 1; each corrupt
  variant is a `copy`), `x01(...)`/`cricket(...)` give the validated `StatsMatch`.
