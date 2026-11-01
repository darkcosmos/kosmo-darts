package io.github.darkcosmos.kosmodarts.capture.truth

import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/**
 * The `utc` text of the truth file (rule CP10, format H1): ISO 8601 in UTC with exactly three fraction digits and the
 * `Z` suffix, for example `2026-10-31T14:03:22.123Z`. Years 0000 to 9999 only, so that the text always has this shape.
 */
object UtcFormat {

    /** Earliest instant the format can write. */
    val MIN: Instant = Instant.parse("0000-01-01T00:00:00Z")

    /** Latest instant the format can write. */
    val MAX: Instant = Instant.parse("9999-12-31T23:59:59.999Z")

    private val SHAPE = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z""")

    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
            .withZone(ZoneOffset.UTC)
            .withResolverStyle(ResolverStyle.STRICT)

    /** Writes [instant], truncating (never rounding) anything finer than the millisecond. */
    fun format(instant: Instant): String {
        require(instant in MIN..MAX) { "Instant $instant is outside the years 0000 to 9999" }
        return FORMATTER.format(instant)
    }

    /** Reads a text written by [format], or returns null if [text] does not have exactly that shape. */
    fun parse(text: String): Instant? {
        if (!SHAPE.matches(text)) return null
        return try {
            Instant.from(FORMATTER.parse(text))
        } catch (_: DateTimeException) {
            // Right shape but not a real date or time (month 13, February 30, hour 24): not a valid stamp.
            null
        }
    }
}
