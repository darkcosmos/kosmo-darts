package io.github.darkcosmos.kosmodarts.audio

import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.darkcosmos.kosmodarts.settings.AudioSettingsRepository
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.KosmoDartsViewModel
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.app.isGameOrBetweenLegs
import io.github.darkcosmos.kosmodarts.ui.app.isNewGameShown
import io.github.darkcosmos.kosmodarts.ui.settings.SettingsViewModel

/**
 * Creates and releases the audio of each screen (ADR 0004, decision 1):
 * - a [GameAudioSession] exists exactly while the game or end screen is shown and the activity is started (AN21):
 *   the end screen keeps it, since the win is announced there, after "Finish game" (AN5);
 * - a voice probe for the Settings screen exists exactly while it is shown and the activity is started (AN17);
 * - the volume buttons set the media volume on the game and end screens (AN19).
 *
 * The ViewModel tells the binder about a new screen before it hands over the cues of the same event, so the first
 * player's name finds its session.
 */
class AudioBinder(
    private val activity: ComponentActivity,
    private val game: KosmoDartsViewModel,
    private val settingsViewModel: SettingsViewModel,
    private val settings: AudioSettingsRepository,
    private val factory: AudioFactory,
) : DefaultLifecycleObserver {

    private var isStarted = false
    private var gameSession: GameAudioSession? = null
    private var gameSpeaker: Speaker? = null
    private var probe: Speaker? = null
    private var voiceCheck = FirstVoiceCheck()
    private val screenListener: (Screen) -> Unit = { update() }
    private val mainHandler = Handler(Looper.getMainLooper())

    fun bind() {
        game.onScreenChanged = screenListener
        activity.lifecycle.addObserver(this)
        update()
    }

    /** "Install a voice", from the game message or the Settings screen (AN17). Returns whether it opened. */
    fun installVoice(): Boolean = VoiceInstaller(activity, (gameSpeaker ?: probe)?.enginePackage).launch()

    override fun onStart(owner: LifecycleOwner) {
        isStarted = true
        update()
    }

    override fun onStop(owner: LifecycleOwner) {
        isStarted = false
        update()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        if (game.onScreenChanged === screenListener) game.onScreenChanged = {}
    }

    private fun update() {
        val screen = game.state.value.screen
        // The between-legs screen keeps the session: a leg or set voice ends there, and "Next leg" speaks (AN27, AN28).
        val isGameShown = screen.isGameOrBetweenLegs || screen is Screen.End || screen is Screen.CricketEnd
        activity.volumeControlStream =
            if (isGameShown) AudioManager.STREAM_MUSIC else AudioManager.USE_DEFAULT_STREAM_TYPE
        // A.4: the session already exists on the setup screen, so the speech engine is ready when "Start" is tapped.
        // No cue comes from the setup screen, so nothing is played there.
        val isSessionNeeded = isGameShown || screen is Screen.Setup
        if (isStarted && isSessionNeeded) openGameSession() else closeGameSession()
        // Posted: this runs inside the ViewModel's event that changed the screen, which must end first.
        gameSpeaker?.let { speaker ->
            val status = speaker.status
            mainHandler.post { if (gameSpeaker === speaker) reportUnavailableVoice(speaker, status) }
        }
        if (isStarted && screen is Screen.Settings) openProbe() else closeProbe()
    }

    private fun openGameSession() {
        if (gameSession != null) return
        val speaker = if (settings.settings.value.voiceEnabled) factory.speaker(activity, warmUp = true) else null
        val session = GameAudioSession(
            sounds = factory.soundPlayer(activity),
            speaker = speaker,
            scheduler = factory.scheduler(),
            // One set of phrase variants per session: "never twice in a row" holds across turns and a rematch.
            phrases = ResourcePhrases(activity.resources, PhraseVariants(factory.variantPicker())),
            settings = { settings.settings.value },
        )
        gameSpeaker = speaker
        voiceCheck = FirstVoiceCheck()
        // Each change is reported with its own status, in order: a later failure is never taken for the first result.
        speaker?.onStatusChanged = { status ->
            mainHandler.post { if (gameSpeaker === speaker) reportUnavailableVoice(speaker, status) }
        }
        gameSession = session
        game.addCueSink(session)
    }

    /** AN16, A.4: [FirstVoiceCheck] decides; the ViewModel then decides whether the message shows. */
    private fun reportUnavailableVoice(speaker: Speaker, status: VoiceStatus) {
        val screen = game.state.value.screen
        val isNewGameShown = screen.isNewGameShown
        if (voiceCheck.shouldReport(status, isNewGameShown)) {
            val canInstall = VoiceInstaller(activity, speaker.enginePackage).canInstall
            game.onEvent(AppEvent.VoiceUnavailable(canInstall))
        }
    }

    private fun closeGameSession() {
        val session = gameSession ?: return
        game.removeCueSink(session)
        session.release()
        gameSession = null
        gameSpeaker = null
    }

    private fun openProbe() {
        if (probe != null) return
        val speaker = factory.speaker(activity, warmUp = false)
        probe = speaker
        settingsViewModel.attachProbe(speaker) { VoiceInstaller(activity, speaker.enginePackage).canInstall }
    }

    private fun closeProbe() {
        val speaker = probe ?: return
        settingsViewModel.detachProbe()
        speaker.release()
        probe = null
    }
}
