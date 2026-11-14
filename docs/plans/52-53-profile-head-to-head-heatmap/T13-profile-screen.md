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
  "Since always" / "Depuis toujours", "Since <date>" / "Depuis le <date>").
- Tests: `ProfileScreenTest.kt`, `ProfileTextTest.kt`; captures `ProfileScreenshotTest.kt`.

## Depends on
T12.

## Rules
PS2 to PS4, PS6, PS8, ST6, ST9; design system sections 2 to 5, 8.

## Tests
- Tabs switch sections; filters and period chips select; "–" shown for null figures; empty states; HS7 failure with
  Retry; TalkBack descriptions on the Back icon and chips; 48 dp targets.
- Captures (Pixel 7, portrait, landscape, 200 % font): empty, X01 filled, Cricket filled with "All variants" (two
  result blocks), empty for filters.

## Done when
`./gradlew build` and `./gradlew :app:testDebugUnitTest` green (captures recorded); `kosmo-detekt` exit 0.

## Model
sonnet. Why: screen from the plan with Compose tests and captures.

## Implementation notes
