# Persistence rules (game in progress and last setup)

Status: v1 (issue #30). This document is the single source of truth for the persistence behaviour and its tests:
tests cite these rules, never the issue text. Rule ids (R1, R2, ...) are stable. They use the prefix R so that they
never collide with the X01 rules of `docs/rules/x01.md` (owner decision Q6).

R1 to R11 match P1 to P11 of issue #30, refined with every owner decision taken on the issue (Q1 to Q9, recorded
below). R12 covers the technical frame (T1, T2) and the owner's request that deletions share the write queue.

Vocabulary:
- *Saved game*: the game in progress stored on the device. There is at most one.
- *Last setup*: player names in throwing order, start score (501/301) and checkout rule of the last game started.
- *Home screen*: the first screen of the app (the "start screen" of issue #30).

## Owner decisions (issue #30, 2026-10-18)

| Id | Decision | Rules |
|---|---|---|
| Q1, Q7 | With a saved game, the home screen shows "New game", then "Resume game" right under it. "New game" asks for confirmation; "Delete" deletes the saved game at once. | R2, R3 |
| Q2 | "Abandon" deletes the saved game. | R6 |
| Q3 | A rematch is a game start: its order (first player moved to the end) becomes the last setup. | R7 |
| Q4 | The summary shows the player the game screen highlights; with the checkout dialog open, the winner and "Checkout to confirm". | R2 |
| Q5 | An active Double/Triple modifier and an open "Abandon the game?" dialog are not restored. | R4 |
| Q6 | Persistence rules use the prefix R. | — |
| Q8 | The button says "Resume game" (« Reprendre la partie »). | R2 |
| U5, Q9 | One message, which says the game was deleted. Every load failure deletes the file, a storage read error included. | R9, R10 |

## Resume

- R1. When the app process is killed in the background while the game screen is shown, and the user returns to the
  app (recents or launcher), the game screen comes back directly, as described in R4, without going through the home
  screen. If another screen was shown, the app comes back on the home screen (with "Resume game" if a game is saved,
  R2).
- R2. On a fresh launch (after a force stop, a reboot, or after the app was removed from the recents) with a saved
  game, the home screen shows a summary of the saved game and two buttons: "New game", then "Resume game" right
  under it. The summary gives the start score (501/301), the checkout rule, each player's name and remaining score
  in throwing order, and the player shown as throwing on the game screen after resuming: the player whose turn waits
  for "Next player" if any, else the player to throw. When the checkout dialog was open, the summary shows the
  winner with "Checkout to confirm" instead. "Resume game" opens the game screen as described in R4. Without a saved
  game, the home screen is unchanged (no summary, no "Resume game").
- R3. "New game" on the home screen while a game is saved opens a confirmation dialog. "Cancel" (or system back)
  closes the dialog and keeps the saved game. "Delete" deletes the saved game at once and opens the setup screen;
  going back from the setup screen then leads to a home screen with nothing to resume. Without a saved game, "New
  game" opens the setup screen directly, as before.
- R4. A resumed game is exactly as it was: every dart of the game, the darts of the current turn, the player shown
  as throwing, a turn waiting for "Next player" (BUST shown after a bust, dart entry keys disabled, "Next player"
  enabled), and the checkout dialog if it was open, with "Finish game" and "Undo" behaving as before. Not restored
  (Q5): an active Double or Triple modifier, and an open "Abandon the game?" dialog. Resuming never announces a
  completed turn again (the turn-completed signal is not fired).
- R5. Undo after a resume works back to the first dart of the game.

## End of game

- R6. The saved game is deleted when the win is confirmed ("Finish game" in the checkout dialog) and when the game
  is abandoned ("Abandon" in the "Abandon the game?" dialog). If the app is killed on the end screen, it reopens on
  the home screen with no "Resume game". A rematch starts a new saved game.

## Last setup

- R7. The last setup is retained every time a game starts: "Start" on the setup screen, and "Rematch" on the end
  screen (in the rematch order, where the first player moved to the end). It prefills the setup screen of the next
  "New game", including after the app was killed or the phone restarted. Without a last setup, the setup screen
  shows the defaults (501, double out, two empty names).
- R8. The last setup is stored separately from the saved game. An unreadable saved game never loses the last setup,
  and an unreadable last setup never loses the saved game. The one exception is R11: when loading fails in an
  unforeseen way, it cannot tell which file caused the failure, so both files are deleted.

## Unreadable files

- R9. A saved game that cannot be loaded shows the message "The saved game could not be resumed and was deleted"
  (« La partie enregistrée n'a pas pu être reprise et a été effacée ») once, in a dialog with an "OK" button over
  the setup screen, prefilled with the last setup (R7). The file is deleted as soon as it is found unreadable, so the
  message never comes back. A saved game cannot be loaded when:
  - storage fails while reading it (an input/output error);
  - it is empty, larger than the size limit, or truncated;
  - it is corrupted (checksum mismatch) or not in the expected structure;
  - its format version is unknown or newer;
  - a value is out of range: start score, checkout rule, player count, name length or content, dart token, or dart
    count;
  - the game engine rejects it on replay (for example a dart after the win);
  - it is inconsistent with the stored waiting-turn flag (see ADR 0001).

  R1 and R2 then do not apply.
- R10. An unreadable last setup is ignored silently, whatever the reason (a storage read error included): the setup
  screen shows the defaults, and the file is deleted. The saved game is not affected (R8).
- R11. No content of either file (any bytes, any size) can crash the app. If loading fails in a way that none of the
  checks of R9 foresaw, both files are deleted and the R9 message is shown, so that the same failure cannot happen at
  every launch.

## Saving

- R12. The saved game is written after every action that changes it:
  - a dart;
  - an undo;
  - "Next player";
  - the checkout dialog, shown by the winning dart or dismissed with Undo.

  Resume writes nothing, since it changes nothing. The saved game is deleted as described in R3, R6 and R9, and the
  last setup is written as described in R7.

  Writes and deletions run off the main thread, one at a time, in action order, on one queue shared by both files
  and by loading. Only the latest pending request for a file needs to run, so a write queued before a deletion never
  recreates the file after it. A rotation or any other configuration change neither reads nor writes the files.

  Each write is atomic: the file holds either the previous or the new content, never a mix. Files go to the app's
  private storage only. A failed write keeps the previous file and never crashes the app.

  A game of more than 10,000 darts is no longer saved after its 10,000th dart (the size limit of R9); saving
  resumes when undo brings it back to 10,000 darts or fewer.

## Matches in legs and sets (issue #39, `docs/rules/match.md`, ADR 0005)

- R13. The match format is saved with the game and with the last setup (with the names, the start score and the
  finish rule).
- R14. Resuming a match restores the sets and legs won, the current leg with its darts and its starter, a turn
  waiting for "Next player", the checkout dialog with its M15 label, and the between-legs screen if it was shown. The
  saved match is deleted only when the match win is confirmed ("Finish game") or on abandon (owner Q7, R6).
- R15. Writes (R12) also happen on "Finish leg", "Finish set" and "Next leg"; resuming on the between-legs screen
  writes nothing.
- R16. A saved game or last setup written by the previous version (format 1) is read as a single game (1 leg and
  1 set), without any message.
