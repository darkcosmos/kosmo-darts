# Capture rules

Status: v1 (issue #41, Kosmo Capture data capture app, with the owner answers recorded in
`docs/plans/41-capture-app.md`); v2: zoom (CP14 to CP17), `docs/plans/41-capture-app-zoom.md`.
This document is the single source of truth for the `:capture` tests: tests cite these rules, never the issue text.
Rule ids (CP1, CP2, ...) are stable. They use the prefix CP so that they never collide with the other rule documents
in this folder. The truth file format is described in `docs/camera/capture-format.md`.

- CP1 Start creates session folder `YYYYMMDD-HHMMSS` (UTC wall clock of the start, from the clock only, never from
  input), volley 1 and video `volley-001.mp4`. If a folder of this installation already has that name (two starts in
  the same second), the suffix `-2`, `-3`... is appended.
- CP2 Outside a session the keypad, Undo and Next volley are disabled. The quality setting and Delete all are enabled
  only outside a session. Start is disabled until the camera is bound and while the previous session is saving.
- CP3 A dart entry is accepted while the current volley has fewer than `DARTS_PER_VOLLEY` (3, named constant in
  `capture.session`) non-voided darts. It is appended with its entry timestamps and triggers one photo `dart-NNN.jpg`,
  NNN = session-wide entry number (voided entries included), zero-padded to 3 digits, wider beyond 999. Volley files
  `volley-NNN.mp4` follow the same padding. When the volley is full the keypad is disabled and the hint "Volley full:
  press Next volley" is shown (no refusal on tap).
- CP4 Undo voids the last non-voided dart of the current volley only: the dart stays in the file with `voided: true`,
  its void timestamps and its photo. Disabled when the current volley has no non-voided dart.
- CP5 Next volley ends the current volley (end timestamps, video position), closes its video and starts the next volley
  and its video. Allowed with no dart; a volley with no non-voided dart is marked `empty: true`.
- CP6 Stop asks for confirmation (Cancel keeps the session untouched). Confirmed, it ends the current volley as CP5
  without starting a new one (an empty last volley keeps its video, marked empty), then ends the session
  (`endReason: STOP`). The screen shows "Saving..." until the last video is finalized and the truth file written.
- CP7 Counters: session = non-voided darts of the running (or last) session; total = non-voided darts of every session
  of this installation. The total is a private counter (not derived from folders), persisted after every dart entry
  and every undo (so it is right after Stop, after an interruption and after a crash), reset by Delete all, restarts
  after reinstall; a missing or corrupt counter file reads as 0 without crashing.
- CP8 Delete all asks for confirmation, deletes every session folder created by this installation and resets the total.
  The screen states that sessions of an earlier installation are not deleted.
  Delete all queries only own MediaStore entries whose `RELATIVE_PATH` starts with `Download/KosmoCapture/`, pending
  entries included, and deletes by the returned URIs only.
- CP9 The truth file is rewritten after every event. Really leaving the screen (activity stopped and not changing
  configurations) or a camera/recording error ends the session as CP6 without confirmation (`endReason:
  INTERRUPTED`), and the open recording is stopped. A configuration change (rotation, dark mode, locale, font scale)
  never interrupts the session.
- CP10 Timestamps: `utc` ISO 8601 UTC with milliseconds (`2026-10-31T14:03:22.123Z`, format H1); `monotonicNanos` =
  `SystemClock.elapsedRealtimeNanos()`; `videoPositionMs` = event monotonic minus the volley video start monotonic
  (recorded at the CameraX `Start` event), floored at 0; `null` for an event recorded before that `Start` event.
- CP11 Quality setting 4K or 1080p, default 4K, kept across launches; the actual video and photo resolutions are stored.
- CP12 The screen stays on during a session only.
- CP13 Photo failure: if the photo of a dart cannot be taken or saved, the dart is kept with `photo: null`, the
  session continues and a non-blocking message "Photo failed for this dart" is shown for a few seconds.
- CP14 Zoom control: outside a session, a slider sets the zoom ratio of the camera (preview, videos and photos alike)
  between the minimum and maximum ratio the camera reports. The slider position `p` (0 to 1) maps to the ratio on a
  logarithmic scale, `min x (max / min)^p`; the ratio is rounded to the nearest 0.1 (half up, applied to the shortest
  decimal form of the value, so 1.05 gives 1.1), then kept inside the camera range. Next to the slider, a "−" and a
  "+" button lower or raise the current ratio by 0.1 per press: the current ratio is rounded to the nearest 0.1 as
  above, 0.1 is subtracted or added, and the result is kept inside the camera range. Holding a button does not
  repeat. The preview follows while the slider moves and after each press. The current ratio is shown next to the
  slider with one decimal and the sign `×` (`2.4×`; `2,4×` in French).
- CP15 Zoom lock: the slider and both buttons are enabled only when no session runs or saves (like the quality
  setting, CP2), the camera permission is granted, the camera is bound and its range is at least 0.1 wide; in
  addition, "−" is disabled when the ratio is at the camera minimum and "+" when it is at the camera maximum.
  Otherwise they are disabled and the ratio stays shown; when the bound camera's range is narrower than 0.1, "Zoom
  not available" replaces the ratio. A zoom change or press received while disabled is ignored.
- CP16 Zoom memory: the ratio chosen is stored in the private settings when the slider is released (not on every
  move) or after each button press that changed it, and read at launch; default 1.0; a missing, unreadable, non-finite, zero or negative stored value reads as
  1.0. After every camera binding (launch, quality change, return to the app, rotation) the stored ratio, kept inside
  the camera range, is applied to the camera, also during a session; keeping it inside the range never changes the
  stored value. If the setting cannot be written, the ratio still applies until the app is closed, without a message.
- CP17 Zoom record: the truth file (version 2) records once per session, at Start, the ratio the app last asked the
  camera for (`zoom.requestedRatio`, null when it asked none) and the ratio the camera reported at Start
  (`zoom.ratio`, null when it reported none).
