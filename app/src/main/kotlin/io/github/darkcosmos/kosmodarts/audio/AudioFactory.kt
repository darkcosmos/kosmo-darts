package io.github.darkcosmos.kosmodarts.audio

import android.content.Context
import android.media.AudioManager
import io.github.darkcosmos.kosmodarts.R
import java.util.Locale

/** Creates the platform audio objects. Tests replace it (`KosmoDartsApplication.replaceAudioFactory`). */
interface AudioFactory {
    fun soundPlayer(context: Context): SoundPlayer

    /** [warmUp]: load the voice model in advance (A.6); the game session does, the Settings probe does not. */
    fun speaker(context: Context, warmUp: Boolean): Speaker

    fun scheduler(): Scheduler

    /** How phrase variants are drawn (AN26); tests use a fixed draw. */
    fun variantPicker(): VariantPicker
}

/** The real [SoundPool][android.media.SoundPool] and text-to-speech engine. */
class SystemAudioFactory : AudioFactory {

    override fun soundPlayer(context: Context): SoundPlayer = SoundPoolPlayer(context)

    /** AN11: the voice speaks the language of the app's texts, which [context]'s resources resolve. */
    override fun speaker(context: Context, warmUp: Boolean): Speaker = TtsSpeaker(
        context = context.applicationContext,
        language = Locale.forLanguageTag(context.getString(R.string.announce_language)),
        deviceLocale = context.resources.configuration.locales[0],
        audioManager = context.getSystemService(AudioManager::class.java),
        warmUp = warmUp,
    )

    override fun scheduler(): Scheduler = HandlerScheduler()

    override fun variantPicker(): VariantPicker = RandomVariantPicker()
}
