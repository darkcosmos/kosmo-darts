# ADR 0004: Audio session lifecycle and settings storage

- Status: Proposed (design review of issue #32; becomes Accepted when the announcements PR is merged)
- Date: 2026-10-19
- Rules: `docs/rules/announcements.md` (AN7, AN13 to AN17, AN19 to AN21). Related: ADR 0002 (save lifecycle),
  ADR 0003 (embedded voice only).

## Context

Issue #32 adds three long-lived resources and one new piece of stored data:
- a `TextToSpeech` binding to the system engine (another app's service);
- a `SoundPool` with seven decoded sounds;
- a second `TextToSpeech` binding for the "Settings" screen, which checks the voice and says "Announcements on";
- three booleans: voice on, sounds on, "no voice" message already shown.

The issue requires that the engine and the sound pool live only while the game screen is shown, that the Settings
screen has its own instance released on leaving, that initialization never blocks input, that nothing is replayed
on recreation, and that settings are stored apart from the saved game file, whose format does not change.

## Decision 1: an audio session exists exactly while its screen is shown and the activity is started

`AudioBinder`, owned by `MainActivity`, observes two things: the current `Screen` (from `KosmoDartsViewModel.state`)
and the activity lifecycle.
- A `GameAudioSession` (sound player, speaker, sequencer) exists exactly while the screen is `Game` or `End` and the
  activity is at least `STARTED`. It is created on entering that state and released on leaving it (`ON_STOP`, or
  another screen). The end screen keeps the session because "Finish game" opens it and the win is announced there
  (AN5); found by the end-to-end tests of the code phase, where a session limited to `Game` was released before
  the win cue arrived. While it exists, `KosmoDartsViewModel.cueSink` points to it; otherwise the sink does nothing.
- A Settings voice probe exists exactly while the screen is `Settings` and the activity is started; it is attached
  to `SettingsViewModel` and released on leaving. Returning from the engine's install screen restarts the activity,
  so the voice is checked again (AN17).
- Cues are pushed to the sink when the event happens, with no buffer and no replay: a session created later (after a
  rotation, a resume, a return to the foreground) never receives an old cue (AN7).

Consequences: a rotation stops the announcement in progress (the activity stops); a game screen in the background
plays nothing (AN21); no `Context` is held by a ViewModel.

Alternatives rejected:
- *Session owned by the ViewModel*, surviving rotation: it would keep an engine binding while the activity is
  stopped, which AN21 forbids, and give the ViewModel a `Context`-bound resource to manage.
- *Session owned by the Application for the whole process*: the engine binding and the decoded sounds would stay in
  memory on every screen, against the issue's lifecycle constraint.
- *Composable `DisposableEffect`*: puts lifecycle logic in composables, against the project rule.
- *A replaying `StateFlow` or `SharedFlow(replay = 1)` of cues*: a new collector would replay the last cue.

## Decision 2: settings in a dedicated SharedPreferences file, with a validated, versioned read

`SharedPreferencesAudioSettingsStore` writes the file `audio_settings` (private mode, `apply()`), separate from
`saved_game` and `last_setup` (ADR 0001). Every write stores `format_version = 1` and the three booleans together.

Reads never trust the content: the store reads `getAll()` once (a `RuntimeException` gives the defaults) and hands
the map to the pure `AudioSettingsDecoder`:
- `format_version` must be the `Int` 1; otherwise (missing, wrong type, any other number) all values are defaults;
- each boolean key of another type, or missing, gets its own default; the others are kept;
- unknown keys are ignored.

An unparsable XML file is handled by the platform as an empty map, so defaults. Losing the settings only restores
the defaults (voice on, sounds on, message not shown yet), so there is no integrity check. The file is in the
`sharedpref` domain, already excluded from backup and device transfer.

`AudioSettingsRepository`, held by `KosmoDartsApplication` like `Persistence`, reads the store once and exposes a
`StateFlow<AudioSettings>` shared by `KosmoDartsViewModel` (game, AN16) and `SettingsViewModel`.

Alternatives rejected:
- *A third codec over `AtomicFileStore` and `SaveFormat`* (ADR 0001): CRC, Base64 and atomic writes protect a game
  that must not be lost; three booleans whose loss is harmless do not need them, and the decoder gives the same
  guarantees that matter here (no crash, versioned, defaults on invalid content).
- *Adding the booleans to the saved game or last setup file*: the issue forbids changing the save format, and the
  settings must survive the end or abandon of a game, which deletes the saved game.
- *Jetpack DataStore*: a new dependency for three booleans.
