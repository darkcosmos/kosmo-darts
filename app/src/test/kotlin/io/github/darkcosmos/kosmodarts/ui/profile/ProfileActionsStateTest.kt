package io.github.darkcosmos.kosmodarts.ui.profile

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

/** PM3 to PM6, PS7, PM8, PM10, PM11: the actions of the Profile screen, with no database and no screen. */
class ProfileActionsStateTest {
    private val kevin = summary(1, "Kevin")
    private val lea = summary(2, "Lea")
    private val ana = summary(3, "Ana")
    private val noGame = emptyList<String>()

    private fun summary(id: Long, name: String) = PlayerSummary(id, name, PlayerListRules.nameKey(name), 3, 5_000L)

    private fun open(id: Long = 1): ProfileState =
        ProfileState().rosterLoaded(1, id, DbResult.Ok(listOf(kevin, lea, ana)))

    private val ProfileState.self: PlayerSummary?
        get() = (roster as? ProfileRoster.Loaded)?.self

    private fun ProfileState.answer(answer: ProfileAnswer): ProfileState = answered(checkNotNull(request), answer)

    @Test
    fun `PM3 an invalid name is refused in the panel, with the typed text kept`() {
        val state = open().renameStarted(noGame)
        assertEquals(ProfilePanel.Rename("Kevin"), state.panel)

        val saving = state.renameTextChanged("   ").renameSaved()
        assertEquals(ProfileRequest.Rename(kevin, "   "), saving.request)
        val invalid = RenameResult.Invalid(PlayerListError.BLANK_NAME)
        val refused = saving.answer(ProfileAnswer.Renamed(DbResult.Ok(invalid)))

        assertNull(refused.request)
        assertEquals(
            ProfilePanel.Rename("   ", RenameError.Invalid(PlayerListError.BLANK_NAME)),
            refused.panel,
        )
        assertEquals(ProfilePanel.Rename("Kev"), refused.renameTextChanged("Kev").panel)
    }

    @Test
    fun `PM3 a renamed profile closes the panel and reads the list again`() {
        val saving = open().renameStarted(noGame).renameTextChanged("kevin").renameSaved()
        val done = saving.answer(ProfileAnswer.Renamed(DbResult.Ok(RenameResult.Renamed("kevin", "kevin"))))

        assertNull(done.panel)
        assertNull(done.request)
        assertEquals(2, done.loadId)
        assertEquals(kevin, done.self)
    }

    @Test
    fun `PM3 Q6 a name taken by another profile offers the merge with the renamed one as source`() {
        val saving = open().renameStarted(noGame).renameTextChanged("lea").renameSaved()
        val clash = saving.answer(ProfileAnswer.Renamed(DbResult.Ok(RenameResult.Clash(lea.id))))
        assertEquals(ProfilePanel.Rename("lea", RenameError.Taken(lea)), clash.panel)

        val confirm = clash.mergeIntoTakenTapped(noGame)
        assertEquals(ProfilePanel.MergeConfirm(lea), confirm.panel)
        assertEquals(ProfileRequest.Merge(kevin, lea), confirm.confirmed(AT).request)
    }

    @Test
    fun `PM3 a rename of a profile that no longer exists closes everything and reads again`() {
        val saving = open().renameStarted(noGame).renameSaved()
        val gone = saving.answer(ProfileAnswer.Renamed(DbResult.Ok(RenameResult.Unknown)))
        assertNull(gone.panel)
        assertEquals(2, gone.loadId)
    }

    @Test
    fun `PM4 a merge picks a target other than the source, then asks to confirm`() {
        val pick = open().mergeStarted(noGame)
        assertEquals(ProfilePanel.MergePick, pick.panel)
        assertSame(pick, pick.mergeTargetPicked(kevin.id, noGame))
        assertSame(pick, pick.mergeTargetPicked(99, noGame))
        assertEquals(ProfilePanel.MergeConfirm(ana), pick.mergeTargetPicked(ana.id, noGame).panel)
    }

    @Test
    fun `PM4 a merge refused for a shared match keeps the confirmation open with the message`() {
        val merging = open().mergeStarted(noGame).mergeTargetPicked(lea.id, noGame).confirmed(AT)
        assertEquals(ProfileRequest.Merge(kevin, lea), merging.request)

        val refused = merging.answer(ProfileAnswer.Merged(DbResult.Ok(MergeResult.SHARED_MATCH)))
        assertEquals(ProfileMessage.SharedMatch, refused.message)
        assertEquals(ProfilePanel.MergeConfirm(lea), refused.panel)
        assertNull(refused.request)
        assertEquals(1, refused.loadId)
    }

    @Test
    fun `PM4 a merged source collapses, with no panel left open, and the list is read again`() {
        val merging = open().mergeStarted(noGame).mergeTargetPicked(lea.id, noGame).confirmed(AT)
        val done = merging.answer(ProfileAnswer.Merged(DbResult.Ok(MergeResult.MERGED)))
        assertNull(done.panel)
        assertEquals(2, done.loadId)
    }

    @Test
    fun `PM5 a reset asks to confirm, then runs with the date of the tap and changes nothing else`() {
        val confirm = open().resetStarted()
        assertEquals(ProfilePanel.ResetConfirm, confirm.panel)
        val resetting = confirm.confirmed(AT)
        assertEquals(ProfileRequest.Reset(kevin, AT), resetting.request)

        val done = resetting.answer(ProfileAnswer.ResetDone(DbResult.Ok(Unit)))
        assertNull(done.panel)
        assertEquals(kevin, done.self)
        assertEquals(2, done.loadId)
    }

    @Test
    fun `PM6 a delete reads the count, shows it in the confirmation, then runs with the date of the tap`() {
        val counting = open().deleteStarted(noGame)
        assertEquals(ProfileRequest.CountDeletion(kevin), counting.request)
        assertNull(counting.panel)

        val confirm = counting.answer(ProfileAnswer.Counted(DbResult.Ok(4)))
        assertEquals(ProfilePanel.DeleteConfirm(4), confirm.panel)
        val deleting = confirm.confirmed(AT)
        assertEquals(ProfileRequest.Delete(kevin, AT), deleting.request)

        val done = deleting.answer(ProfileAnswer.Deleted(DbResult.Ok(4)))
        assertNull(done.panel)
        assertEquals(2, done.loadId)
    }

    @Test
    fun `PM6 the count of the deletion can fail, and Cancel closes the confirmation`() {
        val failed = open().deleteStarted(noGame).answer(ProfileAnswer.Counted(DbResult.Failed))
        assertNull(failed.panel)
        assertEquals(ProfileMessage.DatabaseError, failed.message)

        val confirm = open().deleteStarted(noGame).answer(ProfileAnswer.Counted(DbResult.Ok(0)))
        assertEquals(ProfilePanel.DeleteConfirm(0), confirm.panel)
        val cancelled = confirm.cancelled()
        assertNull(cancelled.panel)
        assertEquals(kevin, cancelled.self)
    }

    @Test
    fun `PM8 a profile of the game in progress cannot be renamed, merged or deleted, and the message names it`() {
        val inGame = listOf("kevin", "Bob")
        val refused = ProfileMessage.InProgress("Kevin")
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
        assertEquals(ProfileMessage.InProgress("Lea"), pick.mergeTargetPicked(lea.id, inGame).message)
        assertEquals(ProfilePanel.MergePick, pick.mergeTargetPicked(lea.id, inGame).panel)

        val taken = open().renameStarted(inGame).renameTextChanged("lea").renameSaved()
            .answer(ProfileAnswer.Renamed(DbResult.Ok(RenameResult.Clash(lea.id))))
        assertEquals(ProfileMessage.InProgress("Lea"), taken.mergeIntoTakenTapped(inGame).message)
    }

    @Test
    fun `PM8 a reset stays allowed for a profile of the game in progress`() {
        assertEquals(ProfilePanel.ResetConfirm, open().resetStarted().panel)
    }

    @Test
    fun `PM10 a database error keeps the panel open with the message, and the action can be retried`() {
        val saving = open().renameStarted(noGame).renameTextChanged("Kev").renameSaved()
        val failed = saving.answer(ProfileAnswer.Renamed(DbResult.Failed))
        assertEquals(ProfilePanel.Rename("Kev"), failed.panel)
        assertEquals(ProfileMessage.DatabaseError, failed.message)
        assertNull(failed.request)
        assertEquals(1, failed.loadId)

        assertEquals(ProfileRequest.Rename(kevin, "Kev"), failed.renameSaved().request)
        assertNull(failed.renameSaved().message)
    }

    @Test
    fun `PM10 a failed merge, reset and delete keep their confirmation open with the message`() {
        val merge = open().mergeStarted(noGame).mergeTargetPicked(lea.id, noGame).confirmed(AT)
            .answer(ProfileAnswer.Merged(DbResult.Failed))
        assertEquals(ProfilePanel.MergeConfirm(lea), merge.panel)
        assertEquals(ProfileMessage.DatabaseError, merge.message)

        val reset = open().resetStarted().confirmed(AT).answer(ProfileAnswer.ResetDone(DbResult.Failed))
        assertEquals(ProfilePanel.ResetConfirm, reset.panel)
        assertEquals(ProfileMessage.DatabaseError, reset.message)

        val delete = open().deleteStarted(noGame).answer(ProfileAnswer.Counted(DbResult.Ok(2))).confirmed(AT)
            .answer(ProfileAnswer.Deleted(DbResult.Failed))
        assertEquals(ProfilePanel.DeleteConfirm(2), delete.panel)
        assertEquals(ProfileMessage.DatabaseError, delete.message)
    }

    @Test
    fun `PM10 while an action waits for the database, every other action is ignored`() {
        val busy = open().resetStarted().confirmed(AT)
        assertSame(busy, busy.manageToggled())
        assertSame(busy, busy.renameStarted(noGame))
        assertSame(busy, busy.cancelled())
        assertSame(busy, busy.confirmed(AT + 1))
    }

    @Test
    fun `PM10 an answer to a request that is not the pending one changes nothing`() {
        val busy = open().resetStarted().confirmed(AT)
        assertSame(busy, busy.answered(ProfileRequest.Reset(kevin, AT + 1), ProfileAnswer.ResetDone(DbResult.Ok(Unit))))
    }

    @Test
    fun `PS7 after a read, a profile that is no longer listed closes the panel and its message`() {
        val merging = open().mergeStarted(noGame)
        val reloaded = merging.rosterLoaded(1, 1, DbResult.Ok(listOf(lea, ana)))
        assertNull(reloaded.panel)
        assertNull(reloaded.message)
    }

    @Test
    fun `after a read, a merge confirmation whose target no longer exists closes`() {
        val confirm = open().mergeStarted(noGame).mergeTargetPicked(lea.id, noGame)
        val reloaded = confirm.rosterLoaded(1, 1, DbResult.Ok(listOf(kevin, ana)))
        assertNull(reloaded.panel)
        assertEquals(kevin, reloaded.self)
    }

    @Test
    fun `after a read, a profile still listed keeps its open panel`() {
        val rename = open().renameStarted(noGame)
        assertEquals(rename.panel, rename.rosterLoaded(1, 1, DbResult.Ok(listOf(kevin, ana))).panel)
    }

    private companion object {
        const val AT = 7_000L
    }

    @Test
    fun `PM4 the merge targets are every profile but the shown one, none before the list is read`() {
        assertEquals(listOf(lea, ana), open(1).mergeTargets)
        assertEquals(listOf(kevin, ana), open(2).mergeTargets)
        assertEquals(emptyList<PlayerSummary>(), ProfileState().mergeTargets)
        val alone = ProfileState().rosterLoaded(1, 1, DbResult.Ok(listOf(kevin)))
        assertEquals(emptyList<PlayerSummary>(), alone.mergeTargets)
    }
}
