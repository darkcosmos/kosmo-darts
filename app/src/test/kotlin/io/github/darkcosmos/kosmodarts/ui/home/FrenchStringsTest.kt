package io.github.darkcosmos.kosmodarts.ui.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The French texts, and both string files kept in sync (CLAUDE.md, "User-facing text"; A3 of issue #49). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "fr")
class FrenchStringsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Issue #39 and #49: the texts of the match and of the new screens, in French (M13 to M19; AN27). */
    @Test
    fun `M13 to M19 AN27 every match text is translated`() {
        val resources = context.resources
        val expected = mapOf(
            context.getString(R.string.setup_match_label) to "MATCH",
            context.getString(R.string.setup_sets_first_to) to "Sets · premier à",
            context.getString(R.string.setup_legs_first_to) to "Manches · premier à",
            context.getString(R.string.setup_legs_value, 3) to "Manches\u00A0: premier à 3",
            context.getString(R.string.setup_sets_value, 2) to "Sets\u00A0: premier à 2",
            context.getString(R.string.setup_fewer_legs) to "Moins de manches",
            context.getString(R.string.setup_more_legs) to "Plus de manches",
            context.getString(R.string.setup_fewer_sets) to "Moins de sets",
            context.getString(R.string.setup_more_sets) to "Plus de sets",
            context.getString(R.string.setup_remove_player, "Ann") to "Retirer Ann",
            context.getString(R.string.setup_player_placeholder, 3) to "Nom du joueur 3",
            context.getString(R.string.match_sets_label) to "SETS",
            context.getString(R.string.match_legs_label) to "MANCHES",
            context.getString(R.string.match_legs_short) to "MAN.",
            context.getString(R.string.match_average_header) to "MOY MATCH",
            context.getString(R.string.end_darts_header) to "FLÉCH.",
            context.getString(R.string.match_score_sets, 1) to "Sets 1",
            context.getString(R.string.match_score_legs, 2) to "Manches 2",
            context.getString(R.string.chip_sets, 1) to "S1",
            context.getString(R.string.chip_legs, 2) to "M2",
            context.getString(R.string.tile_score_sets_legs, 0, 1) to "S0 · M1",
            context.getString(R.string.match_position_set_leg, 2, 3) to "SET 2 · MANCHE 3",
            context.getString(R.string.match_position_leg, 3) to "MANCHE 3",
            context.getString(R.string.game_average, "62,40") to "MOY 62,40",
            resources.getQuantityString(R.plurals.match_sets_won, 1, 1) to "1 set gagné",
            resources.getQuantityString(R.plurals.match_sets_won, 2, 2) to "2 sets gagnés",
            resources.getQuantityString(R.plurals.match_legs_won, 1, 1) to "1 manche gagnée",
            resources.getQuantityString(R.plurals.match_legs_won, 3, 3) to "3 manches gagnées",
            context.getString(R.string.checkout_confirm_leg) to "VALIDER LA MANCHE",
            context.getString(R.string.checkout_confirm_set) to "VALIDER LE SET",
            context.getString(R.string.checkout_confirm_match) to "VALIDER LA PARTIE",
            context.getString(R.string.checkout_route_label) to "SORTIE",
            context.getString(R.string.key_undo) to "ANNULER",
            context.getString(R.string.key_next_player) to "JOUEUR SUIVANT",
            context.getString(R.string.between_leg_won, 2, 3) to "SET 2 · MANCHE 3 GAGNÉE",
            context.getString(R.string.between_leg_won_no_set, 3) to "MANCHE 3 GAGNÉE",
            context.getString(R.string.between_set_won, 2) to "SET 2 GAGNÉ",
            context.getString(R.string.between_starts, "CID") to "CID COMMENCE",
            context.getString(R.string.between_next_leg) to "MANCHE SUIVANTE",
            context.getString(R.string.end_match_winner) to "VAINQUEUR DU MATCH",
            context.getString(R.string.home_resume, "CID") to "REPRENDRE · À CID DE LANCER",
            context.getString(R.string.home_new_game_section) to "NOUVELLE PARTIE",
            context.getString(R.string.mode_x01_description) to "501 ou 301, sortie double ou simple",
            context.getString(R.string.settings_credits) to "CRÉDITS",
            context.getString(R.string.settings_font_credit) to "Police Saira, licence SIL Open Font License 1.1",
            context.getString(R.string.announce_leg_won, "Ann") to "Manche pour Ann",
            context.getString(R.string.announce_set_won, "Ann") to "Set pour Ann",
        )
        expected.forEach { (actual, french) -> assertEquals(french, actual) }
    }

    /** U2: counted texts use plurals; French uses "one" for 0 and 1. */
    @Test
    fun `U2 counted texts are plurals in French`() {
        val resources = context.resources
        assertEquals("141 EN 3 FLÉCHETTES", resources.getQuantityString(R.plurals.checkout_darts, 3, 141, 3))
        assertEquals("40 EN 1 FLÉCHETTE", resources.getQuantityString(R.plurals.checkout_darts, 1, 40, 1))
        assertEquals(
            "Prochain joueur\u00A0: Anna, 1 restant",
            resources.getQuantityString(R.plurals.next_player_tile_description, 1, "Anna", 1),
        )
        assertEquals(
            "Prochain joueur\u00A0: Anna, 301 restants",
            resources.getQuantityString(R.plurals.next_player_tile_description, 301, "Anna", 301),
        )
    }

    @Test
    fun `R9 the message is the owner's text`() {
        assertEquals(
            "La partie enregistrée n'a pas pu être reprise et a été effacée",
            context.getString(R.string.restore_failed_message),
        )
    }

    /**
     * A3: values/ and values-fr/ hold the same translatable strings and plurals, and no text is left in French only.
     * Read from the source files (the test runs in the module directory).
     */
    @Test
    fun `A3 both string files have the same translatable keys`() {
        val english = translatableKeys(File("src/main/res/values/strings.xml"))
        val french = translatableKeys(File("src/main/res/values-fr/strings.xml"))

        assertEquals("missing in French", emptySet<String>(), english - french)
        assertEquals("only in French", emptySet<String>(), french - english)
    }

    /** The names of the `string`, `plurals` and `string-array` elements not marked `translatable="false"`. */
    private fun translatableKeys(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val root = document.documentElement
        return listOf("string", "plurals", "string-array").flatMap { tag ->
            val nodes = root.getElementsByTagName(tag)
            (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
                .filter { it.getAttribute("translatable") != "false" }
                .map { "$tag:${it.getAttribute("name")}" }
        }.toSet()
    }
}
