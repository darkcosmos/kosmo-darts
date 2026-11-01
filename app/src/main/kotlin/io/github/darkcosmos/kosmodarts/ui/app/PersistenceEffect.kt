package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.persistence.LastSetups
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.persistence.SavedCricketGame
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.ui.cricket.toSavedGame
import io.github.darkcosmos.kosmodarts.ui.game.toSavedGame

/** A change to make on the device after an event (rules R3, R6, R7, R12, R13, R15). */
sealed interface PersistenceEffect {
    data class SaveGame(val saved: StoredGame) : PersistenceEffect

    data object DeleteGame : PersistenceEffect

    data class SaveLastSetup(val setups: LastSetups) : PersistenceEffect

    /** PF2: create or touch the profiles of [names] at [at], the match start time. */
    data class TouchProfiles(val names: List<String>, val at: Long) : PersistenceEffect
}

/**
 * The game that must be on the device while [state] is shown (ADR 0002, decision 2). No `else` branch: every new
 * screen must state whether it carries the saved game, since `null` deletes it.
 */
fun savedGameOf(state: AppState): StoredGame? = when (val screen = state.screen) {
    is Screen.Game -> screen.game.toSavedGame(screen.journal)
    is Screen.Home -> screen.resumableGame
    // R14, R15: the match stays saved between legs, with the screen to come back to.
    is Screen.BetweenLegs ->
        SavedGame(screen.match, isAwaitingNextPlayer = false, isBetweenLegs = true, journal = screen.journal)
    is Screen.Setup -> null
    // R6, R14: the saved match is deleted when its win is confirmed.
    is Screen.End -> null
    // Opening Settings from home keeps the saved game of that home screen.
    is Screen.Settings -> screen.home.resumableGame
    // R12, R20, R21: a Cricket game is saved like an X01 one, between legs included, and deleted at its end.
    is Screen.CricketGame -> screen.game.toSavedGame(screen.journal)
    is Screen.CricketBetweenLegs ->
        SavedCricketGame(screen.match, isAwaitingNextPlayer = false, isBetweenLegs = true, journal = screen.journal)
    is Screen.CricketEnd -> null
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
    return listOfNotNull(setupEffect, gameEffect, touchProfilesEffect(before, after))
}

/**
 * PF2: Start (from the setup) and Rematch (from an end screen) open a new game; its players' profiles are created or
 * touched at the match start time. Any other change of screen touches nothing.
 */
private fun touchProfilesEffect(before: AppState, after: AppState): PersistenceEffect.TouchProfiles? {
    val isStart = before.screen is Screen.Setup || before.screen is Screen.End || before.screen is Screen.CricketEnd
    if (!isStart) return null
    return when (val screen = after.screen) {
        is Screen.Game ->
            PersistenceEffect.TouchProfiles(screen.game.match.config.players, screen.journal.startedAt)
        is Screen.CricketGame ->
            PersistenceEffect.TouchProfiles(screen.game.match.config.players, screen.journal.startedAt)
        else -> null
    }
}

/** Applies [effects] in order; writes run in the background. */
fun Persistence.applyEffects(effects: List<PersistenceEffect>) = effects.forEach { effect ->
    when (effect) {
        is PersistenceEffect.SaveGame -> saveGame(effect.saved)
        PersistenceEffect.DeleteGame -> deleteGame()
        is PersistenceEffect.SaveLastSetup -> saveLastSetup(effect.setups)
        is PersistenceEffect.TouchProfiles -> touchProfiles(effect.names, effect.at)
    }
}
