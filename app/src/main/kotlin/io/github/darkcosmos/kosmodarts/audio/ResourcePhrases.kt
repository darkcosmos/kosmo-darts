package io.github.darkcosmos.kosmodarts.audio

import android.content.res.Resources
import io.github.darkcosmos.kosmodarts.R

/** [Phrases] from the app's string resources, in the app language (AN8, AN11). */
class ResourcePhrases(private val resources: Resources) : Phrases {
    override fun turnStart(name: String) = resources.getString(R.string.announce_turn_start, name)

    override fun turnStartWithCheckout(name: String, remaining: Int) =
        resources.getString(R.string.announce_turn_start_checkout, name, remaining)

    override fun turnScore(points: Int) = resources.getString(R.string.announce_turn_score, points)

    override fun oneEighty() = resources.getString(R.string.announce_one_eighty)

    override fun noScore() = resources.getString(R.string.announce_no_score)

    override fun bust() = resources.getString(R.string.announce_bust)

    override fun gameShot(name: String) = resources.getString(R.string.announce_game_shot, name)

    override fun bigFish(name: String) = resources.getString(R.string.announce_big_fish, name)

    override fun announcementsOn() = resources.getString(R.string.announce_announcements_on)
}
