package io.github.darkcosmos.kosmodarts.ui.cricket

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketTarget
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier

private const val DOUBLE_DOTS = 2
private const val TRIPLE_DOTS = 3

/**
 * One dart button of a target row of the Cricket board (docs/rules/cricket.md, CR24). It carries data only: the
 * composable resolves the label and the TalkBack text from resources.
 *
 * @property dart the dart one tap records.
 * @property dotCount the dots drawn under the number: 0 for a single, 2 for a double (and the bull), 3 for a triple.
 * @property isEnabled false while a turn waits for confirmation or a win is pending (CR7, CR25); a dead row (CR15)
 * keeps its buttons enabled.
 */
data class CricketTargetButton(val dart: Dart, val dotCount: Int, val isEnabled: Boolean)

/**
 * CR24: the buttons of [target]'s row, from the start to the end of the row: single, double, triple for 15 to 20, the
 * 25 then the bull for the Bull row (no triple bull on a dartboard, rule D2).
 */
internal fun buttonRow(target: CricketTarget, isEnabled: Boolean): List<CricketTargetButton> =
    if (target.isNumber) {
        val number = target.points
        listOf(
            CricketTargetButton(Dart(number, Multiplier.SINGLE), dotCount = 0, isEnabled = isEnabled),
            CricketTargetButton(Dart(number, Multiplier.DOUBLE), dotCount = DOUBLE_DOTS, isEnabled = isEnabled),
            CricketTargetButton(Dart(number, Multiplier.TRIPLE), dotCount = TRIPLE_DOTS, isEnabled = isEnabled),
        )
    } else {
        listOf(
            CricketTargetButton(Dart.OUTER_BULL, dotCount = 0, isEnabled = isEnabled),
            CricketTargetButton(Dart.BULL, dotCount = DOUBLE_DOTS, isEnabled = isEnabled),
        )
    }
