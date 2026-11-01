package io.github.darkcosmos.kosmodarts.capture.ui.keypad

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier

/** A key of the keypad, free of any Compose type. */
sealed interface KeypadTap {
    data class Number(val number: Int) : KeypadTap

    data class Modifier(val multiplier: Multiplier) : KeypadTap

    data object OuterBull : KeypadTap

    data object Bull : KeypadTap

    data object Miss : KeypadTap
}

/** The outcome of a tap: the next [state] and the [dart] thrown, if the tap threw one. */
data class KeypadResult(val state: KeypadState, val dart: Dart?)

/**
 * The input state of the keypad: whether it accepts darts and which one-shot Double or Triple modifier is pending.
 * Same behavior as the X01 keypad of `:app`: the modifier is cleared by the next dart, tapping the active modifier
 * clears it, and the bull keys need no modifier (engine rule D2: no triple bull, no double outer bull).
 */
data class KeypadState(val isEnabled: Boolean, val multiplier: Multiplier = Multiplier.SINGLE) {
    val areBullKeysEnabled: Boolean
        get() = isEnabled && multiplier == Multiplier.SINGLE

    fun tap(tap: KeypadTap): KeypadResult = when {
        !isEnabled -> KeypadResult(this, null)
        tap is KeypadTap.Modifier -> KeypadResult(copy(multiplier = toggled(tap.multiplier)), null)
        else -> dartOf(tap)?.let { KeypadResult(copy(multiplier = Multiplier.SINGLE), it) } ?: KeypadResult(this, null)
    }

    private fun toggled(tapped: Multiplier): Multiplier = if (multiplier == tapped) Multiplier.SINGLE else tapped

    private fun dartOf(tap: KeypadTap): Dart? = when (tap) {
        is KeypadTap.Number -> Dart(tap.number, multiplier)
        KeypadTap.OuterBull -> Dart.OUTER_BULL.takeIf { areBullKeysEnabled }
        KeypadTap.Bull -> Dart.BULL.takeIf { areBullKeysEnabled }
        KeypadTap.Miss -> Dart.MISS
        is KeypadTap.Modifier -> null
    }
}
