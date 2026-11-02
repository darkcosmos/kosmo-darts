# Tests on the Pixel 7 only

## Goal

Mission asked by Kosmo directly, without a GitHub issue: the test suite only targets the smallest supported screen,
the Pixel 7, in portrait and landscape, at every font scale already tested. Screens smaller than a Pixel 7 (for
example 360 x 640 portrait, 640 x 360 landscape, Robolectric's "small" or default devices) are out of scope for ever.
No app behaviour changes: this mission only changes tests and documents.

## Scope and out of scope

In scope:
- Every Robolectric test and Roborazzi capture of `:app` and `:capture` that runs on a size smaller than the Pixel 7,
  including the tests that name no size and so run on Robolectric's default device (320 x 470 dp, mdpi).
- Named test devices shared by the tests (`TestDevices`), so the supported sizes are written once.
- The list of production code that exists only for smaller screens (section "Production code for smaller screens"),
  as a proposal only.

Out of scope:
- Removing or changing production code (layout policies, scroll fallbacks): proposed for a later mission.
- Font scales: every font scale tested today stays (1.0, 1.3, 1.5, 2.0).
- Tall test windows of Pixel 7 width used to keep long lists unvirtualised (`w411dp-h2400dp`, `w411dp-h3600dp`):
  they are not smaller than the Pixel 7 and stay.
- `:game-engine` (pure JVM, no screen).
- Old plan files (historical), `.github/**`, `CLAUDE.md`, `.claude/**`.

## Reference sizes

`RobolectricDeviceQualifiers.Pixel7` (Roborazzi 1.76.0, read with `javap`) is
`w411dp-h914dp-normal-long-notround-any-420dpi-keyshidden-nonav`: 411 x 914 dp portrait, 914 x 411 dp landscape
(Robolectric swaps width and height for `land`). Robolectric draws no system bars, so the app gets the whole screen.

On the real phone the app gets less: the plan of the Cricket rework (#40, section "Reference device") measured the
Pixel 7 bars as upper bounds, and the owner review of PR #57 named the Pixel 7 landscape inside its bars and cutout
"about 800 x 360 dp". Two sizes of the suite stand for that real usable area and are **the Pixel 7, not a smaller
phone** (Q1):
- portrait inside its bars: `w411dp-h814dp-port-420dpi` (914 - 52 status - 48 navigation);
- landscape inside its bars and cutout: `w800dp-h360dp-land-xxhdpi`.

Every size of the inventory is classified against these four: smaller than the Pixel 7 inside its bars in width or
height means "smaller than the Pixel 7".

## Inventory

Tests counted with `@Test` per class; "capture" means a Roborazzi PNG recorded under `build/outputs/roborazzi`
(record only, never compared; no PNG reference is tracked in git: `git ls-files '*.png'` lists only the 10 mockups of
`docs/design/mockups`).

Size groups found (`grep -rnE 'qualifiers' app/src/test capture/src/test`):

| Group | Size (dp) | Where | Smaller than Pixel 7? |
|---|---|---|---|
| S1 | 360 x 640 portrait, xhdpi (Robolectric `SmallPhone`) | `:app` | yes |
| S2 | 640 x 360 landscape, xhdpi (`:app`) and xxhdpi (`:capture`) | `:app`, `:capture` | yes |
| S3 | 393 x 852 portrait, xxhdpi | `:app` | yes (width) |
| S4 | 411 x 760 portrait, 420 dpi | `:app` | yes (height, under 814) |
| S5 | 731 x 411 landscape, xxhdpi | `:app` | yes (width) |
| S6 | no size: Robolectric default 320 x 470 mdpi (`+fr-land`: 470 x 320) | `:app` | yes |
| K1 | 411 x 814 portrait / 800 x 360 landscape | `:app` | no: Pixel 7 inside its bars (Q1), kept |
| K2 | 412 x 915, 915 x 412 (xxhdpi), 914 x 411 (420 dpi) | `:app`, `:capture` | no: Pixel 7 rounded, moved to the Pixel 7 class |
| K3 | 411 x 2400, 411 x 3600 | `:app` | no: tall windows for lists, kept |

Per test, with what it protects besides the size and its disposition (D5: a rule keeps at least one Pixel 7 test):

| File (tests on a smaller size) | Group | Rule protected | Pixel 7 test already there? | Disposition | Task |
|---|---|---|---|---|---|
| `GameScreenFitTest`: U10 action bar 360x640 (x2), 640x360 (x1), U10 checkout confirm 360x640 | S1, S2 | U10 action bar and confirm whole, 4 players | U10 at 412x915 and 915x412, scale 1 and 2 (no checkout confirm) | U10 bar: delete; U10 checkout confirm: retarget to 411x814 | T2 |
| `GameScreenFitTest`: T9 touch targets 360x640 (x2), 640x360 (x1) | S1, S2 | T9 keys and bar at least 48 dp | none | retarget to 411x814 (scale 1, 2) and 800x360 (scale 2) | T2 |
| `GameScreenFitTest`: U11 labels 640x360, 360x640; U12 header 360x640 | S1, S2 | U11 no break inside a word, U12 header two lines at most | none | retarget to 800x360 and 411x814, scale 2 | T2 |
| `RedesignScreensTest`: U1 small portrait / landscape at 1.0 and 2.0 (x4) | S1, S2 | U1 zone and every key reachable | none | retarget to 411x814 and 800x360 | T2 |
| `RedesignScreensTest`: T5 zone does not move with a route | S1 | T5, CS8 portrait | landscape only (U3 test) | retarget to 411x814 | T2 |
| `RedesignScreensTest`: U7 Start visible under the focused field, scale 2 | S1 | U7 | none | retarget to 411x814 | T2 |
| `CheckoutBannerFitTest`: M15 360x640 at 1.0/1.3/1.5/2.0 (x4) | S1 | M15 banner whole on one line | yes, 411x814 at the same scales | delete | T2 |
| `CheckoutBannerFitTest`: M15 640x360 at 1.0/1.3/1.5/2.0 (x4) | S2 | M15 | yes, 800x360 at 1.0 and 2.0 | delete; add 800x360 at 1.3 and 1.5 | T2 |
| `CheckoutBannerFitTest`: M15 sets match, long name (360x640, 640x360 at 2.0, x4), undo restores name row (640x360) | S1, S2 | M15 with S/M chips, long name; T4 undo | none | retarget to 411x814 and 800x360 at 2.0 | T2 |
| `CheckoutBannerFitTest`: M15 `+fr-land` at 1.0/1.3/1.5/2.0 (x4) | S6 | M15 | covered by 800x360 | moves to Pixel 7 landscape by T1; `frland` pre-fix value dropped (D6) | T1, T2 |
| `VisitTotalLayoutTest`: VT11 narrower phone 393x852 at 2.0 | S3 | VT11 | yes, VT11 Pixel 7 portrait at 2.0 | delete | T2 |
| `DesignSystemScreenshotTest`: `game_4p_small_360x640_fr`, `_font200`, `game_4p_small_640x360_fr`, `_font200` | S1, S2 | captures U1 | `game_4p_route_800x360_fr` and Pixel 7 captures | delete 4 captures | T2 |
| `DesignSystemScreenshotTest`: `setup_4p_focused_small_360x640_fr_font200` | S1 | capture U7 | none | retarget to 411x814, renamed `setup_4p_focused_411x814_fr_font200` | T2 |
| `GameLayoutPolicyTest` (pure JVM): steps at 360x640 and 640x360, 360x640 inputs in 3 other tests | S1, S2 | step order 1-2-3-4, scroll fallback, 48 dp floor | Pixel 7 inputs in the same tests | D7 | T2 |
| `ScreenLayoutPolicyTest` (pure JVM): `hasTwoColumns(640, 360)`, `(360, 640)` | S1, S2 | two-column threshold | yes (800x360, 412x915, 915x412) | delete the two smaller inputs | T2 |
| `CricketEndScreenTest` class at 360x640 (6 tests: CR26, CR27 at 1.0 and 2.0, CR28, next leg) | S1 | CR26, CR27 fit with 4 players | no | class device to 411x814, all kept | T3 |
| `CricketScreenTest`: CR22 two-line zone at 411x760, scale 2 | S4 | CR22 fallback (two-line zone, MPR on the name line) | no: unreachable at 411x814 | D8: delete, keep the policy test | T3 |
| `CricketBoardLayoutPolicyTest` (pure JVM): "small screen" scroll (portrait, landscape 360 high), fixtures measured at 360 dp wide | S1, S2 | CR22 fallback (scroll, 42 dp rows) | Pixel 7 inputs exist | D7 | T3 |
| `CricketScreenshotTest`: `between_legs_4p_360x640_fr`, `end_4p_360x640_fr` | S1 | captures CR26, CR27 | yes: `between_legs_4p_portrait_fr_font200`, `end_4p_portrait_fr_font200`, `between_legs_4p_landscape_fr` | delete | T3 |
| `SetupSuggestionsScreenTest` class at 360x640 (4 tests) | S1 | profile suggestions under the focused field | no | class device to Pixel 7 portrait, all kept | T3 |
| `HistoryScreenTest` HS2 U3, `MatchDetailScreenTest` HS5 U3 at 731x411, scale 2 | S5 | HS2, HS5 last item reachable in landscape | no | retarget to 800x360 | T3 |
| UI classes with no size (S6): `AnnouncementsScreenTest` (18), `ArchiveFlowTest` (13), `CricketResumeScreensTest` (8), `HomeScreenTest` (6), `OwnerEvolutionsScreenTest` (16), `PersistenceScreensTest` (24), `SettingsScreenTest` (17), `VisitTotalScreensTest` (4), `VisitTotalResumeTest` (5), `VisitTotalFrenchScreensTest` (1), text-only classes | S6 | their own rules (none about size) | n/a | Pixel 7 by default (T1), all kept | T1 |
| `ZoomRowTest` (`:capture`): 640x360 zoom row 42 dp, preview 160 dp, slider 96 dp en/fr (x4) | S2 | capture zoom row layout, U17 | 915x412 for row and slider | retarget to 800x360 | T4 |
| `CaptureActionRowTest` U11 at 640x360 | S2 | U11 Undo and Next volley whole | yes, 915x412 | delete if same assertions, else retarget | T4 |
| `CaptureScreenshotsTest`: `640x360_en`, `640x360_fr` | S2 | captures | 915x412 captures | delete 2 capture sets | T4 |

## Production code for smaller screens

Listed for a later decision, **not changed in this mission**: none is trivially dead, and each is also a safety net
on a Pixel 7 whose display size is set larger than default (Q2).

| Id | Code | What it does | Reached on the Pixel 7? |
|---|---|---|---|
| P1 | `GameLayoutPolicy` step 4 (`isScrolling`, `SCORE_FLOOR_*`) and the scroll of `GameScreen`; test helper `RevealNode` | the X01 game screen scrolls below the score floor | T2 records it (D7) |
| P2 | `CricketBoardLayoutPolicy.scrolls` and the `verticalScroll` of `CricketBoard` | the Cricket rows scroll ("a screen smaller than the Pixel 7", KDoc) | no, by its own KDoc; T3 confirms |
| P3 | `CricketBoardLayoutPolicy` `showsTotal = false` (two-line zone, R2.1) | Cricket zone without the big total | no at 411x814 (KDoc of the 760 test) |
| P4 | `ScreenLayoutPolicy` and `ScrollingScreen` scroll | other screens scroll, buttons pinned | yes at font scale 2.0: not a fallback, keep |

Proposal: after Q2, a separate mission may remove P1 step 4, P2 and P3 with their policy tests, if Kosmo confirms
that only the default display size of the Pixel 7 is supported.

## Approach

- A1. One file of named devices per test source set (`TestDevices`): `PIXEL7_PORTRAIT`, `PIXEL7_LANDSCAPE` (from
  `RobolectricDeviceQualifiers.Pixel7`, with `-port` / `-land`), `PIXEL7_PORTRAIT_INSIDE_BARS`
  (`w411dp-h814dp-port-420dpi`), `PIXEL7_LANDSCAPE_INSIDE_BARS` (`w800dp-h360dp-land-xxhdpi`), with the language
  added by `+fr` / `+en` at the use site or by a `fr-` prefix constant when the class needs it.
  Alternative: keep string literals in every test (today's state): rejected, the same sizes are spelled 5 ways.
- A2. `robolectric.properties` of `:app` sets `qualifiers=` the Pixel 7, so a test that names no size runs on the
  Pixel 7. Alternative: a class `@Config` on every UI class: rejected, easy to forget in a new class.
- A3. Each smaller-size test is deleted when a Pixel 7 test protects the same rule, else retargeted to the Pixel 7
  inside its bars (the tightest supported size) with its rule identifier kept.
- A4. Pure JVM policy tests: D7.

## Files and types added or changed

- Added: `app/src/test/kotlin/io/github/darkcosmos/kosmodarts/TestDevices.kt`,
  `capture/src/test/kotlin/io/github/darkcosmos/kosmodarts/capture/TestDevices.kt` (`object` of `const val String`,
  usable in `@Config`).
- Changed: `app/src/test/resources/robolectric.properties`; the test files listed in the "Inventory" table and in the
  task files. No production file, no resource, no dependency.

## Data read, stored or exposed

None. Tests only.

## Screens and states

No screen changes. The captures change as listed in "Test plan".

## Test plan

- No production behaviour changes, so no new rule test; every rule in the "Inventory" keeps at least one Pixel 7 test.
- Retargeted tests keep their assertions (never weakened). A test that turns red once moved to the Pixel 7 is a
  finding: the build agent stops and reports to main (it may be a real bug on the Pixel 7).
- Checks per task: `./gradlew build` green and `kosmo-detekt` exit 0.
- Final grep: `grep -rnE 'w(360|640|393|731)dp|h760dp' app/src/test capture/src/test` prints nothing.

### Measures

Before: `main` at 2f9e437, command `time ./gradlew build --rerun-tasks` on the sandbox. After: T4 runs the same.

| Measure | Before | After (T4) |
|---|---|---|
| Tests `:app` (JUnit XML, `testDebugUnitTest`) | 1595 (0 skipped, 0 failures) | |
| Tests `:capture` | 225 | |
| Tests `:game-engine` | 424 (unchanged expected) | |
| Captures `:app` (PNG under `app/build/outputs/roborazzi`) | 279 (expected after: 273) | |
| Captures `:capture` | 68 (expected after: 48) | |
| `./gradlew build --rerun-tasks` wall time | 5 min 50 s (BUILD SUCCESSFUL, 205 tasks executed) | |
| `kosmo-detekt` exit code | 0 | |

Captures removed, as recorded at 2f9e437 (`find */build/outputs/roborazzi -name '*.png'`):
- `:app` (6): `ds_game_4p_small_360x640_fr`, `ds_game_4p_small_360x640_fr_font200`, `ds_game_4p_small_640x360_fr`,
  `ds_game_4p_small_640x360_fr_font200`, `cricket_between_legs_4p_360x640_fr`, `cricket_end_4p_360x640_fr`;
- `:capture` (20): `capture_<state>_640x360_en` and `_fr` for the 10 states `delete_confirm`, `error`, `idle`,
  `permission_denied`, `permission_missing`, `photo_failure`, `running`, `saving`, `stop_confirm`, `volley_full`.

Renamed: `ds_setup_4p_focused_small_360x640_fr_font200` to `ds_setup_4p_focused_411x814_fr_font200`. No capture
comes from a test without a size (S6): every screenshot class already names the Pixel 7.

## How Kosmo verifies on the phone

Nothing changes in the app. Kosmo checks the pull request description: the before/after table above, and that the
CI build is green. The screenshot artifact of the CI run no longer contains `360x640` or `640x360` images.

## Tasks

| Id | Title | Task file | Depends on | Model | Why this model |
|---|---|---|---|---|---|
| T1 | Pixel 7 as the default test device | `tests-pixel7-only/T1-pixel7-default-device.md` | none | `sonnet` | configuration and wiring with a red-test check |
| T2 | X01 game tests on the Pixel 7 only | `tests-pixel7-only/T2-x01-tests.md` | T1 | `sonnet` | test edits from the table, per-test judgement |
| T3 | Cricket, setup and History tests on the Pixel 7 only | `tests-pixel7-only/T3-cricket-setup-history-tests.md` | T1 | `sonnet` | test edits from the table, per-test judgement |
| T4 | Kosmo Capture tests and the after measures | `tests-pixel7-only/T4-capture-tests-and-measures.md` | T1, T2, T3 | `sonnet` | test edits and measures, no design choice |

`opus` tasks: 0.

## Decisions

- D1 (Kosmo): the smallest supported screen is the Pixel 7, portrait and landscape; smaller screens are out of scope
  for ever; font scales stay.
- D2: the Pixel 7 reference is `RobolectricDeviceQualifiers.Pixel7` (411 x 914 dp, 420 dpi); the near values
  412 x 915 and 915 x 412 move to it (a test red after that 1 dp change is reported, not adjusted).
- D3: `TestDevices` is duplicated in `:capture` (4 lines) rather than shared: `:capture` does not depend on `:app` and
  a shared test fixture module would be a new module for four constants.
- D4: 411 x 814 and 800 x 360 are the Pixel 7 inside its bars and stay (pending Q1).
- D5: no rule loses its last test: a smaller-size test is deleted only when a Pixel 7 test checks the same rule.
- D6: `CheckoutBannerFitTest` drops the `frland` pre-fix value (it was measured on Robolectric's 470 x 320 default
  device); the pre-fix guard of #69 stays at 411 x 814 and 800 x 360.
- D7: pure JVM policy tests: a smaller-size input moves to the Pixel 7 inside its bars when that size still reaches the
  branch under test (the existing "branch reached" assertions prove it); when no supported size reaches it, the input
  stays with a one-line comment naming P1, P2 or P3, because the branch is still production code. It goes with that
  code if a later mission removes it.
- D8: the Robolectric CR22 fallback test at 411 x 760 is deleted (no supported size reaches it); the fallback stays
  proven by `CricketBoardLayoutPolicyTest` under D7 until P3 is decided.

## Open questions

- Q1 (Kosmo): 800 x 360 (landscape) and 411 x 814 (portrait) are your Pixel 7 once the status bar, the navigation bar
  and the camera hole are taken away. Keep them as the Pixel 7 sizes (recommended), or test only the full screen
  914 x 411 / 411 x 914 that Robolectric gives?
- Q2 (Kosmo): with "Display size" set larger than default in the Android settings, a Pixel 7 behaves like a smaller
  phone (narrower than 411 dp, shorter than 914 dp). Is only the default display size supported (recommended, it is the
  meaning of "smaller screens out of scope"), or must larger display sizes keep working?
