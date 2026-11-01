package io.github.darkcosmos.kosmodarts.capture.session

import io.github.darkcosmos.kosmodarts.capture.truth.TruthSession

/**
 * The two counters of the screen (rule CP7): non-voided darts of the running or last [session], and of every session
 * of this installation ([total]). Voided darts never count. Persisting [total] belongs to the storage layer.
 */
data class CaptureCounters(val session: Int = 0, val total: Int = 0) {
    /** Counters when a new session starts: the session counter restarts, the total is kept. */
    fun forNewSession(): CaptureCounters = copy(session = 0)

    /** Counters after [truth] changed: the total moves by the change of the session counter. */
    fun after(truth: TruthSession): CaptureCounters {
        val count = countDarts(truth)
        return CaptureCounters(session = count, total = total + count - session)
    }

    /** Counters after Delete all (rule CP8). */
    fun cleared(): CaptureCounters = CaptureCounters()

    companion object {
        /** Non-voided darts of [truth] (rule CP7). */
        fun countDarts(truth: TruthSession): Int = truth.volleys.sumOf { volley -> volley.darts.count { !it.voided } }
    }
}
