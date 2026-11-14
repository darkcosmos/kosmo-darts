package io.github.darkcosmos.kosmodarts.ui.players

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.FakePlayersRepository
import io.github.darkcosmos.kosmodarts.history.FakeProfileStatsRepository
import io.github.darkcosmos.kosmodarts.history.MergeResult
import io.github.darkcosmos.kosmodarts.history.RenameResult
import io.github.darkcosmos.kosmodarts.testing.savedGame
import io.github.darkcosmos.kosmodarts.ui.home.HomeTags
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileTags
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileTestData
import io.github.darkcosmos.kosmodarts.ui.resume.AppProcess
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * PM1 to PM11, PS1, PS7: Players opened from the home screen, then a Profile and its actions, through the activity,
 * over a fake database.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h2400dp-xxhdpi")
class PlayersFlowTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val kevin = playerSummary(1, "Kevin", matches = 4)
    private val lea = playerSummary(2, "Lea", matches = 2)
    private val repository = FakePlayersRepository(players = DbResult.Ok(listOf(kevin, lea)))
    private val stats = FakeProfileStatsRepository(
        mapOf(
            1L to ProfileTestData.data(listOf(ProfileTestData.x01Raw())),
            2L to ProfileTestData.data(listOf(ProfileTestData.x01Raw()), name = "Lea").copy(profileId = 2),
        ),
    )
    private val process = AppProcess(players = repository, profileStats = stats)

    @After
    fun tearDown() = process.close()

    private fun node(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag)

    /** The reads of a Profile are chained (the data, then its figures): the queue is drained until they are done. */
    private fun settle() {
        repeat(SETTLE_ROUNDS) {
            process.settle()
            composeRule.waitForIdle()
        }
    }

    private fun click(tag: String) {
        node(tag).performClick()
        settle()
    }

    /** Opens the Profile of [id] from the Players list, then unfolds its actions (P1 A). */
    private fun openManage(id: Long) {
        click(PlayersTags.card(id))
        click(ProfileTags.MANAGE)
    }

    private fun openPlayers() {
        process.launchFresh()
        click(HomeTags.PLAYERS)
    }

    private fun back() {
        process.pressBack()
        settle()
    }

    @Test
    fun `PM1 PM2 the Players button lists the profiles and back returns home`() {
        openPlayers()
        node(PlayersTags.card(1)).assertIsDisplayed()
        node(PlayersTags.card(2)).assertIsDisplayed()

        back()
        node(HomeTags.PLAYERS).assertIsDisplayed()
    }

    @Test
    fun `PM2 a failed read is shown, and Retry reads again and shows the profiles`() {
        repository.players = DbResult.Failed
        openPlayers()
        node(PlayersTags.FAILED).assertIsDisplayed()

        repository.players = DbResult.Ok(listOf(kevin))
        click(PlayersTags.RETRY)
        node(PlayersTags.card(1)).assertIsDisplayed()
    }

    @Test
    fun `PM3 a renamed profile is saved with the new name and the panel closes`() {
        repository.renameResult = DbResult.Ok(RenameResult.Renamed("Kev", "kev"))
        openPlayers()
        openManage(1L)
        click(PlayersTags.RENAME)
        node(PlayersTags.RENAME_FIELD).performTextReplacement("Kev")
        composeRule.waitForIdle()
        click(PlayersTags.RENAME_SAVE)

        assertEquals(listOf(1L to "Kev"), repository.renames)
        node(PlayersTags.RENAME_FIELD).assertDoesNotExist()
    }

    @Test
    fun `PM3 Q6 a name that belongs to Lea offers the merge into Lea, then the confirmation`() {
        repository.renameResult = DbResult.Ok(RenameResult.Clash(2))
        openPlayers()
        openManage(1L)
        click(PlayersTags.RENAME)
        node(PlayersTags.RENAME_FIELD).performTextReplacement("lea")
        composeRule.waitForIdle()
        click(PlayersTags.RENAME_SAVE)
        node(PlayersTags.MERGE_INTO_TAKEN).assertIsDisplayed()

        click(PlayersTags.MERGE_INTO_TAKEN)
        node(PlayersTags.CONFIRM_TEXT)
            .assertTextEquals(process.string(R.string.players_merge_confirm_text, "Kevin", "Lea"))
    }

    @Test
    fun `PM4 a merge picks the target, asks to confirm, then merges the source into it`() {
        openPlayers()
        openManage(1L)
        click(PlayersTags.MERGE)
        click(PlayersTags.mergeTarget(2))
        click(PlayersTags.CONFIRM)

        assertEquals(listOf(1L to 2L), repository.merges)
    }

    @Test
    fun `PM4 PM10 a merge refused for a shared match keeps the confirmation open with its message`() {
        repository.mergeResult = DbResult.Ok(MergeResult.SHARED_MATCH)
        openPlayers()
        openManage(1L)
        click(PlayersTags.MERGE)
        click(PlayersTags.mergeTarget(2))
        click(PlayersTags.CONFIRM)

        node(PlayersTags.MESSAGE).assertTextEquals(process.string(R.string.players_shared_match))
        node(PlayersTags.CONFIRM).assertIsDisplayed()
    }

    @Test
    fun `PM5 a reset asks to confirm, then resets the profile`() {
        openPlayers()
        openManage(2L)
        click(PlayersTags.RESET)
        assertEquals(emptyList<Pair<Long, Long>>(), repository.resets)
        click(PlayersTags.CONFIRM)

        assertEquals(listOf(2L), repository.resets.map { it.first })
    }

    @Test
    fun `PM6 a delete shows the number of matches erased, then deletes the profile`() {
        repository.deletionCountResult = DbResult.Ok(3)
        openPlayers()
        openManage(1L)
        click(PlayersTags.DELETE)
        node(PlayersTags.CONFIRM_TEXT).assertTextEquals(
            process.string(
                R.string.players_delete_confirm_text,
                "Kevin",
                process.app.resources.getQuantityString(R.plurals.players_delete_erased, 3, 3),
            ),
        )

        repository.players = DbResult.Ok(listOf(lea))
        click(PlayersTags.CONFIRM)
        assertEquals(listOf(1L), repository.deletes.map { it.first })
        node(PlayersTags.card(1)).assertDoesNotExist()
        node(PlayersTags.card(2)).assertIsDisplayed()
    }

    @Test
    fun `PM10 a database error is shown in the panel, and the action can be retried`() {
        repository.resetResult = DbResult.Failed
        openPlayers()
        openManage(1L)
        click(PlayersTags.RESET)
        click(PlayersTags.CONFIRM)
        node(PlayersTags.MESSAGE).assertTextEquals(process.string(R.string.players_database_error))

        repository.resetResult = DbResult.Ok(Unit)
        click(PlayersTags.CONFIRM)
        assertEquals(2, repository.resets.size)
        node(PlayersTags.MESSAGE).assertDoesNotExist()
    }

    @Test
    fun `PM8 a profile of the game in progress cannot be renamed, merged or deleted`() {
        process.writeGame(savedGame(X01Game(X01Config(listOf("Kevin", "Bob")), emptyList()), false))
        openPlayers()
        openManage(1L)
        click(PlayersTags.RENAME)

        node(PlayersTags.MESSAGE).assertTextEquals(process.string(R.string.players_in_progress, "Kevin"))
        node(PlayersTags.RENAME_FIELD).assertDoesNotExist()
        click(PlayersTags.DELETE)
        node(PlayersTags.CONFIRM_TEXT).assertDoesNotExist()
        assertEquals(emptyList<Pair<Long, String>>(), repository.renames)

        // Lea is not in the game, but merging Lea into Kevin is refused too (D12).
        back()
        openManage(2L)
        click(PlayersTags.MERGE)
        click(PlayersTags.mergeTarget(1))
        node(PlayersTags.MESSAGE).assertTextEquals(process.string(R.string.players_in_progress, "Kevin"))
    }

    @Test
    fun `PS1 PS7 a row opens the Profile with its actions folded, and Back returns to the list`() {
        openPlayers()
        click(PlayersTags.card(1))
        node(ProfileTags.NAME).assertTextEquals("Kevin")
        node(ProfileTags.MANAGE).assertIsDisplayed()
        node(PlayersTags.RENAME).assertDoesNotExist()

        back()
        node(PlayersTags.card(1)).assertIsDisplayed()
    }

    @Test
    fun `PM11 Manage player folds and unfolds the actions inside the Profile`() {
        openPlayers()
        openManage(1L)
        node(PlayersTags.DELETE).assertIsDisplayed()

        click(ProfileTags.MANAGE)
        node(PlayersTags.DELETE).assertDoesNotExist()
    }

    private companion object {
        const val SETTLE_ROUNDS = 3
    }
}
