package io.github.darkcosmos.kosmodarts.ui.history

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HistoryFilters
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.home.HomeTags
import io.github.darkcosmos.kosmodarts.ui.resume.AppProcess
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** HS5, HS6, HS7: the match detail opened from the list, through the activity, on the device's own lifecycle. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class MatchDetailFlowTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val repository = FakeHistoryRepository(
        answer = okList(
            listOf(x01HistoryMatch(7, "Ana", "Bob"), cricketHistoryMatch(8, "Cleo")),
            profilesOf("Ana", "Bob", "Cleo"),
        ),
        matchAnswer = DbResult.Ok(x01Detail(sessionId = 7)),
    )
    private val process = AppProcess(history = repository)

    @After
    fun tearDown() = process.close()

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    private fun click(tag: String) {
        node(tag).performClick()
        process.settle()
        composeRule.waitForIdle()
    }

    private fun openDetail() {
        process.launchFresh()
        click(HomeTags.HISTORY)
        click(HistoryTags.row(7))
    }

    private fun back() {
        process.pressBack()
        process.settle()
        composeRule.waitForIdle()
    }

    @Test
    fun `HS5 a tap on a row opens the detail of that match, read once`() {
        openDetail()
        node(HistoryTags.DETAIL_SUMMARY).assertIsDisplayed()
        node(HistoryTags.legHeading(0)).assertIsDisplayed()
        assertEquals(listOf(7L), repository.matchesAsked)
    }

    @Test
    fun `HS6 Back returns to the list with its filters, without reading the list again`() {
        process.launchFresh()
        click(HomeTags.HISTORY)
        click(HistoryTags.playerChip(2))
        val asked = repository.asked.size
        click(HistoryTags.row(7))

        back()

        node(HistoryTags.playerChip(2)).assertIsDisplayed().assertIsSelected()
        assertEquals(asked, repository.asked.size)
        assertTrue(process.viewModel.state.value.screen is Screen.History)
    }

    @Test
    fun `HS7 a failed read is shown, Retry reads again and shows the match`() {
        repository.matchAnswer = DbResult.Failed
        openDetail()
        node(HistoryTags.DETAIL_FAILED).assertIsDisplayed()

        repository.matchAnswer = DbResult.Ok(x01Detail(sessionId = 7))
        click(HistoryTags.DETAIL_RETRY)
        node(HistoryTags.DETAIL_SUMMARY).assertIsDisplayed()
        assertEquals(listOf(7L, 7L), repository.matchesAsked)
    }

    @Test
    fun `HS6 a rotation keeps the detail without reading again`() {
        openDetail()

        process.rotate()
        process.settle()
        composeRule.waitForIdle()

        node(HistoryTags.DETAIL_SUMMARY).assertIsDisplayed()
        assertEquals(listOf(7L), repository.matchesAsked)
    }

    @Test
    fun `HS6 returning after the process was killed brings the detail back, then Back brings the list`() {
        process.launchFresh()
        click(HomeTags.HISTORY)
        composeRule.onNode(hasText("X01") and isSelectable()).performClick()
        process.settle()
        click(HistoryTags.row(7))

        process.killInBackgroundAndReturn()
        process.settle()
        composeRule.waitForIdle()

        node(HistoryTags.DETAIL_SUMMARY).assertIsDisplayed()
        assertEquals(HistoryFilters(x01Mode(), null), repository.asked.last())
        back()
        composeRule.onNode(hasText("X01") and isSelectable()).assertIsSelected()
        node(HistoryTags.row(7)).assertIsDisplayed()
    }

    private fun x01Mode() = io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode.X01
}
