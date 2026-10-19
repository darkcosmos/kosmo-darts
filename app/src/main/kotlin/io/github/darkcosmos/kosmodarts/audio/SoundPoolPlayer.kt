package io.github.darkcosmos.kosmodarts.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.SoundPool
import androidx.annotation.VisibleForTesting
import io.github.darkcosmos.kosmodarts.R
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * [SoundPlayer] over the framework's [SoundPool] (AN9, AN10, AN19). The sounds load in the background; a sound
 * asked for before it is loaded is skipped, never played late. Lengths are read from the files in the background.
 */
class SoundPoolPlayer(context: Context) : SoundPlayer {

    @VisibleForTesting
    internal val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val loaded = mutableSetOf<Int>()
    private val streams = ArrayDeque<Int>()
    private val durations = ConcurrentHashMap<SoundEffect, Long>()
    private var isReleased = false

    private val soundIds: Map<SoundEffect, Int>

    init {
        pool.setOnLoadCompleteListener { _, soundId, status -> if (status == 0) loaded += soundId }
        soundIds = SoundEffect.entries.associateWith { pool.load(context, resourceOf(it), 1) }
        val appContext = context.applicationContext
        Thread({ readDurations(appContext) }, "kosmodarts-sound-lengths").apply { isDaemon = true }.start()
    }

    override fun play(effect: SoundEffect): Boolean {
        val soundId = soundIds.getValue(effect)
        val volume = volumeOf(effect)
        val streamId = if (isReleased || soundId !in loaded) 0 else pool.play(soundId, volume, volume, 1, 0, 1f)
        if (streamId != 0) {
            streams.addLast(streamId)
            if (streams.size > MAX_TRACKED_STREAMS) streams.removeFirst()
        }
        return streamId != 0
    }

    override fun durationMillis(effect: SoundEffect): Long? = durations[effect]

    override fun stopAll() {
        streams.forEach(pool::stop)
        streams.clear()
    }

    override fun release() {
        if (isReleased) return
        isReleased = true
        streams.clear()
        pool.release()
    }

    private fun readDurations(context: Context) {
        SoundEffect.entries.forEach { effect ->
            readDuration(context, resourceOf(effect))?.let { durations[effect] = it }
        }
    }

    private fun readDuration(context: Context, resourceId: Int): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            context.resources.openRawResourceFd(resourceId).use { file ->
                retriever.setDataSource(file.fileDescriptor, file.startOffset, file.length)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (ignored: IOException) {
            // The default length applies (AnnouncementPlayer.DEFAULT_SOUND_MILLIS).
            null
        } catch (ignored: RuntimeException) {
            // Same: an unreadable length is never worth a crash.
            null
        } finally {
            retriever.release()
        }
    }

    companion object {
        private const val MAX_STREAMS = 6
        private const val MAX_TRACKED_STREAMS = 16

        /**
         * A.3: the victory sound plays with the win voice and must stay under it. About -8 dB; tune it here after
         * listening on the phone.
         */
        const val VICTORY_VOLUME: Float = 0.4f

        /** AN9, A.3: every sound at full media volume, but the victory sound. */
        fun volumeOf(effect: SoundEffect): Float = if (effect == SoundEffect.VICTORY) VICTORY_VOLUME else 1f

        fun resourceOf(effect: SoundEffect): Int = when (effect) {
            SoundEffect.DART_HIT -> R.raw.sfx_dart_hit
            SoundEffect.DART_MISS -> R.raw.sfx_dart_miss
            SoundEffect.DART_CANCEL -> R.raw.sfx_dart_cancel
            SoundEffect.BUST -> R.raw.sfx_bust
            SoundEffect.ONE_EIGHTY -> R.raw.sfx_one_eighty
            SoundEffect.VICTORY -> R.raw.sfx_victory
        }
    }
}
