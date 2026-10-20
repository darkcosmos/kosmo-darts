package io.github.darkcosmos.kosmodarts.audio

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** [Scheduler] on the main thread. */
class HandlerScheduler : Scheduler {
    private val handler = Handler(Looper.getMainLooper())

    override fun now(): Long = SystemClock.uptimeMillis()

    override fun post(delayMillis: Long, action: () -> Unit): Cancellable {
        val runnable = Runnable(action)
        handler.postDelayed(runnable, delayMillis)
        return Cancellable { handler.removeCallbacks(runnable) }
    }
}
