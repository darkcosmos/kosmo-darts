package io.github.darkcosmos.kosmodarts.engine.journal

import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/** One entry of a leg journal, in entry order (DJ1). */
sealed interface JournalEvent {

    /** A dart entered at [enteredAt] (milliseconds since the epoch, from the injected [Clock]). */
    data class DartEntered(
        val dart: Dart,
        val enteredAt: Long,
        val source: DartSource = DartSource.MANUAL,
    ) : JournalEvent

    /** An effective undo: it voids the latest dart still in play (DJ2). */
    data object Undone : JournalEvent
}
