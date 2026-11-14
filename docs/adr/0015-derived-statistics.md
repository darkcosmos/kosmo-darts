# ADR 0015: Derived statistics, board model and back stack

- Status: Proposed (design review of issues #52 and #53); accepted when its PR is merged. No schema change: the archive
  database stays at version 1 and the saved game format is unchanged.

## Context

The Profile (statistics per player), the head-to-head and the heatmap read figures from the archived matches (AR5) and
from the in-memory match journal (DJ1). Those figures must always agree with the game: the in-game MPR (CR13) and the
Profile MPR (STC2) come from the same source. Archived matches are written once and never edited, but they can be
unreadable (ST10). Camera detection (a later milestone) needs the same board model as the heatmap. The history screens
need a Back that returns to the screen the user came from.

## Options

1. Derived figures (chosen, D2): every figure is a pure `:game-engine` function of the archived matches or of the
   journal, computed on read, off the UI thread (ST7). Alternative rejected: a stored statistics cache, updated on
   every archive, reset, merge and delete. It would duplicate values derivable from the journal and need invalidation
   on every write path. A cache is added only if the performance test (plan Approach 3) fails.
2. Useful marks of CR13 replayed from the match journal (chosen, D1). Alternative rejected: store the useful marks in
   the confirmed-leg summaries of the saved game (format v3). It duplicates a value derivable from the journal (R26)
   and needs a stored format change.
3. Invalid archived matches skipped and counted (chosen, D4, ST10). Alternative rejected: a whole-load failure on any
   invalid row, which would hide every valid match of the profile. The invalid rows stay in the database untouched:
   nothing is ever deleted or rewritten by the statistics.
4. Board model made public: `BoardGeometry` and `BoardZone` are public in `:game-engine`, as the reusable board model
   for the heatmap (BC4, BC5) and for the camera milestone. Alternative rejected: keeping the board model internal to
   the dart input code, which would force a second copy of the geometry for the heatmap and the camera.
5. Back stack as a parent chain (chosen, D5): each history `Screen` (`Players`, `History`, `Profile`, `HeadToHead`,
   `MatchDetail`) holds its parent, as `MatchDetail` holds `History` today. Alternative rejected: a `List<Screen>` in
   `AppState`, because every reducer would then read and write the top of a heterogeneous list, and each screen would
   lose its own typed state (filters, scroll position) in its node.

## Decision

- Statistics are derived only. Nothing is stored and nothing is cached (D2). Every figure is computed by `:game-engine`
  from validated per-dart facts; the loader only reads rows (plan Approach 1 and 3).
- CR13 (the in-game MPR) and STC2 (the Profile MPR) use the same function, the useful marks of STC1, replayed from the
  match journal.
- An archived match that breaks the rules is skipped and counted (ST10). Its validation lives in `:game-engine`
  (`StatsMatchValidator`); the validator and the stats functions return values for invalid input and never throw.
- `BoardGeometry` and `BoardZone` are public in `:game-engine`, with the zones of BC4 and the lookup of BC5.
- The history screens use a parent chain in each `Screen` node, with the no-copy rule of HS9. The chain lives in
  `AppState`, so it survives a rotation; it is not saved, so a process death restarts at home.

## Consequences

- No migration and no format change: the archive stays at version 1, the saved game keeps its format.
- Every figure costs a read of the profile's archive on each open. The bound is about 1,000 matches x 250 darts, a
  few tens of MB at worst, read in batches of 100 sessions (plan Approach 3).
- The engine is the only place where a rule is applied to statistics: a screen never counts or filters game data (the
  project's review focus on architecture).
- The board model is a public API of `:game-engine` from this mission on; changes to it are reviewed as API changes.
