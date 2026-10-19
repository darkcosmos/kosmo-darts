# Announcement and sound rules (voice, sound effects, settings)

Status: v2 (issue #32, with the owner evolutions E1 to E5 of PR #33). This document is the single source of truth for the voice announcements, the sound effects
and the "Settings" screen: tests cite these rules, never the issue text. Rule ids (AN1, AN2, ...) are stable. They use
the prefix AN so that they never collide with the X01 rules (`docs/rules/x01.md`) or the persistence rules
(`docs/rules/persistence.md`).

Vocabulary:
- *Cue*: a semantic event produced by `:game-engine` (dart hit, bust, turn start...). It carries no text and no sound.
- *Voice*: an announcement spoken by Android's text-to-speech (TTS) engine.
- *Sound*: one of the seven sound effects of `app/src/main/res/raw/`.
- *Usable voice*: see AN12.

## Owner evolutions after the tests on the Pixel (PR #33 review, 2026-10-19)

They complete or replace the issue body; in case of contradiction they prevail.

| Id | Evolution | Rules |
|---|---|---|
| E1 | Turn start phrases: the voice says "tu"; random variants, never the same twice in a row; game start phrases differ from the next turns; "Il te reste {R}." / "You require {R}." follows when a checkout is possible. | AN4, AN8, AN26 |
| E2 | Bust, 180, big fish and win: the sound replaces the score. The voice says neither "Bust" nor the 180. The win voice is "Victoire de {nom}" / "Game shot! {name} wins", also with sounds off. | AN8, AN9 |
| E3 | The win sound and voice play on the winning dart, when the checkout dialog opens. "Finish game" plays nothing. Refusing the dialog (its Undo) stops everything and plays the cancel sound. | AN5, AN10, AN23 |
| E4 | New "Call each dart" switch, off by default, greyed while the voice is off: the voice says each dart after its impact sound. | AN13 to AN15, AN25 |
| E5 | The seven sounds are the normalized ones of `assets/sound-effects`. | AN24 |

## Cues (`:game-engine`)

- AN1. Every recorded dart produces one dart cue: *hit* when it scores at least one point, *miss* when it scores 0
  (a dart outside the scoring area). A miss never also produces a hit. A hit carries the dart (AN25).
- AN2. A dart that completes a turn other than a checkout also produces one end-of-turn cue, after its dart cue:
  - *bust* when the turn is a bust;
  - else *one hundred and eighty* when the turn scores 180;
  - else *no score* when the turn scores 0;
  - else *turn score N*, N from 1 to 177 (178 and 179 cannot be scored with three darts).

  A checkout dart produces its dart cue, then the win cue (AN5): the winning turn has no score cue.
- AN3. A checkout is *possible* for a remaining score R when at most three darts can score exactly R, the last one
  being a double or the bull in double out, or any scoring dart in single out. It is computed by enumerating the
  darts of the board (X01 rules D1 to D3); there is no table of values in the code. Examples: in double out, 170 and
  2 are possible, 169, 168, 166, 165, 163, 162, 159 and 1 are not; in single out, 180, 2 and 1 are possible, 179 is
  not. Scores under 1 or over 180 are never possible.
- AN4. A *turn start* cue names the player who throws next, with their remaining score when a checkout is possible
  for it (AN3) under the game's checkout rule, and whether it is the first turn of the game. It is produced:
  - when a game is started with "Start" or "Rematch" (first player);
  - when "Next player" moves play on after a normal turn or a bust.
- AN5. The winning dart produces a *win* cue after its dart cue, as the checkout dialog opens (E3): *big fish* when
  the winning turn started at 170, else *game shot*. "Finish game" produces nothing. The checkout dialog's Undo is
  an undo (AN6, AN23).
- AN6. Every undone dart produces one *undo* cue, and nothing else. If an undone event happens again (the same dart
  entered again), its cues are produced again.
- AN7. Cues come only from a new user action on the game screen (AN1, AN2, AN4 to AN6, AN25). Nothing is produced when a
  game is resumed (from the home screen or after Android stopped the app), on a rotation or any other activity
  recreation, on a recomposition, or when the game screen becomes visible again.

## Rendering (`:app`)

- AN8. The voice says, in the app language (texts in `values/` and `values-fr/`, numbers passed as digits). It says
  "tu" in French. One phrase is drawn among the variants of its line (AN26):

  | Cue | English | Français |
  |---|---|---|
  | Turn start, first turn of the game | "{name}, you're throwing first." / "{name} to throw first." / "Game on! {name} throws first." | « {nom}, c'est toi qui commences. » / « On commence avec {nom}. » / « {nom}, à toi l'honneur. » |
  | Turn start, next turns | "{name}, your turn." / "{name}, you're up." / "Over to you, {name}." / "{name}, step up." | « {nom}, c'est ton tour. » / « {nom}, à toi de jouer. » / « À toi, {nom}. » / « {nom}, c'est à toi. » |
  | Turn start, checkout possible | the phrase above, then "You require {R}." | la phrase ci-dessus, puis « Il te reste {R}. » |
  | Turn score N (1 to 177) | "{N}" | « {N} » |
  | No score | "No score" | « Pas de score » |
  | Game shot or big fish | "Game shot! {name} wins" | « Victoire de {nom} » |
  | Dart call (AN25) | see AN25 | voir AN25 |
  | Voice switched on in Settings | "Announcements on" | « Annonces activées » |

  Bust and one hundred and eighty are never spoken: their sound replaces the score, and with sounds off they are
  silent (E2). Miss and undo cues are never spoken. Speech rate, pitch and the fine choice of voice are the system's
  (AN22).
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

  Turn score, no score and turn start have no sound. The win voice follows the win sound, and is spoken even with
  sounds off (when the voice is on).
- AN10. Order and overlap:
  - a dart sound plays at once; dart sounds may overlap each other; a dart sound is never queued nor played late (a
    sound not loaded yet is skipped);
  - on the last dart of a turn: its dart sound, then its dart call (AN25, when on), then either the event sound (bust,
    180, big fish or game shot, then the win voice) or the turn score. Each waits for the end of the previous one. A
    voice and an event sound never overlap;
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
  - under the "Voice" switch, a status line: a usable voice is available, or there is no offline voice for the app
    language (then with an "Install a voice" button, AN17), or the check is in progress;
  - then a "Call each dart" switch (AN25), greyed (disabled) while the voice is off, with the line "Needs the voice" /
    « Nécessite la voix » under it while greyed;
  - a "Sounds" switch;
  - at the bottom, a non-clickable mention: "Sound effects generated with elevenlabs.io" / « Effets sonores générés
    avec elevenlabs.io ».
- AN14. The Voice and Sounds switches are independent: sounds play without the voice and the reverse. Both are on by
  default. "Call each dart" is off by default; it keeps its value while greyed, and only speaks while the voice is
  on.
  Switching the voice on while a usable voice is available says "Announcements on". Switching it on while no usable
  voice is available (or while the check is in progress) leaves it off; the status line says why. Switching the voice
  off stops nothing else. With the voice off, nothing is spoken; with sounds off, no sound is played.
- AN15. The three switches and the AN16 flag are stored on the device, apart from the saved game file, whose format does not
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
  end screen counts as part of the game here: "Finish game" does not cut the win sound and voice that started with
  the checkout dialog (AN5), and a rematch started from it announces its first player (AN4).
- AN22. Player names are spoken at their turn start and at the win. Rate, pitch and voice details come from the
  system settings.

## Undo

- AN23. An undo cue first stops at once every sound and voice in progress or pending, then plays `sfx_dart_cancel`
  (if sounds are on). The voice says nothing on an undo. Undo in the checkout dialog is an undo: it stops the win
  sound and voice (E3).

## Credits

- AN24. The seven sounds were generated with ElevenLabs (free plan, which requires the mention "elevenlabs.io").
  `docs/sounds/CREDITS.md` lists the seven files, the tool, the plan and the generation date (October 2026); the
  mention is also in the app (AN13). The files are used as delivered on the `assets/sound-effects` branch, whose
  last commit evened out their levels (E5). The app applies no gain. `docs/sounds/CREDITS.md` does not describe the
  normalization: the owner withdrew that request (PR #33 comment, 2026-10-19), the gains being no longer known.

## Dart calls and phrase variants

- AN25. With "Call each dart" and the voice on, each hit is called after its impact sound:

  | Dart | English | Français |
  |---|---|---|
  | Single n | "{n}" | « {n} » |
  | Double n | "Double {n}" | « Double {n} » |
  | Triple n | "Triple {n}" | « Triple {n} » |
  | Outer bull (25) | "25" | « 25 » |
  | Bull (50) | "Bull" | « Bull » |
  | Miss | nothing (its sound is enough) | rien |

  A call never comes late: a new dart cancels the call of the previous dart, whether it is still waiting or being
  spoken. A call waits only for its impact sound and for an announcement already being spoken.
- AN26. A phrase with variants (AN8) is drawn at random, never the same as the last one drawn from the same line in
  the same game session. The draw goes through an injectable interface; tests use a fixed draw.
