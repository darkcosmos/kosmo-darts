# History rules (profiles, dart journal, board coordinates, match archive, history screens)

Status: draft for design review (issue #50, owner answers Q1 to Q8 of 2026-11-01, recorded in
`docs/plans/50-history-archive.md`, section "Decisions").
This document is the single source of truth for the match history: tests cite these rules, never the issue text.
Rule ids are stable. Prefixes: PF (profiles), DJ (dart journal), BC (board coordinates), AR (archive), HS (history
screens). They never collide with R, M, PC, CR, AN, VT, CS or the X01 rules.

Vocabulary:
- *Name key*: a name trimmed, normalized to NFC and mapped to one case form, exactly the key `PlayerListRules` uses
  to reject duplicate names. Two names with the same key are the same player.
- *Match*: everything from "Start" (or "Rematch") to the confirmed match win, with its legs and sets (M rules).
- *Match id*: a random UUID created when the match starts and carried by the saved game.

## Profiles

- PF1. A profile has a name (the spelling of its first use), a name key (unique), a reset date (always empty in this
  mission), a creation date and a last-used date. It has no color.
- PF2. "Start" and "Rematch" create a profile for each player whose name key matches no profile, and set the
  last-used date of every player's profile to the start time. An existing profile keeps its first spelling (owner Q3).
- PF3. The setup screen keeps the last-setup prefill (R7, R22). Under the name field that has the focus, it lists the
  profiles whose name contains the typed text (ignoring case; all profiles when the field is empty), most recently
  used first, at most 5, without the profiles already named in another field of the setup. Tapping one puts its name
  in the field. Typing a new name is always possible. No popup (owner Q1, Q2).
- PF4. On every launch, before the setup can open, each name of the last setup and of the saved game whose name key
  matches no profile becomes a profile (at most 4 names per file). This replaces the remembered names; it is
  idempotent and never changes an existing profile.
- PF5. No profile is ever deleted. There is no profile management screen in this mission.

## Dart journal

- DJ1. Each match keeps a journal per leg: the darts entered, in entry order, each with its entry time (milliseconds
  since the epoch, from the injected clock) and its source (`MANUAL`; `CAMERA` reserved), and the undos.
- DJ2. Undo no longer removes a dart from the journal: it marks the latest dart still in play of the current leg as
  voided. A voided dart counts for nothing (score, marks, turns, statistics). An undo that changes nothing (nothing
  to undo) is not journaled. What the player sees of undo is unchanged.
- DJ3. Replaying a leg journal gives, for every dart (voided or not): the player, the visit number in the leg (1 for
  the starter's first visit, counting every player's visits in order), the rank in the visit (1 to 3), and for X01 the
  bust flag of its visit (Cricket: never bust). The values of a voided dart are those of the moment it was entered.
- DJ4. The darts still in play of the current leg journal are exactly the darts of the current leg of the match.
- DJ5. A match started before this version has no journal for its confirmed legs (unknown) and a current leg journal
  rebuilt from its darts, without entry times and without voided darts.

## Board coordinates

- BC1. Coordinates are in millimetres on a board of the official dimensions (source cited in the code): origin at
  the centre of the Bull, y axis towards the 20, x axis towards the 6.
- BC2. The nominal point of a manual dart is the centre of its zone: the mean radius of the zone, on the bisector of
  its sector. Single: the outer single zone. Double and triple: the middle of the ring. 25 and Bull: (0, 0). Miss: no
  coordinates.
- BC3. The engine computes the nominal point; the app never computes coordinates.

## Archive

- AR1. A match is archived when its win is confirmed ("Confirm match", M rules, CR25), X01 or Cricket, with its
  settings, players, legs, sets and the whole journal. Nothing else is archived: an abandoned match, a match deleted by
  "New game" then "Delete" (R3), and an unreadable saved game (R9) are never archived (owner Q5).
- AR2. The archive is written in one transaction; then, and only then, the saved game is deleted (R6).
- AR3. While the archive is being written, the confirm button is disabled. If writing fails, the game stays on the
  CHECKOUT zone (X01) or the win zone (Cricket), the saved game is kept, and a short message under the zone says that
  the match could not be saved in the history; the confirm button is enabled again and retries (owner Q4). No popup.
- AR4. A match id is archived at most once. Archiving a match id already archived succeeds without writing anything.
  On launch, a saved game whose match id is already archived (the app was stopped between AR2's two steps) is deleted
  silently and not offered for resume.
- AR5. The archived match stores: the session (type `MATCH`; `TRAINING` reserved and unused), its start (the
  "Start" or "Rematch" tap) and end (the confirmation), the engine rules version and the app version; the mode and
  variant (X01: `501` or `301`; Cricket: `STANDARD` or `CUT_THROAT`) as queryable values and the complete settings in
  a versioned text; for each player in throwing order: profile, color (ARGB), final rank, legs won and sets won; for
  each leg: set number, leg number in the set, starter and winner; for each dart: player, leg, visit, rank, segment,
  multiplier, bust, voided, source, x and y (BC2), entry time.
- AR6. Results (ranks, legs and sets won, leg winners) come from the engine at confirmation and are stored, not
  recomputed when read.
- AR7. A match started before this version (DJ5) is archived with what exists: no start time, no entry times, no
  voided darts, and no darts for its confirmed legs.
- AR8. No match is ever deleted, anywhere in the app.

## History screens

- HS1. The home screen has a "History" button that opens the History screen.
- HS2. History lists the archived matches from the most recent end to the oldest. Each row shows the end date, the
  mode and variant, each player's name (profile name) in the match color, the winner, and the legs and sets score
  when the format has more than one leg.
- HS3. Two filters: mode (All, X01, Cricket) and player (All, or one profile). They combine. No variant filter
  (owner Q6).
- HS4. With no archived match, History says so explicitly. With matches but none matching the filters, it says that
  no match matches, with the filters still shown.
- HS5. Tapping a match opens its detail: summary (settings, duration, ranks, legs and sets), then leg by leg and visit
  by visit, the darts of each visit. Voided darts are not shown; bust visits are marked. A leg without journal (AR7)
  says that its darts were not recorded. Duration: from start to end; "—" without a start time (owner Q8).
- HS6. Both screens are read-only (no delete button), without popups, in portrait and landscape, follow
  `docs/design/`, and stay fluid with 1,000 archived matches.

## Related rules

- Persistence: R6 (deletion at the confirmed win, now after the archive, AR2), R12 (save size), R26 to R29
  (format version 5, `docs/adr/0011-save-format-v5-journal.md`) (`docs/rules/persistence.md`).
- Database: `docs/adr/0012-archive-database.md`.
- Players: PC9 (the color never carries information alone).
