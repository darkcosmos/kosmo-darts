package io.github.darkcosmos.kosmodarts.capture.truth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Rule CP10: `utc` is ISO 8601 UTC with exactly three fraction digits and `Z` (format H1). */
class UtcFormatTest {

    @Test
    fun `CP10 writes milliseconds and Z`() {
        assertEquals("2026-10-31T14:03:22.123Z", UtcFormat.format(Instant.parse("2026-10-31T14:03:22.123Z")))
    }

    @Test
    fun `CP10 keeps zero milliseconds as 000`() {
        assertEquals("2026-10-31T14:03:22.000Z", UtcFormat.format(Instant.parse("2026-10-31T14:03:22Z")))
    }

    @Test
    fun `CP10 truncates the fraction instead of rounding it`() {
        assertEquals("2026-10-31T23:59:59.999Z", UtcFormat.format(Instant.parse("2026-10-31T23:59:59.999999999Z")))
        assertEquals("1969-12-31T23:59:59.000Z", UtcFormat.format(Instant.parse("1969-12-31T23:59:59.000900Z")))
    }

    @Test
    fun `CP10 parses back to the same instant`() {
        listOf(
            "2026-10-31T14:03:22.123Z",
            "2026-10-31T14:03:22.000Z",
            "0000-01-01T00:00:00.000Z",
            "9999-12-31T23:59:59.999Z",
            "2024-02-29T00:00:00.001Z",
        ).forEach { text -> assertEquals(text, UtcFormat.format(UtcFormat.parse(text)!!)) }
        assertEquals(Instant.parse("2026-10-31T14:03:22.123Z"), UtcFormat.parse("2026-10-31T14:03:22.123Z"))
    }

    @Test
    fun `CP10 parse rejects any other shape or an impossible date`() {
        listOf(
            "2026-10-31T14:03:22Z",
            "2026-10-31T14:03:22.12Z",
            "2026-10-31T14:03:22.1234Z",
            "2026-10-31T14:03:22.123",
            "2026-10-31T14:03:22.123+00:00",
            "2026-10-31 14:03:22.123Z",
            "2026-13-01T00:00:00.000Z",
            "2026-02-30T00:00:00.000Z",
            "2026-10-31T24:00:00.000Z",
            "+12026-10-31T14:03:22.123Z",
            "",
        ).forEach { text -> assertNull(text, UtcFormat.parse(text)) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `CP10 format refuses a year beyond 9999`() {
        UtcFormat.format(Instant.parse("+10000-01-01T00:00:00Z"))
    }
}
