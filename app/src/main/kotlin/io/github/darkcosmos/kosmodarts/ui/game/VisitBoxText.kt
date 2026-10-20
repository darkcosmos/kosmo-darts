package io.github.darkcosmos.kosmodarts.ui.game

import androidx.annotation.StringRes
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.VisitTotal
import io.github.darkcosmos.kosmodarts.engine.x01.VisitTotals

/**
 * Text of one visit total box (docs/rules/visit-total.md, VT8). Composables resolve it with `stringResource` only
 * (VT12).
 *
 * @property valueRes text shown in the box: the points, or BUST.
 * @property descriptionRes TalkBack description of the box.
 * @property formatArgs arguments of both resources: the points (0 for "not played", whose description takes none),
 *   or nothing for bust.
 */
data class VisitBoxText(
    @StringRes val valueRes: Int,
    @StringRes val descriptionRes: Int,
    val formatArgs: List<Int>,
)

/**
 * One box text per player, in configuration order (VT6, VT8). While a turn waits for "Next player" or for the
 * checkout dialog, the engine current player is shown with his last completed visit, not with his new turn (VT6, Q3).
 * While a bust waits, its thrower has no box (`null`): the BUST badge replaces it (VT13, PR37-R2).
 */
fun visitBoxTexts(screen: GameScreenState): List<VisitBoxText?> {
    val state = screen.game.state
    val totals = VisitTotals.of(state)
    val bustThrower = screen.pendingTurn?.playerIndex?.takeIf { screen.isBustShown }
    val waitingNextPlayer = state.currentPlayerIndex.takeIf { screen.isAwaitingConfirmation }
    return totals.mapIndexed { player, total ->
        val shown = when {
            player == bustThrower -> null
            player == waitingNextPlayer -> VisitTotals.lastCompleted(state, player)
            else -> total
        }
        shown?.toBoxText()
    }
}

private fun VisitTotal.toBoxText(): VisitBoxText = when (this) {
    VisitTotal.NotPlayed ->
        VisitBoxText(R.string.game_visit_value, R.string.game_visit_not_played_description, listOf(0))
    is VisitTotal.InProgress ->
        VisitBoxText(R.string.game_visit_value, R.string.game_visit_in_progress_description, listOf(points))
    is VisitTotal.LastVisit ->
        VisitBoxText(R.string.game_visit_value, R.string.game_visit_last_description, listOf(points))
    VisitTotal.Bust -> VisitBoxText(R.string.game_bust, R.string.game_visit_last_bust_description, emptyList())
}
