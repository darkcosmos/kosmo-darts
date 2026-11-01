package io.github.darkcosmos.kosmodarts.ui.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HistoryFilters
import io.github.darkcosmos.kosmodarts.history.HistoryList
import io.github.darkcosmos.kosmodarts.history.HistoryMatch
import io.github.darkcosmos.kosmodarts.history.HistoryProfile

/** The first visible item of the list and its offset in pixels: where the player stopped scrolling (HS6). */
data class HistoryScroll(val index: Int = 0, val offset: Int = 0)

/** What the last read gave (HS7): nothing yet, a failure, or the matches. */
sealed interface HistoryContent {
    data object Loading : HistoryContent

    data object Failed : HistoryContent

    data class Loaded(val matches: List<HistoryMatch>) : HistoryContent
}

/** Which of the five screens of History shows (HS4, HS7). */
enum class HistoryView {
    LOADING,
    FAILED,

    /** Nothing was ever archived (HS4). */
    EMPTY_ARCHIVE,

    /** Matches exist, none matches the filters (HS4). */
    NO_MATCH,
    ROWS,
}

/**
 * State of the History screen (HS2 to HS4, HS6, HS7). Pure: every change returns a new state, and a change of filters
 * or a retry asks for a new read by raising [loadId]; an answer to an older read is ignored.
 *
 * @property profiles the profiles of the player filter, kept from the last successful read.
 * @property loadId number of the read this state waits for or shows.
 */
data class HistoryState(
    val filters: HistoryFilters = HistoryFilters(),
    val scroll: HistoryScroll = HistoryScroll(),
    val content: HistoryContent = HistoryContent.Loading,
    val profiles: List<HistoryProfile> = emptyList(),
    val loadId: Int = FIRST_LOAD,
) {
    val view: HistoryView
        get() = when (content) {
            HistoryContent.Loading -> HistoryView.LOADING
            HistoryContent.Failed -> HistoryView.FAILED
            is HistoryContent.Loaded -> when {
                content.matches.isNotEmpty() -> HistoryView.ROWS
                filters.isActive -> HistoryView.NO_MATCH
                else -> HistoryView.EMPTY_ARCHIVE
            }
        }

    /** The mode filter (HS3); the list goes back to the top. */
    fun withMode(mode: ArchivedMode?): HistoryState =
        if (mode == filters.mode) this else reload(filters.copy(mode = mode), HistoryScroll())

    /** The player filter (HS3); [profileId] `null` is "All". The list goes back to the top. */
    fun withProfile(profileId: Long?): HistoryState =
        if (profileId == filters.profileId) this else reload(filters.copy(profileId = profileId), HistoryScroll())

    /** "Show all matches" (HS4): both filters back to "All". */
    fun showAll(): HistoryState = reload(HistoryFilters(), HistoryScroll())

    /** "Retry" after a failed read (HS7): same filters and scroll. */
    fun retry(): HistoryState = reload(filters, scroll)

    /** The answer to read [id]; an older answer changes nothing. A failure keeps the profiles already known. */
    fun loaded(id: Int, result: DbResult<HistoryList>): HistoryState = when {
        id != loadId -> this
        result is DbResult.Ok ->
            copy(content = HistoryContent.Loaded(result.value.matches), profiles = result.value.profiles)
        else -> copy(content = HistoryContent.Failed)
    }

    fun scrolled(index: Int, offset: Int): HistoryState =
        if (scroll.index == index && scroll.offset == offset) this else copy(scroll = HistoryScroll(index, offset))

    /** What survives a return to the app after the process was killed (HS6). */
    fun snapshot(): HistorySnapshot = HistorySnapshot(filters, scroll)

    private fun reload(newFilters: HistoryFilters, newScroll: HistoryScroll) =
        copy(filters = newFilters, scroll = newScroll, content = HistoryContent.Loading, loadId = loadId + 1)

    companion object {
        private const val FIRST_LOAD = 1

        /** The state of a History screen that comes back after a process kill: it reads again (HS6). */
        fun restored(snapshot: HistorySnapshot): HistoryState =
            HistoryState(filters = snapshot.filters, scroll = snapshot.scroll)
    }
}

/**
 * The filters and the scroll position, as the activity stores them for a return after the process was killed (HS6).
 * Numbers and a mode name only: no player name leaves the app's memory through the saved instance state.
 */
data class HistorySnapshot(val filters: HistoryFilters, val scroll: HistoryScroll) {

    fun encode(): String = listOf(
        filters.mode?.name ?: NONE,
        filters.profileId?.toString() ?: NONE,
        scroll.index.toString(),
        scroll.offset.toString(),
    ).joinToString(SEPARATOR)

    companion object {
        private const val NONE = "-"
        private const val SEPARATOR = ";"
        private const val FIELD_COUNT = 4

        /** The snapshot [text] stands for, or `null` when it is missing or not exactly what [encode] writes. */
        fun decode(text: String?): HistorySnapshot? {
            val fields = text?.split(SEPARATOR)?.takeIf { it.size == FIELD_COUNT } ?: return null
            val mode = fields[0].takeIf { it != NONE }?.let { name -> ArchivedMode.entries.find { it.name == name } }
            val profileId = fields[1].takeIf { it != NONE }?.toLongOrNull()
            val index = fields[2].toIntOrNull()
            val offset = fields[3].toIntOrNull()
            val isModeValid = fields[0] == NONE || mode != null
            val isProfileValid = fields[1] == NONE || profileId != null
            if (!isModeValid || !isProfileValid || index == null || offset == null || index < 0 || offset < 0) return null
            return HistorySnapshot(HistoryFilters(mode, profileId), HistoryScroll(index, offset))
        }
    }
}
