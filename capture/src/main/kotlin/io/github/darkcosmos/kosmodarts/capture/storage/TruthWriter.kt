package io.github.darkcosmos.kosmodarts.capture.storage

import io.github.darkcosmos.kosmodarts.capture.session.CaptureCounters
import io.github.darkcosmos.kosmodarts.capture.truth.TruthJson
import io.github.darkcosmos.kosmodarts.capture.truth.TruthSession
import java.io.IOException

/** Outcome of one [TruthWriter.write]; a failure is kept, never swallowed. */
data class TruthWriteResult(val truthFailure: IOException? = null, val totalFailure: IOException? = null) {
    val succeeded: Boolean get() = truthFailure == null && totalFailure == null
}

/**
 * Rewrites the truth file and the persisted total after every event (rules CP7 and CP9). Both writes are attempted
 * even when the first one fails, and each failure is reported in the result.
 */
class TruthWriter(private val storage: CaptureStorage, private val settings: CaptureSettings) {
    fun write(entry: CaptureEntry, truth: TruthSession, counters: CaptureCounters): TruthWriteResult {
        val truthFailure = attempt { storage.writeTruth(entry, TruthJson.encode(truth)) }
        val totalFailure = attempt { settings.saveTotal(counters.total) }
        return TruthWriteResult(truthFailure, totalFailure)
    }

    private fun attempt(action: () -> Unit): IOException? =
        try {
            action()
            null
        } catch (e: IOException) {
            e
        }
}
