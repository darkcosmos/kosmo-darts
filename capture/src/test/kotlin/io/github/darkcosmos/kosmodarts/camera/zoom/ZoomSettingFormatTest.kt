package io.github.darkcosmos.kosmodarts.camera.zoom

import org.junit.Assert.assertEquals
import org.junit.Test

/** Rule CP16 (parse fallbacks). */
class ZoomSettingFormatTest {
    @Test
    fun invalidTextFallsBackToOne() {
        listOf(null, "", "abc", "NaN", "Infinity", "0", "-1").forEach {
            assertEquals("text $it", 1.0f, ZoomSettingFormat.parse(it), 0f)
        }
    }

    @Test
    fun parsesAndFormatsRatio() {
        assertEquals(2.4f, ZoomSettingFormat.parse("2.4"), 0f)
        assertEquals("2.4", ZoomSettingFormat.format(2.4f))
    }
}
