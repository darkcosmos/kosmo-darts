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

    /** Issue #39: every new text of the match, in French (docs/rules/match.md, M13 to M18; AN27). */
    @Test
    fun `M13 to M18 AN27 every match text is translated`() {
        val expected = mapOf(
            context.getString(R.string.setup_match_label) to "Match",
            context.getString(R.string.setup_legs_per_set) to "Manches par set",
            context.getString(R.string.setup_legs) to "Manches",
            context.getString(R.string.setup_sets) to "Sets",
            context.getString(R.string.setup_first_to, 3) to "Premier à 3",
            context.getString(R.string.setup_legs_per_set_value, 3) to "Manches par set\u00A0: premier à 3",
            context.getString(R.string.setup_legs_value, 3) to "Manches\u00A0: premier à 3",
            context.getString(R.string.setup_sets_value, 2) to "Sets\u00A0: premier à 2",
            context.getString(R.string.setup_fewer_legs) to "Moins de manches",
            context.getString(R.string.setup_more_legs) to "Plus de manches",
            context.getString(R.string.setup_fewer_sets) to "Moins de sets",
            context.getString(R.string.setup_more_sets) to "Plus de sets",
            context.getString(R.string.match_sets_label) to "Sets",
            context.getString(R.string.match_legs_label) to "Manches",
            context.getString(R.string.match_count_overflow, 5) to "×5",
            context.getString(R.string.match_score_sets, 1) to "Sets 1",
            context.getString(R.string.match_score_legs, 2) to "Manches 2",
            context.getString(R.string.match_score_separator) to " · ",
            context.resources.getQuantityString(R.plurals.match_sets_won, 1, 1) to "1 set gagné",
            context.resources.getQuantityString(R.plurals.match_sets_won, 2, 2) to "2 sets gagnés",
            context.resources.getQuantityString(R.plurals.match_legs_won, 1, 1) to "1 manche gagnée",
            context.resources.getQuantityString(R.plurals.match_legs_won, 3, 3) to "3 manches gagnées",
            context.getString(R.string.match_indicators_description, "1 set gagné", "2 manches gagnées") to
                "1 set gagné, 2 manches gagnées",
            context.getString(R.string.checkout_finish_leg) to "Terminer la manche",
            context.getString(R.string.checkout_finish_set) to "Terminer le set",
            context.getString(R.string.checkout_finish) to "Terminer la partie",
            context.getString(R.string.between_leg_won_by, "Ann") to "Manche gagnée par Ann",
            context.getString(R.string.between_set_won_by, "Ann") to "Set gagné par Ann",
            context.getString(R.string.between_title) to "Score du match",
            context.getString(R.string.between_next_starter, "Bob") to "Bob commence la manche suivante",
            context.getString(R.string.between_next_leg) to "Manche suivante",
            context.getString(R.string.announce_leg_won, "Ann") to "Manche pour Ann",
            context.getString(R.string.announce_set_won, "Ann") to "Set pour Ann",
        )
        expected.forEach { (actual, french) -> assertEquals(french, actual) }
    }

    @Test
    fun `R9 the message is the owner's text`() {
        assertEquals(
            "La partie enregistrée n'a pas pu être reprise et a été effacée",
            context.getString(R.string.restore_failed_message),
        )
    }
}
