# T13: Profile screen

## Goal
The Profile screen: header, tabs, filters, periods, X01 and Cricket figures, states. The trend curve slot shows a
placeholder until T14; the actions panel comes in T15. Root "Screens and states", Profile.

## Files
- `app/.../ui/profile/ProfileScreen.kt`, `ProfileHeader.kt`, `ProfileFilters.kt`, `X01Section.kt`,
  `CricketSection.kt`, `ProfileTags.kt`, `ProfileText.kt` (formatting with existing `ui/format` helpers, "–" for
  null values) (new).
- `app/.../ui/app/KosmoDartsApp.kt`: show the screen.
- `res/values/strings.xml`, `res/values-fr/strings.xml`: every text (titles, labels, filters, periods, states,
  "Since always" / "Depuis toujours", "Since <date>" / "Depuis le <date>" with the date in the device locale format);
  every count (matches, legs, ignored matches) as a `<plurals>` resource in both languages; the ST10 notice.
- Layout: in portrait the tabs stay pinned at the top while the header and content scroll (D8).
- Tests: `ProfileScreenTest.kt`, `ProfileTextTest.kt`; captures `ProfileScreenshotTest.kt`.

## Depends on
T12.

## Rules
PS2 to PS4, PS6, PS8, ST6, ST9, ST10; design system sections 2 to 5, 8.

## Tests
- Tabs switch sections; filters and period chips select; "–" shown for null figures; empty states; HS7 failure with
  Retry; TalkBack descriptions on the Back icon and chips; 48 dp targets.
- `ProfileTextTest`: plurals for 0, 1, 2 matches in English and French; "Depuis le" with a French-formatted date and
  "Since" with an English one.
- Ignored-matches notice shown when the state count is above 0, absent at 0; tabs still visible after scrolling in
  portrait.
- Captures (Pixel 7, portrait, landscape, 200 % font): empty, X01 filled, Cricket filled with "All variants" (two
  result blocks), empty for filters.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green (captures recorded); `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen from the plan with Compose tests and captures.

## Implementation notes
- `ui/profile/`: `ProfileScreen` (frame = `HistoryScaffold`, which gained `maxWidth` for the 960 dp landscape; states
  loading / failed + Retry / no match at all / content), `ProfileHeader` (`ProfileHeader`: name, Since line ST9,
  ST10 notice; `ProfileTabs`, pinned through `stickyHeader` in portrait, D8), `ProfileFilters` (ST3, ST4 with
  `SegmentedChoice`, stacked one option per line from font scale 1.3 so a word is never cut), `X01Section` and
  `CricketSection` (build `FigureBlock` lists; `trendBlock` is the PS5 slot, a "matches in the curve" row that T14
  replaces), `ProfileText` (pure texts on `Resources`: plurals, Since, dash), `ProfileTags`.
- Landscape (`ScreenLayoutPolicy.hasTwoColumns`): left column header, tabs, filters; right column the blocks in two
  columns (taken in turn). Both columns scroll on their own.
- `KosmoDartsApp` shows `ProfileScreen(screen.profile, onEvent)`. Nothing opens a Profile yet: the Players row and the
  match detail links are T15 (T10's `Screen.open(Screen.Profile(screen, id, ProfileState(now = stamp.at)))`).
- Divergences: the Heatmap tab shows a one-line placeholder text (key `profile_heatmap_placeholder`, T22 replaces it);
  the header has no "Manage player" / "Compare with..." buttons and the actions panel is not shown (T15, T25).
  `profile_leg_count` is used for "Legs won" values; tab labels use `WordSafeText` (U11).
- TalkBack: each figure row is one merged element "label, value"; tabs are `Role.Tab`, options `Role.RadioButton` with
  their selected state; Back keeps the description of `ScreenTopBar`. Chip and tab texts are their own descriptions, so
  the state holder adds none.
- Tests: `ProfileScreenTest` (20), `ProfileTextTest` (10), captures `ProfileScreenshotTest` (empty, X01, Cricket
  "All variants", no match for the filters; portrait EN, landscape FR, 200 % font portrait and landscape).
