package io.github.darkcosmos.kosmodarts.capture.session

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureNamingTest {

    @Test
    fun `CP1 folder name comes from the UTC wall clock`() {
        val name = CaptureNaming.sessionFolder(Instant.parse("2026-10-31T14:03:22.123Z"), emptySet())
        assertEquals("20261031-140322", name)
    }

    @Test
    fun `CP1 folder name at midnight and on 31 December`() {
        assertEquals("20270101-000000", CaptureNaming.sessionFolder(Instant.parse("2027-01-01T00:00:00Z"), emptySet()))
        val lastSecond = Instant.parse("2026-12-31T23:59:59.999Z")
        assertEquals("20261231-235959", CaptureNaming.sessionFolder(lastSecond, emptySet()))
    }

    @Test
    fun `CP1 folder name ignores a non UTC default zone`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"))
            val name = CaptureNaming.sessionFolder(Instant.parse("2026-10-31T14:03:22Z"), emptySet())
            assertEquals("20261031-140322", name)
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun `CP1 collision with an own folder appends -2 then -3`() {
        val start = Instant.parse("2026-10-31T14:03:22Z")
        assertEquals("20261031-140322-2", CaptureNaming.sessionFolder(start, setOf("20261031-140322")))
        assertEquals(
            "20261031-140322-3",
            CaptureNaming.sessionFolder(start, setOf("20261031-140322", "20261031-140322-2")),
        )
    }

    @Test
    fun `CP3 file names are padded to 3 digits then grow`() {
        assertEquals("dart-001.jpg", CaptureNaming.photoFile(1))
        assertEquals("dart-999.jpg", CaptureNaming.photoFile(999))
        assertEquals("dart-1000.jpg", CaptureNaming.photoFile(1000))
        assertEquals("volley-001.mp4", CaptureNaming.videoFile(1))
        assertEquals("volley-1000.mp4", CaptureNaming.videoFile(1000))
        assertEquals("truth.json", CaptureNaming.TRUTH_FILE)
    }
}
