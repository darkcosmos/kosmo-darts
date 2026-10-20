package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.ui.game.toSavedGame

/** A change to make on the device after an event (rules R3, R6, R7, R12). */
sealed interface PersistenceEffect {
    data class SaveGame(val saved: SavedGame) : PersistenceEffect

    data object DeleteGame : PersistenceEffect

    data class SaveLastSetup(val config: X01Config) : PersistenceEffect
}

/**
 * The game that must be on the device while [state] is shown (ADR 0002, decision 2). No `else` branch: every new
 * screen must state whether it carries the saved game, since `null` deletes it.
 */
fun savedGameOf(state: AppState): SavedGame? = when (val screen = state.screen) {
    is Screen.Game -> screen.game.toSavedGame()
    is Screen.Home -> screen.resumableGame
    is Screen.Setup -> null
    is Screen.End -> null
    // Opening Settings from home keeps the saved game of that home screen.
    is Screen.Settings -> screen.home.resumableGame
}

/** What to write or delete after the event that changed [before] into [after]. Pure. */
fun persistenceEffects(before: AppState, after: AppState): List<PersistenceEffect> {
    val savedBefore = savedGameOf(before)
    val savedAfter = savedGameOf(after)
    val gameEffect = when {
        savedAfter == savedBefore -> null
        savedAfter == null -> PersistenceEffect.DeleteGame
        else -> PersistenceEffect.SaveGame(savedAfter)
    }
    val setupEffect = after.lastSetup?.takeIf { it != before.lastSetup }?.let(PersistenceEffect::SaveLastSetup)
    return listOfNotNull(setupEffect, gameEffect)
}

/** Applies [effects] in order; writes run in the background. */
fun Persistence.applyEffects(effects: List<PersistenceEffect>) = effects.forEach { effect ->
    when (effect) {
        is PersistenceEffect.SaveGame -> saveGame(effect.saved)
        PersistenceEffect.DeleteGame -> deleteGame()
        is PersistenceEffect.SaveLastSetup -> saveLastSetup(effect.config)
    }
}
