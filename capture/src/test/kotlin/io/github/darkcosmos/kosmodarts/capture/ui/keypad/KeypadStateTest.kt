package io.github.darkcosmos.kosmodarts.capture.ui.keypad

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The keypad input state, with engine rule D2 (no triple bull, no double outer bull). */
class KeypadStateTest {
    private val on = KeypadState(isEnabled = true)

    @Test
    fun tripleThenTwentyThrowsTripleTwentyAndClearsTheModifier() {
        val armed = on.tap(KeypadTap.Modifier(Multiplier.TRIPLE)).state
        val result = armed.tap(KeypadTap.Number(20))
        assertEquals(Dart.triple(20), result.dart)
        assertEquals(Multiplier.SINGLE, result.state.multiplier)
    }

    @Test
    fun tappingTheActiveModifierClearsIt() {
        val armed = on.tap(KeypadTap.Modifier(Multiplier.DOUBLE)).state
        assertEquals(Multiplier.SINGLE, armed.tap(KeypadTap.Modifier(Multiplier.DOUBLE)).state.multiplier)
        assertEquals(Multiplier.TRIPLE, armed.tap(KeypadTap.Modifier(Multiplier.TRIPLE)).state.multiplier)
    }

    @Test
    fun specialKeysThrowSegments25And50And0() {
        assertEquals(25, on.tap(KeypadTap.OuterBull).dart?.segment)
        assertEquals(50, on.tap(KeypadTap.Bull).dart?.segment)
        assertEquals(0, on.tap(KeypadTap.Miss).dart?.segment)
    }

    @Test
    fun bullKeysAreDisabledUnderAModifierAndThrowNothing() {
        val triple = on.tap(KeypadTap.Modifier(Multiplier.TRIPLE)).state
        assertFalse(triple.areBullKeysEnabled)
        assertNull(triple.tap(KeypadTap.Bull).dart)
        assertNull(triple.tap(KeypadTap.OuterBull).dart)
        assertEquals(Multiplier.TRIPLE, triple.tap(KeypadTap.Bull).state.multiplier)
        assertTrue(on.areBullKeysEnabled)
    }

    @Test
    fun missUnderAModifierThrowsMissAndClearsTheModifier() {
        val result = on.tap(KeypadTap.Modifier(Multiplier.DOUBLE)).state.tap(KeypadTap.Miss)
        assertEquals(Dart.MISS, result.dart)
        assertEquals(Multiplier.SINGLE, result.state.multiplier)
    }

    @Test
    fun aDisabledKeypadThrowsNothingAndIgnoresModifiers() {
        val off = KeypadState(isEnabled = false)
        listOf(KeypadTap.Number(5), KeypadTap.Bull, KeypadTap.OuterBull, KeypadTap.Miss).forEach {
            assertNull(off.tap(it).dart)
        }
        assertEquals(off, off.tap(KeypadTap.Modifier(Multiplier.DOUBLE)).state)
    }
}
