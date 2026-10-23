package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.persistence.LastSetups
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import kotlin.random.Random

// Parts of [AppReducer] about the setup and the start of a match. Pure, except for the injected random of Shuffle.

/** The setup after an editing [event]; any other event leaves it unchanged. */
internal fun editSetup(setup: SetupState, event: AppEvent, random: Random): SetupState = when (event) {
    is AppEvent.StartScoreSelected -> setup.withStartScore(event.score)
    is AppEvent.CheckoutRuleSelected -> setup.withCheckoutRule(event.rule)
    is AppEvent.CricketVariantSelected -> setup.withCricketVariant(event.variant)
    is AppEvent.BullInPlayToggled -> setup.withBullInPlay(event.isInPlay)
    is AppEvent.NameChanged -> setup.withName(event.index, event.name)
    AppEvent.PlayerAdded -> setup.withPlayerAdded()
    is AppEvent.PlayerRemoved -> setup.withPlayerRemoved(event.index)
    AppEvent.PlayersShuffled -> setup.shuffled(random)
    // M13: the steppers, clamped by the setup.
    is AppEvent.LegsPerSetChanged -> setup.withLegsPerSet(setup.legsPerSet + event.delta)
    is AppEvent.SetsChanged -> setup.withSets(setup.sets + event.delta)
    else -> setup
}

/** The setup screen of [mode], prefilled with the last setup of that mode (R7, R13, R22, CR21). */
internal fun AppState.toSetup(mode: GameMode): AppState =
    copy(screen = Screen.Setup(SetupState.prefilled(lastSetup, mode)))

/**
 * A new match from Start or Rematch: its roster and X01 settings become the last setup, and the settings of the other
 * modes in [previous] are kept (R7, R13, R22).
 */
internal fun newMatchState(setup: X01MatchConfig, previous: LastSetups?): AppState = AppState(
    screen = Screen.Game(GameScreenState(X01Match.start(setup)), isNewGame = true),
    lastSetup = LastSetups.afterX01(previous, setup),
)

/**
 * The setup of a rematch (M17, owner Q3): same players, settings and format; the first player moves to the end, so
 * the next one starts the first leg (A, B, C -> B, C, A). Each player keeps his color slot (PC7).
 */
internal fun rematchSetup(match: X01Match): X01MatchConfig {
    val config = match.config
    val rotated = X01Config(config.players.drop(1) + config.players.first(), config.startScore, config.checkoutRule)
    val slots = match.colorSlots
    return X01MatchConfig(rotated, match.format, slots.drop(1) + slots.first())
}
