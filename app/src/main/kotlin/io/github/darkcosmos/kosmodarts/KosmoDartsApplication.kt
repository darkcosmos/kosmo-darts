package io.github.darkcosmos.kosmodarts

import android.app.Application
import androidx.annotation.VisibleForTesting
import io.github.darkcosmos.kosmodarts.audio.AudioFactory
import io.github.darkcosmos.kosmodarts.audio.SystemAudioFactory
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.history.ArchiveCheck
import io.github.darkcosmos.kosmodarts.history.ArchiveRepository
import io.github.darkcosmos.kosmodarts.history.HistoryDatabase
import io.github.darkcosmos.kosmodarts.history.MatchArchiveRequest
import io.github.darkcosmos.kosmodarts.history.RoomHistoryRepository
import io.github.darkcosmos.kosmodarts.history.RoomProfileRepository
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
open class KosmoDartsApplication : Application() {

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

    protected open fun createPersistence(): Persistence {
        val saveThread = Executors.newSingleThreadExecutor { task ->
            Thread(task, SAVE_THREAD_NAME).apply { isDaemon = true }
        }
        // History reads have their own thread, so that a long list never delays a save (ADR 0012).
        val readThread = Executors.newSingleThreadExecutor { task ->
            Thread(task, READ_THREAD_NAME).apply { isDaemon = true }
        }
        // The database file is opened by the first query, which runs on the save thread (ADR 0012): building it here
        // opens nothing, and a game never starts it on the main thread.
        val database = HistoryDatabase.open(this)
        val archive = ArchiveRepository(database, PlayerListRules::nameKey)
        return Persistence(
            gameStore = AtomicFileStore(File(filesDir, GAME_FILE)),
            setupStore = AtomicFileStore(File(filesDir, SETUP_FILE)),
            executor = saveThread,
            profiles = RoomProfileRepository(database, PlayerListRules::nameKey),
            archiveRequest = MatchArchiveRequest(archive, appVersion()),
            archiveCheck = ArchiveCheck(archive),
            history = RoomHistoryRepository(database),
            readExecutor = readThread,
        )
    }

    /** The build's version name. The build file sets it, so its absence is a programming error (D31). */
    private fun appVersion(): String =
        checkNotNull(packageManager.getPackageInfo(packageName, 0).versionName) { "The build sets no versionName" }

    internal companion object {
        const val GAME_FILE = "saved_game"
        const val SETUP_FILE = "last_setup"
        private const val SAVE_THREAD_NAME = "kosmodarts-save"
        private const val READ_THREAD_NAME = "kosmodarts-history-read"
    }
}
