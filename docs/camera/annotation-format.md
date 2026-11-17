# Kosmo camera annotation file format, versions 1, 2 and 3

Status: v3 (issue #42, camera phase 0b-1; v1 was written by the part A tool, v2 by the batch 1 tool, both are still
read). Written by the annotation tool (`tools/camera/`, rule CV16) from Kosmo's clicks, read by every later phase 0b
tool (geometry, drift, diagnosis, dataset, metrics). Rules cited as CVn are in `docs/rules/camera.md`; the truth file
it refers to is described in `docs/camera/capture-format.md`.
Reference implementation: `tools/camera/kosmo_camera/annotations.py` (`encode`, `decode`, `read`, `write_atomic`,
`validate_against`, `migrate_v1`, `migrate_v2`, `segment_for`, `segments_sha256`, `control_for`, `controls_sha256`).

## File

One file per capture session: `~/kosmo-data/annotations/<session>.json`, outside the captures folder, which stays
read-only. The annotation tool rewrites the whole file after every action, through a temporary file in the same
folder and an atomic rename (CV16), so the file on disk is always complete. A partial file is valid: `calibrations`
and `controls` may still be empty and darts may be missing while the annotation is in progress. A version 1 or 2 file
is never rewritten by opening or reading it: the tool writes it back as version 3 on the first saved change (CV16,
CV20, CV25).

## Encoding

- One JSON object (RFC 8259), UTF-8 without byte order mark, two-space indentation, keys always in the order of the
  tables below, no trailing newline.
- Every field is always present, with one exception: `positionMs` of a control is present exactly when its `source`
  is `"video"` (version 3). A value that does not exist is written as `null`, never left out.
- Readers must reject a file larger than 1 MiB (1 048 576 bytes), nesting deeper than 8 levels, duplicate keys,
  unknown or missing fields, `NaN` and infinities. The reference reader also rejects any value that breaks a rule
  below.

## Version policy

Same policy as the truth file. `format` is always `"kosmo-camera-annotations"` and `version` an integer. A reader
checks both first and rejects any other format or any version it does not know (lower or higher). Any change to the
file (a field added, removed or renamed, a new allowed value, a change of unit or meaning) increases `version` by 1
and is described in a new section of this document. Versions 1 and 2 have no optional field and version 3 only the
`positionMs` of a video control, so a reader also rejects unknown fields.

Known versions: 1 (part A, section "Version 1 (reference)"), 2 (batch 1, section "Version 2 (reference)") and 3
(current). The reference reader accepts all three and always returns the version 3 model, migrating a version 1 or 2
file in memory (sections "Migration from version 2" and "Migration from version 1"). Writers write version 3 only.
Any other version, 0, 4 or higher, is rejected.

## Coordinates

Image pixels, as floats: origin at the top left corner of the image, `x` to the right, `y` down, in the stored pixel
order of the JPEG (or of the video frame, for a video control). The EXIF orientation tag is ignored everywhere
(annotation page, server and image loaders, decision D8). A coordinate is valid from 0 to the image width (`x`) or
height (`y`), both included. Integers are accepted on reading and read as floats; the writer always writes floats.

## Version 3

Root object, keys in this order:

| Key | Type | Meaning |
|---|---|---|
| `format` | string | Always `"kosmo-camera-annotations"`. |
| `version` | integer | Always `3`. |
| `session`, `truthSha256` | string | As in version 2. |
| `calibrations` | array | Calibration segments (CV20) as in version 2, each with `snap` added after `points`. At most 64 items. |
| `controls` | array | Control clicks (CV25), empty until Kosmo clicks a control. At most 1024 items. |
| `darts` | array | As in version 2. |

`calibrations` item: the version 2 segment keys (`fromDart`, `photo`, `width`, `height`, `points`, same rules), then:

| Key | Type | Meaning |
|---|---|---|
| `snap` | array | Exactly 4 statuses aligned with `points`: `"snapped"`, `"unsnapped"` (CV31) or `"manual"` (clicked before snapping existed, every point of a segment migrated from version 2). Never `null`: a segment point is never hidden. |

`controls` item (one control: the 4 calibration points clicked again on one image, independent of the segments):

| Key | Type | Meaning |
|---|---|---|
| `dart` | integer | Dart number in the truth file (at least 1); a judged dart (CV5). |
| `source` | string | `"photo"` (the dart photo) or `"video"` (a frame of the volley video). |
| `positionMs` | integer | Present only when `source` is `"video"`: position of the frame in the volley video, in milliseconds (at least 0). Absent on a photo control. |
| `width`, `height` | integer | Size in pixels of that image, as stored (at least 1). |
| `points` | array | Exactly 4 entries in CV2 order (`cal1` to `cal4`), each a point `{ "x", "y" }` within `width` and `height`, or `null` when Kosmo marked that point hidden. All 4 may be `null`. |
| `snap` | array | Exactly 4 entries aligned with `points`: `"snapped"` or `"unsnapped"` (CV31) for a point, `null` exactly for a `null` point. `"manual"` is not allowed on a control. |

Control rules (CV25): `controls` is sorted by `dart`, then the photo control before the video control of the same
dart; at most one control per (`dart`, `source`). Controls never change the segments: `segment_for` and
`segments_sha256` ignore them. `control_for(annotations, dart, source)` returns the control of a dart for a source,
or none. `controls_sha256` is the SHA-256 of the `controls` array encoded with sorted keys and no spaces (`snap`
included), so a diagnosis can detect stale controls. `segments_sha256` hashes the segments without their `snap`, so
the hash of a segment is the same before and after the migration from version 2.

## Migration from version 2

The reader migrates a version 2 file in memory, without loss; `encode` then writes it as version 3. Reading never
rewrites the file: the annotation tool writes version 3 on the first saved change only (CV16, CV25). A version 1
file goes through the migration from version 1, then this one.

| Version 2 | Version 3 |
|---|---|
| `"version": 2` | `"version": 3` |
| `session`, `truthSha256`, `darts` | Copied unchanged. |
| `calibrations` items | Every value copied, `"snap": ["manual", "manual", "manual", "manual"]` added after `points`. |
| (absent) | `"controls": []` after `calibrations`. |

Proof (`tools/camera/tests/test_annotations.py`): the version 2 and version 1 examples below migrate to the version 3
example without its controls and its second segment, byte for byte; a version 2 file written by the batch 1 tool
(`tests/data/annotations-v2-tool-written.json`) and 50 seeded random version 2 files are decoded and encoded as
version 3, and dropping the empty `controls` and the `"manual"` statuses gives back the original version 2 text. A
version 2 body holding `controls` or `snap` is rejected (unknown field).

## Version 2 (reference)

Written by the batch 1 annotation tool; read only since version 3, then migrated (section "Migration from version
2").

Root object:

| Key | Type | Meaning |
|---|---|---|
| `format` | string | Always `"kosmo-camera-annotations"`. |
| `version` | integer | Always `2`. |
| `session` | string | Session name of the truth file: `YYYYMMDD-HHMMSS`, optionally followed by `-2`, `-3`... |
| `truthSha256` | string | SHA-256 of the bytes of the session's `truth.json` when the file was written, 64 lowercase hexadecimal digits. |
| `calibrations` | array | Calibration segments (CV20), empty until Kosmo has clicked the 4 points of the first one. At most 64 items. |
| `darts` | array | One entry per annotated dart, dart numbers strictly ascending. |

`calibrations` item (a segment):

| Key | Type | Meaning |
|---|---|---|
| `fromDart` | integer | First dart number the segment applies to. The first item is exactly `1`; then strictly ascending. |
| `photo` | string | File name of the session photo the points were clicked on: `dart-NNN.jpg` (3 digits, wider beyond 999). |
| `width`, `height` | integer | Size in pixels of that photo, as stored (at least 1). |
| `points` | array | Exactly 4 points `{ "x", "y" }` in CV2 order: `cal1` to `cal4`, outer edge of the double ring on wires 5\|20, 13\|6, 17\|3, 8\|11. Each point lies within `width` and `height`. |

Segment semantics (CV20): a segment applies from its `fromDart` until the dart before the `fromDart` of the next
segment; the last one applies to the end of the session. The segment of a dart is the last one whose `fromDart` is at
most the dart number (`segment_for`). The first segment may be clicked on any photo of the session (the tool uses the
photo of the first judged dart); every later segment starts at a judged dart (CV5) and is clicked on that dart's
photo. A new segment is added when the tripod moved during the session (drift measure, CV18). `segments_sha256` is the
SHA-256 of the `calibrations` array encoded with sorted keys and no spaces; the drift measure records it to detect a
stale measure (in version 3 it is computed without `snap`, so it keeps the same value).

`darts` entry:

| Key | Type | Meaning |
|---|---|---|
| `number` | integer | Dart entry number in the truth file (at least 1). At most one entry per number. |
| `status` | string | `VISIBLE` (tip seen and clicked), `HIDDEN` (dart on the board, tip not visible), `OFF_BOARD` (dart not on the board). |
| `tip` | object or null | `{ "x", "y" }` of the tip, set exactly when `status` is `VISIBLE`, `null` otherwise. |

## Version 1 (reference)

Written by the part A annotation tool; read only. Same document as version 2 except:

- `version` is always `1`;
- instead of `calibrations`, the root holds `calibration`: one object or `null` (until Kosmo has clicked the 4
  points), with the keys `photo`, `width`, `height` and `points` and the same rules as a version 2 segment, without
  `fromDart`.

A version 1 file holding `calibrations`, or a version 2 file holding `calibration`, is rejected (unknown field).

## Migration from version 1

The reader migrates a version 1 file in memory, without loss, to version 2, then to version 3 (section "Migration
from version 2"); `encode` then writes it as version 3. Reading never rewrites the file: the annotation tool writes
version 3 on the first saved change only (CV16, CV20, CV25).

| Version 1 | Version 2 |
|---|---|
| `"version": 1` | `"version": 2` |
| `session`, `truthSha256`, `darts` | Copied unchanged (the stale `truthSha256` warning keeps working). |
| `"calibration": null` | `"calibrations": []` |
| `"calibration": { photo, width, height, points }` | `"calibrations": [ { "fromDart": 1, photo, width, height, points } ]`, every value copied. |

Proof (`tools/camera/tests/test_annotations.py`): the version 1 example below migrates to the version 2 example
model, and encodes to the version 3 example without its controls and its second segment, byte for byte; a version 1
file written by the part A tool (`tests/data/annotations-v1-tool-written.json`) and 50 seeded random version 1 files
go through migrate, encode and decode, and the inverse mapping gives back the original version 1 content.

## Consistency with the truth file

`validate_against` (reference implementation) checks an annotation against the session's `truth.json`:

- Errors: `session` differs from the truth file; a dart number that does not exist in the truth file; a dart that is
  not judged (CV5: voided, or `photo: null`); a segment photo that is not a photo of the session; a segment after the
  first whose `fromDart` is not a judged dart or whose `photo` is not the photo of that dart (CV20); a tip outside
  the photo size declared by the truth file (`photo.width`, `photo.height`); a control on a dart that does not exist
  or is not judged (CV5); a video control whose volley has no video or whose dart `entry.videoPositionMs` is `null`
  (CV25). The annotation tool also checks clicks against the real JPEG size.
- Warning only: `truthSha256` differs from the SHA-256 of the current `truth.json`. Kosmo may copy the same session
  from the phone again; the annotation stays readable and editable, and the tool shows the warning.

## Example

A complete version 3 annotation of a session with three darts: a visible tip, a hidden tip and a dart off the
board; two calibration segments, the first migrated from version 2 (`"manual"`), the second from dart 3 snapped; and
three controls: the photo of dart 1 with its fourth point hidden, a video frame of dart 1 at 750 ms, and the photo of
dart 3 with one point the snapping could not place (`"unsnapped"`). This is the exact text the reference writer
produces (tested by `test_annotations.py`).

```json
{
  "format": "kosmo-camera-annotations",
  "version": 3,
  "session": "20261031-140322",
  "truthSha256": "24c0d987d33c4c93aba546caf5d77fa49ee86b3eb2dc62c4763734f3d5083fcf",
  "calibrations": [
    {
      "fromDart": 1,
      "photo": "dart-001.jpg",
      "width": 4080,
      "height": 3072,
      "points": [
        {
          "x": 2040.5,
          "y": 610.25
        },
        {
          "x": 3301.0,
          "y": 1290.0
        },
        {
          "x": 2702.75,
          "y": 2650.0
        },
        {
          "x": 1102.0,
          "y": 2300.5
        }
      ],
      "snap": [
        "manual",
        "manual",
        "manual",
        "manual"
      ]
    },
    {
      "fromDart": 3,
      "photo": "dart-003.jpg",
      "width": 4080,
      "height": 3072,
      "points": [
        {
          "x": 2044.25,
          "y": 612.5
        },
        {
          "x": 3303.5,
          "y": 1291.75
        },
        {
          "x": 2701.0,
          "y": 2652.25
        },
        {
          "x": 1100.75,
          "y": 2301.0
        }
      ],
      "snap": [
        "snapped",
        "snapped",
        "snapped",
        "snapped"
      ]
    }
  ],
  "controls": [
    {
      "dart": 1,
      "source": "photo",
      "width": 4080,
      "height": 3072,
      "points": [
        {
          "x": 2041.0,
          "y": 611.5
        },
        {
          "x": 3301.25,
          "y": 1290.5
        },
        {
          "x": 2702.5,
          "y": 2650.75
        },
        null
      ],
      "snap": [
        "snapped",
        "snapped",
        "snapped",
        null
      ]
    },
    {
      "dart": 1,
      "source": "video",
      "positionMs": 750,
      "width": 3840,
      "height": 2160,
      "points": [
        {
          "x": 1921.5,
          "y": 430.25
        },
        {
          "x": 3106.0,
          "y": 907.5
        },
        {
          "x": 2543.75,
          "y": 1864.0
        },
        {
          "x": 1037.25,
          "y": 1617.5
        }
      ],
      "snap": [
        "snapped",
        "snapped",
        "snapped",
        "snapped"
      ]
    },
    {
      "dart": 3,
      "source": "photo",
      "width": 4080,
      "height": 3072,
      "points": [
        {
          "x": 2044.0,
          "y": 612.75
        },
        {
          "x": 3303.25,
          "y": 1292.0
        },
        {
          "x": 2701.5,
          "y": 2652.0
        },
        {
          "x": 1100.5,
          "y": 2301.25
        }
      ],
      "snap": [
        "snapped",
        "unsnapped",
        "snapped",
        "snapped"
      ]
    }
  ],
  "darts": [
    {
      "number": 1,
      "status": "VISIBLE",
      "tip": {
        "x": 2010.5,
        "y": 640.0
      }
    },
    {
      "number": 2,
      "status": "HIDDEN",
      "tip": null
    },
    {
      "number": 3,
      "status": "OFF_BOARD",
      "tip": null
    }
  ]
}
```

### Version 2 example (reference)

The same annotation with one segment and no control, as the batch 1 tool wrote it in version 2. The reader migrates
it to the version 3 example above without its controls and its second segment, byte for byte once encoded.

```json
{
  "format": "kosmo-camera-annotations",
  "version": 2,
  "session": "20261031-140322",
  "truthSha256": "24c0d987d33c4c93aba546caf5d77fa49ee86b3eb2dc62c4763734f3d5083fcf",
  "calibrations": [
    {
      "fromDart": 1,
      "photo": "dart-001.jpg",
      "width": 4080,
      "height": 3072,
      "points": [
        {
          "x": 2040.5,
          "y": 610.25
        },
        {
          "x": 3301.0,
          "y": 1290.0
        },
        {
          "x": 2702.75,
          "y": 2650.0
        },
        {
          "x": 1102.0,
          "y": 2300.5
        }
      ]
    }
  ],
  "darts": [
    {
      "number": 1,
      "status": "VISIBLE",
      "tip": {
        "x": 2010.5,
        "y": 640.0
      }
    },
    {
      "number": 2,
      "status": "HIDDEN",
      "tip": null
    },
    {
      "number": 3,
      "status": "OFF_BOARD",
      "tip": null
    }
  ]
}
```

### Version 1 example (reference)

The same annotation as the part A tool wrote it in version 1. The reader migrates it to the version 2 example above,
then to version 3 like it.

```json
{
  "format": "kosmo-camera-annotations",
  "version": 1,
  "session": "20261031-140322",
  "truthSha256": "24c0d987d33c4c93aba546caf5d77fa49ee86b3eb2dc62c4763734f3d5083fcf",
  "calibration": {
    "photo": "dart-001.jpg",
    "width": 4080,
    "height": 3072,
    "points": [
      {
        "x": 2040.5,
        "y": 610.25
      },
      {
        "x": 3301.0,
        "y": 1290.0
      },
      {
        "x": 2702.75,
        "y": 2650.0
      },
      {
        "x": 1102.0,
        "y": 2300.5
      }
    ]
  },
  "darts": [
    {
      "number": 1,
      "status": "VISIBLE",
      "tip": {
        "x": 2010.5,
        "y": 640.0
      }
    },
    {
      "number": 2,
      "status": "HIDDEN",
      "tip": null
    },
    {
      "number": 3,
      "status": "OFF_BOARD",
      "tip": null
    }
  ]
}
```
