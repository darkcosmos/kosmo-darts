package io.github.darkcosmos.kosmodarts.capture

/**
 * Robolectric qualifier strings of the supported Kosmo Capture test devices (landscape only), usable in
 * `@Config(qualifiers = ...)`. Same constants as `:app` (`:capture` does not depend on it).
 *
 * The sizes are literals copied from `RobolectricDeviceQualifiers.Pixel7` of Roborazzi 1.76.0 (411 x 914 dp, 420 dpi):
 * appending `-land` to that constant would put the orientation after `-nonav`, an invalid qualifier order.
 * The "inside bars" size is the Pixel 7 once the status bar and cutout are removed: the tightest supported size.
 */
object TestDevices {
    const val PIXEL7_LANDSCAPE = "w914dp-h411dp-land-420dpi"
    const val PIXEL7_LANDSCAPE_INSIDE_BARS = "w800dp-h360dp-land-xxhdpi"

    const val FR_PIXEL7_LANDSCAPE = "fr-$PIXEL7_LANDSCAPE"
    const val FR_PIXEL7_LANDSCAPE_INSIDE_BARS = "fr-$PIXEL7_LANDSCAPE_INSIDE_BARS"

    const val EN_PIXEL7_LANDSCAPE = "en-$PIXEL7_LANDSCAPE"
    const val EN_PIXEL7_LANDSCAPE_INSIDE_BARS = "en-$PIXEL7_LANDSCAPE_INSIDE_BARS"
}
