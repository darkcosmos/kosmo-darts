package io.github.darkcosmos.kosmodarts.ui.players

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListError
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.MergeResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.history.RenameResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** PM2 to PM6, PM8, PM10, PM11: the state of the Players screen, with no database and no screen. */
class PlayersStateTest {
    private val kevin = summary(1, "Kevin")
    private val lea = summary(2, "Lea")
    private val ana = summary(3, "Ana")
    private val noGame = emptyList<String>()

    private fun summary(id: Long, name: String) = PlayerSummary(id, name, PlayerListRules.nameKey(name), 3, 5_000L)

    private fun loaded(vararg players: PlayerSummary): PlayersState =
        PlayersState().loaded(1, DbResult.Ok(players.toList()))

    private fun open(id: Long = 1): PlayersState = loaded(kevin, lea, ana).profileTapped(id)

    /** Plays the request the state raised: the state answers it with [answer]. */
    private fun PlayersState.answer(answer: PlayersAnswer): PlayersState = answered(checkNotNull(request), answer)

    @Test
    fun `PM2 a read gives the list, an empty list, or a failure that a retry reads again`() {
        assertEquals(PlayersView.LOADING, PlayersState().view)
        assertEquals(PlayersView.LIST, loaded(kevin).view)
        assertEquals(PlayersView.EMPTY, loaded().view)

        val failed = PlayersState().loaded(1, DbResult.Failed)
        assertEquals(PlayersView.FAILED, failed.view)
        val retried = failed.retry()
        assertEquals(PlayersView.LOADING, retried.view)
        assertEquals(2, retried.loadId)
        assertEquals(PlayersView.LIST, retried.loaded(2, DbResult.Ok(listOf(kevin))).view)
    }

    @Test
    fun `PM10 an answer to an older read is ignored, and a retry only exists after a failure`() {
        val state = loaded(kevin)
        assertSame(state, state.loaded(7, DbResult.Failed))
        assertSame(state, state.retry())
    }

    @Test
    fun `PM2 a tap opens a card, another tap on it closes it, another card replaces it`() {
        val state = open(1)
        assertEquals(kevin, state.expanded)
        assertEquals(lea, state.profileTapped(2).expanded)
        assertNull(state.profileTapped(1).expanded)
        assertSame(state, state.profileTapped(99))
    }

    @Test
    fun `PM3 an invalid name is refused in the panel, with the typed text kept`() {
        val state = open().renameStarted(noGame)
        assertEquals(PlayersPanel.Rename("Kevin"), state.panel)

        val saving = state.renameTextChanged("   ").renameSaved()
        assertEquals(PlayersRequest.Rename(kevin, "   "), saving.request)
        val invalid = RenameResult.Invalid(PlayerListError.BLANK_NAME)
        val refused = saving.answer(PlayersAnswer.Renamed(DbResult.Ok(invalid)))

        assertNull(refused.request)
        assertEquals(
            PlayersPanel.Rename("   ", RenameError.Invalid(PlayerListError.BLANK_NAME)),
            refused.panel,
        )
        assertEquals(PlayersPanel.Rename("Kev"), refused.renameTextChanged("Kev").panel)
    }

    @Test
    fun `PM3 a renamed profile closes the panel and reads the list again`() {
        val saving = open().renameStarted(noGame).renameTextChanged("kevin").renameSaved()
        val done = saving.answer(PlayersAnswer.Renamed(DbResult.Ok(RenameResult.Renamed("kevin", "kevin"))))

        assertNull(done.panel)
        assertNull(done.request)
        assertEquals(2, done.loadId)
        assertEquals(kevin, done.expanded)
    }

    @Test
    fun `PM3 Q6 a name taken by another profile offers the merge with the renamed one as source`() {
        val saving = open().renameStarted(noGame).renameTextChanged("lea").renameSaved()
        val clash = saving.answer(PlayersAnswer.Renamed(DbResult.Ok(RenameResult.Clash(lea.id))))
        assertEquals(PlayersPanel.Rename("lea", RenameError.Taken(lea)), clash.panel)

        val confirm = clash.mergeIntoTakenTapped(noGame)
        assertEquals(PlayersPanel.MergeConfirm(lea), confirm.panel)
        assertEquals(PlayersRequest.Merge(kevin, lea), confirm.confirmed(AT).request)
    }

    @Test
    fun `PM3 a rename of a profile that no longer exists closes everything and reads again`() {
        val saving = open().renameStarted(noGame).renameSaved()
        val gone = saving.answer(PlayersAnswer.Renamed(DbResult.Ok(RenameResult.Unknown)))
        assertNull(gone.panel)
        assertNull(gone.expandedId)
        assertEquals(2, gone.loadId)
    }

    @Test
    fun `PM4 a merge picks a target other than the source, then asks to confirm`() {
        val pick = open().mergeStarted(noGame)
        assertEquals(PlayersPanel.MergePick, pick.panel)
        assertSame(pick, pick.mergeTargetPicked(kevin.id, noGame))
        assertSame(pick, pick.mergeTargetPicked(99, noGame))
        assertEquals(PlayersPanel.MergeConfirm(ana), pick.mergeTargetPicked(ana.id, noGame).panel)
    }

    @Test
    fun `PM4 a merge refused for a shared match keeps the confirmation open with the message`() {
        val merging = open().mergeStarted(noGame).mergeTargetPicked(lea.id, noGame).confirmed(AT)
        assertEquals(PlayersRequest.Merge(kevin, lea), merging.request)

        val refused = merging.answer(PlayersAnswer.Merged(DbResult.Ok(MergeResult.SHARED_MATCH)))
        assertEquals(PlayersMessage.SharedMatch, refused.message)
        assertEquals(PlayersPanel.MergeConfirm(lea), refused.panel)
        assertNull(refused.request)
        assertEquals(1, refused.loadId)
    }

    @Test
    fun `PM4 a merged source collapses, with no panel left open, and the list is read again`() {
        val merging = open().mergeStarted(noGame).mergeTargetPicked(lea.id, noGame).confirmed(AT)
        val done = merging.answer(PlayersAnswer.Merged(DbResult.Ok(MergeResult.MERGED)))
        assertNull(done.panel)
        assertNull(done.expandedId)
        assertEquals(2, done.loadId)
    }

    @Test
    fun `PM5 a reset asks to confirm, then runs with the date of the tap and changes nothing else`() {
        val confirm = open().resetStarted()
        assertEquals(PlayersPanel.ResetConfirm, confirm.panel)
        val resetting = confirm.confirmed(AT)
        assertEquals(PlayersRequest.Reset(kevin, AT), resetting.request)

        val done = resetting.answer(PlayersAnswer.ResetDone(DbResult.Ok(Unit)))
        assertNull(done.panel)
        assertEquals(kevin, done.expanded)
        assertEquals(1, done.loadId)
    }

    @Test
    fun `PM6 a delete reads the count, shows it in the confirmation, then runs with the date of the tap`() {
        val counting = open().deleteStarted(noGame)
        assertEquals(PlayersRequest.CountDeletion(kevin), counting.request)
        assertNull(counting.panel)

        val confirm = counting.answer(PlayersAnswer.Counted(DbResult.Ok(4)))
        assertEquals(PlayersPanel.DeleteConfirm(4), confirm.panel)
        val deleting = confirm.confirmed(AT)
        assertEquals(PlayersRequest.Delete(kevin, AT), deleting.request)

        val done = deleting.answer(PlayersAnswer.Deleted(DbResult.Ok(4)))
        assertNull(done.panel)
        assertNull(done.expandedId)
        assertEquals(2, done.loadId)
    }

    @Test
    fun `PM6 the count of the deletion can fail, and Cancel closes the confirmation`() {
        val failed = open().deleteStarted(noGame).answer(PlayersAnswer.Counted(DbResult.Failed))
        assertNull(failed.panel)
        assertEquals(PlayersMessage.DatabaseError, failed.message)

        val confirm = open().deleteStarted(noGame).answer(PlayersAnswer.Counted(DbResult.Ok(0)))
        assertEquals(PlayersPanel.DeleteConfirm(0), confirm.panel)
        val cancelled = confirm.cancelled()
        assertNull(cancelled.panel)
        assertEquals(kevin, cancelled.expanded)
    }

    @Test
    fun `PM8 a profile of the game in progress cannot be renamed, merged or deleted, and the message names it`() {
        val inGame = listOf("kevin", "Bob")
        val refused = PlayersMessage.InProgress("Kevin")
        assertEquals(refused, open().renameStarted(inGame).message)
        assertNull(open().renameStarted(inGame).panel)
        assertEquals(refused, open().mergeStarted(inGame).message)
        assertNull(open().mergeStarted(inGame).panel)
        assertEquals(refused, open().deleteStarted(inGame).message)
        assertNull(open().deleteStarted(inGame).request)
    }

    @Test
    fun `PM8 a profile cannot be the target of a merge while it is in the game in progress`() {
        val inGame = listOf("Lea")
        val pick = open().mergeStarted(inGame)
        assertEquals(PlayersMessage.InProgress("Lea"), pick.mergeTargetPicked(lea.id, inGame).message)
        assertEquals(PlayersPanel.MergePick, pick.mergeTargetPicked(lea.id, inGame).panel)

        val taken = open().renameStarted(inGame).renameTextChanged("lea").renameSaved()
            .answer(PlayersAnswer.Renamed(DbResult.Ok(RenameResult.Clash(lea.id))))
        assertEquals(PlayersMessage.InProgress("Lea"), taken.mergeIntoTakenTapped(inGame).message)
    }

    @Test
    fun `PM8 a reset stays allowed for a profile of the game in progress`() {
        assertEquals(PlayersPanel.ResetConfirm, open().resetStarted().panel)
    }

    @Test
    fun `PM10 a database error keeps the panel open with the message, and the action can be retried`() {
        val saving = open().renameStarted(noGame).renameTextChanged("Kev").renameSaved()
        val failed = saving.answer(PlayersAnswer.Renamed(DbResult.Failed))
        assertEquals(PlayersPanel.Rename("Kev"), failed.panel)
        assertEquals(PlayersMessage.DatabaseError, failed.message)
        assertNull(failed.request)
        assertEquals(1, failed.loadId)

        assertEquals(PlayersRequest.Rename(kevin, "Kev"), failed.renameSaved().request)
        assertNull(failed.renameSaved().message)
    }

    @Test
    fun `PM10 a failed merge, reset and delete keep their confirmation open with the message`() {
        val merge = open().mergeStarted(noGame).mergeTargetPicked(lea.id, noGame).confirmed(AT)
            .answer(PlayersAnswer.Merged(DbResult.Failed))
        assertEquals(PlayersPanel.MergeConfirm(lea), merge.panel)
        assertEquals(PlayersMessage.DatabaseError, merge.message)

        val reset = open().resetStarted().confirmed(AT).answer(PlayersAnswer.ResetDone(DbResult.Failed))
        assertEquals(PlayersPanel.ResetConfirm, reset.panel)
        assertEquals(PlayersMessage.DatabaseError, reset.message)

        val delete = open().deleteStarted(noGame).answer(PlayersAnswer.Counted(DbResult.Ok(2))).confirmed(AT)
            .answer(PlayersAnswer.Deleted(DbResult.Failed))
        assertEquals(PlayersPanel.DeleteConfirm(2), delete.panel)
        assertEquals(PlayersMessage.DatabaseError, delete.message)
    }

    @Test
    fun `PM10 while an action waits for the database, every other action is ignored`() {
        val busy = open().resetStarted().confirmed(AT)
        assertSame(busy, busy.profileTapped(2))
        assertSame(busy, busy.renameStarted(noGame))
        assertSame(busy, busy.cancelled())
        assertSame(busy, busy.confirmed(AT + 1))
    }

    @Test
    fun `PM10 an answer to a request that is not the pending one changes nothing`() {
        val busy = open().resetStarted().confirmed(AT)
        assertSame(busy, busy.answered(PlayersRequest.Reset(kevin, AT + 1), PlayersAnswer.ResetDone(DbResult.Ok(Unit))))
    }

    @Test
    fun `after a read, an expanded profile that no longer exists collapses with no panel left open`() {
        val merging = open().mergeStarted(noGame)
        val reloaded = merging.loaded(1, DbResult.Ok(listOf(lea, ana)))
        assertNull(reloaded.expandedId)
        assertNull(reloaded.panel)
        assertNull(reloaded.message)
    }

    @Test
    fun `after a read, a merge confirmation whose target no longer exists closes`() {
        val confirm = open().mergeStarted(noGame).mergeTargetPicked(lea.id, noGame)
        val reloaded = confirm.loaded(1, DbResult.Ok(listOf(kevin, ana)))
        assertNull(reloaded.panel)
        assertEquals(kevin, reloaded.expanded)
    }

    @Test
    fun `after a read, a profile still listed keeps its open panel`() {
        val rename = open().renameStarted(noGame)
        assertEquals(rename.panel, rename.loaded(1, DbResult.Ok(listOf(kevin, ana))).panel)
    }

    private companion object {
        const val AT = 7_000L
    }
}
