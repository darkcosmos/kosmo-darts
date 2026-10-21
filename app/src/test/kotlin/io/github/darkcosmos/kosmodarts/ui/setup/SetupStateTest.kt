package io.github.darkcosmos.kosmodarts.ui.setup

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

    @Test
    fun removingAPlayerRemovesTheLastFieldDownToOne() {
        val one = SetupState(names = listOf("Ann", "Bob")).withLastPlayerRemoved()

        assertEquals(listOf("Ann"), one.names)
        assertFalse(one.canRemovePlayer)
        assertEquals(one, one.withLastPlayerRemoved())
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
            { it.withLastPlayerRemoved() },
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
