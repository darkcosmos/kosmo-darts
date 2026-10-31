package io.github.darkcosmos.kosmodarts.capture.truth

import io.github.darkcosmos.kosmodarts.capture.truth.TruthJsonGoldenTest.Companion.GOLDEN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decoder is strict (format and version policy of `docs/camera/capture-format.md`; darts per rule D2 through the
 * engine; stamps per CP10; voided per CP4; empty per CP5; end of session per CP6 and CP9).
 */
class TruthJsonRejectTest {

    /** [GOLDEN] with exactly one occurrence of [old] replaced by [new]. */
    private fun golden(old: String, new: String): String {
        val first = GOLDEN.indexOf(old)
        assertTrue("fixture text not found: $old", first >= 0)
        assertEquals("fixture text not unique: $old", first, GOLDEN.lastIndexOf(old))
        return GOLDEN.replaceRange(first, first + old.length, new)
    }

    private fun assertRejected(text: String, vararg fragments: String) {
        val result = TruthJson.decode(text)
        assertTrue("accepted: $text", result is TruthDecodeResult.Failure)
        val message = (result as TruthDecodeResult.Failure).message
        fragments.forEach { assertTrue("'$message' should contain '$it'", message.contains(it)) }
    }

    @Test
    fun `golden text itself is accepted, with surrounding JSON whitespace`() {
        assertTrue(TruthJson.decode(" \t\r\n$GOLDEN\n") is TruthDecodeResult.Success)
    }

    @Test
    fun `rejects an unknown format`() {
        assertRejected(golden("\"kosmo-capture-truth\"", "\"kosmo-capture\""), "$.format", "unknown format")
    }

    @Test
    fun `rejects any other version, checked before the rest of the file`() {
        assertRejected(golden("\"version\": 1,", "\"version\": 2,"), "$.version", "unsupported version 2")
        assertRejected(golden("\"version\": 1,", "\"version\": 0,"), "unsupported version 0")
        assertRejected(golden("\"version\": 1,", "\"version\": \"1\","), "$.version", "expected an integer")
        assertRejected(golden("\"version\": 1,", "\"version\": 1.0,"), "$.version", "expected an integer")
        assertRejected("{\"format\": \"kosmo-capture-truth\", \"version\": 2}", "unsupported version 2")
    }

    @Test
    fun `rejects an unknown clock`() {
        assertRejected(golden("\"elapsedRealtimeNanos\"", "\"uptimeNanos\""), "$.clock")
    }

    @Test
    fun `rejects a missing required field, nullable ones included`() {
        assertRejected(golden("  \"session\": \"20261031-140322\",\n", ""), "$.session", "missing field")
        assertRejected(golden("\"voidedAt\": null,\n", ""), "$.volleys[0].darts[0].voidedAt", "missing field")
        assertRejected(
            golden(",\n            \"videoPositionMs\": 892", ""),
            "$.volleys[0].darts[0].photo.videoPositionMs", "missing field",
        )
    }

    @Test
    fun `rejects an unknown field`() {
        assertRejected(golden("\"sdk\": 37", "\"sdk\": 37, \"serial\": \"x\""), "$.device", "unknown field \"serial\"")
        assertRejected(
            golden("\"monotonicNanos\": 11100000000\n", "\"monotonicNanos\": 11100000000, \"videoPositionMs\": 0\n"),
            "$.endedAt", "unknown field \"videoPositionMs\"",
        )
    }

    @Test
    fun `rejects a wrong type`() {
        assertRejected(golden("\"sdk\": 37", "\"sdk\": \"37\""), "$.device.sdk", "expected a 32-bit integer")
        assertRejected(golden("\"sdk\": 37", "\"sdk\": 3000000000"), "$.device.sdk", "expected a 32-bit integer")
        assertRejected(golden("\"empty\": true", "\"empty\": 1"), "$.volleys[1].empty", "expected true or false")
        assertRejected(golden("\"20261031-140322\"", "null"), "$.session", "expected a string")
        assertRejected(golden("\"darts\": []", "\"darts\": {}"), "$.volleys[1].darts", "expected an array")
        assertRejected(golden("\"darts\": []", "\"darts\": [1]"), "$.volleys[1].darts[0]", "expected an object")
        assertRejected(golden("\"x\": 0.1", "\"x\": \"0.1\""), "gravity.x", "expected a float")
        assertRejected(golden("\"x\": 0.1", "\"x\": 1e999"), "gravity.x", "expected a float")
        // Over Long.MAX_VALUE.
        assertRejected(golden("6042000000", "99999999999999999999"), "photo.monotonicNanos", "expected an integer")
    }

    @Test
    fun `rejects a bad multiplier, a triple bull and segment 21 (rule D2)`() {
        val firstDart = "$.volleys[0].darts[0]"
        assertRejected(golden("\"TRIPLE\"", "\"QUADRUPLE\""), "$firstDart.multiplier", "one of SINGLE, DOUBLE, TRIPLE")
        assertRejected(golden("\"TRIPLE\"", "\"triple\""), "multiplier")
        assertRejected(golden("\"segment\": 20", "\"segment\": 50"), firstDart, "cannot be hit with multiplier TRIPLE")
        assertRejected(golden("\"segment\": 20", "\"segment\": 21"), "$.volleys[0].darts[0]", "Segment 21")
        assertRejected(golden("\"segment\": 5", "\"segment\": 21"), "Segment 21 does not exist")
        assertRejected(golden("\"segment\": 5", "\"segment\": -5"), "Segment -5 does not exist")
    }

    @Test
    fun `CP10 rejects a bad utc text, a negative clock or position`() {
        assertRejected(golden("\"2026-10-31T14:03:28.223Z\"", "\"2026-10-31T14:03:28Z\""), "$.endedAt.utc")
        assertRejected(golden("\"2026-10-31T14:03:28.223Z\"", "\"2026-02-30T14:03:28.223Z\""), "$.endedAt.utc")
        assertRejected(golden("\"2026-10-31T14:03:28.223Z\"", "1"), "$.endedAt.utc", "expected a string")
        assertRejected(golden("\"monotonicNanos\": 11100000000", "\"monotonicNanos\": -1"), "$.endedAt", "negative")
        assertRejected(golden("\"videoPositionMs\": 892", "\"videoPositionMs\": -1"), "photo", "negative")
    }

    @Test
    fun `CP4 and CP5 reject flags that contradict the data`() {
        assertRejected(golden("\"voided\": true", "\"voided\": false"), "$.volleys[0].darts[1].voided", "voidedAt")
        assertRejected(golden("\"empty\": true", "\"empty\": false"), "$.volleys[1].empty", "does not match")
    }

    @Test
    fun `CP6 and CP9 reject an end reason without an end time and a missing volley end`() {
        val noEnd = golden(
            "\"endedAt\": {\n    \"utc\": \"2026-10-31T14:03:28.223Z\",\n    \"monotonicNanos\": 11100000000\n  },",
            "\"endedAt\": null,",
        )
        assertRejected(noEnd, "$:", "both set or both null")
        val runningFirstVolley = golden(
            "\"end\": {\n        \"utc\": \"2026-10-31T14:03:26.123Z\",\n        \"monotonicNanos\": 9000000000,\n" +
                "        \"videoPositionMs\": 3850\n      },",
            "\"end\": null,",
        )
        assertRejected(runningFirstVolley, "Only the last volley may have no end")
        assertRejected(golden("\"index\": 2", "\"index\": 3"), "Volley indexes")
        assertRejected(golden("\"number\": 2", "\"number\": 3"), "Dart numbers")
    }

    @Test
    fun `rejects invalid JSON`() {
        assertRejected("$GOLDEN x", "after the end")
        assertRejected("$GOLDEN{}", "after the end")
        assertRejected("[]", "$: expected an object")
        assertRejected("", "unexpected end")
        assertRejected(golden("\"sdk\": 37", "\"sdk\": 037"), "Invalid JSON")
        assertRejected(golden("\"sdk\": 37", "\"sdk\": NaN"), "Invalid JSON")
        assertRejected(golden("\"sdk\": 37", "\"sdk\": 37,"), "Invalid JSON")
        assertRejected(golden("\"0.1.0\"", "\"0.1\u0001.0\""), "control character")
        assertRejected(golden("\"0.1.0\"", "\"0.1\\x.0\""), "invalid escape")
        assertRejected(golden("\"0.1.0\"", "\"0.1\\u00G0\""), "invalid \\u escape")
        assertRejected(golden("\"empty\": true", "\"empty\": tru"), "invalid literal")
        assertRejected(golden("\"sdk\": 37", "'sdk': 37"), "expected a key")
    }

    @Test
    fun `rejects a duplicate key`() {
        assertRejected(golden("\"sdk\": 37", "\"sdk\": 37, \"sdk\": 37"), "duplicate key \"sdk\"")
    }

    @Test
    fun `rejects every truncation of the golden text`() {
        for (length in 0 until GOLDEN.length) {
            val result = TruthJson.decode(GOLDEN.take(length))
            assertFalse("prefix of length $length accepted", result is TruthDecodeResult.Success)
        }
    }

    @Test
    fun `rejects nesting deeper than the limit without exhausting the stack`() {
        assertRejected("[".repeat(100_000), "nesting deeper than ${JsonParser.MAX_DEPTH}")
    }

    @Test
    fun `rejects a text over 10 MiB before parsing it`() {
        val limit = TruthJson.MAX_TEXT_BYTES

        assertRejected(" ".repeat(limit + 1), "larger than $limit bytes")
        // Fewer characters than the limit, but more UTF-8 bytes (2 bytes per 'é').
        assertRejected("é".repeat(limit / 2 + 1), "larger than $limit bytes")
        // Exactly at the limit: parsed, and rejected as JSON instead.
        assertRejected(" ".repeat(limit), "Invalid JSON", "unexpected end")
    }
}
