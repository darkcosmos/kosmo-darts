# Persistence rules (game in progress and last setup)

Status: draft v1 (issue #30). This document is the source of truth for the persistence tests.
Rule ids (R1, R2, ...) are stable and referenced by tests. They use the prefix R so that they never collide with the
X01 rules of `docs/rules/x01.md` (owner decision on issue #30, Q6). R1 to R11 match P1 to P11 of issue #30, refined
with the owner's answers on the issue; R12 covers the technical frame (T1, T2).

Vocabulary:
- *Saved game*: the game in progress stored on the device. There is at most one.
- *Last setup*: player names in throwing order, start score (501/301) and checkout rule of the last game started.
- *Home screen*: the first screen of the app (the "start screen" of issue #30).

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
  game" opens the setup screen directly, as today.
- R4. A resumed game is exactly as it was: every dart of the game, the darts of the current turn, the player shown
  as throwing, a turn waiting for "Next player" (BUST shown after a bust, dart entry keys disabled, "Next player"
  enabled), and the checkout dialog if it was open, with "Finish game" and "Undo" behaving as before. Not restored
  (owner decision, Q5): an active Double or Triple modifier, and an open "Abandon the game?" dialog. Resuming never
  announces a completed turn again (the turn-completed signal is not fired).
- R5. Undo after a resume works back to the first dart of the game.

## End of game

- R6. The saved game is deleted when the win is confirmed ("Finish game" in the checkout dialog) and when the game
  is abandoned ("Abandon" in the "Abandon the game?" dialog). If the app is killed on the end screen, it reopens on
  the home screen with no "Resume game". A rematch starts a new saved game.

## Last setup

- R7. The last setup is retained every time a game starts: "Start" on the setup screen, and "Rematch" on the end
  screen (in the rematch order, where the first player moved to the end). It prefills the setup screen of the next
  "New game", including after the app was killed or the phone restarted. Without a last setup, the setup screen
  shows today's defaults (501, double out, two empty names).
- R8. The last setup is stored separately from the saved game. An unreadable saved game never loses the last setup,
  and an unreadable last setup never loses the saved game.

## Unreadable files

- R9. A saved game that cannot be resumed shows the message "The saved game could not be resumed" once, in a dialog
  with an "OK" button over the setup screen (prefilled with the last setup, R7); the file is deleted when it is found
  unreadable, so the message never comes back. A saved game cannot be resumed when it cannot be read from storage,
  or when it is empty, larger than the size limit, truncated, corrupted (checksum mismatch), not in the expected
  structure, of an unknown or newer format version, out of range (start score, checkout rule, player count, name
  length or content, dart token, dart count), rejected by the game engine on replay (for example a dart after the
  win), or inconsistent with the stored waiting-turn flag (see the save format ADR). R1 and R2 then do not apply.
  Exception: when the file exists but storage fails while reading it (an input/output error, not invalid content),
  the message is shown the same way but the file is kept, so a later launch can still resume it; the message then
  comes back at each launch while the error lasts (ADR 0002, decision 5).
- R10. An unreadable last setup is ignored silently: the setup screen shows the defaults, and the file is deleted
  (kept if storage failed while reading it, as in R9). The saved game is not affected (R8).
- R11. No content of either file (any bytes, any size) can crash the app.

## Saving

- R12. The saved game is written after every action that changes it (dart, undo, end of turn confirmed, checkout
  dialog shown or dismissed with Undo, resume excepted since it changes nothing) and deleted as described in R3 and
  R6; the last setup is written as described in R7. Writes run off the main thread, one at a time, in action order;
  only the latest pending content of a file needs to be written. A rotation or any other configuration change
  neither reads nor writes the files. Each write is atomic (the file holds either the
  previous or the new content, never a mix) and goes to the app's private storage only. A failed write keeps the
  previous file and never crashes the app. A game of more than 10,000 darts is no longer saved after its 10,000th
  dart (the size limit of R9); saving resumes if undo brings it back under the limit.
