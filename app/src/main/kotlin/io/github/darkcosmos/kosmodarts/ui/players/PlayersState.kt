package io.github.darkcosmos.kosmodarts.ui.players

import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary

/** What the last read gave (PM2, PM10): nothing yet, a failure, or the profiles. */
sealed interface PlayersContent {
    data object Loading : PlayersContent

    data object Failed : PlayersContent

    data class Loaded(val players: List<PlayerSummary>) : PlayersContent
}

/** Which of the four screens of Players shows (PM2, PM10). */
enum class PlayersView { LOADING, FAILED, EMPTY, LIST }

/**
 * State of the Players screen (PM2, PM10): the list of profiles and nothing else, since the actions live in the
 * Profile (PS7). Pure: every change returns a new state.
 *
 * @property loadId number of the read this state waits for or shows; an answer to an older read is ignored.
 */
data class PlayersState(
    val content: PlayersContent = PlayersContent.Loading,
    val loadId: Int = FIRST_LOAD,
) {
    val players: List<PlayerSummary>
        get() = (content as? PlayersContent.Loaded)?.players.orEmpty()

    val view: PlayersView
        get() = when (content) {
            PlayersContent.Loading -> PlayersView.LOADING
            PlayersContent.Failed -> PlayersView.FAILED
            is PlayersContent.Loaded -> if (content.players.isEmpty()) PlayersView.EMPTY else PlayersView.LIST
        }

    /** "Retry" after a failed read (PM10). */
    fun retry(): PlayersState =
        if (content == PlayersContent.Failed) copy(content = PlayersContent.Loading, loadId = loadId + 1) else this

    /** PS7: asks for a new read, after a Profile action changed what the list shows. */
    fun reload(): PlayersState = copy(loadId = loadId + 1)

    /** The answer to read [id]; an older answer changes nothing. */
    fun loaded(id: Int, result: DbResult<List<PlayerSummary>>): PlayersState = when {
        id != loadId -> this
        result is DbResult.Ok -> copy(content = PlayersContent.Loaded(result.value))
        else -> copy(content = PlayersContent.Failed)
    }

    private companion object {
        const val FIRST_LOAD = 1
    }
}
