# Announcement and sound rules (voice, sound effects, settings)

Status: v1 (issue #32). This document is the single source of truth for the voice announcements, the sound effects
and the "Settings" screen: tests cite these rules, never the issue text. Rule ids (AN1, AN2, ...) are stable. They use
the prefix AN so that they never collide with the X01 rules (`docs/rules/x01.md`) or the persistence rules
(`docs/rules/persistence.md`).

Vocabulary:
- *Cue*: a semantic event produced by `:game-engine` (dart hit, bust, turn start...). It carries no text and no sound.
- *Voice*: an announcement spoken by Android's text-to-speech (TTS) engine.
- *Sound*: one of the seven sound effects of `app/src/main/res/raw/`.
- *Usable voice*: see AN12.

## Cues (`:game-engine`)

- AN1. Every recorded dart produces one dart cue: *hit* when it scores at least one point, *miss* when it scores 0
  (a dart outside the scoring area). A miss never also produces a hit.
- AN2. A dart that completes a turn other than a checkout also produces one end-of-turn cue, after its dart cue:
  - *bust* when the turn is a bust;
  - else *one hundred and eighty* when the turn scores 180;
  - else *no score* when the turn scores 0;
  - else *turn score N*, N from 1 to 179.

  A checkout dart produces its dart cue only: the winning turn has no score cue.
- AN3. A checkout is *possible* for a remaining score R when at most three darts can score exactly R, the last one
  being a double or the bull in double out, or any scoring dart in single out. It is computed by enumerating the
  darts of the board (X01 rules D1 to D3); there is no table of values in the code. Examples: in double out, 170 and
  2 are possible, 169, 168, 166, 165, 163, 162, 159 and 1 are not; in single out, 180, 2 and 1 are possible, 179 is
  not. Scores under 1 or over 180 are never possible.
- AN4. A *turn start* cue names the player who throws next, with their remaining score when a checkout is possible
  for it (AN3) under the game's checkout rule. It is produced:
  - when a game is started with "Start" or "Rematch" (first player);
  - when "Next player" moves play on after a normal turn or a bust.
- AN5. A *win* cue is produced only when "Finish game" is tapped in the checkout dialog: *big fish* when the winning
  turn started at 170, else *game shot*. Nothing is produced when the checkout dialog's Undo is used.
- AN6. Every undone dart produces one *undo* cue, and nothing else. If an undone event happens again (the same dart
  entered again), its cues are produced again.
- AN7. Cues come only from a new user action on the game screen (AN1, AN2, AN4 to AN6). Nothing is produced when a
  game is resumed (from the home screen or after Android stopped the app), on a rotation or any other activity
  recreation, on a recomposition, or when the game screen becomes visible again.

## Rendering (`:app`)

- AN8. The voice says, in the app language (texts in `values/` and `values-fr/`, numbers passed as digits):

  | Cue | English | Français |
  |---|---|---|
  | Turn start | "{name}" | « {nom} » |
  | Turn start, checkout possible | "{name}, you require {R}" | « {nom}, il te reste {R} » |
  | Turn score N (1 to 179) | "{N}" | « {N} » |
  | One hundred and eighty | "One hundred and eighty!" | « Cent quatre-vingts ! » |
  | No score | "No score" | « Pas de score » |
  | Bust | "Bust" | « Bust » |
  | Game shot | "Game shot! {name} wins" | « Victoire de {nom} » |
  | Big fish | "Big Fish! {name} wins" | « Big Fish ! Victoire de {nom} » |
  | Voice switched on in Settings | "Announcements on" | « Annonces activées » |

  Hit, miss and undo cues are never spoken. Speech rate, pitch and the fine choice of voice are the system's (AN22).
- AN9. Sounds:

  | Cue | Sound |
  |---|---|
  | Hit | `sfx_dart_hit` |
  | Miss | `sfx_dart_miss` (instead of the hit) |
  | Undo | `sfx_dart_cancel` |
  | Bust | `sfx_bust` |
  | One hundred and eighty | `sfx_one_eighty` |
  | Big fish | `sfx_big_fish` (instead of `sfx_game_shot`) |
  | Game shot | `sfx_game_shot` |

  Turn score, no score and turn start have no sound.
- AN10. Order and overlap:
  - a dart sound plays at once; dart sounds may overlap each other; a dart sound is never queued nor played late (a
    sound not loaded yet is skipped);
  - the event sound of a cue plays after the dart sound of the same dart has ended; its voice plays after the event
    sound has ended. A voice and an event sound never overlap;
  - a turn start voice waits for the end of the announcement in progress or pending.
- AN11. The voice speaks the language the app's texts are shown in (English or French). There is no language
  setting. The country of the phone's locale only orders several voices of that language. There is no fallback to
  another language: without a usable voice in the app language, AN16 applies.
- AN12. A *usable voice* is a voice of the app language (AN11) whose `isNetworkConnectionRequired()` is `false` and
  whose features contain neither `KEY_FEATURE_NOT_INSTALLED` nor `KEY_FEATURE_NETWORK_SYNTHESIS`. A network voice is
  never chosen, even if it is the only
  one. No text is ever sent to the TTS engine before a usable voice is set on it.

## Settings

- AN13. A gear button in the top bar of the home screen opens the "Settings" screen. Neither the setup screen nor the
  game screen gives access to it, and the game screen has no sound control. System back, and the back arrow of its
  top bar, return from "Settings" to
  the home screen, with its saved game summary unchanged. The "Settings" screen shows:
  - a "Voice" switch;
  - a "Sounds" switch;
  - under the "Voice" switch, a status line: a usable voice is available, or there is no offline voice for the app
    language (then with an "Install a voice" button, AN17), or the check is in progress;
  - at the bottom, a non-clickable mention: "Sound effects generated with elevenlabs.io" / « Effets sonores générés
    avec elevenlabs.io ».
- AN14. The switches are independent: sounds play without the voice and the reverse. Both are on by default.
  Switching the voice on while a usable voice is available says "Announcements on". Switching it on while no usable
  voice is available (or while the check is in progress) leaves it off; the status line says why. Switching the voice
  off stops nothing else. With the voice off, nothing is spoken; with sounds off, no sound is played.
- AN15. The switches and the AN16 flag are stored on the device, apart from the saved game file, whose format does not
  change. They survive the app being stopped, and are not cleared by the end or the abandon of a game.

## No usable voice

- AN16. On a game start (AN4) with the voice switched on, if the game screen finds no usable voice and the message
  was never shown on this device, a message explains it once, with an "Install a voice" button (AN17) and "OK".
  System back or "OK" closes it; a tap outside does not. The game goes on with sounds only. Once shown, it is never
  shown again, even after the app is stopped.
- AN17. "Install a voice" (in the AN16 message and on "Settings") opens `TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA`
  of the default TTS engine: the system engine downloads the voice, not the app. Without a TTS engine, or when no
  activity answers the intent, the explanation shows without the button, and a failed launch never crashes. Voice
  availability is checked again each time the "Settings" screen is opened or shown again (for example when coming back
  from the engine's install screen).
- AN18. If the voice becomes unusable during a game (error from the engine, voice removed), the app goes silent,
  without crash and without message. Sounds keep playing.

## Volume and other apps

- AN19. Voice and sounds use the media volume. On the game screen and the end screen that follows it, the phone's volume buttons set the media volume.
  Silent and vibrate modes do not mute them. Do Not Disturb applies its own media setting; the app does not work
  around it.
- AN20. Each voice announcement requests transient audio focus with ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`)
  and abandons it at the end of the utterance, when it is stopped, or on an error. When focus is refused (during a
  call, for example), the announcement is skipped and never played later; sounds still play. Sounds never request
  focus.
- AN21. When the game screen is no longer visible (another app, screen off, leaving the game screen), everything in
  progress or pending stops, and nothing is played until it is visible again; then only new cues play (AN7). The
  end screen counts as part of the game here: the win is announced on it, after "Finish game" (AN5), and a rematch
  starts from it.
- AN22. Player names are spoken at their turn start and at the win. Rate, pitch and voice details come from the
  system settings.

## Undo

- AN23. An undo cue first stops at once every sound and voice in progress or pending, then plays `sfx_dart_cancel`
  (if sounds are on). The voice says nothing on an undo.

## Credits

- AN24. The seven sounds were generated with ElevenLabs (free plan, which requires the mention "elevenlabs.io").
  `docs/sounds/CREDITS.md` lists the seven files, the tool, the plan and the generation date (October 2026); the
  mention is also in the app (AN13). The files are used as delivered on the `assets/sound-effects` branch, unchanged.
