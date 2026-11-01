package io.github.darkcosmos.kosmodarts.capture.session

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val MIN_DIGITS = 3

/** File and folder names of a capture session (rules CP1 and CP3). */
object CaptureNaming {
    /** Name of the truth file in a session folder. */
    const val TRUTH_FILE = "truth.json"

    private val folderFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC)

    /**
     * Session folder name `YYYYMMDD-HHMMSS` from the UTC wall clock of the start (rule CP1). When [existing] already
     * holds that name, the suffix `-2`, `-3`... is appended.
     */
    fun sessionFolder(start: Instant, existing: Set<String>): String {
        val base = folderFormat.format(start)
        if (base !in existing) return base
        return generateSequence(2) { it + 1 }.map { "$base-$it" }.first { it !in existing }
    }

    /** Video file of volley [index], `volley-001.mp4`, wider beyond 999 (rule CP3). */
    fun videoFile(index: Int): String = "volley-${padded(index)}.mp4"

    /** Photo file of dart entry [number], `dart-001.jpg`, wider beyond 999 (rule CP3). */
    fun photoFile(number: Int): String = "dart-${padded(number)}.jpg"

    private fun padded(number: Int): String = number.toString().padStart(MIN_DIGITS, '0')
}
