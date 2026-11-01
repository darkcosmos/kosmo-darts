package io.github.darkcosmos.kosmodarts.history

import android.database.SQLException

/**
 * Result of a database access (ADR 0012, "Failure contract", AR9): nothing above the history repositories sees a
 * database exception.
 */
sealed interface DbResult<out T> {
    /** The access succeeded with [value]. */
    data class Ok<out T>(val value: T) : DbResult<T>

    /** The database could not open, is corrupt, full, closed, or the access failed. The cause is not kept. */
    data object Failed : DbResult<Nothing>
}

/**
 * Runs [block] and wraps its value in [DbResult.Ok], or returns [DbResult.Failed] when Room or SQLite throws: an
 * [SQLException] (every `SQLiteException` included) or an [IllegalStateException] (a database that cannot open or is
 * closed). The cause is deliberately dropped: it is never logged, so no data reaches the logs (review S4).
 */
inline fun <T> dbResult(block: () -> T): DbResult<T> = try {
    DbResult.Ok(block())
} catch (ignored: SQLException) {
    DbResult.Failed
} catch (ignored: IllegalStateException) {
    DbResult.Failed
}
