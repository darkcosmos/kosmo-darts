# T4: Archived match builder

## Goal
`:game-engine` builds the complete `ArchivedMatch` of a confirmed X01 or Cricket match from the match, its journal
and the end time (AR5, AR6; AR7 withdrawn, clean slate D25). Root plan "Approach" 2.

## Files
- Added in `game-engine/src/main/kotlin/.../engine/archive/`: `ArchivedMatch.kt` (session type `MATCH`/`TRAINING`,
  `startedAt: Long`, `endedAt`, `rulesVersion`, `mode`, `variant`, typed settings, players, legs, darts),
  `ArchivedPlayer.kt` (position, name, color slot, final rank, legs won, sets won), `ArchivedLeg.kt` (ordinal, set
  number, leg number in set, starter, winner), `ArchivedDart.kt` (`JournalDart` fields + segment,
  multiplier, `BoardPoint?` for a miss until open question 2 is answered), `MatchArchiver.kt`
  (`fun archive(match: X01Match, journal, endedAt)` and the Cricket overload), `EngineRulesVersion.kt` (`ENGINE_RULES_VERSION = 1`).
- Tests in `game-engine/src/test/kotlin/.../engine/archive/`.

## Depends on
T2 (nominal points), T3 (journal and replay).

## Rules
AR5, AR6, BC2, DJ3, DJ4; M rules for set and leg numbers (`docs/rules/match.md`); CR12 ranking.

## Approach
- Only a match whose win is pending confirmation is archived; anything else throws `IllegalArgumentException`.
- Ranks, legs and sets won, leg winners from `MatchScore` and `MatchRanking` (AR6), including the deciding leg.
- Set and leg numbers come from the leg ordinal and the match format; starter from M6 (`legs % players`) as the
  match does it.
- Darts: replay of each leg journal (T3), nominal point (T2). Voided darts are kept with `voided = true`.
- Every field is non-nullable (D31), except the dart point (root plan, open question 2).
- The journal must have one entry per leg; its last leg's darts in play must equal the match's current leg (DJ4),
  else `IllegalArgumentException`.

## Tests
- X01 single leg, 2 players; X01 2 sets of 3 legs, 3 players, with undos; Cricket standard and cut-throat with
  25-Bull, 4 players, 2 legs; solo match.
- Results equal `MatchScore` / `MatchRanking`; set and leg numbers across a set boundary.
- One archive per X01 finish mode: double-out (a bust visit, then a double finish) and single-out (a single
  finish), the bust flags and the winning dart (not bust) checked in the archived darts.

## Done when
`./gradlew :game-engine:test` and `./gradlew build` green; `kosmo-detekt` exit 0.

## Model
sonnet. Why: engine mapping over existing match types with specified rules.

## Implementation notes
