package io.github.darkcosmos.kosmodarts.audio

import android.content.res.Resources
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.text.dartWords

/** [Phrases] from the app's string resources, in the app language (AN8, AN11, AN25, AN26). */
class ResourcePhrases(
    private val resources: Resources,
    private val variants: PhraseVariants = PhraseVariants(RandomVariantPicker()),
) : Phrases {

    override fun turnStart(name: String, isGameStart: Boolean): String {
        val line = if (isGameStart) R.array.announce_game_start else R.array.announce_next_turn
        val phrases = resources.getStringArray(line)
        val format = phrases[variants.next(resources.getResourceEntryName(line), phrases.size)]
        return String.format(resources.configuration.locales[0], format, name)
    }

    override fun checkoutSuffix(remaining: Int) = resources.getString(R.string.announce_checkout_suffix, remaining)

    override fun turnScore(points: Int) = resources.getString(R.string.announce_turn_score, points)

    override fun noScore() = resources.getString(R.string.announce_no_score)

    override fun win(name: String) = resources.getString(R.string.announce_win, name)

    override fun dartCall(dart: Dart): String? = dartWords(resources, dart)

    override fun announcementsOn() = resources.getString(R.string.announce_announcements_on)
}
