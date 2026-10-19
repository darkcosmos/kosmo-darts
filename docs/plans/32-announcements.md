# Plan: voice announcements, sound effects and the Settings screen (issue #32)

## Goal

Issue: https://github.com/darkcosmos/kosmo-darts/issues/32

During a 501/301 game, a voice announces whose turn it is, the turn scores and the win, and short sound effects
mark every dart, bust, 180 and win. Everything stays fully offline: only an embedded (on-device) voice is ever used.
A new "Settings" screen, opened from the home screen, switches the voice and the sounds on or off.

Rules: `docs/rules/announcements.md` (AN1 to AN24, new). Decision: `docs/adr/0003-embedded-voice-only.md` (new).
Credits: `docs/sounds/CREDITS.md` (new). The mission branch `feat/announcements` starts from `assets/sound-effects`
(commit b632807, itself on top of `main` 39ad177); the seven `.ogg` files are not renamed nor modified.

Measured sound durations (Ogg Vorbis, last granule position / sample rate): hit 0.51 s, miss 0.42 s, cancel 1.00 s,
bust 1.91 s, one eighty 4.00 s, big fish 4.00 s, game shot 5.16 s.

## Scope and out of scope

In scope (issue #32, D1 to D8):
- Engine cues (AN1 to AN7) and the checkout-possible computation (AN3) in `:game-engine`.
- Rendering cues to localized speech and sounds in `:app`, behind two replaceable interfaces (`Speaker`,
  `SoundPlayer`), with the ordering rules (AN8 to AN10, AN23).
- Embedded voice selection (AN11, AN12), audio focus (AN20), lifecycle (AN21).
- The "Settings" screen, its own ViewModel, stored settings (AN13 to AN15).
- The "no usable voice" message and the "Install a voice" button (AN16 to AN18).
- `docs/rules/announcements.md`, ADR 0003, `docs/sounds/CREDITS.md`, this plan.

Out of scope (issue): sound controls during a game, checkout suggestions ("double 16"), language or voice setting in
the app, announcements during a call, camera mode, replacing the sounds.

Out of scope (this plan):
- Keeping an announcement going across a rotation: a rotation recreates the activity, which stops what is playing
  (AN21); nothing is replayed (AN7). See Known limitations.
- TalkBack coordination: TalkBack and our voice may overlap. Reported, not solved (issue, UX notes).

## Approach

### 1. Engine: cues and checkout possibility (`:game-engine`, pure JVM)

New files in `io.github.darkcosmos.kosmodarts.engine.x01`:

```kotlin
/** What to announce or play, without text or sound (docs/rules/announcements.md). */
sealed interface X01Cue {
    data object DartHit : X01Cue                     // AN1
    data object DartMiss : X01Cue                    // AN1
    data object DartUndone : X01Cue                  // AN6
    data class TurnScore(val points: Int) : X01Cue   // AN2, 1..179
    data object OneEighty : X01Cue                   // AN2
    data object NoScore : X01Cue                     // AN2
    data object Bust : X01Cue                        // AN2
    data class TurnStart(val playerIndex: Int, val name: String, val checkoutRemaining: Int?) : X01Cue  // AN4
    data class GameShot(val playerIndex: Int, val name: String) : X01Cue  // AN5
    data class BigFish(val playerIndex: Int, val name: String) : X01Cue   // AN5
}

object X01Cues {
    /** AN1, AN2: cues of the dart that changed [before] into [after] (exactly one more dart). */
    fun afterDart(before: X01Game, after: X01Game): List<X01Cue>
    /** AN4: the player who throws next in [game]; null once the leg is finished. */
    fun turnStart(game: X01Game): X01Cue.TurnStart?
    /** AN5: the win of a finished [game]; null while it is in progress. */
    fun checkoutConfirmed(game: X01Game): X01Cue?
    /** AN6 */
    val dartUndone: X01Cue
}

object Checkouts {
    /** AN3: whether at most three darts can finish exactly [remaining] under [rule]. Enumeration, no table. */
    fun isPossible(remaining: Int, rule: CheckoutRule): Boolean
}
```

- `afterDart` reads `after.darts.last()` for the dart cue (`value == 0` -> miss), and compares
  `completedTurns.size` before and after to find a completed turn; its `outcome` and the sum of its darts choose the
  end-of-turn cue (bust first, then 180, then 0, then N). A `CHECKOUT` turn adds nothing.
- `checkoutConfirmed` reads the last completed turn: `remainingBefore == 170` -> `BigFish`, else `GameShot`.
- `Checkouts.isPossible` enumerates the 62 scoring darts (singles, doubles, triples of 1 to 20, 25, 50) as 1, 2 or 3
  dart sequences, the last one being a finishing dart (`countsAsDouble` in double out, any scoring dart in single
  out). The possible values are computed once per rule into a lazy set (2..170 at most), so a call is O(1). Misses
  are not enumerated: they never help to finish.
- Game-rule values are named constants in `:game-engine` (A3): `BIG_FISH_REMAINING = 170`,
  `MAX_TURN_SCORE = 180`, and the existing three-dart turn length, made `internal const val DARTS_PER_TURN` in one
  place (today a private constant of `X01Replay.kt`). The board darts come from `Dart.NUMBERS`, `Multiplier.entries`,
  `Dart.OUTER_BULL` and `Dart.BULL`; nothing re-declares them.
- The engine decides *what*; the hook points (when) are in the app, because "Next player", the checkout dialog and
  the game start are screen concepts, not engine concepts (`GameScreenState`, `AppReducer`).

Alternatives considered: a cue list stored inside `X01Game` (rejected: `X01Game` is a pure replay of darts, and
"Next player" / "Finish game" are not engine actions); cues computed in `:app` (rejected by the issue: the engine
decides what to announce).

### 2. App: hook points in the game ViewModel

`TurnCompletedHook` and `TurnCompletion.kt` are replaced by `GameCues.kt` (`ui/app`):

```kotlin
/** AN1 to AN7: cues of the event that changed [before] into [after]. Pure. */
fun gameCues(before: AppState, event: AppEvent, after: AppState): List<X01Cue>
```

It returns cues only for these five hook points, on the game screen:
1. dart entry: the game has exactly one more dart -> `X01Cues.afterDart` (includes "turn completed", AN2);
2. undo: exactly one dart less -> `X01Cues.dartUndone`;
3. turn start: `StartRequested` or `Rematch` that opens the game screen, or `NextPlayerTapped` that ended a waiting
   turn -> `X01Cues.turnStart`;
4. win confirmation: `FinishGameTapped` that moves from the checkout dialog to the end screen ->
   `X01Cues.checkoutConfirmed`;
5. (the "turn completed" hook is the end-of-turn part of 1.)

Everything else returns an empty list: `ResumeTapped`, the initial state built after process death, rotation (the
ViewModel survives and receives no event), recomposition (no event), Back, Abandon (AN7).

`KosmoDartsViewModel.onEvent` calls `gameCues(before, event, after)` and hands a non-empty list to
`var cueSink: CueSink` (fun interface, default no-op), like the current `turnCompletedHook`. The sink is set by the
activity only while the game screen is visible (section 4); there is no buffered flow, so no cue can be delivered
twice or late.

### 3. App: rendering cues (`io.github.darkcosmos.kosmodarts.audio`)

Pure Kotlin (no `android.*`), JVM-tested:
- `SoundEffect` enum: `DART_HIT`, `DART_MISS`, `DART_CANCEL`, `BUST`, `ONE_EIGHTY`, `BIG_FISH`, `GAME_SHOT`.
- `Phrases` interface (one function per AN8 line, numbers as `Int`), implemented by `ResourcePhrases(resources)`
  with `getString(R.string.announce_..., name, number)`.
- `CueScript.of(cue, phrases): CueScript(dartSound: SoundEffect?, eventSound: SoundEffect?, speech: String?,
  isTurnStart: Boolean, isUndo: Boolean)`: the AN8/AN9 tables, nothing else.
- `interface SoundPlayer { fun play(effect): Boolean; fun durationMillis(effect): Long?; fun stopAll(); fun release() }`
  (`play` returns false when the sound is not loaded yet: it is then skipped, AN10).
- `interface Speaker { val status: StateFlow<VoiceStatus>; fun speak(text: String, onEnd: () -> Unit);
  fun stop(); fun release() }`. `onEnd` is called exactly once per `speak`, on the main thread: after the utterance,
  on error, on stop, or at once when the utterance is skipped (no usable voice, focus refused).
- `interface Scheduler { fun post(delayMillis: Long, action: () -> Unit): Cancellable }`.
- `AnnouncementPlayer(soundPlayer, speaker, scheduler, settings: () -> AudioSettings)`: the sequencer.
  - Dart sound: `play` at once if sounds are on; remembers `dartSoundEndsAt = now + duration` (AN10).
  - Event sound: scheduled at the end of the dart sound (or of the announcement in progress); its voice is queued
    after the event sound's duration.
  - Turn start voice: queued after whatever is in progress or pending (AN10).
  - A single FIFO queue of steps (`Sound(effect)`, `Speech(text)`); one step at a time; `Sound` steps advance after
    `durationMillis` (default 1,000 ms when unknown), `Speech` steps advance on `onEnd`.
  - Voice off -> `Speech` steps are dropped; sounds off -> `Sound` steps and dart sounds are dropped (AN14).
  - Undo (AN23): increments a generation counter, cancels scheduled steps, clears the queue, `speaker.stop()`,
    `soundPlayer.stopAll()`, then plays `DART_CANCEL` if sounds are on. Late `onEnd` callbacks of the old generation
    are ignored.
  - `stopAll()` (AN21): same as undo, without the cancel sound.

Android implementations (Robolectric-tested):
- `SoundPoolPlayer(context)`: `SoundPool` with `AudioAttributes(USAGE_GAME, CONTENT_TYPE_SONIFICATION)` (media
  volume, AN19), max 6 streams; loads the seven raw resources asynchronously (load-complete listener); durations read
  with `MediaMetadataRetriever` on a background thread at load. `stopAll` stops the stream ids it started.
- `TtsSpeaker(context, appLanguage, deviceCountry, audioManager)`:
  - creates `TextToSpeech(context, listener)`; status `Checking` until `onInit`;
  - on `SUCCESS`: reads `voices`, maps them to `VoiceCandidate(name, language, country, networkRequired,
    notInstalled, quality)`, calls the pure `VoiceChoice.choose(...)` (AN11, AN12), then `setVoice(chosen)`; status
    `Available` only if `setVoice` returns `SUCCESS`, else `NoUsableVoice(canInstall)`;
  - on init failure: `NoEngine`;
  - never calls `setLanguage` (it may select a network voice) and never `speak`s unless the status is `Available`
    (AN12). A `speak` while `Checking` is held (only the latest one) until the status is known, then spoken or
    skipped: the first player's name at game start is not lost while the engine binds;
  - per utterance: `AudioFocusRequest(AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)` with `AudioAttributes(USAGE_GAME)`
    (see Open questions), the same attributes on the TTS (`setAudioAttributes`); refused -> skipped (AN20);
    `UtteranceProgressListener` `onDone`,
    `onError`, `onStop` -> abandon focus, post `onEnd` to the main thread;
  - an `onError` sets the status to `NoUsableVoice` silently for the rest of the session (AN18); `onEnd` is still
    called once for that utterance, so the queue never stalls;
  - texts go to `speak(CharSequence, QUEUE_FLUSH, params, utteranceId)` as plain `String`s (no SSML, no spans);
    names are already capped at 20 code points by the engine config (X01 rule C4). Logs never contain a text or a
    name: errors log the utterance id and the error code only (S5).
- `VoiceChoice.choose(candidates, language, country)` (pure): keeps usable voices (AN12) of `language`; orders by
  same country first, then higher quality, then name (deterministic). Returns `null` when none. Besides AN12, it
  also rejects voices whose features contain `KEY_FEATURE_NETWORK_SYNTHESIS` (S2); `null` features count as empty.
- App language (AN11): a translatable string resource `announce_language` = `en` in `values/`, `fr` in
  `values-fr/`. It is the language the app's texts are really shown in (a German phone gets English texts, so an
  English voice). The device country (`configuration.locales[0].country`) is used only if that locale's language
  equals it.
- `HandlerScheduler`: `Handler(Looper.getMainLooper())`.
- `VoiceInstaller`: builds `Intent(ACTION_INSTALL_TTS_DATA).setPackage(tts.defaultEngine)`; a `null` or blank
  default engine gives `canInstall = false` (S3); else `canInstall` = `packageManager.resolveActivity(intent, 0) !=
  null`. `launch` catches `ActivityNotFoundException` and `SecurityException` and returns `false`; the caller then
  shows the explanation without the button (AN17).

### 4. Lifecycle: who creates and releases what

`AudioBinder` (in `MainActivity`, a `DefaultLifecycleObserver` + a collector of `viewModel.state`):
- A `GameAudioSession` (one `SoundPoolPlayer`, one `TtsSpeaker` only if the voice is on, one `AnnouncementPlayer`)
  exists exactly while the screen is `Game` **and** the activity is started. It is created on entering that state
  and released (stop, `tts.shutdown()`, `soundPool.release()`) on leaving it: leaving the game screen, or `ON_STOP`
  (AN21). While it exists, `viewModel.cueSink` points to it; otherwise the sink is a no-op.
- While the game screen is shown, `activity.volumeControlStream = AudioManager.STREAM_MUSIC` (AN19); reset to
  `USE_DEFAULT_STREAM_TYPE` elsewhere.
- A `SettingsVoiceProbe` (its own `TtsSpeaker`) exists exactly while the screen is `Settings` and the activity is
  started; it is attached to the `SettingsViewModel` and released on leaving. Coming back from the engine's install
  screen restarts the activity, so a new probe re-checks the voice (AN17).
- Creation never blocks: `TextToSpeech` binds asynchronously and `SoundPool` loads asynchronously (issue constraint).

Alternative considered: keeping the session in the ViewModel so that it survives rotation. Rejected for now: it would
keep a TTS binding while the activity is stopped, which AN21 forbids anyway, and rotation mid-announcement is rare.

This lifecycle and the settings storage (section 5) are recorded in `docs/adr/0004-audio-session-and-settings.md`
(A1), with their rejected alternatives.

### 5. Settings: storage and ViewModel

- `AudioSettings(voiceEnabled = true, soundsEnabled = true, noVoiceNoticeShown = false)`.
- `AudioSettingsStore` interface; `SharedPreferencesAudioSettingsStore` in a dedicated file
  `audio_settings` (`Context.MODE_PRIVATE`), apart from `saved_game` and `last_setup` (AN15). Writes with `apply()`.
  Backup and device transfer already exclude the `sharedpref` domain (`data_extraction_rules.xml`,
  `full_backup_content.xml`).
- Validated reads (S1, ADR 0004): the store reads `getAll()` once and decodes it with the pure
  `AudioSettingsDecoder.decode(map: Map<String, Any?>): AudioSettings`, which never throws:
  - key `format_version` must be the `Int` 1; missing, wrong type, 0, 2 or any other value -> all defaults;
  - each of `voice_enabled`, `sounds_enabled`, `no_voice_notice_shown` must be a `Boolean`; a missing key or any
    other type gives that key's default only (per-key fallback);
  - unknown keys are ignored.
  
  Every write stores the three booleans and `format_version = 1` together. An unparsable XML file is already
  handled by the platform (empty map, so defaults). `getAll` itself is wrapped: any `RuntimeException` -> defaults.
- `AudioSettingsRepository` (process-wide, held by `KosmoDartsApplication` like `persistence`): reads once,
  exposes `StateFlow<AudioSettings>`, and `setVoiceEnabled`, `setSoundsEnabled`, `markNoVoiceNoticeShown`.
- Alternative considered: a fourth codec over `AtomicFileStore` and `SaveFormat` (ADR 0001). Rejected in
  ADR 0004: three booleans whose loss only restores defaults, and `SharedPreferences` is the platform's tool for
  it; the decoder above gives the same guarantees (no crash, versioned, defaults on invalid content).
- `SettingsViewModel(repository)` (created in `MainActivity` with `by viewModels()`, same pattern as the existing
  ViewModel; no new dependency): state `SettingsUiState(voiceEnabled, soundsEnabled, voiceStatus, canInstall)`;
  `onVoiceToggled(on)`, `onSoundsToggled(on)`, `attachProbe(speaker)`, `detachProbe()`. Voice on is accepted only
  with `VoiceStatus.Available`, and then speaks "Announcements on" through the probe (AN14).
- Refused "voice on" (U1): the switch stays enabled and stays off (it snaps back: the stored value is unchanged, so
  the state never shows "on"). The status line under it says why ("No offline voice..." or "Checking the voice..."),
  and it is a polite live region, so TalkBack reads the reason after the tap. The switch row's `stateDescription`
  stays the platform's on/off.

### 6. Navigation (existing plain-state navigation, no library)

- `Screen.Settings(home: Screen.Home)`: keeps the home state it came from, so Back restores the home screen with its
  saved game summary.
- `savedGameOf(Settings)` = `screen.home.resumableGame`: opening Settings must never delete the saved game
  (persistence ADR 0002, decision 2). `AppReducerTest` and `PersistenceEffectsTest` cover it.
- `AppEvent.SettingsTapped` (home only; ignored elsewhere, AN13); `Back` on Settings -> `screen.home`.
- `isOnGameScreen` is unchanged: after process death on Settings, the app comes back on the home screen.

### 7. No-voice message (AN16)

- `Screen.Game` gets two fields, neither saved (`savedGameOf` reads `game` only):
  - `isNewGame: Boolean = false`, set to `true` by `AppReducer` only when Start or Rematch opens the game screen
    (A2: the flag lives in `AppState`, the reducer is pure, a JVM test reaches it). Resume and the initial state after
    process death build `Screen.Game` with `false`;
  - `noVoiceNotice: NoVoiceNotice? = null` (`NoVoiceNotice(canInstall: Boolean)`), the open message.
- `GameAudioSession` reports its voice status. The activity forwards `NoUsableVoice`/`NoEngine` as
  `AppEvent.VoiceUnavailable(canInstall)` to `KosmoDartsViewModel.onEvent`. The ViewModel adds the stored settings
  to the decision: the pure function `shouldShowNoVoiceNotice(screen, settings)` is true only on `Screen.Game` with
  `isNewGame`, `settings.voiceEnabled` and not `settings.noVoiceNoticeShown`. If true, the ViewModel calls
  `markNoVoiceNoticeShown()` first, then the reducer opens the message (so a crash or kill never shows it twice).
  If false, the event changes nothing.
- The message is an `AlertDialog`: "No offline voice" / « Aucune voix hors ligne », a text, "Install a voice" (only
  if `canInstall`) and "OK". Back closes it (`AppEvent.NoVoiceNoticeDismissed`); a tap outside does not
  (`dismissOnClickOutside = false`), so a keypad tap that lands as the message opens is swallowed by the scrim
  without closing it unread (U5). "Install a voice" launches `VoiceInstaller` (activity callback, not in the
  reducer) and closes the message. The keypad stays as it was underneath; no dart is lost or added.

### 8. Manifest, permissions, dependencies

- Only change: `<queries><intent><action android:name="android.intent.action.TTS_SERVICE" /></intent></queries>`,
  needed on Android 11+ to bind the TTS engine and resolve its install activity.
- No permission, no exported component, no dependency. `SoundPool`, `MediaMetadataRetriever`, `TextToSpeech`,
  `AudioManager`, `AudioFocusRequest` are framework classes. Icons: no Material icons artifact is on the classpath
  (material3 no longer brings it), so the gear is a vector drawable `ic_settings.xml` copied from Material Symbols
  "settings" (Apache License 2.0; source and licence noted in the file header).

## Files and types added or changed

`:game-engine`:
- `engine/x01/X01Cue.kt` (new): `X01Cue`, `X01Cues`.
- `engine/x01/Checkouts.kt` (new).
- Tests: `X01CuesTest.kt`, `CheckoutsTest.kt` (new).

`:app` main:
- `audio/SoundEffect.kt`, `audio/Phrases.kt`, `audio/CueScript.kt`, `audio/SoundPlayer.kt`, `audio/Speaker.kt`,
  `audio/Scheduler.kt`, `audio/AnnouncementPlayer.kt`, `audio/VoiceChoice.kt`, `audio/VoiceStatus.kt` (pure).
- `audio/ResourcePhrases.kt`, `audio/SoundPoolPlayer.kt`, `audio/TtsSpeaker.kt`, `audio/HandlerScheduler.kt`,
  `audio/VoiceInstaller.kt`, `audio/GameAudioSession.kt`, `audio/AudioBinder.kt` (Android).
- `settings/AudioSettings.kt`, `settings/AudioSettingsStore.kt`, `settings/AudioSettingsDecoder.kt` (pure),
  `settings/SharedPreferencesAudioSettingsStore.kt`, `settings/AudioSettingsRepository.kt`.
- `ui/app/NoVoiceNotice.kt` (`NoVoiceNotice`, `shouldShowNoVoiceNotice`).
- `ui/settings/SettingsScreen.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsTags.kt`.
- `ui/app/GameCues.kt` (new, replaces `TurnCompletion.kt` and `TurnCompletedHook.kt`), `ui/app/CueSink.kt`.
- Changed: `AppState.kt` (`Screen.Settings`, `Screen.Game.isNewGame` and `noVoiceNotice`, events `SettingsTapped`,
  `VoiceUnavailable`, `NoVoiceNoticeDismissed`), `AppReducer.kt`, `PersistenceEffect.kt` (`savedGameOf`),
  `KosmoDartsViewModel.kt`,
  `KosmoDartsApplication.kt` (settings repository), `MainActivity.kt` (second ViewModel, `AudioBinder`, install
  callback), `KosmoDartsApp.kt`, `HomeScreen.kt` (top bar with gear), `GameScreen.kt` (no-voice dialog only).
- Resources: `values/strings.xml`, `values-fr/strings.xml` (announcements, settings, message),
  `drawable/ic_settings.xml`, `drawable/ic_arrow_back.xml`.
- `AndroidManifest.xml` (`<queries>`).

`:app` tests: see Test plan. `TurnCompletionTest.kt` becomes `GameCuesTest.kt`.

Docs: `docs/rules/announcements.md`, `docs/adr/0003-embedded-voice-only.md`,
`docs/adr/0004-audio-session-and-settings.md`, `docs/sounds/CREDITS.md`, this plan.

Commits are ordered so the PR can be read in slices (A4): docs, engine cues, app audio, Settings and navigation,
no-voice message, captures.

## Data read, stored or exposed

- Stored: `shared_prefs/audio_settings.xml` in the app's private storage: three booleans. No name, no game data.
  Excluded from backup and transfer by the existing rules.
- Sent to the TTS engine (another app, the system's engine): announcement texts, which contain player names and
  scores. Only after a usable embedded voice is set (AN12); never with a network voice. ADR 0003 explains the residual
  risk: the engine is a third-party app with its own permissions; we cannot prove what it does, we only refuse the
  voices that declare a network need or are not installed, and we never call `setLanguage`.
- Read: the list of TTS voices (name, locale, features), the default engine package, the app locale.
- Outgoing intent: `ACTION_INSTALL_TTS_DATA`, explicit package of the default engine, no extras.
- Nothing is exposed: no exported component added, no content provider, no log of names.

## Screens and states

- Home: new top bar with a gear `IconButton` (48 dp target, content description "Settings" / « Réglages »), in both
  states (with and without saved game). The top bar takes the status bar and display cutout insets
  (`WindowInsets.safeDrawing` top and horizontal), like the existing screens' `safeDrawingPadding` (U4).
- Settings (new), scrollable, centered column max 560 dp like the home screen, `safeDrawingPadding` (U4):
  - top bar: back arrow `IconButton` (48 dp, content description "Back" / « Retour », vector drawable
    `ic_arrow_back.xml` from Material Symbols, Apache 2.0) that sends `AppEvent.Back`, and the title "Settings" /
    « Réglages » (heading) (U3); system back does the same;
  - row "Voice" + switch (the whole row toggles, 56 dp min height, `toggleable` semantics);
  - status line: "Offline voice available" / "Checking the voice…" / "No offline voice for this language. The game
    uses sounds only." / "No speech engine on this phone." + "Install a voice" button when `canInstall`;
  - row "Sounds" + switch;
  - bottom: "Sound effects generated with elevenlabs.io" (body small, on-surface-variant, not clickable).
  - TalkBack reading order follows the visual order: back, title, Voice, status line, Install a voice, Sounds,
    credit line. The status line is a polite live region (U2).
- Game: unchanged, except the one-time no-voice dialog. No sound control.

## Test plan

Rule ids are cited in test names and KDoc.

### `:game-engine` (JUnit 6, existing setup)

`CheckoutsTest`:
- AN3 double out: 170, 167, 164, 161, 160, 100, 50, 40, 2 possible; 169, 168, 166, 165, 163, 162, 159 and 1 not;
  exhaustive check that the possible set in double out is exactly 2..170 minus {169, 168, 166, 165, 163, 162, 159}
  (written as a test oracle from the published rule, not shared with the code).
- AN3 single out: 180, 177, 171, 2, 1 possible; 179, 178, 176, 175, 173, 172, 169, 166, 163 not.
- Impossible inputs: 0, -1, 181, 501, `Int.MAX_VALUE` -> false, both rules.
- AN3 at 301 and 501: the start scores are never possible.

`X01CuesTest`:
- AN1 hit for S1, T20, 25, Bull; miss for Miss; never both.
- AN2 turn scores 1, 60, 179 -> `TurnScore`; 180 -> `OneEighty`; three misses -> `NoScore`; bust on dart 1, 2 and 3
  -> `Bust` (and a bust with 0 points stays `Bust`); a checkout dart -> only `DartHit`; first and second darts of a
  turn -> dart cue only.
- AN4 turn start: name only at 501; with remaining when possible (40 double out); 169 double out -> no remaining;
  single out 1 -> remaining; finished leg -> null; 1 player (the same name again after the turn), 4 players
  (wrap-around from the 4th to the 1st), start at 301.
- AN5 win from 170 -> `BigFish`; from 100 -> `GameShot`; a 170 left that is finished in a later turn
  (e.g. 170 -> 20 in turn n, then D10) -> `GameShot`; game in progress -> null.
- AN6 `dartUndone`.
- `afterDart` with a pair that is not exactly one dart apart (same game, two darts more) throws
  `IllegalArgumentException`: documented misuse.

### `:app` pure JVM (JUnit 4, no Robolectric)

`GameCuesTest` (replaces `TurnCompletionTest`), on `AppReducer` transitions:
- each of the five hook points gives the expected cues;
- AN7: `ResumeTapped`, Back, AbandonConfirmed/Dismissed, NumberTapped while awaiting (keypad disabled), Next player
  with nothing waiting, Finish without dialog -> empty;
- AN6: undo of a checkout dart -> `DartUndone` only, then the same dart again -> `DartHit` again; undo of a third
  dart then the same dart -> end-of-turn cue again; undo of the first dart of a turn; undo after "Next player"
  (the previous player's turn reopens: `DartUndone` only, and entering the third dart again gives its end-of-turn
  cue).
- turn start at game start: `StartRequested` and `Rematch` -> `TurnStart`; an invalid `StartRequested` (setup
  errors) -> empty.

`AnnouncementPlayerTest` (fake `SoundPlayer`, fake `Speaker`, fake `Scheduler` with a virtual clock):
- AN10 order: dart sound at t=0, event sound at end of the dart sound, voice after the event sound; turn start voice
  after the end-of-turn voice; voice and event sound never overlap (the fake records intervals).
- AN10 three fast dart sounds overlap, are played at once, none delayed; a not-loaded sound is skipped, not delayed.
- AN23 undo while a voice and pending steps exist: `stop` called, queue empty, then `DART_CANCEL` played; late
  `onEnd` from the stopped utterance plays nothing; with sounds off, no `DART_CANCEL`.
- AN14 voice off -> no `speak`; sounds off -> no `play`; both off -> nothing.
- AN20 a skipped utterance (fake calls `onEnd` at once) lets the next step run; sounds still play.
- AN21 `stopAll` then a late scheduled step -> nothing played.
- AN10 a dart sound entered while a voice or an event sound plays is played at once and leaves the queue unchanged;
  a turn start voice queues behind a long pending end-of-turn sequence (game shot sound 5,160 ms then voice);
  a sound with unknown duration advances after the 1,000 ms default (T4).
- AN18 a `Speech` step whose `onEnd` comes from an error lets the next step run (no stall).

`CueScriptTest`: every cue -> its sound and phrase (AN8, AN9), with a fake `Phrases`.

`VoiceChoiceTest` (AN11, AN12; canary-protected):
- only a network voice -> null; only a not-installed voice -> null; a usable voice of another language -> null;
- usable `fr-FR` and `fr-CA` with country `CA` -> `fr-CA`; with country `BE` -> best quality, then name;
- a network voice of the right country and a usable one of another country -> the usable one.

`SettingsViewModelTest` (fake store, fake probe speaker): defaults on/on; independent toggles; voice on with
`Available` -> stored and "Announcements on" spoken; voice on with `NoUsableVoice`, `NoEngine` or `Checking` -> the
state stays off (snap-back), nothing stored, nothing spoken; voice off -> stored, nothing spoken; no probe attached
-> status `Checking`.

`AudioSettingsDecoderTest` (S1, AN15), pure, maps as `getAll()` returns them:
- empty map -> defaults (on, on, not shown);
- valid version 1 with each combination of the three booleans -> those values (round trip with the encoder's map);
- `format_version` missing, `"1"` (String), `0`, `2`, `99` -> all defaults, even when the booleans are valid;
- one key with a wrong type (`"true"`, `1`, `1L`, `null`) -> that key's default, the others kept (per-key fallback);
- unknown extra keys -> ignored.

`SharedPreferencesAudioSettingsStoreTest` (Robolectric): write then read in a new store instance -> same values;
a preferences file holding a wrong-typed value or `format_version = 2` -> defaults, no exception; the file is
`audio_settings`, distinct from `saved_game` and `last_setup`, and the game files are untouched by a settings write.

`NoVoiceNoticeTest` (T1, AN16), pure, over `AppReducer`, `shouldShowNoVoiceNotice` and `KosmoDartsViewModel` with
a fake settings repository:
- after `StartRequested`, and after `Rematch`, with voice on and the flag unset, `VoiceUnavailable(canInstall)` opens
  the message with that `canInstall`;
- after `ResumeTapped`, and on a game screen built by `initialState` after process death -> no message;
- voice off -> no message (open question 2);
- flag already set -> no message;
- `markNoVoiceNoticeShown` is called before the message opens (the fake records the order of the call and of the
  state change), and the message is still open: the user has not answered;
- `NoVoiceNoticeDismissed` closes it; a second `VoiceUnavailable` in the same game does not reopen it;
- `savedGameOf` of a game screen with the message open equals the one without it (not saved);
- the dialog is modal in the UI, so the reducer needs no extra rule for darts; the Robolectric test checks that a
  tap on the scrim neither closes the message nor enters a
  dart (U5).

`AppReducerTest` / `PersistenceEffectsTest` additions: `SettingsTapped` only from home; Back from settings returns the
same `Screen.Home` (with its saved game); opening and leaving settings writes and deletes nothing.

### Robolectric (`ShadowTextToSpeech.addVoice`, `getSpokenTextList`, `ShadowAudioManager`, fake sound player)

The activity gets its `SoundPlayer` and `Speaker` factories from `KosmoDartsApplication` (replaceable in tests like
`replacePersistence`): the TTS tests use the real `TtsSpeaker` over `ShadowTextToSpeech` (tests call the shadow's
`onInitListener`), and a recording `SoundPlayer` fake (one shared timeline with the speaker's `speak` calls).

`AnnouncementsScreenTest`:
- spoken texts in English and in French (`@Config(qualifiers = "fr")`) for: game start name, "you require 40",
  turn score, 180, no score, bust, game shot, big fish;
- order on one timeline: dart sound, event sound, end-of-turn voice, turn start voice (advance the main looper clock);
- undo during a voice: `isStopped`, then cancel sound; sounds off -> no cancel sound;
- only a network voice (D5): nothing spoken (`getSpokenTextList` empty and `setVoice` never called with it), the
  first-game message shows, the Settings status says no voice; same with a `KEY_FEATURE_NOT_INSTALLED` voice;
- the message shows once: second game, and after activity recreation and a new process (`AppProcess`), not again;
- voice off -> nothing spoken; sounds off -> nothing played;
- AN7: rotation (`AppProcess.rotate`) and resume (home, and `killInBackgroundAndReturn`) -> no new spoken text and
  no new sound;
- AN20: `setNextFocusRequestResponse(AUDIOFOCUS_REQUEST_FAILED)` -> utterance skipped, sounds played; granted ->
  focus requested with `GAIN_TRANSIENT_MAY_DUCK` and abandoned after `onDone`;
- AN21: `ON_STOP` during a pending sequence -> nothing more played; TTS shut down.
- AN18 (T2): a usable voice, then the shadow's `UtteranceProgressListener.onError` fires on an announcement: the next
  cues speak nothing (`getSpokenTextList` unchanged), no message shows, the next dart and event sounds still play,
  and the queue goes on (a turn start voice after the error does not block the sounds after it).
- AN19 (T3): `activity.volumeControlStream` is `AudioManager.STREAM_MUSIC` on the game screen, and
  `USE_DEFAULT_STREAM_TYPE` on home, setup, end and Settings, including after a rotation on the game screen.
- AN12 Checking-hold (T3), `TtsSpeakerTest` over `ShadowTextToSpeech`: two `speak` calls before `onInit` -> nothing
  spoken; `onInit(SUCCESS)` with a usable voice -> only the latest text spoken, once; with only a network voice ->
  nothing spoken and the held `onEnd` called once; `onInit(ERROR)` -> nothing spoken, status `NoEngine`;
  `setVoice` returning `ERROR` -> nothing spoken, status `NoUsableVoice`.
- AN12 (S2): a voice with `KEY_FEATURE_NETWORK_SYNTHESIS` only -> same result as a network voice.
- `VoiceInstallerTest` (S3): null default engine -> `canInstall` false; no resolvable activity -> false; launch that
  throws `ActivityNotFoundException` or `SecurityException` (shadow) -> returns false, no crash.
- `SoundPoolPlayerTest` (T6): `play` before `ShadowSoundPool.notifyResourceLoaded` -> false and nothing played; after
  -> true and `wasResourcePlayed`; `stopAll` after two plays stops both stream ids; `release` then `play` -> false.
- `ResourcePhrasesTest` (T6): each AN8 phrase in English and French, with a name and numbers, equals the table of
  the rules (string parity is also checked by the existing `FrenchStringsTest` pattern).

`SettingsScreenTest`:
- gear on home (with and without saved game) opens Settings; no gear on setup and game screens (AN13);
- defaults on/on; independent switches; values kept after activity recreation and after a new process (AN15);
- elevenlabs.io mention shown at the bottom, English and French;
- install button shown with a resolvable install activity (`ShadowPackageManager`), hidden without; tap with no
  activity -> no crash;
- leaving and coming back re-checks the voice (new probe instance).

### Roborazzi captures (portrait and landscape; English, plus French portrait as existing tests do)

- home with the gear (no saved game, and with a saved game);
- Settings with a voice available;
- Settings without voice, with and without the install button; Settings while checking;
- the first-game message;
- the game screen (no sound control).

Settings (voice available and no voice with button) and the first-game message are also captured in French
landscape.

### Canaries (not committed; shown in the PR)

Before trusting the tests, inject each anomaly locally, run the tests, show the failure, revert:
1. D5: `VoiceChoice` without the `networkRequired` filter -> `VoiceChoiceTest` and the Robolectric network-voice test
   fail.
2. D5: `TtsSpeaker` speaks while `Checking` without a voice -> the network-voice test fails.
3. AN7: `gameCues` returns `turnStart` on `ResumeTapped` -> `GameCuesTest` and the resume test fail.
4. AN7: `AudioBinder` replays the last cues when a session is created -> the rotation test fails.

### Local checks

`./gradlew build :app:assembleDebug` (as CI), plus `aapt2 dump permissions` on the debug APK (no `INTERNET`).

## How Kosmo verifies on the phone (Pixel 7)

The 18 steps of the issue (copied here so they are in the repository), then the extra states the squad asked for.

1. In Settings, Accessibility, Text-to-speech output, engine gear, Install voice data: remove the French voice if
   present.
2. Uninstall the old APK, install the one of the last commit, then turn on airplane mode.
3. On the home screen, open the gear. In "Réglages", Voix and Sons are on by default, and the status line says no
   offline voice is installed, with the "Installer une voix" button. The elevenlabs.io mention is visible at the
   bottom. Go back to home.
4. Start a game: the "no voice" message shows once. Dart sounds work. Check the game screen has no sound control.
5. Start a second game: no new message.
6. Turn off airplane mode, install the French voice with the Settings button, then turn airplane mode on again.
7. Back in "Réglages": the status line says a voice is available. Switch the voice off then on: "Annonces activées".
8. Start a two-player game: the first player's name is spoken.
9. Play and check sounds and announcements: a normal turn; a dart outside the board (miss sound); a turn of 0
   ("Pas de score"); a bust (bust sound, then "Bust"); 180 (180 sound, then "Cent quatre-vingts !"); 40 left at the
   start of a turn ("…, il te reste 40"); 169 left in double out (no remaining spoken).
10. Undo a dart during an announcement: everything stops, then the cancel sound.
11. Rotate the screen, then leave and resume the game: nothing is replayed.
12. Play music in another app: it gets quieter during announcements, then comes back.
13. Put the phone on silent: sounds and voice go on. The volume buttons set the media volume.
14. In "Réglages", turn off sounds only, play a turn, then turn off the voice only and play again: each switch acts
    alone.
15. Close and reopen the app: the settings are kept.
16. Finish a game from 170: Big Fish sound, then "Big Fish ! Victoire de …".
17. Finish another game normally: game shot sound, then "Victoire de …".
18. Switch the phone to English: announcements are in English if an offline English voice is installed; otherwise
    the Settings status line says there is no voice.

Extra states (U6):
19. With no voice installed, in "Réglages": tap the Voix switch to turn it on after turning it off: it stays off and
    the status line explains why (TalkBack on: the reason is read).
20. Rotate the phone on "Réglages" and on the "no voice" message: both are readable in landscape, nothing cut off.
21. Rotate during an announcement: it stops, nothing is replayed.
22. With TalkBack on, open "Réglages" from home: the gear is read "Réglages", the back arrow "Retour", then the
    switches and the status line in order.
23. Without a speech engine (if one can be disabled in Apps): Settings shows the explanation without the button.
24. When the "no voice" message is open (step 4), tap outside it: it stays open; only "OK", "Installer une voix" or
    the back button close it (not covered by an automated test, see Implementation notes).
25. Finish a game: the win is announced on the end screen; there, the volume buttons still set the media volume.
    Tap "Revanche": the first player of the rematch is announced.

## Known limitations

- A rotation stops the announcement in progress (nothing is replayed).
- TalkBack and our voice may overlap.
- The "no voice" message opens when the voice check ends, usually before the first dart; a tap at that very moment
  lands on the message's scrim and is ignored (U5).
- Durations of sounds are read at load; until they are known (a fraction of a second), a 1 s default is used.
- The TTS engine is a third-party app: ADR 0003.
- One failed utterance (engine error) silences the voice until the game screen is shown again (AN18); sounds keep
  playing, and the Settings status line checks the voice again on its next visit.
- With no usable voice, the Voice switch keeps showing its stored value (on by default) and the status line says why
  no voice speaks; a tap to switch it on snaps back (AN14).

## Open questions

1. Audio usage of the voice: `USAGE_ASSISTANCE_SONIFICATION` or `USAGE_GAME`? Proposed: `USAGE_GAME` for both voice
   and sounds, which follows the media volume as D6 requires (sonification may follow another volume on some
   devices). No question for Kosmo unless a reviewer disagrees.
2. AN16 when the voice was switched off before the first game: proposed, no message (the user chose silence). No
   question for Kosmo unless a reviewer disagrees.

## Implementation notes (code phase)

Differences from the design above, each recorded where it applies:
- The game audio session lives on the game screen **and the end screen** (ADR 0004 decision 1, AN19, AN21). The
  end-to-end tests showed that "Finish game" opens the end screen before the win cue is handed over; a session
  limited to the game screen was released first, so the win was never announced. A rematch then keeps the same
  session and announces its first player at once.
- Only the result of the **first** voice check of a session is reported to the ViewModel (`AudioBinder`): a voice
  that fails later (AN18) silences the game without the AN16 message.
- The ViewModel exposes `addCueSink`/`removeCueSink` (several sinks: the session, and tests) and `onScreenChanged`,
  called before the cues of the same event, so that the session exists when the first player's name is cued.
- `AudioSettingsDecoder` is named `AudioSettingsCodec` (encode and decode in one pure object).
- The two icons come from Material Icons (not Material Symbols), Apache 2.0, noted in each drawable.
- The no-voice dialog is in its own file, `ui/game/NoVoiceDialog.kt` (detekt `TooManyFunctions`).
- `KEY_FEATURE_NETWORK_SYNTHESIS` is deprecated: its value `"networkTts"` is a private constant of `TtsSpeaker`, so no
  deprecated API and no suppression are needed.
- A tap outside the no-voice dialog (U5) is set with `DialogProperties(dismissOnClickOutside = false)`; Robolectric
  cannot tap outside a dialog window, so it is not covered by a test. The keypad being inert while the message is
  open is covered by `NoVoiceNoticeTest`.
- The canaries were run on the committed code and reverted (`git checkout`); their output is in the PR.
- Code review round 1 (all four APPROVE, minor findings only) led to: 56 dp of top space on the home screen so that
  scrolled content never passes under the gear (U7); the in-memory settings store moved to the tests and the
  ViewModel's settings parameter made required (A5); end-to-end tests for the win on the end screen and the rematch
  (T10), and for what plays after returning to a hidden game screen (T9); a looper helper in the AN18 test (T11).

## Owner evolutions E1 to E5 (PR #33 review, 2026-10-19)

Kosmo's review of PR #33 completes or replaces parts of the issue; `docs/rules/announcements.md` v2 records them
(table "Owner evolutions", rules AN1, AN2, AN4, AN5, AN8 to AN10, AN13 to AN15, AN21, AN23 to AN26).

### Engine (`:game-engine`)

- `X01Cue.DartHit` becomes `data class DartHit(val dart: Dart)` (AN1, AN25: the app needs the dart to call it).
- `X01Cue.TurnStart` gets `isGameStart: Boolean`, true when no dart was thrown yet (AN4, E1). A turn start cue is
  only produced on Start, Rematch and "Next player" (unchanged), so an empty game means the first turn.
- E3: `X01Cues.afterDart` returns `[DartHit(dart), GameShot | BigFish]` for a checkout dart. `checkoutConfirmed` is
  removed. `gameCues` no longer reacts to "Finish game"; the checkout dialog's Undo stays an undo cue.

### Rendering (`:app`, pure)

- `Phrases`: `turnStart(name, isGameStart)`, `checkoutSuffix(remaining)`, `turnScore`, `noScore`, `win(name)`,
  `dartCall(dart): String?` (null for a miss), `announcementsOn`. `oneEighty`, `bust` and `bigFish` are removed (E2).
- E1, AN26: `PhraseVariants(picker: VariantPicker)` remembers the last index drawn per line and asks
  `fun interface VariantPicker { fun pick(allowed: List<Int>): Int }` to choose among the other indices. Production:
  `RandomVariantPicker(Random.Default)`; tests: a picker that returns the first allowed index (fixed draw).
  `ResourcePhrases(resources, variants)` reads `string-array` resources (`announce_game_start`, `announce_next_turn`)
  and formats the drawn item with the name. A game session owns one `ResourcePhrases`, so "never twice in a row" holds
  across turns and a rematch.
- `CueScript` (E2, E3, AN25): `DartHit` -> `DartSound(DART_HIT, call = phrases.dartCall(dart))`; `DartMiss` ->
  `DartSound(DART_MISS, call = null)`; `Bust` -> `[Sound(BUST)]`; `OneEighty` -> `[Sound(ONE_EIGHTY)]`;
  `GameShot` -> `[Sound(GAME_SHOT), Speech(win)]`; `BigFish` -> `[Sound(BIG_FISH), Speech(win)]`; `TurnStart` ->
  `[Speech(turnStart + optional suffix)]`.
- `AnnouncementPlayer` (AN10, AN25):
  - a dart sound with a call plays the impact sound at once, then, when "Call each dart" and the voice are on:
    removes any call still waiting in the queue, interrupts a call in progress (its late end is ignored), and puts
    the new call at the head of the queue;
  - each running step gets a token; only the end of the current token advances the queue (replaces the single
    generation counter, which `stopAll` still uses);
  - the call waits for its impact sound like any step (AN10), so on the last dart the order is impact, call, then the
    event sound or the score.

### Settings

- `AudioSettings.callEachDart = false` (AN14); key `call_each_dart` in the codec. `format_version` stays 1: an older
  file without the key gives `false` (per-key default), so no migration is needed.
- `SettingsUiState.callEachDart`, `SettingsEvent.CallEachDartToggled(on)`. Row order (AN13, U9): Voice, status line,
  Install a voice, Call each dart, Sounds, credit. The "Call each dart" row has `enabled = voiceEnabled` (greyed when
  the voice is off); its value is kept while greyed and a tap does nothing. While greyed, a supporting line "Needs the
  voice" / « Nécessite la voix » shows under it and is part of the row's TalkBack text (U10).
- Strings: `settings_call_each_dart` ("Call each dart" / « Annoncer chaque fléchette »), `settings_call_each_dart_needs_voice`.

### Sounds (E5)

- `origin/assets/sound-effects` (18e4956, "normalize sound effect levels") merged into the mission branch (689980b).
  Durations are unchanged (measured). `docs/sounds/CREDITS.md` records the normalization; the per-file gains were
  asked to Kosmo on PR #33 (not in the commit).

### Tests added or changed

- Engine: `X01CuesTest` (checkout dart gives the win cue; `isGameStart`; `DartHit(dart)`).
- `GameCuesTest`: "Finish game" gives nothing; the dialog's Undo gives the undo cue.
- `PhraseVariantsTest`: fixed draw; never twice in a row; each line has its own memory.
- `CueScriptTest`: bust and 180 sound only; win sound then "win" voice; dart calls; miss without call.
- `AnnouncementPlayerTest`: call after impact; a new dart cancels a waiting call and interrupts a call being spoken;
  last-dart order (impact, call, event sound or score); calls off or voice off -> no call; bust and 180 with sounds
  off -> nothing; win with sounds off -> win voice.
- `ResourcePhrasesTest`: every variant and dart call in English and French.
- `AnnouncementsScreenTest`: start phrase, varied next-turn phrases, no score voice for bust and 180, win sound and
  voice on the winning dart, nothing on "Finish game", cancel sound when the dialog is refused, big fish only from
  170, dart calls end to end.
- `SettingsScreenTest`, `SettingsViewModelTest`, `AudioSettingsCodecTest`: third switch off by default, greyed with
  the voice off, kept.
- Roborazzi: Settings with three switches, and with the third one greyed.
- Canary: "Finish game" plays the win again -> the "nothing on Finish game" test must fail.

### Design round 3: additions (findings T13 to T18, S8, A6, A7, U9, U10)

Tests (in addition to the list above):
- `AnnouncementPlayerTest` (T14, A6): a new dart removes a waiting call; a new dart interrupts a call being spoken and
  its late end is ignored; a call does not interrupt a turn-start, score or win voice being spoken and waits behind
  it; a call waiting behind such a voice is dropped by the next dart.
- `AnnouncementsScreenTest` (T14, T15): "Finish game" tapped while the win voice is spoken: the voice is not stopped,
  no second win plays, and the rematch announces its first player after the win voice; on the checkout dart with
  calls on: impact, call, win sound, win voice, in that order; undo of the winning dart then the same dart again: the
  win sound and voice again; resuming a saved waiting game (normal, bust and checkout) plays nothing.
- `ResourcePhrasesTest` (T16): every scoring dart (singles, doubles, triples 1 to 20, 25, 50) has a non-empty call in
  English and French; a miss has none. `PhraseVariantsTest`: a seeded `RandomVariantPicker` over 1,000 draws never
  repeats a phrase twice in a row.
- `AudioSettingsCodecTest` (T17, S8): a version 1 file without `call_each_dart` gives `false` and keeps the other
  keys; a wrongly typed `call_each_dart` (string) gives `false`; no exception.
- `SettingsViewModelTest`, `SettingsScreenTest` (U10): voice off -> the call switch is disabled, keeps its value,
  a tap does nothing, and "Needs the voice" / « Nécessite la voix » shows (English and French); voice on -> enabled,
  no supporting line; row order as AN13.
- Roborazzi: Settings with the three switches, and with the third one greyed, in English and French, portrait and
  landscape.

Rules: AN2 says 1 to 177 like AN8 (T18); AN13 gives the row order and the supporting line (U9, U10).

Known limitations (A7): the "never twice in a row" memory belongs to the game audio session, so it starts again when
the session is recreated (rotation, return from the background).

### How Kosmo verifies E1 to E5 on the phone (T13)

The earlier steps 9 (bust and 180), 16 and 17 (big fish and win after "Finish game") and 25 described the
behaviour before E2 and E3; they are replaced by the steps below. The others are unchanged.

E1. Start a two-player game: the first player is announced with a game start phrase ("…, c'est toi qui commences",
    "On commence avec …" or "…, à toi l'honneur"). At each "Joueur suivant", a next-turn phrase ("…, c'est ton tour",
    "…, à toi de jouer", "À toi, …", "…, c'est à toi"); the same phrase never comes twice in a row. With 40 left:
    the phrase, then « Il te reste 40. »
E2. Score 180: only the 180 sound, no voice. Bust: only the bust sound, no voice. Turn off the sounds in Settings,
    then do a 180 and a bust again: silence. A turn of 0 still says « Pas de score ».
E3. Finish from 170 (T20, T20, Bull): the Big Fish sound starts when the third dart is entered, then « Victoire de … »,
    with the checkout dialog open. Tap "Terminer la partie": nothing more plays, and the voice is not cut. In another
    game, enter the winning dart then tap "Annuler" in the dialog: everything stops, then the cancel sound.
    With sounds off, the win still says « Victoire de … ».
E4. In Settings, "Annoncer chaque fléchette" is off. Turn the voice off: the switch is greyed with « Nécessite la
    voix » under it. Turn the voice on, then the switch on. Play 18, Double 20, Triple 20, Bull and a miss: each dart
    is called after its impact sound, except the miss (sound only). Enter three darts quickly: no call comes late.
    On a last dart: impact, call, then the score (or the special sound).
E5. Play the seven sounds at a constant media volume: even levels, no clipping.
