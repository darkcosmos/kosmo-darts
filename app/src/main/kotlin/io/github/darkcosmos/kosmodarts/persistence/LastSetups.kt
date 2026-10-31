package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatchConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListRules
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig

/**
 * The players shared by every mode (rule R22, owner Q9): names in throwing order, already normalized, with their color
 * slots (R17).
 *
 * @throws IllegalArgumentException if the names break `PlayerListRules` or the slots are not one distinct slot per
 *   player.
 */
data class SharedRoster(val players: List<String>, val colorSlots: List<ColorSlot>) {
    init {
        require(PlayerListRules.normalize(players) == players) { "Roster names must be normalized" }
        require(PlayerListRules.violations(players).isEmpty()) { "Invalid roster names" }
        require(colorSlots.size == players.size && colorSlots.toSet().size == colorSlots.size) {
            "One distinct color slot per player expected"
        }
    }
}

/** The X01 settings of the last X01 game started (R7, R13, R22): start score, checkout rule and match format. */
data class X01Settings(
    val startScore: Int = X01Config.DEFAULT_START_SCORE,
    val checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
    val format: MatchFormat = MatchFormat(),
)

/** The Cricket settings of the last Cricket game started (R22): variant, 25-Bull and match format. */
data class CricketSettings(
    val variant: CricketVariant = CricketVariant.STANDARD,
    val isBullInPlay: Boolean = true,
    val format: MatchFormat = MatchFormat(),
)

/**
 * The last setup (rules R7, R13, R22, R25): the shared [roster] and, per mode, the settings of the last game started
 * in that mode. A `null` mode was never started since the file was written: it uses its defaults, with the roster.
 */
data class LastSetups(
    val roster: SharedRoster,
    val x01: X01Settings? = null,
    val cricket: CricketSettings? = null,
) {
    /** The X01 setup to prefill: the roster with the last X01 settings, or the X01 defaults. */
    val x01Setup: X01MatchConfig
        get() {
            val settings = x01 ?: X01Settings()
            val config = X01Config(roster.players, settings.startScore, settings.checkoutRule)
            return X01MatchConfig(config, settings.format, roster.colorSlots)
        }

    /** The Cricket setup to prefill: the roster with the last Cricket settings, or the Cricket defaults (R25). */
    val cricketSetup: CricketMatchConfig
        get() {
            val settings = cricket ?: CricketSettings()
            val config = CricketConfig(roster.players, settings.variant, settings.isBullInPlay)
            return CricketMatchConfig(config, settings.format, roster.colorSlots)
        }

    /** After an X01 Start or Rematch: its roster and X01 settings; the Cricket settings are kept (R22). */
    fun withX01(setup: X01MatchConfig): LastSetups = copy(roster = rosterOf(setup), x01 = x01SettingsOf(setup))

    /** After a Cricket Start or Rematch: its roster and Cricket settings; the X01 settings are kept (R22). */
    fun withCricket(setup: CricketMatchConfig): LastSetups =
        copy(roster = rosterOf(setup), cricket = cricketSettingsOf(setup))

    companion object {
        /** The last setups after an X01 Start or Rematch, from the [previous] ones if any (R22). */
        fun afterX01(previous: LastSetups?, setup: X01MatchConfig): LastSetups =
            previous?.withX01(setup) ?: LastSetups(rosterOf(setup), x01 = x01SettingsOf(setup))

        /** The last setups after a Cricket Start or Rematch, from the [previous] ones if any (R22). */
        fun afterCricket(previous: LastSetups?, setup: CricketMatchConfig): LastSetups =
            previous?.withCricket(setup) ?: LastSetups(rosterOf(setup), cricket = cricketSettingsOf(setup))

        private fun rosterOf(setup: X01MatchConfig) = SharedRoster(setup.config.players, setup.colorSlots)

        private fun rosterOf(setup: CricketMatchConfig) = SharedRoster(setup.config.players, setup.colorSlots)

        private fun x01SettingsOf(setup: X01MatchConfig) =
            X01Settings(setup.config.startScore, setup.config.checkoutRule, setup.format)

        private fun cricketSettingsOf(setup: CricketMatchConfig) =
            CricketSettings(setup.config.variant, setup.config.isBullInPlay, setup.format)
    }
}
