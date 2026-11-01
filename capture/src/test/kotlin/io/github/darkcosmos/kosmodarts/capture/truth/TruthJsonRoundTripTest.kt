package io.github.darkcosmos.kosmodarts.capture.truth

import io.github.darkcosmos.kosmodarts.capture.truth.TruthFixtures.at
import io.github.darkcosmos.kosmodarts.capture.truth.TruthFixtures.photo
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Encode then decode gives back the same session (CP3, CP4, CP5, CP6, CP9, CP10, CP13, CP17). */
class TruthJsonRoundTripTest {

    private fun roundTrip(session: TruthSession): TruthSession {
        val result = TruthJson.decode(TruthJson.encode(session))
        assertTrue("decode failed: $result", result is TruthDecodeResult.Success)
        return (result as TruthDecodeResult.Success).session
    }

    @Test
    fun `full session round trip keeps voided, null photo, empty volley, miss, 25 and bull`() {
        val session = TruthFixtures.full()

        val decoded = roundTrip(session)

        assertEquals(session, decoded)
        val (first, second, third) = decoded.volleys
        assertTrue("CP4 voided dart keeps its photo", first.darts[1].voided && first.darts[1].photo != null)
        assertNull("CP13 photo failure", first.darts[2].photo)
        assertEquals(Dart.MISS, first.darts[2].dart)
        assertEquals(listOf(Dart.BULL, Dart.OUTER_BULL), second.darts.map { it.dart })
        assertNull("CP10 position before the video start", second.darts[0].entry.videoPositionMs)
        assertTrue("CP5 empty volley", third.empty)
        assertFalse(first.empty)
    }

    @Test
    fun `CP9 killed session round trip keeps no end and a running last volley`() {
        val session = TruthFixtures.killed()

        val decoded = roundTrip(session)

        assertEquals(session, decoded)
        assertNull(decoded.endedAt)
        assertNull(decoded.endReason)
        assertNull(decoded.volleys.last().end)
    }

    @Test
    fun `CP17 zoom requested 2_4 and reported 2_3999999 round trip exactly`() {
        val zoom = ZoomRecord(requestedRatio = 2.4f, ratio = 2.3999999f)

        val decoded = roundTrip(TruthFixtures.full().copy(zoom = zoom))

        assertEquals(zoom, decoded.zoom)
    }

    @Test
    fun `CP17 zoom with both values null round trips`() {
        val zoom = ZoomRecord(requestedRatio = null, ratio = null)

        val decoded = roundTrip(TruthFixtures.session(TruthFixtures.killed().volleys, zoom = zoom))

        assertEquals(zoom, decoded.zoom)
    }

    @Test
    fun `CP17 zoom values have no upper bound and exponent forms round trip`() {
        listOf(
            ZoomRecord(requestedRatio = 1500f, ratio = 1.0E10f),
            ZoomRecord(requestedRatio = 1.0E-4f, ratio = Float.MIN_VALUE),
            ZoomRecord(requestedRatio = null, ratio = Float.MAX_VALUE),
        ).forEach { zoom ->
            val decoded = roundTrip(TruthFixtures.session(TruthFixtures.killed().volleys, zoom = zoom))

            assertEquals(zoom, decoded.zoom)
        }
    }

    @Test
    fun `CP9 CP17 killed session keeps its zoom record`() {
        val session = TruthFixtures.killed().copy(zoom = ZoomRecord(requestedRatio = 3f, ratio = 2.9f))

        val decoded = roundTrip(session)

        assertEquals(session, decoded)
        assertNull(decoded.endedAt)
        assertEquals(ZoomRecord(requestedRatio = 3f, ratio = 2.9f), decoded.zoom)
    }

    @Test
    fun `CP17 ZoomRecord refuses NaN, infinities, 0 and negative ratios`() {
        val invalid = listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -0f, -1f, -Float.MIN_VALUE)
        invalid.forEach { value ->
            assertThrows("requestedRatio $value", IllegalArgumentException::class.java) {
                ZoomRecord(requestedRatio = value, ratio = 1f)
            }
            assertThrows("ratio $value", IllegalArgumentException::class.java) {
                ZoomRecord(requestedRatio = 1f, ratio = value)
            }
        }
    }

    @Test
    fun `CP5 a volley whose only dart is voided reads back as empty`() {
        val volley = TruthVolley(
            index = 1, video = "volley-001.mp4", start = at(0), end = at(3_000, 2_900),
            darts = listOf(TruthDart(1, Dart.single(7), at(1_000, 900), at(2_000, 1_900), null, null)),
        )

        val decoded = roundTrip(TruthFixtures.session(listOf(volley), at(3_100), EndReason.INTERRUPTED))

        assertTrue(decoded.volleys.single().empty)
        assertEquals(EndReason.INTERRUPTED, decoded.endReason)
    }

    @Test
    fun `strings with quotes, control characters, non-ASCII and lone surrogates round trip`() {
        // A surrogate pair (U+1F3AF), then a lone high and a lone low surrogate.
        val model = "Pixel 7 " + String(Character.toChars(0x1F3AF)) + " " + 0xD800.toChar() + " " + 0xDC00.toChar()
        val device = DeviceInfo(manufacturer = "Gö\"o\\g/le\u0001\n\t\r\b\u000C", model = model, sdk = 37)

        val decoded = roundTrip(TruthFixtures.session(TruthFixtures.killed().volleys, device = device))

        assertEquals(device, decoded.device)
    }

    @Test
    fun `large session of 1000 darts round trips`() {
        var number = 0
        val volleys = (1..334).map { index ->
            val start = index * 10_000L
            val darts = (0 until 3).mapNotNull { slot ->
                if (number == 1000) return@mapNotNull null
                number++
                val dart = Dart((number % 20) + 1, Multiplier.entries[number % 3])
                val offset = slot * 1_000L
                val photo = photo(number, start + offset + 30, offset + 30)
                TruthDart(number, dart, at(start + offset, offset), null, photo, Gravity(0f, -9.81f, slot.toFloat()))
            }
            TruthVolley(index, "volley-%03d.mp4".format(index), at(start), at(start + 9_000, 8_900), darts)
        }
        val session = TruthFixtures.session(volleys, at(4_000_000), EndReason.STOP)

        val decoded = roundTrip(session)

        assertEquals(1000, decoded.volleys.sumOf { it.darts.size })
        assertEquals(session, decoded)
    }
}
