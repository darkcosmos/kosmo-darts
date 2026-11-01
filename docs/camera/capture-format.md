# Kosmo Capture truth file format, version 2

Status: v2 (issue #41, camera phase 0a; version 2 adds the zoom record, CP17). Version 1 is described in the section
"Version 1" at the end. Written by the Kosmo Capture app (`:capture`), read by the phase 0b tools.
Rules cited as CPn are in `docs/rules/capture.md`; the dart values follow rules D1 and D2 of `docs/rules/x01.md`.
Reference implementation: `capture/src/main/kotlin/io/github/darkcosmos/kosmodarts/capture/truth/` (`TruthJson`).

## Files of a session

Each session is one folder in the phone's shared storage, copied to the PC over USB:

```
Download/KosmoCapture/<session>/
  truth.json          this file
  volley-001.mp4      one video per volley, no sound
  volley-002.mp4
  dart-001.jpg        one photo per dart entry, voided entries included
  dart-002.jpg
  ...
```

- `<session>` is `YYYYMMDD-HHMMSS`, the UTC wall clock of Start; a second session started in the same second gets
  `-2`, then `-3`... (CP1).
- `NNN` in `volley-NNN.mp4` is the volley index and in `dart-NNN.jpg` the dart entry number, zero-padded to 3 digits
  and wider beyond 999 (`dart-1000.jpg`) (CP3).
- A photo that failed is missing from the folder and its dart has `photo: null` (CP13).

## Encoding

- One JSON object (RFC 8259), UTF-8 without byte order mark, two-space indentation, keys always in the order of the
  tables below, no trailing newline. Non-ASCII text is written as is; `"`, `\` and control characters are escaped.
- The app rewrites the whole file after every event (Start, dart, Undo, Next volley, Stop, interruption) (CP9), so
  the file on disk is always a complete, valid document of the session so far.
- Every field is always present. A value that does not exist is written as `null`, never left out.
- Readers must reject a file larger than 10 MiB (10 485 760 bytes), nesting deeper than 32 levels, duplicate keys
  and unknown fields. The reference reader also rejects any value that breaks a rule below.

## Version policy

- `format` is always `"kosmo-capture-truth"` and `version` an integer. A reader checks both first and rejects any
  other format or any version it does not know (lower or higher). This reader knows version 2 only: it rejects a
  version 1 file with "unsupported version 1" and does not migrate it.
- Any change to the file (a field added, removed or renamed, a new allowed value, a change of unit or meaning)
  increases `version` by 1 and is described in a new section of this document. Version 2 has no optional field, so
  a version 2 reader also rejects unknown fields.
- The app writes one version only (version 2). Files of an older version stay readable only through a reader that
  knows it; see "Version 1".

## Clocks and alignment

Every event has a stamp with two clocks (CP10):

- `utc`: wall clock, ISO 8601 in UTC with exactly three fraction digits and `Z`, for example
  `2026-10-31T14:03:22.123Z`. Truncated to the millisecond. For people and for sorting sessions; it can jump if the
  phone's clock is changed, so never compute durations with it.
- `monotonicNanos`: `SystemClock.elapsedRealtimeNanos()`, nanoseconds since the phone booted, deep sleep included.
  The `clock` field names it. Use it for every duration and ordering inside a session. It restarts at each reboot,
  so it only compares stamps of the same session.

Events inside a volley also have `videoPositionMs`: milliseconds from the start of the volley video, computed as the
event's `monotonicNanos` minus the `monotonicNanos` of the video's first recorded frame (the CameraX `Start` event),
floored at 0. It is `null` for an event that happened before the video started (the first moments of a volley): that
event has no frame in the video.

To align a photo with its video: open `volleys[i].video` and seek to `darts[j].photo.videoPositionMs` (or
`darts[j].entry.videoPositionMs` for the moment the dart was typed). `photo.timestampSource` says whether the
camera's own sensor timestamps (video frames, photo metadata) use the same clock as `monotonicNanos` (`REALTIME`) or
an unknown one (`UNKNOWN`).

## Fields

Root object:

| Field | Type | Meaning |
|---|---|---|
| `format` | string | Always `"kosmo-capture-truth"`. |
| `version` | integer | `2` for this document. |
| `session` | string | Session folder name (CP1). |
| `app` | object | `id`: application id (`io.github.darkcosmos.kosmodarts.capture`); `version`: version name. |
| `device` | object | `manufacturer`, `model` (strings) and `sdk` (integer, Android API level) of the phone. |
| `clock` | string | Always `"elapsedRealtimeNanos"`: the clock of every `monotonicNanos`. |
| `startedAt` | start stamp | When Start was pressed. |
| `endedAt` | start stamp or null | When the session ended; null while it runs. |
| `endReason` | string or null | `"STOP"` (Stop confirmed, CP6) or `"INTERRUPTED"` (screen really left, camera or recording error, CP9); null exactly when `endedAt` is null. |
| `video` | object | `requestedQuality`: `"UHD"` (4K) or `"FHD"` (1080p), the quality setting (CP11); `width`, `height`: actual video size in pixels. |
| `photo` | object | `width`, `height`: actual photo size in pixels (CP11); `timestampSource`: `"REALTIME"` or `"UNKNOWN"`. |
| `zoom` | object | Zoom of the session, recorded once at Start (CP17). `requestedRatio`: the zoom ratio the app last asked the camera for, null when it asked none; `ratio`: the zoom ratio the camera reported at Start, null when it reported none. Each is a float (written like `gravity`) or null, finite and greater than 0 when not null; the two may differ. No upper bound is enforced: the values are whatever the app asked or the camera reported, so phase 0b tools must not assume a plausible range (for example, they must handle a ratio above 1000 themselves). |
| `volleys` | array of volley | At least one; indexes 1, 2, 3... in order. |

Volley:

| Field | Type | Meaning |
|---|---|---|
| `index` | integer | 1 for the first volley, then +1. |
| `video` | string | Video file name, `volley-NNN.mp4`. |
| `start` | start stamp | When the volley started (Start or Next volley). |
| `end` | event stamp or null | When it ended (Next volley, Stop or interruption, CP5, CP6, CP9); null while it runs. Every volley but the last has an end; the last has one too once the session ended. |
| `empty` | boolean | True when the volley has no non-voided dart (CP5). Derived from `darts`; a reader may recompute it. |
| `darts` | array of dart | Every entry of the volley in order, voided ones included; at most 3 non-voided (CP3). |

Dart:

| Field | Type | Meaning |
|---|---|---|
| `number` | integer | Session-wide entry number: 1, 2, 3... in order across the volleys, voided entries included (CP3). |
| `segment` | integer | `0` miss, `1` to `20` a number, `25` outer bull, `50` bull. |
| `multiplier` | string | `"SINGLE"`, `"DOUBLE"` or `"TRIPLE"`; always `"SINGLE"` for 0, 25 and 50 (rule D2). |
| `voided` | boolean | True when Undo voided the entry (CP4); exactly when `voidedAt` is not null. |
| `entry` | event stamp | When the dart was typed. |
| `voidedAt` | event stamp or null | When Undo voided it. |
| `photo` | object or null | `file` (`dart-NNN.jpg`) plus the event stamp fields of the moment the photo was taken; null when the photo failed (CP13). |
| `gravity` | object or null | `x`, `y`, `z`: last reading of the gravity sensor at the entry, in m/s² along the phone axes (floats); null when no reading was available. |

Stamps:

| Kind | Fields |
|---|---|
| start stamp (`startedAt`, `endedAt`, volley `start`) | `utc` (string), `monotonicNanos` (integer, at least 0) |
| event stamp (volley `end`, `entry`, `voidedAt`, `photo`) | `utc`, `monotonicNanos`, `videoPositionMs` (integer at least 0, or null) |

## Session that did not end normally

- Interruption (CP9): leaving the screen for real, or a camera or recording error, ends the session like Stop:
  `endReason` is `"INTERRUPTED"`, `endedAt` and the last volley's `end` are set.
- Killed session: if the app or the phone dies before the session ends, the last file written stays. It reads back
  with `endedAt` and `endReason` null and the last volley's `end` null. Its last video may be unreadable or cut;
  entries after the last write are lost.

## Example

A stopped session with two volleys: a triple 20 with its photo and gravity, a 5 voided by Undo whose photo failed,
then an empty volley. This is the exact text the reference writer produces (tested by `TruthJsonGoldenTest`); the
device strings show the escaping.

```json
{
  "format": "kosmo-capture-truth",
  "version": 2,
  "session": "20261031-140322",
  "app": {
    "id": "io.github.darkcosmos.kosmodarts.capture",
    "version": "0.1.0"
  },
  "device": {
    "manufacturer": "Gööglé",
    "model": "Pixel \"7\" \\ a\u0001",
    "sdk": 37
  },
  "clock": "elapsedRealtimeNanos",
  "startedAt": {
    "utc": "2026-10-31T14:03:22.123Z",
    "monotonicNanos": 5000000000
  },
  "endedAt": {
    "utc": "2026-10-31T14:03:28.223Z",
    "monotonicNanos": 11100000000
  },
  "endReason": "STOP",
  "video": {
    "requestedQuality": "UHD",
    "width": 3840,
    "height": 2160
  },
  "photo": {
    "width": 4080,
    "height": 3072,
    "timestampSource": "REALTIME"
  },
  "zoom": {
    "requestedRatio": 2.4,
    "ratio": 2.4
  },
  "volleys": [
    {
      "index": 1,
      "video": "volley-001.mp4",
      "start": {
        "utc": "2026-10-31T14:03:22.123Z",
        "monotonicNanos": 5000000000
      },
      "end": {
        "utc": "2026-10-31T14:03:26.123Z",
        "monotonicNanos": 9000000000,
        "videoPositionMs": 3850
      },
      "empty": false,
      "darts": [
        {
          "number": 1,
          "segment": 20,
          "multiplier": "TRIPLE",
          "voided": false,
          "entry": {
            "utc": "2026-10-31T14:03:23.123Z",
            "monotonicNanos": 6000000000,
            "videoPositionMs": 850
          },
          "voidedAt": null,
          "photo": {
            "file": "dart-001.jpg",
            "utc": "2026-10-31T14:03:23.165Z",
            "monotonicNanos": 6042000000,
            "videoPositionMs": 892
          },
          "gravity": {
            "x": 0.1,
            "y": -9.8,
            "z": 0.25
          }
        },
        {
          "number": 2,
          "segment": 5,
          "multiplier": "SINGLE",
          "voided": true,
          "entry": {
            "utc": "2026-10-31T14:03:24.123Z",
            "monotonicNanos": 7000000000,
            "videoPositionMs": 1850
          },
          "voidedAt": {
            "utc": "2026-10-31T14:03:25.123Z",
            "monotonicNanos": 8000000000,
            "videoPositionMs": 2850
          },
          "photo": null,
          "gravity": null
        }
      ]
    },
    {
      "index": 2,
      "video": "volley-002.mp4",
      "start": {
        "utc": "2026-10-31T14:03:26.123Z",
        "monotonicNanos": 9000000000
      },
      "end": {
        "utc": "2026-10-31T14:03:28.123Z",
        "monotonicNanos": 11000000000,
        "videoPositionMs": 1900
      },
      "empty": true,
      "darts": []
    }
  ]
}
```

## Version 1

Version 1 is identical to version 2 without the root `zoom` object: `"version": 1`, and `photo` is followed directly
by `volleys`. Every other field, rule and limit is the same.

Every version 1 file was recorded by an app without zoom control, so at the CameraX default zoom ratio 1.0. A phase
0b tool that reads version 1 files may treat them as `zoom` 1.0 (`requestedRatio` null, `ratio` 1.0). The reference
reader does not: it knows version 2 only and rejects version 1 with "unsupported version 1".
