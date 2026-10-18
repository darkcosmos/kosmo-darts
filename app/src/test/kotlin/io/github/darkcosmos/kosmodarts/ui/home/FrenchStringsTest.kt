package io.github.darkcosmos.kosmodarts.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** French typography of the new home texts: a non-breaking space before the colon, so it never starts a line. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "fr")
class FrenchStringsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `R2 checkout to confirm uses a non-breaking space before the colon`() {
        assertEquals("Checkout à confirmer\u00A0: Ann", context.getString(R.string.home_saved_game_checkout, "Ann"))
    }

    @Test
    fun `R9 the message is the owner's text`() {
        assertEquals(
            "La partie enregistrée n'a pas pu être reprise et a été effacée",
            context.getString(R.string.restore_failed_message),
        )
    }
}
