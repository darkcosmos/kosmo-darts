# Kosmo camera annotation file format, version 1

Status: v1 (issue #42, camera phase 0b-1). Written by the annotation tool (`tools/camera/`, rule CV16) from Kosmo's
clicks, read by every later phase 0b tool (geometry, dataset, metrics). Rules cited as CVn are in
`docs/rules/camera.md`; the truth file it refers to is described in `docs/camera/capture-format.md`.
Reference implementation: `tools/camera/kosmo_camera/annotations.py` (`encode`, `decode`, `read`, `write_atomic`,
`validate_against`).

## File

One file per capture session: `~/kosmo-data/annotations/<session>.json`, outside the captures folder, which stays
read-only. The annotation tool rewrites the whole file after every action, through a temporary file in the same
folder and an atomic rename (CV16), so the file on disk is always complete. A partial file is valid: `calibration`
may still be `null` and darts may be missing while the annotation is in progress.

## Encoding

- One JSON object (RFC 8259), UTF-8 without byte order mark, two-space indentation, keys always in the order of the
  tables below, no trailing newline.
- Every field is always present. A value that does not exist is written as `null`, never left out.
- Readers must reject a file larger than 1 MiB (1 048 576 bytes), nesting deeper than 8 levels, duplicate keys,
  unknown or missing fields, `NaN` and infinities. The reference reader also rejects any value that breaks a rule
  below.

## Version policy

Same policy as the truth file. `format` is always `"kosmo-camera-annotations"` and `version` an integer. A reader
checks both first and rejects any other format or any version it does not know (lower or higher). Any change to the
file (a field added, removed or renamed, a new allowed value, a change of unit or meaning) increases `version` by 1
and is described in a new section of this document. Version 1 has no optional field, so a version 1 reader also
rejects unknown fields.

## Coordinates

Photo pixels, as floats: origin at the top left corner of the image, `x` to the right, `y` down, in the stored pixel
order of the JPEG. The EXIF orientation tag is ignored everywhere (annotation page, server and image loaders,
decision D8). A coordinate is valid from 0 to the photo width (`x`) or height (`y`), both included. Integers are
accepted on reading and read as floats; the writer always writes floats.

## Fields

Root object:

| Key | Type | Meaning |
|---|---|---|
| `format` | string | Always `"kosmo-camera-annotations"`. |
| `version` | integer | Always `1`. |
| `session` | string | Session name of the truth file: `YYYYMMDD-HHMMSS`, optionally followed by `-2`, `-3`... |
| `truthSha256` | string | SHA-256 of the bytes of the session's `truth.json` when the file was written, 64 lowercase hexadecimal digits. |
| `calibration` | object or null | The 4 calibration points, `null` until Kosmo has clicked them. |
| `darts` | array | One entry per annotated dart, dart numbers strictly ascending. |

`calibration` object:

| Key | Type | Meaning |
|---|---|---|
| `photo` | string | File name of the session photo the points were clicked on: `dart-NNN.jpg` (3 digits, wider beyond 999). |
| `width`, `height` | integer | Size in pixels of that photo, as stored (at least 1). |
| `points` | array | Exactly 4 points `{ "x", "y" }` in CV2 order: `cal1` to `cal4`, outer edge of the double ring on wires 5\|20, 13\|6, 17\|3, 8\|11. Each point lies within `width` and `height`. |

`darts` entry:

| Key | Type | Meaning |
|---|---|---|
| `number` | integer | Dart entry number in the truth file (at least 1). At most one entry per number. |
| `status` | string | `VISIBLE` (tip seen and clicked), `HIDDEN` (dart on the board, tip not visible), `OFF_BOARD` (dart not on the board). |
| `tip` | object or null | `{ "x", "y" }` of the tip, set exactly when `status` is `VISIBLE`, `null` otherwise. |

## Consistency with the truth file

`validate_against` (reference implementation) checks an annotation against the session's `truth.json`:

- Errors: `session` differs from the truth file; a dart number that does not exist in the truth file; a dart that is
  not judged (CV5: voided, or `photo: null`); a calibration photo that is not a photo of the session; a tip outside
  the photo size declared by the truth file (`photo.width`, `photo.height`). The annotation tool also checks clicks
  against the real JPEG size.
- Warning only: `truthSha256` differs from the SHA-256 of the current `truth.json`. Kosmo may copy the same session
  from the phone again; the annotation stays readable and editable, and the tool shows the warning.

## Example

A complete annotation of a session with three judged darts: a visible tip, a hidden tip and a dart off the board.
This is the exact text the reference writer produces (tested by `test_annotations.py`).

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
