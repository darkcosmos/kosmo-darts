# T2: Closing-hit cue, rendering, rules and tests

## Goal
The dart that makes a Cricket target dead gives `CricketCue.ClosingHit` and plays `SoundEffect.SECTOR_CLOSING`; when
that dart wins the leg, it stays a `DartHit` followed by the win (root plan, Approach steps 1 and 2; Decisions D1, D2,
D4 to D7).

## Files
- `game-engine/.../engine/cricket/CricketCue.kt`: `data class ClosingHit(val dart: Dart)` with KDoc (CR17, AN33);
  `CricketCues.afterDart`: miss, closed-sector hit (dead before), closing hit (live before, dead after, no winner),
  hit (a winning closing dart included); the win cue still follows the dart cue.
- `game-engine/src/test/.../engine/cricket/CricketCuesTest.kt`.
- `app/.../audio/CueScript.kt`: `ClosingHit` -> `DartSound(SECTOR_CLOSING, phrases.dartCall(dart))`; KDoc AN33. Add
  the new cue to every `when` over `CricketCue` (`grep -rn 'CricketCue\.' app/src game-engine/src`) (design review
  architecture note).
- `app/src/test/.../audio/CueScriptTest.kt`, `audio/AnnouncementPlayerTest.kt`, `ui/app/CricketGameCuesTest.kt`.
- `docs/rules/cricket.md` (CR15, new CR17, reserved sentence) and `docs/rules/announcements.md` (AN29, AN30, new
  AN33): as in the root plan, "Rule changes", T2 part.

## Depends on
T1 (`SoundEffect.SECTOR_CLOSING`).

## Rules
CR2, CR4, CR9, CR15, CR17, AN6, AN7, AN10, AN23, AN25, AN27, AN29, AN30, AN31, AN33.

## Tests
Root plan, "Test plan", every case: Kosmo's case (A: T20 20 20, then B: T20 is `ClosingHit`); every later dart with
a mark on the dead target is `ClosedSectorHit`; thrower-only closure is `DartHit`; surplus; Cut-throat; 4 players;
solo; Bull; 25-Bull off; winning closing dart `[DartHit, Win]` for MATCH (the existing solo test, unchanged) and LEG,
and in 2 players; winning dart closing only for the thrower `[DartHit, Win]`;
undo then rethrow; miss; and the app cases (`CueScriptTest`, `AnnouncementPlayerTest` 3 s wait and undo,
`CricketGameCuesTest` Kosmo's case, winning closing dart, resume then closing dart, undo). Existing expectations
that encode the old rule (closing dart = `DartHit`) are updated, never deleted.

## Done when
`./gradlew :game-engine:test`, `./gradlew :app:testDebugUnitTest` and `./gradlew build` green; `kosmo-detekt` exit 0;
committed.

## Model
sonnet. Why: the rule is fully specified in the plan with its tests; one engine cue plus its wiring.

## Implementation notes
- No deviation. `CricketCues.afterDart` decides `ClosingHit` with `after.isClosedSectorHit(dart)` (target dead after)
  and no winner; a closed-sector hit is still decided on `before`. `CueScript.of` is the only `when` over `CricketCue`.
- `docs/sounds/CREDITS.md` line 3 narrowed to the seven earlier sounds (orchestrator request); E5 in
  `docs/rules/announcements.md` left unchanged (historical row).
- `AudioFakes.kt` and `SoundPoolPlayerTest.kt` were already done by T1.
