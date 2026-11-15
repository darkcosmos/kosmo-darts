# Plan: Cricket sound on the dart that closes a sector (issue #82)

## Goal

Issue #82 (https://github.com/darkcosmos/kosmo-darts/issues/82): in Cricket, the dart that closes a sector for the
last player who still had it open (the target becomes dead, CR15) plays a new, dedicated sound, `sfx_closed_sector`,
instead of the dart hit sound. The existing `sfx_closed_cricket_sector` keeps playing for every later dart with a mark
on an already-dead sector (AN30, unchanged). Kosmo: two separate requests (D1).

## Scope and out of scope

In scope:
- A new Cricket cue, the *closing hit*, decided in `:game-engine` from the game before and after the dart.
- A closing dart that wins the leg keeps the normal hit sound and call, then the win, with no closing sound (D5).
- A new sound effect `SECTOR_CLOSING`, playing `res/raw/sfx_closed_sector.ogg` (the issue's file, unchanged).
- Rules: CR15 updated, new CR17, AN29, AN30 and AN24 updated, new AN33; `docs/sounds/CREDITS.md` gets the new file.
- JVM tests citing those rules, including Kosmo's exact scenario (D2).

Out of scope:
- X01 cues and sounds (unchanged; X01 tests stay green untouched).
- The old sound, its rule for later darts (AN30) and its file.
- Any edit of the audio file (gain, length, format): used as delivered (D4, AN24 practice).
- Any voice line for a closure; any visual change (the dead row display, CR23, is unchanged).
- `docs/adr/0004-audio-session-and-settings.md` ("seven decoded sounds"): an ADR records a past decision and is not
  rewritten.

## Approach

1. Engine, `CricketCues.afterDart(before: CricketGame, after: CricketGame)`. With `target` the CR2 target of the dart:
   - 0 marks (CR2): `DartMiss`;
   - `target in before.state.deadTargets` (CR15): `ClosedSectorHit` (AN30, unchanged);
   - `target in after.state.deadTargets` (live before, dead after) and `after.state.winnerIndex == null`: new
     `ClosingHit(dart)` (CR17, AN33);
   - otherwise `DartHit` (a winning closing dart included, D5).
   The win cue still follows the dart cue (AN31), so a winning closing dart gives `[DartHit, Win]`, exactly as any
   winning dart today; the match overload keeps adding the leg outcome to `Win` (AN27). No new state, no stored data.
2. App: `SoundEffect.SECTOR_CLOSING` -> `R.raw.sfx_closed_sector`; `CueScript.of(CricketCue.ClosingHit)` ->
   `AudioAction.DartSound(SECTOR_CLOSING, phrases.dartCall(dart))`: a dart sound (AN10: plays at once, may overlap,
   never queued), followed by the dart call when "Call each dart" is on (AN25).
3. The 3 s length needs no code: `SoundPoolPlayer` reads each sound's real length and `AnnouncementPlayer` makes the
   dart call and the next announcement wait for it (AN10, D4). Decoded size (3 s, stereo, 48 kHz, 16 bit) is about
   576 KB, below the SoundPool per-sound limit.

Alternatives considered:
- Decide the closing dart in `:app` from the board rows: rejected, game-rule logic belongs to `:game-engine`
  (CLAUDE.md, "Architecture").
- Reuse `ClosedSectorHit` with a flag: rejected, two events with two sounds read better as two cue types, and `when`
  exhaustiveness forces every renderer to handle the new one.
- Produce `ClosingHit` for a winning closing dart and swap it for the hit sound in `:app` (`CueScript` or
  `GameCues`): rejected, which cues an action produces is decided by `CricketCues` (AN29), and keeping it there makes
  the rule a `:game-engine` JVM test.

## Files and types added or changed

- `game-engine/.../engine/cricket/CricketCue.kt`: new `CricketCue.ClosingHit(val dart: Dart)`; `CricketCues.afterDart`
  as in Approach step 1.
- `game-engine/src/test/.../engine/cricket/CricketCuesTest.kt`: new and updated cases (Test plan).
- `app/src/main/res/raw/sfx_closed_sector.ogg`: new, the issue's file unchanged (51,006 bytes, SHA-256
  `0e10525b020682abed7835aa4a8c021435d22dd4485e61297c8fde2a2146e4bc`; Ogg Vorbis, stereo, 48 kHz, 3.0 s).
- `app/.../audio/SoundEffect.kt`: `SECTOR_CLOSING`; KDoc "eight sound effects".
- `app/.../audio/SoundPoolPlayer.kt`: `resourceOf(SECTOR_CLOSING) = R.raw.sfx_closed_sector`.
- `app/.../audio/CueScript.kt`: `ClosingHit` mapping.
- Tests under `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/`: `audio/AudioFakes.kt` (length `3_000L`),
  `audio/SoundPoolPlayerTest.kt`, `audio/CueScriptTest.kt`, `audio/AnnouncementPlayerTest.kt`,
  `ui/app/CricketGameCuesTest.kt`.
- `docs/rules/cricket.md`, `docs/rules/announcements.md`, `docs/sounds/CREDITS.md`.

## Data read, stored or exposed

None new. The cue derives from the configuration and the ordered dart list (single source of truth); the save format
and the history archive are unchanged. Nothing leaves the device.

## Screens and states

No screen change. Audio only:
- closing dart, no win: `sfx_closed_sector` instead of `sfx_dart_hit`, then its dart call if "Call each dart" is on;
- closing dart that wins: `sfx_dart_hit` and its dart call, then the win (match: `sfx_victory` with the victory voice;
  leg or set: the voice line), exactly as any winning dart today; no closing sound (D5);
- later dart with marks on that dead sector: `sfx_closed_cricket_sector` (unchanged);
- sounds off: no sound (existing setting).

## Rule changes (written by T1 and T2)

T2, `docs/rules/cricket.md`:
- CR15, first consequence bullet: the dart of P3 that gives him his third mark on 20 makes 20 dead; it is not a
  closed-sector hit but a *closing hit* (CR17). Undo bullet: the target is live again, and the same dart thrown again
  is a closing hit again (owner Q7).
- CR17 (new, engine section; CR17 to CR19 were reserved): a *closing hit* is a dart with at least one mark on a target
  that was live before it and is dead after it (CR15). In a solo game, the dart that gives the player his third mark on
  a target (owner Q5). With 25-Bull off, 25 and Bull darts are never closing hits. A closing hit may also win the leg
  (CR9); its cue is then a normal hit (AN33). The reserved sentence becomes "CR18 and CR19 are reserved".

T2, `docs/rules/announcements.md`:
- AN29: the cue list adds the *closing hit* (CR17, when the dart does not win); a *hit* is a dart with at least one
  mark on a target that was live before it and that is not a non-winning closing hit.
- AN30: "the dart that completes the closure plays the hit sound" becomes "the dart that completes the closure is a
  closing hit (AN33)"; the undone-closing-dart sentence says it is a closing hit again when thrown again.
- AN33 (new, Cricket section): `sfx_closed_sector` plays instead of the hit sound for a closing hit (CR17). It is a
  dart sound (AN10) and its full length (3 s) is kept: the dart call (AN25) and the next announcement wait for its end
  (owner Q3). When the closing dart wins the leg, it plays no closing sound: it is a hit, so `sfx_dart_hit` and its
  dart call, then the win (AN27, AN31), like any winning dart (owner Q4, option b). Undo and resume are unchanged
  (AN23, AN7; owner Q7).

T1, `docs/rules/announcements.md`:
- Line 11 and AN24: eight sounds; `sfx_closed_sector` was added for the Cricket closing hit (AN33), with the same
  licence as the other sounds, as stated by the owner in issue #82. AN24's "all generated with ElevenLabs" sentence is
  narrowed to the seven earlier sounds; no claim about the new file's tool is added.

T1, `docs/sounds/CREDITS.md`: table row "`sfx_closed_sector.ogg` | A Cricket dart closes a sector for the last player
who had it open (AN33)"; bullet "`sfx_closed_sector.ogg`: same licence as the other sounds, as stated by the owner in
issue #82 (« Même licence que les autres sons »)". Nothing else is claimed.

## Test plan

Engine, `CricketCuesTest` (JVM, `:game-engine`); each name cites its rules:
- `CR17 AN33 Kosmo's case: A throws T20 20 20, then B's T20 closes 20 and is a closing hit` (2 players, Standard;
  asserts `[ClosingHit(T20)]`, D2).
- `AN30 CR15 after the closure every dart with a mark on 20 is a closed-sector hit`: same game, B's next `20`, then
  A's next turn `D20` and `T20`: each `[ClosedSectorHit]`.
- Update `AN30 CR15 the dart that closes the target is a hit, the next one on it in the same visit is not`: closing
  hit, then closed-sector hit (rename to say "closing hit").
- `CR17 closing a target for the thrower while an opponent still has it open is a normal hit`.
- `CR17 CR4 a closing dart with surplus marks (B has 1 mark, throws T20) is a closing hit and scores nothing`.
- `CR17 Cut-throat: the closing dart is a closing hit`.
- `CR17 4 players: only the dart of the last of the four to close 20 is a closing hit` (the three earlier closers
  give `DartHit`).
- `CR17 solo: each target's closing dart is a closing hit`; update `AN30 CR15 a solo player's closed target is dead at
  once` (its `T20` is now a closing hit, its next `D20` still a closed-sector hit).
- `CR17 the Bull closed by 25 then BULL is a closing hit`; `CR17 CR2 with 25-Bull off, 25 and BULL are misses, never
  closing hits`.
- The existing `AN31 the winning dart gives its dart cue then the win cue` (solo, its last dart `25` closes the Bull,
  so it is a winning closing dart) stays unchanged and green: `[DartHit(25), Win(MATCH)]`; add `AN33` to its name.
- `AN33 AN31 a 2-player closing dart that wins gives the hit cue then the win, never the closing cue` (Anna leads on
  points and her dart closes the last target that Bob had already closed: `[DartHit, Win]`).
- `AN33 AN27 a leg won by a closing dart gives the hit cue then the leg win cue` (match overload,
  `[DartHit, Win(outcome = LEG)]`).
- `CR9 AN31 a winning dart that closes a target only for the thrower stays a hit then the win` (Bob still has it
  open; unchanged behavior).
- `CR17 AN30 undo of the closing dart makes the target live; thrown again it is a closing hit again` (replaces the
  existing "is a normal hit" expectation).
- A miss on the last open player's turn stays `DartMiss`.

App (JVM/Robolectric, `:app`):
- `CueScriptTest`: `AN33 a closing hit is the closing sound with its dart call`; existing Cricket cases unchanged.
- `SoundPoolPlayerTest`: the resource list includes `R.raw.sfx_closed_sector`; it plays at full volume (AN9).
- `AnnouncementPlayerTest`: `AN33 AN10 the dart call and the next announcement wait for the end of the 3 s closing
  sound` (fake length 3_000L); `AN23 AN33 undo during the closing sound stops it and plays the cancel sound`.
- `CricketGameCuesTest`: `AN33 Kosmo's case through the game events gives the closing cue, then the closed-sector
  cue`; `AN33 AN31 a winning closing dart gives the hit cue then the win cue`; `AN7 AN33 resume gives no cue, then the dart that
  closes a target gives the closing cue` (a save where only Anna closed 20, via `CricketSaveFixtures` or the save
  codec); `AN6 undo of the closing dart gives only the undo cue`.

CLAUDE.md edge cases: bust, double-out and the X01 checkout dialog do not exist in Cricket (CR7); the Cricket win
confirmation is covered by the winning-dart cases; undo at every step (closing dart, dart after it, winning closing
dart through the existing CR11 undo); 1, 2 and 4 players; Standard and Cut-throat; 501 and 301 not affected (X01
tests untouched); restart and rematch start from a fresh dart list; process death: the AN7 resume case. No UI test or
capture: no screen changes.

## How Kosmo verifies

On his Pixel 7, debug APK of the CI for the latest commit, after uninstalling the previous version, sounds on:
1. Cricket, 2 players A and B. A: T20, 20, 20, then "Joueur suivant". B: T20. Expected: the new closing sound, not
   the usual dart sound; the 20 row shows "closed by all".
2. B: 20. Expected: the old "closed sector" sound. On A's next turn, D20: the old sound again.
3. B: T19 (A has no mark on 19): the usual dart sound (only B has closed 19).
4. Start a new game (or come right after step 1, before step 2): A: T20, 20, 20, "Joueur suivant"; B: T20 (closing
   sound). Tap "Annuler" once: the cancel sound, the 20 row is live again (CR11). B: T20 again: the closing sound
   again.
5. In Settings, turn on "Call each dart": on a closing dart, the closing sound plays (about 3 s), then the voice says
   the dart.
6. Finish a game where the winning dart closes the last sector for everybody: the usual dart sound (and its call if
   on), then the victory (sound and voice), no closing sound. In a match, a leg won this way: the usual dart sound,
   then "Manche pour ...".
7. Solo Cricket: each sector closed plays the closing sound, except the last one, which wins (usual dart sound, then
   the victory); a later dart on a closed sector plays the old sound.
8. Cricket with 25-Bull off: 25 and Bull are misses, no closing sound.
9. Leave a game where only A has closed 20, force-stop the app, resume it: nothing plays on resume; B's T20 plays the
   closing sound.
10. 4 players: only the fourth player to close a number hears the closing sound. Cut-throat: same behavior.

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | New sound file, `SECTOR_CLOSING` effect, credits and AN24 | `82-cricket-sector-closing-sound/T1-sound-asset.md` | - | haiku | Copy of a given file with checksum, an enum value, a mapping, listed tests and given doc text; no design choice |
| T2 | Closing-hit cue (none on a winning dart), rendering, rules CR15/CR17/AN29/AN30/AN33 and tests | `82-cricket-sector-closing-sound/T2-closing-hit-cue.md` | T1 | sonnet | Rule fully specified here with its test list; one engine cue plus wiring |

No `opus` task.

## Decisions

- D1 (Kosmo, Q1 = C, Q2 = A): two separate requests. "Toute fléchette qui arrive dans un secteur DEJA fermé doit
  joué le son sfx_closed_cricket_sector. Là, je parle d'ajouter un son sur la fléchette qui ferme le secteur." The new
  `sfx_closed_sector` plays on the dart that closes the sector for the last player; AN30 is unchanged for later darts.
- D2 (Kosmo, reported test): 2 players, A throws T20, 20, 20, then B throws T20: the closing dart must play the new
  sound (today: the normal dart sound). Engine and app tests use this exact sequence.
- D3 (Kosmo, Q6): file name `sfx_closed_sector.ogg`, as in the archive. Cue `CricketCue.ClosingHit`, effect
  `SoundEffect.SECTOR_CLOSING` (plan).
- D4 (Kosmo, Q3): "on laisse la durée": the 3 s file is used as is; the dart call and the next announcement wait for
  its end.
- D5 (Kosmo, Q4 "sur victoire c'est uniquement victoire seule", then "option b"): on a closing dart that wins the leg,
  only the closing sound is dropped: the normal dart sound and its dart call still play, then the win (AN27), as for
  any winning dart. The engine gives `[DartHit, Win]`, never `ClosingHit`, for such a dart.
- D6 (Kosmo, Q5): solo, the closing sound plays at each sector the player closes (the last one wins, so D5 applies).
- D7 (Kosmo, Q7): undo unchanged (AN23); undoing the closing dart reopens the row and plays the cancel sound; thrown
  again, it plays the closing sound again. Nothing plays on resume (AN7).
- D8 (Kosmo, Q6): licence: "Même licence que les autres sons". CREDITS and AN24 record exactly that, attributed to the
  owner in issue #82, with no claim added by the squad about the file's tool or plan.
- D9 (design review S1): T1 lists the zip with `jar tf` and extracts it only if its single entry is
  `sfx_closed_sector.ogg` (no other entry, no path); the pinned size and SHA-256 of the `.ogg` remain the integrity check.
- D10 (design review U1): verification step 4 starts from a fresh game or right after step 1, with one "Annuler" on
  B's closing T20.
- D11 (design review, architecture note): the new `SoundEffect` and `CricketCue` values are added to every `when`
  over them, and every "seven sounds" wording found by grep is updated (T1, T2).
- D12: plan approved by all four reviewers in design review (0 blocking); minors S1 and U1 and the architecture note
  folded in as D9 to D11.

## Open questions

None.
