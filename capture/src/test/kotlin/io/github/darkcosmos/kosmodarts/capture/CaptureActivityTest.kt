package io.github.darkcosmos.kosmodarts.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** The placeholder activity starts without crashing. */
@RunWith(AndroidJUnit4::class)
class CaptureActivityTest {
    @Test
    fun launchesWithoutCrashing() {
        val activity = Robolectric.buildActivity(CaptureActivity::class.java).setup().get()
        assertFalse(activity.isFinishing)
    }
}
