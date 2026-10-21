package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Match
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import io.github.darkcosmos.kosmodarts.ui.setup.SetupState
import kotlin.random.Random

// Parts of [AppReducer] about the setup and the start of a match. Pure, except for the injected random of Shuffle.

/** The setup after an editing [event]; any other event leaves it unchanged. */
internal fun editSetup(setup: SetupState, event: AppEvent, random: Random): SetupState = when (event) {
    is AppEvent.StartScoreSelected -> setup.withStartScore(event.score)
    is AppEvent.CheckoutRuleSelected -> setup.withCheckoutRule(event.rule)
    is AppEvent.NameChanged -> setup.withName(event.index, event.name)
    AppEvent.PlayerAdded -> setup.withPlayerAdded()
    AppEvent.PlayerRemoved -> setup.withLastPlayerRemoved()
    AppEvent.PlayersShuffled -> setup.shuffled(random)
    // M13: the steppers, clamped by the setup.
    is AppEvent.LegsPerSetChanged -> setup.withLegsPerSet(setup.legsPerSet + event.delta)
    is AppEvent.SetsChanged -> setup.withSets(setup.sets + event.delta)
    else -> setup
}

/** A new match from Start or Rematch: its setup becomes the last setup (R7, R13). */
internal fun newMatchState(setup: X01MatchConfig): AppState = AppState(
    screen = Screen.Game(GameScreenState(X01Match.start(setup)), isNewGame = true),
    lastSetup = setup,
)

/**
 * The setup of a rematch (M17, owner Q3): same players, settings and format; the first player moves to the end, so
 * the next one starts the first leg (A, B, C -> B, C, A).
 */
internal fun rematchSetup(match: X01Match): X01MatchConfig {
    val config = match.config
    val rotated = X01Config(config.players.drop(1) + config.players.first(), config.startScore, config.checkoutRule)
    return X01MatchConfig(rotated, match.format)
}
