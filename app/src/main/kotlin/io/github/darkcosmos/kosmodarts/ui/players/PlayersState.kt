package io.github.darkcosmos.kosmodarts.ui.players

import io.github.darkcosmos.kosmodarts.engine.player.PlayerListError
import io.github.darkcosmos.kosmodarts.engine.profile.ProfileRules
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.MergeResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.history.RenameResult

/** What the last read gave (PM2, PM10): nothing yet, a failure, or the profiles. */
sealed interface PlayersContent {
    data object Loading : PlayersContent

    data object Failed : PlayersContent

    data class Loaded(val players: List<PlayerSummary>) : PlayersContent
}

/** Which of the four screens of Players shows (PM2, PM10). */
enum class PlayersView { LOADING, FAILED, EMPTY, LIST }

/** What is wrong with the name typed in the rename panel (PM3). */
sealed interface RenameError {
    /** The name is not valid. */
    data class Invalid(val violation: PlayerListError) : RenameError

    /** The name key belongs to [target]: the screen offers to merge the renamed profile into it (Q6). */
    data class Taken(val target: PlayerSummary) : RenameError
}

/** The inline panel open under the expanded profile (PM11): never a dialog. */
sealed interface PlayersPanel {
    data class Rename(val text: String, val error: RenameError? = null) : PlayersPanel

    /** "Merge Kevin into:" with the other profiles as rows. */
    data object MergePick : PlayersPanel

    data class MergeConfirm(val target: PlayerSummary) : PlayersPanel

    data object ResetConfirm : PlayersPanel

    /** [count] is the number of matches the deletion erases (PM6). */
    data class DeleteConfirm(val count: Int) : PlayersPanel
}

/** The short message shown in the panel or under the profile (PM4, PM8, PM10, PM11). */
sealed interface PlayersMessage {
    /** PM8: [playerName] is in the game in progress; finish or abandon it first. */
    data class InProgress(val playerName: String) : PlayersMessage

    /** PM4: both profiles played a same match. */
    data object SharedMatch : PlayersMessage

    /** PM10: a database error; everything is unchanged and the action can be retried. */
    data object DatabaseError : PlayersMessage
}

/** The action waiting for the database. At most one at a time; the app queues it as an effect. */
sealed interface PlayersRequest {
    data class Rename(val profile: PlayerSummary, val name: String) : PlayersRequest

    data class Merge(val source: PlayerSummary, val target: PlayerSummary) : PlayersRequest

    /** [at] is the reset date (PM5). */
    data class Reset(val profile: PlayerSummary, val at: Long) : PlayersRequest

    /** Reads how many matches the deletion would erase, for the confirmation (PM6). */
    data class CountDeletion(val profile: PlayerSummary) : PlayersRequest

    /** [at] creates the anonymous profile when absent (PM7). */
    data class Delete(val profile: PlayerSummary, val at: Long) : PlayersRequest
}

/** The answer of the database to a [PlayersRequest]; [DbResult.Failed] is a database error (PM10). */
sealed interface PlayersAnswer {
    val result: DbResult<Any>

    data class Renamed(override val result: DbResult<RenameResult>) : PlayersAnswer

    data class Merged(override val result: DbResult<MergeResult>) : PlayersAnswer

    data class ResetDone(override val result: DbResult<Unit>) : PlayersAnswer

    data class Counted(override val result: DbResult<Int>) : PlayersAnswer

    /** The result is the number of matches erased, counted in the transaction. */
    data class Deleted(override val result: DbResult<Int>) : PlayersAnswer
}

/**
 * State of the Players screen (PM2 to PM11). Pure: every change returns a new state. A change that needs the database
 * sets [request]; the answer comes back through [answered]. The state knows nothing of the saved game: the user actions
 * that PM8 can refuse get the names of its players as `savedNames`.
 *
 * @property expandedId the profile whose card is open (one at a time).
 * @property loadId number of the read this state waits for or shows; an answer to an older read is ignored.
 */
data class PlayersState(
    val content: PlayersContent = PlayersContent.Loading,
    val loadId: Int = FIRST_LOAD,
    val expandedId: Long? = null,
    val panel: PlayersPanel? = null,
    val message: PlayersMessage? = null,
    val request: PlayersRequest? = null,
) {
    val players: List<PlayerSummary>
        get() = (content as? PlayersContent.Loaded)?.players.orEmpty()

    /** The profile whose card is open, or `null`. */
    val expanded: PlayerSummary?
        get() = players.find { it.id == expandedId }

    /** Whether an action waits for the database: every user action is ignored meanwhile. */
    val isBusy: Boolean
        get() = request != null

    val view: PlayersView
        get() = when (content) {
            PlayersContent.Loading -> PlayersView.LOADING
            PlayersContent.Failed -> PlayersView.FAILED
            is PlayersContent.Loaded -> if (content.players.isEmpty()) PlayersView.EMPTY else PlayersView.LIST
        }

    /** "Retry" after a failed read (PM10). */
    fun retry(): PlayersState =
        if (content == PlayersContent.Failed) copy(content = PlayersContent.Loading, loadId = loadId + 1) else this

    /**
     * The answer to read [id]; an older answer changes nothing. An expanded profile that is no longer listed (merged
     * or deleted) collapses, and so does a merge confirmation whose target is gone.
     */
    fun loaded(id: Int, result: DbResult<List<PlayerSummary>>): PlayersState = when {
        id != loadId -> this
        result is DbResult.Ok -> copy(content = PlayersContent.Loaded(result.value)).withoutVanishedProfiles()
        else -> copy(content = PlayersContent.Failed)
    }

    private fun withoutVanishedProfiles(): PlayersState {
        val ids = players.map { it.id }.toSet()
        val target = (panel as? PlayersPanel.MergeConfirm)?.target
        return when {
            expandedId != null && expandedId !in ids -> copy(expandedId = null, panel = null, message = null)
            target != null && target.id !in ids -> copy(panel = null, message = null)
            else -> this
        }
    }

    /** A tap on the card of the profile [id]: opens it, or closes it when it was open. */
    fun profileTapped(id: Long): PlayersState = when {
        isBusy || players.none { it.id == id } -> this
        id == expandedId -> copy(expandedId = null, panel = null, message = null)
        else -> copy(expandedId = id, panel = null, message = null)
    }

    /** "Cancel" of any panel, or a tap on the open card: closes the panel, with its message. */
    fun cancelled(): PlayersState = if (isBusy) this else copy(panel = null, message = null)

    private companion object {
        const val FIRST_LOAD = 1
    }
}

internal fun PlayersState.reload(): PlayersState = copy(loadId = loadId + 1)

/** Runs [action] on the expanded profile; nothing happens while a request is pending or when no card is open. */
internal inline fun PlayersState.withExpanded(action: (PlayerSummary) -> PlayersState): PlayersState {
    val profile = expanded
    return if (isBusy || profile == null) this else action(profile)
}

/** PM8: the first of [profiles] that the players of the game in progress ([savedNames]) lock, or `null`. */
internal fun lockedBy(savedNames: List<String>, vararg profiles: PlayerSummary): PlayerSummary? =
    profiles.firstOrNull { ProfileRules.isLocked(setOf(it.nameKey), savedNames) }

internal fun PlayersState.refused(locked: PlayerSummary): PlayersState =
    copy(message = PlayersMessage.InProgress(locked.name))
