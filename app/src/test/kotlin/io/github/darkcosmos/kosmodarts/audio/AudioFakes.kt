package io.github.darkcosmos.kosmodarts.audio

/** One thing heard, at a virtual time, on the shared [Timeline]. */
sealed interface Heard {
    val at: Long

    data class Sound(val effect: SoundEffect, override val at: Long) : Heard

    data class Speech(val text: String, override val at: Long) : Heard
}

/** What a test heard, in order. */
class Timeline {
    val heard = mutableListOf<Heard>()

    val sounds: List<SoundEffect>
        get() = heard.filterIsInstance<Heard.Sound>().map { it.effect }

    val speech: List<String>
        get() = heard.filterIsInstance<Heard.Speech>().map { it.text }
}

/** A clock the test moves by hand. Actions run in time order when the clock passes them. */
class VirtualScheduler : Scheduler {
    private var time = 0L
    private var nextId = 0
    private val tasks = mutableListOf<Task>()

    override fun now(): Long = time

    override fun post(delayMillis: Long, action: () -> Unit): Cancellable {
        val task = Task(time + delayMillis, nextId++, action)
        tasks += task
        return Cancellable { tasks -= task }
    }

    fun advanceBy(millis: Long) {
        val target = time + millis
        while (true) {
            val next = tasks.filter { it.at <= target }.minWithOrNull(compareBy<Task>({ it.at }, { it.id })) ?: break
            tasks -= next
            time = next.at
            next.action()
        }
        time = target
    }

    val pendingCount: Int
        get() = tasks.size

    private class Task(val at: Long, val id: Int, val action: () -> Unit)
}

/** A [SoundPlayer] that records what it plays. Every sound is loaded unless listed in [notLoaded]. */
class FakeSoundPlayer(
    private val timeline: Timeline,
    private val clock: Scheduler,
    private val durations: Map<SoundEffect, Long> = DEFAULT_DURATIONS,
) : SoundPlayer {
    val notLoaded = mutableSetOf<SoundEffect>()
    var stopAllCount = 0
        private set
    var isReleased = false
        private set

    override fun play(effect: SoundEffect): Boolean {
        if (isReleased || effect in notLoaded) return false
        timeline.heard += Heard.Sound(effect, clock.now())
        return true
    }

    override fun durationMillis(effect: SoundEffect): Long? = durations[effect]

    override fun stopAll() {
        stopAllCount++
    }

    override fun release() {
        isReleased = true
    }

    companion object {
        /** The real lengths of the files, rounded (docs/plans/32-announcements.md). */
        val DEFAULT_DURATIONS: Map<SoundEffect, Long> = mapOf(
            SoundEffect.DART_HIT to 510L,
            SoundEffect.DART_MISS to 420L,
            SoundEffect.DART_CANCEL to 1_000L,
            SoundEffect.BUST to 1_910L,
            SoundEffect.ONE_EIGHTY to 4_000L,
            SoundEffect.VICTORY to 4_000L,
        )
    }
}

/**
 * A [Speaker] that records what it says. An utterance lasts [speechMillis] on the [clock], unless [skipAll] (focus
 * refused, no voice) or [failNext] (engine error).
 */
class FakeSpeaker(
    private val timeline: Timeline,
    private val clock: Scheduler,
    initialStatus: VoiceStatus = VoiceStatus.AVAILABLE,
    private val speechMillis: Long = 800,
) : Speaker {
    override var status: VoiceStatus = initialStatus
        private set
    override var onStatusChanged: (VoiceStatus) -> Unit = {}
    override val enginePackage: String? = "com.example.tts"

    var skipAll = false
    var failNext = false
    var stopCount = 0
        private set
    var isReleased = false
        private set
    private var current: Cancellable? = null
    private var currentOnEnd: (() -> Unit)? = null

    /** End calls, counted per utterance, to check that each one ends exactly once. */
    val endCounts = mutableListOf<Int>()

    fun changeStatus(next: VoiceStatus) {
        status = next
        onStatusChanged(next)
    }

    override fun speak(text: String, onEnd: () -> Unit) {
        val index = endCounts.size
        endCounts += 0
        val end = {
            endCounts[index]++
            onEnd()
        }
        when {
            isReleased || skipAll || status != VoiceStatus.AVAILABLE -> end()
            failNext -> {
                failNext = false
                changeStatus(VoiceStatus.NO_USABLE_VOICE)
                end()
            }
            else -> {
                timeline.heard += Heard.Speech(text, clock.now())
                currentOnEnd = end
                current = clock.post(speechMillis) {
                    currentOnEnd = null
                    end()
                }
            }
        }
    }

    override fun stop() {
        stopCount++
        current?.cancel()
        current = null
        currentOnEnd?.let {
            currentOnEnd = null
            it()
        }
    }

    override fun release() {
        stop()
        isReleased = true
    }
}

/** Phrases that name the line of the table they come from, so tests read clearly. */
object TestPhrases : Phrases {
    override fun turnStart(name: String, isGameStart: Boolean) = if (isGameStart) "first $name" else "turn $name"
    override fun checkoutSuffix(remaining: Int) = "requires $remaining"
    override fun turnScore(points: Int) = "score $points"
    override fun noScore() = "no score"
    override fun win(name: String) = "win $name"
    override fun dartCall(dart: io.github.darkcosmos.kosmodarts.engine.x01.Dart) =
        if (dart.value == 0) null else "call ${dart.value}"
    override fun announcementsOn() = "announcements on"
}

/** The fixed draw of the tests: always the first allowed variant (AN26). */
val FirstVariant = VariantPicker { it.first() }
