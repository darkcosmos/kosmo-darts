package io.github.darkcosmos.kosmodarts.ui.resume

import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import io.github.darkcosmos.kosmodarts.KosmoDartsApplication
import io.github.darkcosmos.kosmodarts.MainActivity
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.history.ArchiveCheck
import io.github.darkcosmos.kosmodarts.history.FakeArchiveGateway
import io.github.darkcosmos.kosmodarts.history.MatchArchiveRequest
import io.github.darkcosmos.kosmodarts.persistence.AtomicFileStore
import io.github.darkcosmos.kosmodarts.persistence.ByteStore
import io.github.darkcosmos.kosmodarts.persistence.GameSaveCodec
import io.github.darkcosmos.kosmodarts.persistence.LastSetupCodec
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.persistence.ReadResult
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.persistence.toLastSetups
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.io.File
import java.util.concurrent.Executors

/**
 * Drives [MainActivity] like Android does, with Robolectric, over the real files of the app:
 * - [launchFresh]: a launch after a force stop, a reboot or a removal from the recents (no saved instance state);
 * - [killInBackgroundAndReturn]: the process is killed in the background, then the user returns to the app (the
 *   saved instance state comes back, with a new ViewModel that reads the files again);
 * - [rotate]: a configuration change (the ViewModel survives).
 *
 * The [persistence] counts reads and writes of the game file, so that tests can check that nothing is read or written
 * again on rotation.
 */
internal class AppProcess {

    val app: KosmoDartsApplication = ApplicationProvider.getApplicationContext()

    val gameFile: File = File(app.filesDir, KosmoDartsApplication.GAME_FILE)
    private val setupFile = File(app.filesDir, KosmoDartsApplication.SETUP_FILE)

    val gameStore = CountingStore(AtomicFileStore(gameFile))
    private val saveThread = Executors.newSingleThreadExecutor()
    val archive = FakeArchiveGateway()
    val persistence = Persistence(
        gameStore,
        AtomicFileStore(setupFile),
        saveThread,
        archiveRequest = MatchArchiveRequest(archive, "1.0"),
        archiveCheck = ArchiveCheck(archive),
    )

    private var controller: ActivityController<MainActivity>? = null

    init {
        app.replacePersistence(persistence)
    }

    val activity: MainActivity
        get() = checkNotNull(controller).get()

    val viewModel: KosmoDartsViewModel
        get() = ViewModelProvider(activity)[KosmoDartsViewModel::class.java]

    fun string(id: Int, vararg args: Any): String = app.getString(id, *args)

    fun writeGame(saved: StoredGame) = AtomicFileStore(gameFile).write(checkNotNull(GameSaveCodec().encode(saved)))

    fun writeRawGame(bytes: ByteArray) = AtomicFileStore(gameFile).write(bytes)

    fun writeSetup(config: X01Config) =
        AtomicFileStore(setupFile).write(LastSetupCodec().encode(X01MatchConfig(config).toLastSetups()))

    fun writeRawSetup(bytes: ByteArray) = AtomicFileStore(setupFile).write(bytes)

    fun launchFresh(): MainActivity {
        finish()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        return activity
    }

    fun killInBackgroundAndReturn(): MainActivity {
        persistence.awaitIdle()
        val running = checkNotNull(controller)
        val bundle = Bundle()
        running.pause().stop()
        running.saveInstanceState(bundle)
        running.destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup(bundle)
        return activity
    }

    /** Recreates the activity as Android does on a configuration change, keeping its ViewModel. */
    fun rotate() {
        checkNotNull(controller).recreate()
    }

    /** Waits for the save thread, then delivers its answers to the main thread (the archive result, AR2). */
    fun settle() {
        persistence.awaitIdle()
        shadowOf(Looper.getMainLooper()).idle()
    }

    fun pressBack() = activity.onBackPressedDispatcher.onBackPressed()

    /** The app goes to the background (another app, screen off): the activity is stopped, not destroyed. */
    fun stopActivity() {
        checkNotNull(controller).pause().stop()
    }

    /** Back to the app after [stopActivity]. */
    fun restartActivity() {
        checkNotNull(controller).restart().start().resume()
    }

    /** Stops the activity and waits for every pending write. */
    fun finish() {
        controller?.let { running ->
            persistence.awaitIdle()
            running.pause().stop().destroy()
        }
        controller = null
    }

    fun close() {
        finish()
        saveThread.shutdown()
    }
}

/** Counts the reads and writes that reach [delegate]. */
internal class CountingStore(private val delegate: ByteStore) : ByteStore {
    @Volatile var reads = 0
        private set

    @Volatile var writes = 0
        private set

    override fun read(maxBytes: Int): ReadResult {
        reads++
        return delegate.read(maxBytes)
    }

    override fun write(bytes: ByteArray) {
        writes++
        delegate.write(bytes)
    }

    override fun delete() {
        writes++
        delegate.delete()
    }
}
