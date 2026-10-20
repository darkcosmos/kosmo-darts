package io.github.darkcosmos.kosmodarts

import android.app.Application
import androidx.annotation.VisibleForTesting
import io.github.darkcosmos.kosmodarts.audio.AudioFactory
import io.github.darkcosmos.kosmodarts.audio.SystemAudioFactory
import io.github.darkcosmos.kosmodarts.persistence.AtomicFileStore
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsRepository
import io.github.darkcosmos.kosmodarts.settings.SharedPreferencesAudioSettingsStore
import java.io.File
import java.util.concurrent.Executors

/**
 * Holds the process-wide [persistence] and [audioSettings], which outlive activities and ViewModels (ADR 0002,
 * decision 1; ADR 0004, decision 2).
 */
class KosmoDartsApplication : Application() {

    private var current: Persistence? = null
    private var currentAudioSettings: AudioSettingsRepository? = null

    /** Sound settings of the device (ADR 0004, decision 2). Read on first use, on the main thread. */
    val audioSettings: AudioSettingsRepository
        get() = currentAudioSettings ?: AudioSettingsRepository(
            SharedPreferencesAudioSettingsStore(
                getSharedPreferences(SharedPreferencesAudioSettingsStore.FILE_NAME, MODE_PRIVATE),
            ),
        ).also { currentAudioSettings = it }

    /** Creates the sound player and the speaker of each screen. Tests replace it with [replaceAudioFactory]. */
    var audioFactory: AudioFactory = SystemAudioFactory()
        private set

    /** Created on first use, on the main thread. */
    val persistence: Persistence
        get() = current ?: createPersistence().also { current = it }

    /** Lets a test use its own [Persistence] (for example one that counts reads and writes) before any activity. */
    @VisibleForTesting
    internal fun replacePersistence(persistence: Persistence) {
        current = persistence
    }

    /** Lets a test record sounds and speech before any activity. */
    @VisibleForTesting
    internal fun replaceAudioFactory(factory: AudioFactory) {
        audioFactory = factory
    }

    /** Lets a test start from given sound settings before any activity. */
    @VisibleForTesting
    internal fun replaceAudioSettings(repository: AudioSettingsRepository) {
        currentAudioSettings = repository
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
