# ADR 0003: Embedded voice only

- Status: Accepted (PR #33, merged on 2026-10-20 as 61d80a9; proposed in the design review of issue #32)
- Date: 2026-10-19
- Rules: `docs/rules/announcements.md` (AN11, AN12, AN16 to AN18, AN20).

## Context

Kosmo Darts works fully offline and does not declare the `INTERNET` permission. The voice announcements of issue #32
use Android's text-to-speech (TTS) service. The TTS engine is another app (Google's speech services on a Pixel 7, or
any engine the user installed). It runs in its own process, with its own permissions, `INTERNET` included.

So the absence of `INTERNET` in our manifest does not stop announcement texts from leaving the phone: if the engine
synthesizes with a *network voice*, it sends the text to its server. Our texts contain player names (personal data
typed by the users) and scores.

## Decision

The app speaks only with an *embedded* voice, and never sends a text to the engine before such a voice is set:
1. After the engine is initialized, the app reads `TextToSpeech.getVoices()` and keeps only the voices of the app
   language whose `isNetworkConnectionRequired()` is `false` and whose features contain neither
   `TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED` nor `TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS` (AN12).
2. It sets the chosen voice with `setVoice`, and speaks only if that call succeeds.
3. It never calls `setLanguage`: the engine may then pick any voice of that language, a network one included.
4. When no voice qualifies, the app stays silent (sounds still play) and offers "Install a voice", which opens the
   engine's own install screen (`ACTION_INSTALL_TTS_DATA`): the engine downloads the voice, not the app (AN16, AN17).
5. A network voice is never chosen, even when it is the only one.

## Consequences

- On a phone without an installed voice for the app language, there are no announcements until the user installs
  one. The first game explains it once (AN16) and Settings shows it (AN13).
- Residual risk: the app relies on what the engine declares about its voices. An engine that declares a network voice
  as embedded, or that sends texts for other purposes (logging, telemetry), cannot be detected by the app. This is
  the engine's responsibility, under the user's choice of engine.
- Tests cover the selection with fake voices (network only, not installed, other language) and Robolectric's
  `ShadowTextToSpeech`. Canary versions of the selection are shown failing those tests before they are trusted.

## Alternatives rejected

- *Use the engine's default voice with `setLanguage`*: simplest, but the default voice may be a network voice.
- *Ask the engine for embedded synthesis per utterance* (`Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS` in the speak
  parameters): deprecated since API 21, and engines are free to ignore it. Voice selection replaces it.
- *Fall back to another language's embedded voice*: the owner refused it (issue #32, D3): a French text spoken by an
  English voice is worse than silence.
- *Pre-recorded voice files in the app*: names cannot be pre-recorded, and the APK size would grow.
- *Bundle our own TTS engine library*: a large dependency, against the minimal-dependency rule.
