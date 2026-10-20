package io.github.darkcosmos.kosmodarts.audio

/** Plays the sound effects. Replaced by a fake in tests. Main thread only. */
interface SoundPlayer {
    /** Starts [effect] at once. Returns `false` when it could not start (not loaded yet, released): it is skipped. */
    fun play(effect: SoundEffect): Boolean

    /** Length of [effect], or `null` while it is unknown. */
    fun durationMillis(effect: SoundEffect): Long?

    /** Stops every sound started so far. */
    fun stopAll()

    /** Frees the sounds; [play] then returns `false`. */
    fun release()
}
