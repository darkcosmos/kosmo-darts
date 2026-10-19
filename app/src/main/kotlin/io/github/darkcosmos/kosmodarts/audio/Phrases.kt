package io.github.darkcosmos.kosmodarts.audio

/** The texts the voice says, in the app language (AN8). Numbers are passed as digits. */
interface Phrases {
    fun turnStart(name: String): String

    fun turnStartWithCheckout(name: String, remaining: Int): String

    fun turnScore(points: Int): String

    fun oneEighty(): String

    fun noScore(): String

    fun bust(): String

    fun gameShot(name: String): String

    fun bigFish(name: String): String

    fun announcementsOn(): String
}
