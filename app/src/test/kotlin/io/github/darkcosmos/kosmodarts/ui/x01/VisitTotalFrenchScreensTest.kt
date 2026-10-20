package io.github.darkcosmos.kosmodarts.ui.x01

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.game.KeypadTags
import io.github.darkcosmos.kosmodarts.ui.setup.SetupTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * French texts of the visit total box (docs/rules/visit-total.md, VT8). The expected descriptions are written out,
 * with the non-breaking space (U+00A0) before the colon, so that a wrong translation fails.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "fr")
class VisitTotalFrenchScreensTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int): String = composeRule.activity.getString(id)

    private fun key(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun tap(vararg tags: String) = tags.forEach { key(it).performClick() }

    private fun single(vararg numbers: Int) = numbers.forEach { tap(KeypadTags.number(it)) }

    private fun triple(number: Int) = tap(KeypadTags.TRIPLE, KeypadTags.number(number))

    private fun assertBox(player: Int, text: String, description: String) {
        key(GameTags.visitTotal(player)).performScrollTo()
            .assertTextEquals(text)
            .assertContentDescriptionEquals(description)
    }

    @Test
    fun `VT8 French values and descriptions`() {
        composeRule.onNodeWithText(string(R.string.home_new_game)).performScrollTo().performClick()
        composeRule.onNodeWithText("301").performClick()
        listOf("Ann", "Bob").forEachIndexed { index, name ->
            key(SetupTags.nameField(index)).performScrollTo().performTextReplacement(name)
        }
        composeRule.onNodeWithText(string(R.string.setup_start)).performScrollTo().performClick()

        assertBox(0, "0", "Volée en cours\u00A0: 0")
        assertBox(1, "0", "Pas encore joué")

        single(20)
        assertBox(0, "20", "Volée en cours\u00A0: 20")

        triple(20)
        triple(20)
        assertBox(0, "140", "Dernière volée\u00A0: 140")
        tap(KeypadTags.NEXT_PLAYER)
        single(1, 1, 1)
        tap(KeypadTags.NEXT_PLAYER)

        // Ann has 161: T20 T20 T20 leaves 1, a bust in double out.
        triple(20)
        triple(20)
        triple(20)
        // VT13: while the bust waits, no box for Ann; Bob keeps his last turn (VT6).
        key(GameTags.visitTotal(0)).assertDoesNotExist()
        assertBox(1, "3", "Dernière volée\u00A0: 3")
        tap(KeypadTags.NEXT_PLAYER)
        assertBox(0, "BUST", "Dernière volée\u00A0: bust")
    }
}
