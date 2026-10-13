package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.X01Turn

/** Called once per completed turn, when it starts waiting for confirmation. Will announce the turn score later. */
fun interface TurnCompletedHook {
    fun onTurnCompleted(turn: X01Turn)
}
