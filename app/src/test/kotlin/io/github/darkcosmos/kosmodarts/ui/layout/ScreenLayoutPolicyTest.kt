package io.github.darkcosmos.kosmodarts.ui.layout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two columns of the between-legs screen (owner review of PR #57, M16). Pure JVM. */
class ScreenLayoutPolicyTest {

    @Test
    fun `landscape phones show two columns`() {
        assertTrue(ScreenLayoutPolicy.hasTwoColumns(800f, 360f))
        assertTrue(ScreenLayoutPolicy.hasTwoColumns(640f, 360f))
        assertTrue(ScreenLayoutPolicy.hasTwoColumns(915f, 412f))
    }

    @Test
    fun `portrait and narrow windows keep one column`() {
        assertFalse(ScreenLayoutPolicy.hasTwoColumns(412f, 915f))
        assertFalse(ScreenLayoutPolicy.hasTwoColumns(360f, 640f))
        assertFalse(ScreenLayoutPolicy.hasTwoColumns(560f, 400f))
    }
}
