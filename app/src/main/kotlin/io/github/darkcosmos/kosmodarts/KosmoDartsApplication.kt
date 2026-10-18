package io.github.darkcosmos.kosmodarts

import android.app.Application
import androidx.annotation.VisibleForTesting
import io.github.darkcosmos.kosmodarts.persistence.AtomicFileStore
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import java.io.File
import java.util.concurrent.Executors

/** Holds the process-wide [persistence], which outlives activities and ViewModels (ADR 0002, decision 1). */
class KosmoDartsApplication : Application() {

    private var current: Persistence? = null

    /** Created on first use, on the main thread. */
    val persistence: Persistence
        get() = current ?: createPersistence().also { current = it }

    /** Lets a test use its own [Persistence] (for example one that counts reads and writes) before any activity. */
    @VisibleForTesting
    internal fun replacePersistence(persistence: Persistence) {
        current = persistence
    }

    private fun createPersistence(): Persistence {
        val saveThread = Executors.newSingleThreadExecutor { task ->
            Thread(task, SAVE_THREAD_NAME).apply { isDaemon = true }
        }
        return Persistence(
            gameStore = AtomicFileStore(File(filesDir, GAME_FILE)),
            setupStore = AtomicFileStore(File(filesDir, SETUP_FILE)),
            executor = saveThread,
        )
    }

    internal companion object {
        const val GAME_FILE = "saved_game"
        const val SETUP_FILE = "last_setup"
        private const val SAVE_THREAD_NAME = "kosmodarts-save"
    }
}
