package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.PlayerSummary
import io.github.darkcosmos.kosmodarts.history.PlayersRepository
import java.util.concurrent.Executor

/**
 * The database access of the Players screen (PM2 to PM6, PM10): reads on [readExecutor], writes on [queue], the
 * persistence queue. Without a [repository] every call answers [DbResult.Failed].
 */
class PlayersPersistence(
    private val repository: PlayersRepository?,
    private val queue: Executor,
    private val readExecutor: Executor,
) {
    /** PM2: reads the profiles of the screen. [onResult] runs on the read thread. */
    fun load(onResult: (DbResult<List<PlayerSummary>>) -> Unit) {
        readExecutor.execute { onResult(repository?.list() ?: DbResult.Failed) }
    }

    /** PM6: reads how many matches deleting the profile [id] erases; answers like [load]. */
    fun countDeletion(id: Long, onResult: (DbResult<Int>) -> Unit) {
        readExecutor.execute { onResult(repository?.deletionCount(id) ?: DbResult.Failed) }
    }

    /**
     * Runs one write on the queue, after every write still pending, and hands its result to [onResult] on the queue
     * thread.
     */
    fun <T> change(change: (PlayersRepository) -> DbResult<T>, onResult: (DbResult<T>) -> Unit) {
        queue.execute { onResult(repository?.let(change) ?: DbResult.Failed) }
    }
}
