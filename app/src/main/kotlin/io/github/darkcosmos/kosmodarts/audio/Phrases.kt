package io.github.darkcosmos.kosmodarts.audio

import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/** The texts the voice says, in the app language (AN8, AN25). Numbers are passed as digits. */
interface Phrases {
    /** E1, AN26: one of the variants of the game start or next turn line, never the last one drawn. */
    fun turnStart(name: String, isGameStart: Boolean): String

    /** E1: said after the turn start phrase when a checkout is possible. */
    fun checkoutSuffix(remaining: Int): String

    fun turnScore(points: Int): String

    fun noScore(): String

    /** E2: the win, for a game shot and a big fish alike. */
    fun win(name: String): String

    /** AN25: the call of a scoring dart, or `null` for a miss. */
    fun dartCall(dart: Dart): String?

    fun announcementsOn(): String
}
