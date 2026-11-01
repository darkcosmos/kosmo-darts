# History rules (profiles, dart journal, board coordinates, match archive, history screens)

Status: draft for design review (issue #50, owner answers Q1 to Q8 of 2026-11-01 and the clean slate decisions D24,
D25, recorded in `docs/plans/50-history-archive.md`, section "Decisions").
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
  profiles whose name key contains the name key of the typed text (all profiles when the field is empty), most
  recently used first, at most 5, without the profiles already named in another field of the setup, and without the
  profile whose name is exactly the text of the focused field (a profile with the same key but another spelling stays,
  so tapping it applies the profile's spelling). Tapping one puts its name in the field. Typing a new name is always
  possible. No popup (owner Q1, Q2). Matching is done in memory on name keys, never with SQL `LIKE`.
- PF4. Withdrawn (owner decision D25, clean slate): no profile is created from the last setup or the saved game.
  Profiles are created only by PF2, at the first "Start" or "Rematch" with a name.
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
- DJ5. Withdrawn (clean slate, R30): every match has a journal for each of its legs and an entry time for each dart.

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
- AR3. While the archive is being written, the confirm button is disabled, its label says "Saving…" and its
  description says the match is being saved; a second tap does nothing. If writing fails, the game stays on the
  CHECKOUT zone (X01) or the win zone (Cricket), the saved game is kept, and a short text message under the zone (also
  announced by TalkBack, never shown by color only) says that the match could not be saved in the history; the
  confirm button is enabled again and retries (owner Q4). No popup. The saved game is deleted only after the archive
  was written or answered "already archived" (AR4). The archiving state is not saved: after a process death the game
  resumes on the zone with the confirm button enabled.
- AR4. A match id is archived at most once. Archiving a match id already archived succeeds without writing anything.
  On launch, a saved game whose match id the database positively reports as archived (the app was stopped between
  AR2's two steps) is deleted silently and not offered for resume. Any database error during this check keeps the
  saved game and offers it as usual.
- AR5. The archived match stores: the session (type `MATCH`; `TRAINING` reserved and unused), its start (the
  "Start" or "Rematch" tap) and end (the confirmation), the engine rules version and the app version; the mode and
  variant (X01: `501` or `301`; Cricket: `STANDARD` or `CUT_THROAT`) as queryable values and the complete settings in
  a versioned text; for each player in throwing order: profile, color (ARGB), final rank, legs won and sets won; for
  each leg: set number, leg number in the set, starter and winner; for each dart: player, leg, visit, rank, segment,
  multiplier, bust, voided, source, x and y (BC2), entry time.
- AR6. Results (ranks, legs and sets won, leg winners) come from the engine at confirmation and are stored, not
  recomputed when read.
- AR7. Withdrawn (clean slate, R30): every archived match has its start time, all its legs' darts and their times.
- AR8. No match is ever deleted, anywhere in the app.
- AR9. A database that cannot open, is corrupt, is full or fails on a read or write never crashes the app and is
  never deleted or recreated empty by the app. At launch, the saved game is then loaded and offered as usual (R2).
  The archive fails as AR3 says. History and detail show HS7.
- AR10. The archived dart values derived by the engine (visit, rank, bust) are a snapshot under the session's rules
  version; they are never recomputed when read.

## History screens

- HS1. The home screen has a "History" button that opens the History screen.
- HS2. History lists the archived matches from the most recent end to the oldest. Each row shows the end date, the
  mode and variant, each player's name (profile name) in the match color, the winner, and the legs and sets score
  when the format has more than one leg.
- HS3. Two filters: mode (All, X01, Cricket) and player (All, or one profile). They combine. No variant filter
  (owner Q6).
- HS4. With no archived match, History says so explicitly. With matches but none matching the filters, it says that
  no match matches, with the filters still shown and a "Show all matches" button that resets both filters.
- HS5. Tapping a match opens its detail: summary (settings, duration, ranks, legs and sets), then leg by leg and visit
  by visit, the darts of each visit. Voided darts are not shown; bust visits are marked. Duration: from start to end
  (owner Q8).
- HS6. Both screens are read-only (no delete button), without popups, in portrait and landscape, follow
  `docs/design/`, and stay fluid with 1,000 archived matches. Each has a visible Back action with a description;
  filters, scroll position and the open detail survive a rotation and a return to the app; Back from the detail
  restores the History filters and scroll position.
- HS7. When a History or detail read fails (AR9), the screen shows a short message in the player's language with a
  "Retry" button and the Back action, never a technical text, and is never stuck on loading. While loading, nothing
  is shown for the first 300 ms, then a loading indicator with a TalkBack description.

## Related rules

- Persistence: R6 (deletion at the confirmed win, now after the archive, AR2), R12 (save size), R26 to R30
  (clean slate, format version 1, `docs/adr/0011-save-format-v1-journal.md`) (`docs/rules/persistence.md`).
- Database: `docs/adr/0012-archive-database.md`.
- Players: PC9 (the color never carries information alone).
