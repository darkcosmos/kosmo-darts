package io.github.darkcosmos.kosmodarts.capture.truth

import io.github.darkcosmos.kosmodarts.capture.truth.TruthFixtures.at
import io.github.darkcosmos.kosmodarts.capture.truth.TruthFixtures.photo
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The exact text of a small truth file (CP3 to CP6, CP10, CP13): fixed key order, two-space indentation, non-ASCII
 * written as UTF-8, quotes, backslashes and control characters escaped, no trailing newline. The example of
 * `docs/camera/capture-format.md` is this text; update both together.
 */
class TruthJsonGoldenTest {

    @Test
    fun `encodes the documented text`() {
        assertEquals(GOLDEN, TruthJson.encode(small()))
    }

    @Test
    fun `decodes the documented text`() {
        assertEquals(TruthDecodeResult.Success(small()), TruthJson.decode(GOLDEN))
    }

    @Test
    fun `the text survives UTF-8 bytes`() {
        val bytes = TruthJson.encode(small()).toByteArray(Charsets.UTF_8)

        assertEquals(TruthDecodeResult.Success(small()), TruthJson.decode(String(bytes, Charsets.UTF_8)))
    }

    companion object {

        fun small(): TruthSession =
            TruthFixtures.session(
                device = DeviceInfo(manufacturer = "Gööglé", model = "Pixel \"7\" \\ a\u0001", sdk = 37),
                volleys = listOf(
                    TruthVolley(
                        index = 1, video = "volley-001.mp4", start = at(0), end = at(4_000, 3_850),
                        darts = listOf(
                            TruthDart(
                                1, Dart.triple(20), at(1_000, 850), null, photo(1, 1_042, 892),
                                Gravity(0.1f, -9.8f, 0.25f),
                            ),
                            TruthDart(2, Dart.single(5), at(2_000, 1_850), at(3_000, 2_850), null, null),
                        ),
                    ),
                    TruthVolley(
                        index = 2, video = "volley-002.mp4", start = at(4_000), end = at(6_000, 1_900),
                        darts = emptyList(),
                    ),
                ),
                endedAt = at(6_100),
                endReason = EndReason.STOP,
            )

        val GOLDEN: String = """
            {
              "format": "kosmo-capture-truth",
              "version": 1,
              "session": "20261031-140322",
              "app": {
                "id": "io.github.darkcosmos.kosmodarts.capture",
                "version": "0.1.0"
              },
              "device": {
                "manufacturer": "Gööglé",
                "model": "Pixel \"7\" \\ a\u0001",
                "sdk": 37
              },
              "clock": "elapsedRealtimeNanos",
              "startedAt": {
                "utc": "2026-10-31T14:03:22.123Z",
                "monotonicNanos": 5000000000
              },
              "endedAt": {
                "utc": "2026-10-31T14:03:28.223Z",
                "monotonicNanos": 11100000000
              },
              "endReason": "STOP",
              "video": {
                "requestedQuality": "UHD",
                "width": 3840,
                "height": 2160
              },
              "photo": {
                "width": 4080,
                "height": 3072,
                "timestampSource": "REALTIME"
              },
              "volleys": [
                {
                  "index": 1,
                  "video": "volley-001.mp4",
                  "start": {
                    "utc": "2026-10-31T14:03:22.123Z",
                    "monotonicNanos": 5000000000
                  },
                  "end": {
                    "utc": "2026-10-31T14:03:26.123Z",
                    "monotonicNanos": 9000000000,
                    "videoPositionMs": 3850
                  },
                  "empty": false,
                  "darts": [
                    {
                      "number": 1,
                      "segment": 20,
                      "multiplier": "TRIPLE",
                      "voided": false,
                      "entry": {
                        "utc": "2026-10-31T14:03:23.123Z",
                        "monotonicNanos": 6000000000,
                        "videoPositionMs": 850
                      },
                      "voidedAt": null,
                      "photo": {
                        "file": "dart-001.jpg",
                        "utc": "2026-10-31T14:03:23.165Z",
                        "monotonicNanos": 6042000000,
                        "videoPositionMs": 892
                      },
                      "gravity": {
                        "x": 0.1,
                        "y": -9.8,
                        "z": 0.25
                      }
                    },
                    {
                      "number": 2,
                      "segment": 5,
                      "multiplier": "SINGLE",
                      "voided": true,
                      "entry": {
                        "utc": "2026-10-31T14:03:24.123Z",
                        "monotonicNanos": 7000000000,
                        "videoPositionMs": 1850
                      },
                      "voidedAt": {
                        "utc": "2026-10-31T14:03:25.123Z",
                        "monotonicNanos": 8000000000,
                        "videoPositionMs": 2850
                      },
                      "photo": null,
                      "gravity": null
                    }
                  ]
                },
                {
                  "index": 2,
                  "video": "volley-002.mp4",
                  "start": {
                    "utc": "2026-10-31T14:03:26.123Z",
                    "monotonicNanos": 9000000000
                  },
                  "end": {
                    "utc": "2026-10-31T14:03:28.123Z",
                    "monotonicNanos": 11000000000,
                    "videoPositionMs": 1900
                  },
                  "empty": true,
                  "darts": []
                }
              ]
            }
        """.trimIndent()
    }
}
