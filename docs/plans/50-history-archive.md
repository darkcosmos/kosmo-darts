# Plan: profiles, match archive and History screens (issue #50)

## Goal

Issue #50 (H1 of "Historique et statistiques"): every confirmed X01 or Cricket match is archived locally in full;
players become profiles; Kosmo browses the list of matches and the detail of each, dart by dart. Nothing archived is
ever deleted. Rules: `docs/rules/history.md` (PF, DJ, BC, AR, HS) and `docs/rules/persistence.md` R26 to R30.

## Scope and out of scope

In scope: profiles (automatic creation at Start and Rematch, suggestions in the setup); the clean slate of the stored
formats (saved game and last setup restart at version 1, D24, D25); the dart journal with entry times and voided
darts; nominal board coordinates; the saved game version 1 with match id and journal; the Room
database v1 with its exported schema; the archive at the confirmed win, idempotent, with its failure path; History and
match detail screens in portrait and landscape; `docs/backlog.md`; the GitHub issue for a release version (T15).

Out of scope: H2 (free color per match, Players screen: rename, merge, reset), H3 (statistics, profile screen), H4
(heatmap, head to head), training mode (`TRAINING` is only a reserved value), camera data (`CAMERA` reserved), Android
Auto Backup (backlog only), Paging. Also out of scope (D25): any reading, conversion or cleanup of data written by an
earlier build, and any creation of profiles from the last setup or the saved game (PF4 withdrawn).

## Approach

1. **Dependencies first (T1).** Room 2.8.5 + KSP 2.3.13 + Room Gradle plugin in `:app` (AGP 9 built-in Kotlin,
   Kotlin 2.4.21), schema export, one placeholder entity. Fallback rule D3: if Room 2.8 fails to compile or its KSP
   step fails, switch to Room3 3.0.3, no workaround, no long diagnosis; record it in ADR 0012 and in the notes.
2. **Engine (T2 to T4), pure JVM, clock injected.**
   - `engine/board`: `BoardGeometry` (official dimensions, source cited) and `nominalPoint(dart): BoardPoint?` (BC).
   - `engine/journal`: events (`DartEntered`, `Undone`), `LegJournal`, `MatchJournal(matchId, startedAt, legs)`,
     `Clock` (`fun interface`, `nowMillis()`), and the replay to `JournalDart` (player, visit, rank, bust, voided,
     time) with an X01 and a Cricket leg replayer (DJ1 to DJ4). Alternatives: ADR 0011, "Options".
   - `engine/archive`: `ArchivedMatch` (session times, rules version, mode, variant, settings values, players with
     rank, legs and sets won, legs with set/leg number, starter, winner, darts with nominal points) built from an
     `X01Match` or `CricketMatch`, its `MatchJournal` and the end time; `ENGINE_RULES_VERSION = 1`. Results come from
     `MatchScore` and `MatchRanking` (AR6). The engine knows nothing of colors and app version: the app adds them.
3. **Clean slate and saved game v1 (T5, T14)**, ADR 0011: both files restart at version 1 and read no earlier
   version (R30); the saved game adds match id, start time and journals (R26, R29); limits R28. T5 does the saved
   game, T14 the last setup.
4. **App journal (T6).** The reducers carry a `MatchJournal` next to the match: "Start" and "Rematch" create the
   match id (`UUID.randomUUID`) and start time; a dart records `DartEntered` with `clock.nowMillis()`; an effective
   undo records `Undone`; a confirmed leg opens a new leg journal. The ViewModel injects the clock.
5. **Database (T7)**, ADR 0012: entities, DAOs, `HistoryDatabase`, `ArchiveRepository.archive(...)` in one
   transaction, `HistoryRepository` reads; mapping from `ArchivedMatch` plus colors (slot to ARGB from
   `PlayerPalette`) and app version (`PackageManager` `versionName`, never null, D31). Every column `NOT NULL` (D31).
6. **Profiles (T8).** `PlayerListRules.nameKey` made public and used for profiles; profiles written at Start/Rematch
   on the persistence queue (PF2) through the one `ProfileDao.upsertUsed`; a `Failed` result is ignored (AR9). No
   launch migration (PF4 withdrawn, D25).
7. **Archive flow (T9).** `FinishGameTapped` with a pending win and no archive running: state `archiving` (button
   disabled, AR3), effect `ArchiveMatch` on the persistence queue. The unit `MatchArchiveRequest` runs the archive and
   deletes the save only on `ARCHIVED` or `ALREADY_ARCHIVED` (AR2); events `ArchiveSucceeded` (End screen, as today)
   or `ArchiveFailed` (stay, message, AR3). On launch, the unit `ArchiveCheck` answers `Archived`, `NotArchived` or
   `Failed` for the saved match id; only `Archived` deletes the save (AR4, AR9). `Persistence` only sequences these
   units (review A5).
8. **Database failures (AR9, HS7).** One failure contract (ADR 0012, "Failure contract"): repositories return
   `DbResult.Ok` or `DbResult.Failed`, never throw; the read executor posts a failure state; no crash, no reset.
9. **Screens (T10 to T12).** Pure state holders tested on the JVM; composables only render. History reads go through
   a read executor (ADR 0012); tests inject a same-thread executor and a fake clock, never sleeps or idling waits.
   List: one summary query plus one player query, grouped in memory, `LazyColumn` with stable keys. No new mockup:
   screens composed from `docs/design/design-system.md` components (Q7), with one shared layout composable
   `HistoryScaffold` (top bar with Back, edge-to-edge insets and cutout, single column with a maximum content width in
   landscape) for both screens (review U10).

## Files and types added or changed

- `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `app/schemas/**` (T1, T7).
- `game-engine/.../engine/board/*`, `engine/journal/*`, `engine/archive/*`; `engine/player/PlayerListRules.kt`
  (`nameKey` public, T8).
- `app/.../persistence/*`: `GameSaveCodec` and `LastSetupCodec` (own version 1, old layouts removed), `SaveFormat`,
  `SavedGame`, `SavedCricketGame`, `SaveLoader`, `InvalidSaveReason.INCONSISTENT_JOURNAL`, `Persistence` (archive
  and profile requests); their tests (cases of versions 1 to 3 only removed, listed in the task notes).
- `app/.../history/` (new): entities, DAOs, `HistoryDatabase`, `DbResult`, `ArchiveRepository`, `ProfileRepository`,
  `HistoryRepository`, `ArchiveCheck`, `MatchArchiveRequest`, `SettingsText`.
- `game-engine/.../engine/journal/GameProgress.kt`: the one place that applies a dart or an undo to the match and the
  journal together (T6, ADR 0011).
- `app/.../ui/app/*`: `AppState`, `AppReducer`, `CricketReducer`, `SetupTransitions`, `PersistenceEffect`,
  `KosmoDartsViewModel`; `ui/history/*` (new: list, detail, state holders, tags); `ui/home/HomeScreen.kt`; setup name
  rows (`ui/components/PlayerNameRow.kt`).
- `app/src/main/res/values/strings.xml`, `values-fr/strings.xml`; `docs/backlog.md` (new).

## Data read, stored or exposed

- Stored, private app storage only: the saved game version 1 (adds match id, times, journal), the last setup
  version 1 (content unchanged) and `kosmo-history.db`
  (profiles: names only; matches, darts, times, app version). No personal data beyond the names typed by the players.
- Nothing exposed: no new permission, no provider, no export; backup stays disabled (`allowBackup="false"`).
- Read: the saved game and last setup of this version only; no data of an earlier build (R30).

## Screens and states

- **Setup**: suggestions under the focused name field (PF3): up to 5 rows of 48dp, profile names; none when no
  profile matches. Works with 200 % font and in landscape (list scrolls with the screen).
- **X01 CHECKOUT zone / Cricket win zone**: new states `archiving` (confirm disabled, label « Enregistrement… »,
  description saying the match is being saved) and `archive failed` (text message under the zone in a polite live
  region, confirm enabled, AR3).
- **Home**: a "History" / « Historique » button under the existing ones (HS1).
- **History**: filter row (mode: segmented All / X01 / Cricket; player: a chip row "All" + profiles, 48dp, scrollable,
  selected state exposed to TalkBack, the selected chip scrolled into view), then the list (HS2); states: loading
  (HS7), empty archive, no match for the filters (with "Show all matches", HS4), read failure (HS7), filled.
  Row: date, mode and variant, players (names wrap to two lines then ellipsize, never clipped, at 200 %), the winner
  by text (« Vainqueur : Kevin »), the score; one merged TalkBack description; height at least 48dp.
- **Match detail**: summary card, then one section per leg (« Set 2 · Manche 3 » as a heading, not pinned, starter,
  winner), one row per visit: player name in his color (PC9), darts as text (« T20 », « D16 », « Bull », « 25 »,
  « Raté »; Cricket as on the game screen), visit total, « BUST » as text;
  states loading, read failure (HS7), filled.
- Strings (`values/` and `values-fr/`): History, filters, empty states, row texts, duration, "Bust", leg
  headers, archive messages, read failure, "Retry",
  "Show all matches". Plurals (`plurals`) for legs, sets, darts and minutes; dates and durations formatted for the
  device locale. Exact texts are chosen in T9 to T12 and listed in their notes; French first-class.

## Test plan

- **Engine (JUnit, cites rules)**: BC2 nominal point of every zone (20 sectors × S/D/T, 25, Bull, miss), axis
  orientation (20 on +y, 6 on +x, 3 on -y, 11 on -x); DJ2/DJ3 journals with undos across turns, the a,b,u,u,c case,
  bust visits, Cricket no bust; undo of a winning dart, after a bust, at the start of a leg; double-out and
  single-out busts on 501 and 301 (T3, T4 tests); DJ4 after every undo; archive builder for X01 (each
  finish mode) and Cricket, single leg and 2 sets of 3 legs, 1 to 4 players, results equal to
  `MatchScore`/`MatchRanking`, voided darts kept with their positions.
- **App JVM**: saved game codec round trips (both modes, undos, several legs), last setup round trips, `version=1`
  the only version accepted by each codec (R30: a `version=4` saved game gives R9, a last setup R10), each R29
  rejection, size limit both sides (R28); reducers record darts with the fake clock and undos only when effective;
  settings text; history state holders (filters, grouping, duration).
- **Robolectric, in-memory Room**: every DAO; archive idempotent (AR4); archive failure keeps the saved game (AR3,
  failing repository fake and a closed database); no profile created at launch (PF4 withdrawn); 1,000 matches
  listed by the list query, time logged in the notes (no timing assert).
- **Database failures (AR9, HS7)**, each with a fake returning `Failed` and with a closed database: the AR4 check
  fails, the save is not deleted; the archive fails (AR3); the list
  read fails and the detail read fails, each giving the failure state, and "Retry" reads again.
- **Compose UI and Roborazzi** (French, Pixel 7): setup suggestions; History loading, empty, no-match, read failure,
  filled with 4 long names; detail X01 2 sets, Cricket, read failure; CHECKOUT and Cricket archiving and
  archive failure; each in portrait and landscape, and with `fontScale = 2f`.

## How Kosmo verifies on the phone

All texts are checked in French (phone in French); every screen below is checked in portrait, in landscape, and with
the largest font size of the phone.

1. Uninstall the previous version, install the new one, then open « Historique » from the home screen: it says that
   no match was played yet. On the first « Nouvelle partie », no name is suggested (no player yet).
2. Start a match, play a few darts, close the app from the recents, reopen it: « Reprendre la partie » brings the
   match back exactly; finish and confirm it: it appears in History with its duration.
3. Play an X01 match in 2 sets of 3 legs with some Undo, confirm: History shows it first, with colors, winner and
   score; the detail shows each leg, visit by visit, the busts marked, the undone darts absent.
4. Play a Cricket match with 4 players with long names (for example « Jean-Christophe », « Marie-Charlotte »): the
   History row shows every name, wrapped or shortened with « … », never cut in the middle of a letter.
5. Filter by Cricket, then by a player: only his matches stay. Choose a player and the mode where he has no match:
   History says that no match matches, and « Afficher tous les matchs » brings the whole list back.
6. Rotate the phone on History with a filter and on a detail: the filter, the position in the list and the detail
   stay. Back from a detail returns to the same place in the list.
7. On a new setup, focus a name: earlier players are suggested, most recent first; typing "kev" filters; "kevin" for
   an existing "Kevin" shows "Kevin" in History.
8. Abandon a match: it never appears.
9. The "match could not be saved" message, and the "History could not be read" message, cannot be triggered by
   hand on a healthy phone: only the captures attached to the PR prove them (CHECKOUT and Cricket zones, History and
   detail, in portrait, landscape and largest font).

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | Room and KSP setup spike, Room3 fallback | `50-history-archive/T1-room-spike.md` | - | sonnet | Build wiring with a written fallback rule, no design choice left |
| T2 | Board geometry and nominal points | `50-history-archive/T2-board-coordinates.md` | - | sonnet | Fully specified rule (BC) with listed tests |
| T3 | Dart journal and replay | `50-history-archive/T3-dart-journal.md` | - | sonnet | Specified engine rules DJ1 to DJ4 with tests |
| T4 | Archived match builder | `50-history-archive/T4-archived-match.md` | T2, T3 | sonnet | Engine mapping over existing match types, rules AR5, AR6 |
| T5 | Saved game format v1 with match id and journal (clean slate) | `50-history-archive/T5-save-v1-journal.md` | T3 | sonnet | No migration any more (D24, D25); format fully specified in ADR 0011 with listed tests |
| T6 | Journal, match id and clock in the reducers | `50-history-archive/T6-app-journal.md` | T5 | sonnet | State holder wiring with JVM tests |
| T7 | Room schema v1, DAOs and archive repository | `50-history-archive/T7-room-schema.md` | T1, T4 | opus | Case: new stored format (schema v1 that can never be dropped) |
| T8 | Profiles: creation at Start and Rematch | `50-history-archive/T8-profiles.md` | T7 | sonnet | Specified rules PF1, PF2 over T7's DAOs |
| T9 | Archive at the confirmed win, failure and AR4 | `50-history-archive/T9-archive-flow.md` | T6, T7, T8 | sonnet | Effect and reducer wiring with specified rules |
| T10 | Profile suggestions in the setup | `50-history-archive/T10-profile-suggestions.md` | T8 | sonnet | Screen change from rules PF3, state holder tests |
| T11 | History screen and home entry | `50-history-archive/T11-history-list.md` | T9 | sonnet | Screen from the design system, state holder tests |
| T12 | Match detail screen | `50-history-archive/T12-match-detail.md` | T11 | sonnet | Screen from the design system, state holder tests |
| T13 | Backlog entries | `50-history-archive/T13-backlog.md` | - | haiku | Three documentation entries, text given |
| T14 | Last setup format restarts at version 1 (clean slate) | `50-history-archive/T14-last-setup-v1.md` | T5 | sonnet | Removal of specified branches and old-version tests, no design choice |
| T15 | GitHub issue for a real release version | `50-history-archive/T15-release-issue.md` | T13 | haiku | Issue text given in the task, one command |

One `opus` task (T7).

## Decisions

- D1 (Kosmo Q1): keep the last-setup prefill; add the profile list. PF3. The migration of profiles from the last
  setup and the saved game (PF4) was withdrawn by D25.
- D2 (Kosmo Q2, Q3, Q5, Q6, Q7, Q8): suggestions under the focused field, no popup; a profile keeps its first
  spelling; abandon, "New game" then "Delete" and unreadable saves are never archived; filters mode All/X01/Cricket and
  player All/one, no variant filter; screens composed without mockups; duration from Start/Rematch to the
  confirmation (the "—" for pre-update matches is void since D25: every match has a start).
- D3 (issue, fallback rule): T1 tries Room 2.8.5 with KSP 2.3.13. On a compile or KSP failure, it switches to Room3
  3.0.3 at once and reports it; no workaround, no long diagnosis. Versions re-checked at the start of T1.
- D4 (Kosmo Q4): archive failure keeps the CHECKOUT (or Cricket win) zone with a short message; confirm retries. AR3.
- D5 (R12 question): the 10,000-dart limit keeps counting darts in play of the current leg; voided darts do not
  count, so undo still brings saving back as R12 says. The saved game size limit becomes 1 MiB (R28, ADR 0011).
  The archive is built from memory, never from the file.
- D6: the journal is stored as events (dart with time, undo) and replayed by the engine; voided flags, visits, ranks
  and busts are derived (ADR 0011, "Options").
- D7: entry times are absolute epoch milliseconds; every match has a start time and every dart an entry time (D25).
- D8: profiles carry `last_used_at` (not asked by the issue) for the "most recently used first" order; set at
  Start/Rematch (PF2), so a profile of an abandoned match also counts as used.
- D9: on launch, a saved game whose match id is already archived is deleted silently (AR4).
- D10: no coroutines code of our own and no Paging: blocking DAOs on the persistence queue (writes) and a read
  executor (History), ADR 0012.
- D11: the X01 variant is the start score (`501`/`301`); the Cricket variant is `STANDARD`/`CUT_THROAT`.
- D12: the profile name key is `PlayerListRules`' duplicate key, made public, so the setup and the profiles never
  disagree on "same name".

- Design review round 1 (commit c4ae6cb): architecture approved; test, security and UX requested changes. Taken in:
  - D13 (S1, A minor): database failures follow one contract, `DbResult` (ADR 0012, "Failure contract"); AR9 and HS7
    added. The save is deleted only after `ARCHIVED`/`ALREADY_ARCHIVED` or a positive AR4 answer.
  - D14 (T3): a second confirm while archiving does nothing; the archiving state is not saved (AR3).
  - D15 (A1): the `darts` line stays beside the journal (reused body decoder, cross-check); `GameProgress` is the one
    place that changes both (ADR 0011).
  - D16 (A2, A3, A5, A6): dart values are a snapshot (AR10); one profile upsert; small units sequenced by
    `Persistence`; table `match_record`.
  - D17 (S2, S3, S4): strict number parsing and a 30,000-event cap per leg journal (ADR 0011); manifest check
    (`MultiInstanceInvalidationService` not exported); suggestions filtered in memory on name keys; no names or ids in
    logs.
  - D18 (T7 minor, UX choice): a profile whose name is exactly the focused field's text is hidden; the same key with
    another spelling stays selectable (PF3).
  - D19 (U2 to U10): archiving label and live-region message; History row, filter, loading, rotation and detail
    accessibility rules (HS4, HS6, HS7, "Screens and states"); plurals and locale formats; shared `HistoryScaffold`.
  - D20 (T4 to T6 minors, A4, A7): named in their task files.

- Design review round 2 (commit 4f514d4): the four reviewers approved the plan. Minors taken in:
  - D21 (A8): T1 verifies the corrupt-file behavior with the Room version it keeps and completes ADR 0012 "Failure
    contract" before T7 starts; T7 uses it and does not choose.
  - D22 (T8 minor): T7 asserts only what is true for a corrupt file: `Failed`, no crash, the file keeps its bytes, no
    destructive migration.
  - D23 (T9 minor): determinism (direct executor, fake `Clock`, no sleep or idling wait) is a "Done when" item of T7
    and T11.
- Plan approved at design review round 2. Then Kosmo asked for a clean slate (2026-11-01); the plan is revised and
  goes back to design review:
  - D24 (Kosmo, verbatim): "Concernant le format de sauvegarde, on va tailler dans le lard... Jusqu'à présent on
    était en mode proto. Avec ces évolutions, on peut considérer que l'on va vers une vraie v1. Considere donc que
    l'on fait table rase des précédentes versions." The saved game restarts at format version 1 (match id, start time,
    journal); versions 1 to 4 are neither read nor migrated (R27, DJ5, AR7 withdrawn; HS5 "—" and the "not recorded"
    leg line removed). T5 is no longer `opus`.
  - D25 (Kosmo, verbatim): "On fait table rase de tout. Plus globalement, Tant que l'on n'a pas une vraie clé
    d'application donc qui permet des mises à jour, considère que je fais tout le temps de la suppression et
    réinstallation de l'appli. Donc je n'ai aucun historique, aussi bien sur les utilisateurs, sur les formats de
    sauvegarde et sur tout le bazar. Une fois qu'on aura fini tous les travaux autour de l'historique, on fera la
    mission. Enfin non, même. Prévois de demander à la squad de créer une issue GitHub pour enfin passer sur une
    vraie version release pouvant être mise à jour au niveau de l'application. C'est qu'à partir de ce moment-là
    qu'on considérera qu'on doit assurer de la backward compatibilité." General rule until that release: no backward
    compatibility of any stored data (saved game, last setup, settings, database); no reading, conversion or cleanup
    of data of an earlier build. Consequences: PF4 withdrawn (profiles only from PF2); the last setup also restarts
    at version 1 (T14; R16, R18, R25 withdrawn); no launch step for old files, a stray one falls into R9 or R10 (R30,
    ADR 0011 "Clean slate"); T15 opens the release issue; T13 adds its backlog line.
  - D26 (D25, Room, issue constraint unchanged): the database stays schema version 1 with its exported schema and no
    destructive fallback (ADR 0012); until the release version it may be edited in place by owner decision only.
    Its columns are mandatory (D31): `started_at` and `entered_at` are `NOT NULL`, `has_journal` is removed.

- Design review round 3 (commit e81c60b): the four reviewers approved the revised plan. Minors taken in:
  - D27 (A9): the nullable columns are justified in ADR 0012 (schema kept as approved, room for later sources);
    making them `NOT NULL` before the release stays an owner decision. Superseded by D31.
  - D28 (A10): ADR 0011 states that ADR 0001, 0005, 0006 and 0008 are historical only and that no code reads them.
  - D29 (T1, security same request): T5 and T14 each keep one literal fixture of the old version 1 layout, rejected
    (R9 for the saved game, R10 silently for the last setup) and never read as the new version 1.
  - D30 (T2): T14 "Done when" requires `SaveFuzzTest` to still cover both codecs once `VERSION_1` to `VERSION_4` are
    removed.
- Plan approved at design review round 3; no decision is pending.
- Kosmo approved the plan with one change (2026-11-01):
  - D31 (Kosmo, verbatim): "Le plan me convient, on met juste par contre toutes les colonnes mandatory, puisque il
    n'y a pas d'histoire de backward compatibilité." Every column of schema version 1 is `NOT NULL` and every entity
    field is a non-nullable Kotlin type, with no null branch in mappers, screens or tests (ADR 0012 "Tables").
    `started_at` and `entered_at` become `NOT NULL`; `has_journal` is removed (always 1 since D25, read by nothing);
    `app_version` is the build's version name, never null. `reset_at`, `x_mm` and `y_mm` have no value in some case
    and wait for open question 2.

## Open questions

1. If the Room version kept in T1 offers no supported way to stop the framework from deleting a corrupt database
   file (ADR 0012, "Failure contract"), T1 stops and main decides with Kosmo: accept the framework behavior (a corrupt
   history is lost), or another option T1 reports.
2. Mandatory columns that have no value in some case (D31), to answer before T7 starts: `profile.reset_at` (a
   profile is never reset in this mission, PF1) and `dart.x_mm`, `dart.y_mm` (a miss has no coordinates, BC2). For
   each: keep it nullable as the only exception, make it `NOT NULL` with a stated value (for example `reset_at` equal
   to `created_at`), or remove it until the mission that needs it (`reset_at`: H2). Until answered, these three stay
   nullable in ADR 0012.
