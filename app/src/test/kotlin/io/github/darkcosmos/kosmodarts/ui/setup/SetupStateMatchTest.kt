package io.github.darkcosmos.kosmodarts.ui.setup

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The match format of the setup (docs/rules/match.md, M1, M13; R7, R13). */
class SetupStateMatchTest {

    /** M13: defaults 1 and 1, − disabled, + enabled, label "Legs" with one set. */
    @Test
    fun `M13 default format`() {
        val setup = SetupState()

        assertEquals(1 to 1, setup.legsPerSet to setup.sets)
        assertFalse(setup.canDecreaseLegs)
        assertFalse(setup.canDecreaseSets)
        assertTrue(setup.canIncreaseLegs)
        assertTrue(setup.canIncreaseSets)
        assertFalse(setup.hasSets)
    }

    /** M13, A1: values are clamped to MatchFormat.MIN and MatchFormat.MAX, and + is disabled at the top. */
    @Test
    fun `M13 values are clamped`() {
        val top = SetupState().withLegsPerSet(MatchFormat.MAX + 5).withSets(MatchFormat.MAX + 1)

        assertEquals(MatchFormat.MAX to MatchFormat.MAX, top.legsPerSet to top.sets)
        assertFalse(top.canIncreaseLegs)
        assertFalse(top.canIncreaseSets)
        assertTrue(top.hasSets)
        val bottom = top.withLegsPerSet(0).withSets(-3)
        assertEquals(MatchFormat.MIN to MatchFormat.MIN, bottom.legsPerSet to bottom.sets)
    }

    /** M8 (owner Q8): legs and sets with a single player. */
    @Test
    fun `M13 a single player can play a match`() {
        val setup = SetupState(names = listOf("Solo")).withLegsPerSet(3).withSets(2)

        assertEquals(
            SetupResult.Valid(X01MatchConfig(X01Config(listOf("Solo")), MatchFormat(3, 2))),
            setup.start(),
        )
    }

    /** R7, R13: the last format prefills the setup. */
    @Test
    fun `R13 the last format prefills the setup`() {
        val setup = SetupState.prefilled(X01MatchConfig(X01Config(listOf("Ann")), MatchFormat(5, 3)))

        assertEquals(5 to 3, setup.legsPerSet to setup.sets)
    }

    /** A setup error keeps the format. */
    @Test
    fun `M13 an invalid start keeps the format`() {
        val invalid = SetupState(names = listOf("", "")).withLegsPerSet(4).start() as SetupResult.Invalid

        assertEquals(4, invalid.setup.legsPerSet)
    }
}
