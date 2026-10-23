package io.github.darkcosmos.kosmodarts.ui.theme

import androidx.compose.ui.graphics.Color
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot

/**
 * Colors of the players (docs/rules/players.md, PC9): slot n is drawn with token `playerN`, and text on it with
 * `onPlayerN`. Pure: no composition needed, so it is tested on the JVM.
 */
object PlayerPalette {

    /** Token `playerN` of [slot]. */
    fun colorOf(slot: ColorSlot, colors: KosmoColors = KosmoColors.Default): Color = when (slot) {
        ColorSlot.SLOT_1 -> colors.player1
        ColorSlot.SLOT_2 -> colors.player2
        ColorSlot.SLOT_3 -> colors.player3
        ColorSlot.SLOT_4 -> colors.player4
    }

    /** Token `onPlayerN` of [slot]: text and icons drawn on [colorOf]. */
    fun onColorOf(slot: ColorSlot, colors: KosmoColors = KosmoColors.Default): Color = when (slot) {
        ColorSlot.SLOT_1 -> colors.onPlayer1
        ColorSlot.SLOT_2 -> colors.onPlayer2
        ColorSlot.SLOT_3 -> colors.onPlayer3
        ColorSlot.SLOT_4 -> colors.onPlayer4
    }
}
