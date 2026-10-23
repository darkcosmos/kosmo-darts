package io.github.darkcosmos.kosmodarts.ui.setup

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatchConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01ConfigError
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.persistence.CricketSettings
import io.github.darkcosmos.kosmodarts.persistence.LastSetups
import io.github.darkcosmos.kosmodarts.persistence.SharedRoster
import io.github.darkcosmos.kosmodarts.persistence.X01Settings
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Cricket setup (docs/rules/cricket.md, CR21; docs/rules/persistence.md, R22). */
class SetupStateCricketTest {

    private val roster = SharedRoster(
        listOf("Cid", "Ann", "Bob"),
        listOf(ColorSlot.SLOT_3, ColorSlot.SLOT_1, ColorSlot.SLOT_4),
    )

    /** CR21: Standard, 25-Bull on and a single game by default. */
    @Test
    fun `CR21 the defaults are Standard with 25-Bull and a single game`() {
        val setup = SetupState.prefilled(null, GameMode.CRICKET)

        assertEquals(GameMode.CRICKET, setup.mode)
        assertEquals(CricketVariant.STANDARD, setup.cricketVariant)
        assertTrue(setup.isBullInPlay)
        assertEquals(1 to 1, setup.legsPerSet to setup.sets)
        assertEquals(listOf("", ""), setup.names)
    }

    /** CR21, R22: the shared roster with its slots and the last Cricket settings prefill the setup. */
    @Test
    fun `R22 prefilled takes the roster and the last Cricket settings`() {
        val last = LastSetups(
            roster,
            x01 = X01Settings(301, CheckoutRule.SINGLE_OUT, MatchFormat(2, 3)),
            cricket = CricketSettings(CricketVariant.CUT_THROAT, isBullInPlay = false, MatchFormat(4, 1)),
        )

        val setup = SetupState.prefilled(last, GameMode.CRICKET)

        assertEquals(listOf("Cid", "Ann", "Bob"), setup.names)
        assertEquals(roster.colorSlots, setup.roster.slots)
        assertEquals(CricketVariant.CUT_THROAT, setup.cricketVariant)
        assertEquals(false, setup.isBullInPlay)
        assertEquals(4 to 1, setup.legsPerSet to setup.sets)
    }

    /** R22: a mode never started uses its own defaults with the shared roster, not the other mode's settings. */
    @Test
    fun `R22 a Cricket setup without Cricket settings takes the roster and the Cricket defaults`() {
        val last = LastSetups(roster, x01 = X01Settings(301, CheckoutRule.SINGLE_OUT, MatchFormat(2, 3)))

        val setup = SetupState.prefilled(last, GameMode.CRICKET)

        assertEquals(listOf("Cid", "Ann", "Bob"), setup.names)
        assertEquals(CricketVariant.STANDARD, setup.cricketVariant)
        assertTrue(setup.isBullInPlay)
        assertEquals(1 to 1, setup.legsPerSet to setup.sets)
    }

    /** R22: the X01 setup is unchanged by the Cricket settings. */
    @Test
    fun `R22 the X01 prefill ignores the Cricket settings`() {
        val last = LastSetups(
            roster,
            x01 = X01Settings(301, CheckoutRule.SINGLE_OUT, MatchFormat(2, 1)),
            cricket = CricketSettings(CricketVariant.CUT_THROAT, false, MatchFormat(4, 1)),
        )

        val setup = SetupState.prefilled(last, GameMode.X01)

        assertEquals(GameMode.X01, setup.mode)
        assertEquals(301, setup.startScore)
        assertEquals(CheckoutRule.SINGLE_OUT, setup.checkoutRule)
        assertEquals(2 to 1, setup.legsPerSet to setup.sets)
    }

    /** CR21: the variant and the Bull can be changed. */
    @Test
    fun `CR21 the settings can be changed`() {
        val setup = SetupState(mode = GameMode.CRICKET)
            .withCricketVariant(CricketVariant.CUT_THROAT)
            .withBullInPlay(false)

        assertEquals(CricketVariant.CUT_THROAT, setup.cricketVariant)
        assertEquals(false, setup.isBullInPlay)
    }

    /** CR21, CR1: start returns a Cricket match config with the roster, the slots and the format. */
    @Test
    fun `CR21 start returns the Cricket config`() {
        val setup = SetupState.prefilled(LastSetups(roster), GameMode.CRICKET)
            .withCricketVariant(CricketVariant.CUT_THROAT)
            .withBullInPlay(false)
            .withLegsPerSet(3)

        assertEquals(
            SetupResult.ValidCricket(
                CricketMatchConfig(
                    CricketConfig(listOf("Cid", "Ann", "Bob"), CricketVariant.CUT_THROAT, isBullInPlay = false),
                    MatchFormat(3, 1),
                    roster.colorSlots,
                ),
            ),
            setup.start(),
        )
    }

    /** CR21: an X01 setup still starts an X01 game, whatever the Cricket settings. */
    @Test
    fun `CR20 an X01 setup starts an X01 game`() {
        val setup = SetupState(names = listOf("Ann", "Bob")).withCricketVariant(CricketVariant.CUT_THROAT)

        assertEquals(
            SetupResult.Valid(X01MatchConfig(X01Config(listOf("Ann", "Bob")), MatchFormat(1, 1))),
            setup.start(),
        )
    }

    /** CR1, C4: the name errors of the shared list rules point at their fields, as in X01. */
    @Test
    fun `CR1 name errors of a Cricket start are shown on their fields`() {
        val setup = SetupState(names = listOf("Ann", " ann", "")).copy(mode = GameMode.CRICKET)

        val invalid = setup.start() as SetupResult.Invalid

        assertEquals(listOf(X01ConfigError.DUPLICATE_NAME), invalid.setup.errorsFor(0))
        assertEquals(listOf(X01ConfigError.DUPLICATE_NAME), invalid.setup.errorsFor(1))
        assertEquals(listOf(X01ConfigError.BLANK_NAME), invalid.setup.errorsFor(2))
        assertEquals(GameMode.CRICKET, invalid.setup.mode)
    }

    /** CR1: editing any field clears the errors, the settings included. */
    @Test
    fun `CR1 a change clears the errors`() {
        val invalid = SetupState(names = listOf("", "Bob")).copy(mode = GameMode.CRICKET).start() as SetupResult.Invalid

        assertTrue(invalid.setup.withBullInPlay(false).nameErrors.isEmpty())
    }
}
