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
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
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

/** HS1, HS6, HS7: History opened from the home screen, through the activity, on the device's own lifecycle. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class HistoryFlowTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val repository = FakeHistoryRepository(
        okList(
            listOf(x01HistoryMatch(1, "Ana", "Bob"), cricketHistoryMatch(2, "Cleo")),
            profilesOf("Ana", "Bob", "Cleo"),
        ),
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

    private fun openHistory() {
        process.launchFresh()
        click(HomeTags.HISTORY)
    }

    private fun back() {
        process.pressBack()
        process.settle()
        composeRule.waitForIdle()
    }

    @Test
    fun `HS1 the History button opens the list of matches, and back returns home`() {
        openHistory()
        node(HistoryTags.row(1)).assertIsDisplayed()
        node(HistoryTags.row(2)).assertIsDisplayed()
        assertEquals(listOf(HistoryFilters()), repository.asked)

        back()
        node(HomeTags.HISTORY).assertIsDisplayed()
    }

    @Test
    fun `HS3 choosing a filter reads again with it`() {
        openHistory()
        composeRule.onNode(hasText("CRICKET") and isSelectable()).performClick()
        process.settle()
        composeRule.waitForIdle()
        assertEquals(listOf(HistoryFilters(), HistoryFilters(ArchivedMode.CRICKET)), repository.asked)
    }

    @Test
    fun `HS7 a failed read is shown, and Retry reads again and shows the matches`() {
        repository.answer = DbResult.Failed
        openHistory()
        node(HistoryTags.FAILED).assertIsDisplayed()

        repository.answer = okList(listOf(x01HistoryMatch(1, "Ana", "Bob")), profilesOf("Ana", "Bob"))
        click(HistoryTags.RETRY)
        node(HistoryTags.row(1)).assertIsDisplayed()
        assertEquals(2, repository.asked.size)
    }

    @Test
    fun `HS6 a rotation keeps the filters without reading again`() {
        openHistory()
        click(HistoryTags.playerChip(2))
        assertEquals(2, repository.asked.size)

        process.rotate()
        process.settle()
        composeRule.waitForIdle()
        node(HistoryTags.playerChip(2)).assertIsDisplayed().assertIsSelected()
        assertEquals(2, repository.asked.size)
    }

    @Test
    fun `HS6 returning after the process was killed brings History back with its filters, read again`() {
        openHistory()
        composeRule.onNode(hasText("CRICKET") and isSelectable()).performClick()
        process.settle()
        click(HistoryTags.playerChip(3))
        val asked = repository.asked.size

        process.killInBackgroundAndReturn()
        process.settle()
        composeRule.waitForIdle()

        node(HistoryTags.playerChip(3)).assertIsDisplayed().assertIsSelected()
        composeRule.onNode(hasText("CRICKET") and isSelectable()).assertIsSelected()
        assertEquals(HistoryFilters(ArchivedMode.CRICKET, 3), repository.asked.last())
        assertEquals(asked + 1, repository.asked.size)
        assertTrue(process.viewModel.state.value.screen is Screen.History)
    }

    @Test
    fun `HS6 after a kill the stored game of the home screen is still offered when back is pressed`() {
        openHistory()
        process.killInBackgroundAndReturn()
        process.settle()
        composeRule.waitForIdle()
        back()
        node(HomeTags.HISTORY).assertIsDisplayed()
    }
}
