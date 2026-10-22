package io.github.darkcosmos.kosmodarts.ui.setup

import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.engine.x01.X01ConfigError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SetupStateTest {

    @Test
    fun defaultIs501DoubleOutWithTwoBlankNames() {
        val setup = SetupState()

        assertEquals(501, setup.startScore)
        assertEquals(CheckoutRule.DOUBLE_OUT, setup.checkoutRule)
        assertEquals(listOf("", ""), setup.names)
        assertTrue(setup.screenErrors.isEmpty())
        assertTrue(setup.errorsFor(0).isEmpty())
    }

    @Test
    /** R7: the last setup prefills names in order, start score and checkout rule. */
    fun `R7 prefilled keeps names in order, start score and checkout rule`() {
        val lastSetup = X01MatchConfig(X01Config(listOf("Cid", "Ann", "Bob"), 301, CheckoutRule.SINGLE_OUT))

        assertEquals(
            SetupState(301, CheckoutRule.SINGLE_OUT, listOf("Cid", "Ann", "Bob")),
            SetupState.prefilled(lastSetup),
        )
    }

    @Test
    fun `R7 prefilled without a last setup is the default`() {
        assertEquals(SetupState(), SetupState.prefilled(null))
    }

    @Test
    fun settingsCanBeChanged() {
        val setup = SetupState().withStartScore(301).withCheckoutRule(CheckoutRule.SINGLE_OUT)

        assertEquals(301, setup.startScore)
        assertEquals(CheckoutRule.SINGLE_OUT, setup.checkoutRule)
    }

    @Test
    fun nameIsChangedAtItsPosition() {
        assertEquals(listOf("", "Bob"), SetupState().withName(1, "Bob").names)
    }

    @Test
    fun playersCanBeAddedUpToFour() {
        val four = SetupState().withPlayerAdded().withPlayerAdded()

        assertEquals(4, four.names.size)
        assertFalse(four.canAddPlayer)
        assertEquals(four, four.withPlayerAdded())
    }

    /** M13, PC3: the cross removes any row, down to one player, and frees its color slot. */
    @Test
    fun `M13 PC3 removing a player at any position down to one`() {
        val three = SetupState(names = listOf("Ann", "Bob", "Cid"))

        val withoutBob = three.withPlayerRemoved(1)
        assertEquals(listOf("Ann", "Cid"), withoutBob.names)
        assertEquals(listOf(ColorSlot.SLOT_1, ColorSlot.SLOT_3), withoutBob.roster.slots)

        val one = withoutBob.withPlayerRemoved(0)
        assertEquals(listOf("Cid"), one.names)
        assertEquals(listOf(ColorSlot.SLOT_3), one.roster.slots)
        assertFalse(one.canRemovePlayer)
        assertEquals(one, one.withPlayerRemoved(0))
    }

    /** PC2, PC3: a player added after a removal gets the freed slot, with an empty name. */
    @Test
    fun `PC2 a player added after a removal gets the freed slot`() {
        val readded = SetupState(names = listOf("Ann", "Bob", "Cid")).withPlayerRemoved(1).withPlayerAdded()

        assertEquals(listOf("Ann", "Cid", ""), readded.names)
        assertEquals(listOf(ColorSlot.SLOT_1, ColorSlot.SLOT_3, ColorSlot.SLOT_2), readded.roster.slots)
    }

    /** M13: after a removal, the errors of the last start no longer point at moved fields: they are cleared. */
    @Test
    fun `M13 a removal clears the name errors so none points at a moved field`() {
        val invalid = (SetupState(names = listOf("Ann", " ", "ann")).start() as SetupResult.Invalid).setup
        assertEquals(listOf(X01ConfigError.BLANK_NAME), invalid.errorsFor(1))

        val removed = invalid.withPlayerRemoved(1)

        assertEquals(listOf("Ann", "ann"), removed.names)
        assertTrue(removed.names.indices.all { removed.errorsFor(it).isEmpty() })
        // The next start reports the remaining duplicate at the new positions.
        val again = removed.start() as SetupResult.Invalid
        assertEquals(listOf(X01ConfigError.DUPLICATE_NAME), again.setup.errorsFor(0))
        assertEquals(listOf(X01ConfigError.DUPLICATE_NAME), again.setup.errorsFor(1))
    }

    /** PC8: the setup prefilled from the last setup restores each player's slot. */
    @Test
    fun `PC8 prefilled restores the color slots`() {
        val lastSetup = X01MatchConfig(
            X01Config(listOf("Cid", "Ann")),
            colorSlots = listOf(ColorSlot.SLOT_4, ColorSlot.SLOT_2),
        )

        val setup = SetupState.prefilled(lastSetup)

        assertEquals(listOf(ColorSlot.SLOT_4, ColorSlot.SLOT_2), setup.roster.slots)
        assertEquals(listOf(ColorSlot.SLOT_4, ColorSlot.SLOT_2), (setup.start() as SetupResult.Valid).config.colorSlots)
    }

    /** PC4: shuffle keeps each name with its slot, and Start passes the slots to the match. */
    @Test
    fun `PC4 PC5 shuffled names keep their slots up to the match configuration`() {
        val setup = SetupState(names = listOf("Ann", "Bob", "Cid", "Dan")).shuffled(Random(SEED))
        val bySlot = mapOf("Ann" to ColorSlot.SLOT_1, "Bob" to ColorSlot.SLOT_2, "Cid" to ColorSlot.SLOT_3)

        val config = (setup.start() as SetupResult.Valid).config

        config.config.players.zip(config.colorSlots).forEach { (name, slot) ->
            bySlot[name]?.let { assertEquals(name, it, slot) }
        }
        assertEquals(setup.roster.slots, config.colorSlots)
    }

    @Test
    fun shuffleUsesTheInjectedRandomness() {
        val names = listOf("Ann", "Bob", "Cid", "Dan")

        val shuffled = SetupState(names = names).shuffled(Random(SEED))

        assertEquals(names.shuffled(Random(SEED)), shuffled.names)
    }

    @Test
    fun validSetupBuildsTheConfiguration() {
        val setup = SetupState(names = listOf(" Ann ", "Bob"), startScore = 301, checkoutRule = CheckoutRule.SINGLE_OUT)

        val result = setup.start()

        assertEquals(
            SetupResult.Valid(X01MatchConfig(X01Config(listOf("Ann", "Bob"), 301, CheckoutRule.SINGLE_OUT))),
            result,
        )
    }

    @Test
    fun nameErrorsAreReportedUnderTheConcernedFields() {
        val setup = SetupState(names = listOf("Ann", " ", "ann", "Bob"))

        val invalid = setup.start() as SetupResult.Invalid

        assertEquals(listOf(X01ConfigError.DUPLICATE_NAME), invalid.setup.errorsFor(0))
        assertEquals(listOf(X01ConfigError.BLANK_NAME), invalid.setup.errorsFor(1))
        assertEquals(listOf(X01ConfigError.DUPLICATE_NAME), invalid.setup.errorsFor(2))
        assertTrue(invalid.setup.errorsFor(3).isEmpty())
        assertTrue(invalid.setup.screenErrors.isEmpty())
    }

    @Test
    fun aFieldCanHaveSeveralErrors() {
        val long = "x".repeat(X01Config.MAX_NAME_LENGTH + 1)

        val invalid = SetupState(names = listOf(long, long)).start() as SetupResult.Invalid

        assertEquals(listOf(X01ConfigError.NAME_TOO_LONG, X01ConfigError.DUPLICATE_NAME), invalid.setup.errorsFor(0))
    }

    @Test
    fun errorsThatAreNotAboutANameAreReportedAtScreenLevel() {
        val invalid = SetupState(startScore = 401, names = listOf("Ann")).start() as SetupResult.Invalid

        assertEquals(listOf(X01ConfigError.INVALID_START_SCORE), invalid.setup.screenErrors)
        assertTrue(invalid.setup.errorsFor(0).isEmpty())
    }

    @Test
    fun anyChangeClearsTheErrors() {
        val invalid = (SetupState().start() as SetupResult.Invalid).setup
        val edits = listOf<(SetupState) -> SetupState>(
            { it.withName(0, "Ann") },
            { it.withStartScore(301) },
            { it.withCheckoutRule(CheckoutRule.SINGLE_OUT) },
            { it.withPlayerAdded() },
            { it.withPlayerRemoved(0) },
            { it.shuffled(Random(SEED)) },
        )

        assertTrue(invalid.errorsFor(0).isNotEmpty())
        edits.forEach { edit ->
            val edited = edit(invalid)
            assertTrue(edited.names.indices.all { edited.errorsFor(it).isEmpty() })
            assertTrue(edited.screenErrors.isEmpty())
        }
    }

    private companion object {
        const val SEED = 42
    }
}
