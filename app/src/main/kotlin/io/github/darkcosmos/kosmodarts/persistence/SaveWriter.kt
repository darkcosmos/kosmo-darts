package io.github.darkcosmos.kosmodarts.persistence

import java.io.IOException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/**
 * Writes one [store] in the background, latest content first (rule R12). Requests come from the main thread; the
 * [executor] runs one task at a time, in order, and is shared by every writer and by loading (ADR 0002, decision 1).
 *
 * Only the latest request matters: a request that arrives while another one is still pending replaces it, so the
 * file always ends with the content of the latest action. A failed write keeps the previous file and is not retried:
 * the next action writes the latest content anyway.
 */
class SaveWriter(private val store: ByteStore, private val executor: Executor) {

    private sealed interface Request {
        class Write(val bytes: ByteArray) : Request

        data object Delete : Request
    }

    private val pending = AtomicReference<Request?>(null)

    fun write(bytes: ByteArray) = submit(Request.Write(bytes))

    fun delete() = submit(Request.Delete)

    private fun submit(request: Request) {
        // Schedule a drain only if none is pending: the pending drain will pick up the latest request.
        if (pending.getAndSet(request) == null) executor.execute(::drain)
    }

    private fun drain() {
        when (val request = pending.getAndSet(null)) {
            is Request.Write -> try {
                store.write(request.bytes)
            } catch (ignored: IOException) {
                // Rule R12: the previous content is kept, and the next action writes again. Nothing is logged: the
                // content holds player names.
            }
            Request.Delete -> store.delete()
            null -> Unit
        }
    }
}
