package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.HeadToHeadData
import io.github.darkcosmos.kosmodarts.history.HeadToHeadPicker
import io.github.darkcosmos.kosmodarts.history.ProfileStatsData
import io.github.darkcosmos.kosmodarts.history.ProfileStatsRepository
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadResult
import io.github.darkcosmos.kosmodarts.ui.headtohead.HeadToHeadSelections
import io.github.darkcosmos.kosmodarts.ui.headtohead.result
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileFigures
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileQuery
import io.github.darkcosmos.kosmodarts.ui.profile.figures
import java.util.concurrent.Executor

/**
 * The reads of the Profile screen (ST7, ST10, HS7): everything runs on [readExecutor], never on the main thread nor on
 * the save queue. Without a [repository] the read answers [DbResult.Failed].
 */
class ProfileStatsPersistence(
    private val repository: ProfileStatsRepository?,
    private val readExecutor: Executor,
) {
    /** Reads the statistics input of the profile [profileId]. [onResult] runs on the read thread. */
    fun load(profileId: Long, onResult: (DbResult<ProfileStatsData>) -> Unit) {
        readExecutor.execute { onResult(repository?.load(profileId) ?: DbResult.Failed) }
    }

    /** HH1: reads the picker of the profile [profileId]. [onResult] runs on the read thread. */
    fun loadPicker(profileId: Long, onResult: (DbResult<HeadToHeadPicker>) -> Unit) {
        readExecutor.execute { onResult(repository?.loadPicker(profileId) ?: DbResult.Failed) }
    }

    /** HH2, ST10: reads the common matches of [a] and [b]. [onResult] runs on the read thread. */
    fun loadPair(a: Long, b: Long, onResult: (DbResult<HeadToHeadData>) -> Unit) {
        readExecutor.execute { onResult(repository?.loadPair(a, b) ?: DbResult.Failed) }
    }

    /** Computes the result of [data] for [selections]. [onResult] runs on the read thread. */
    fun computeHeadToHead(
        data: HeadToHeadData,
        selections: HeadToHeadSelections,
        now: Long,
        onResult: (HeadToHeadResult) -> Unit,
    ) {
        readExecutor.execute { onResult(data.result(selections, now)) }
    }

    /** Computes the figures of [data] for [query]. [onResult] runs on the read thread. */
    fun computeFigures(data: ProfileStatsData, query: ProfileQuery, onResult: (ProfileFigures) -> Unit) {
        readExecutor.execute { onResult(data.figures(query)) }
    }
}
