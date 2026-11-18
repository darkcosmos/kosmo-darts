# ADR 0019: The camera preview shows exactly the analysed or recorded field (Kosmo Capture and game app)

- Status: Accepted as a requirement (Kosmo, 2026-11-18, D51); no implementation yet. Date: 2026-11-18.

## Context

During the K7 control of phase 0b-1, Kosmo found that on the video frames the target is cut at the top and the
bottom. Same sensor: the photo is 4:3 (4080 x 3072), the video is 16:9 and cropped at the top and the bottom,
possibly with an extra crop for stabilization. The preview of Kosmo Capture followed the photo framing: CameraX use
cases had no common viewport. Several calibration points are outside the video frame (marked hidden), and a photo
cannot be compared with a video frame directly (different framing and resolution, D50).

## Decision

Every future capture (Kosmo Capture) and the game app of phases 1 to 3:

1. show in the preview exactly the field that is analysed or recorded: one common `ViewPort` shared by the preview,
   photo, video and analysis use cases through a `UseCaseGroup`, or a single image source for all of them;
2. disable video stabilization, or account for its crop in the recorded framing;
3. write the framing actually recorded (crop rectangle in sensor coordinates, output size, stabilization state) into
   the truth file of each capture.

## Alternatives rejected

- Keeping separate framings and mapping them afterwards: the mapping mixes cropping with real movement, and points
  outside a frame are lost.
- Recording the framing without showing it: the person framing the board would still cut it without knowing.

## Consequences

- No code in phase 0b-1: the requirement and its justification only. The capture format gains the framing fields
  when Kosmo Capture is next changed.
- In phase 0b-1 the movement at the tap (H2) is measured inside the video stream, at constant framing (D50).
