package io.github.darkcosmos.kosmodarts.ui.app

import androidx.compose.ui.graphics.toArgb
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMatch
import io.github.darkcosmos.kosmodarts.persistence.LastSetups
import io.github.darkcosmos.kosmodarts.persistence.Persistence
import io.github.darkcosmos.kosmodarts.persistence.SavedCricketGame
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.ui.cricket.toSavedGame
import io.github.darkcosmos.kosmodarts.ui.game.ArchiveStatus
import io.github.darkcosmos.kosmodarts.ui.game.toSavedGame
import io.github.darkcosmos.kosmodarts.ui.theme.PlayerPalette

/** A change to make on the device after an event (rules R3, R6, R7, R12, R13, R15). */
sealed interface PersistenceEffect {
    data class SaveGame(val saved: StoredGame) : PersistenceEffect

    data object DeleteGame : PersistenceEffect

    data class SaveLastSetup(val setups: LastSetups) : PersistenceEffect

    /** PF2: create or touch the profiles of [names] at [at], the match start time. */
    data class TouchProfiles(val names: List<String>, val at: Long) : PersistenceEffect

    /** PF3: read the profiles for the suggestions; the answer comes back as [AppEvent.ProfilesLoaded]. */
    data object LoadProfiles : PersistenceEffect

    /**
     * AR1, AR2: write [archived] to the history with one ARGB color per player, then delete the saved game, only if the
     * write succeeded. The answer comes back as [AppEvent.ArchiveSucceeded] or [AppEvent.ArchiveFailed].
     */
    data class ArchiveMatch(val archived: ArchivedMatch, val colorsArgb: List<Int>) : PersistenceEffect
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
        // AR2: the archive request deletes the saved game itself, after the write; no second delete here.
        archiveOf(before) != null -> null
        savedAfter == null -> PersistenceEffect.DeleteGame
        else -> PersistenceEffect.SaveGame(savedAfter)
    }
    val setupEffect = after.lastSetup?.takeIf { it != before.lastSetup }?.let(PersistenceEffect::SaveLastSetup)
    return listOfNotNull(
        setupEffect,
        gameEffect,
        touchProfilesEffect(before, after),
        archiveEffect(before, after),
        loadProfilesEffect(before, after),
    )
}

/** PF3: the profiles are read each time the setup opens (and only then). */
private fun loadProfilesEffect(before: AppState, after: AppState): PersistenceEffect.LoadProfiles? =
    PersistenceEffect.LoadProfiles.takeIf { after.screen is Screen.Setup && before.screen !is Screen.Setup }

/** The match being written to the history while [state] is shown (AR3), or `null`. */
private fun archiveOf(state: AppState): ArchivedMatch? {
    val archive = when (val screen = state.screen) {
        is Screen.Game -> screen.game.archive
        is Screen.CricketGame -> screen.game.archive
        else -> return null
    }
    return (archive as? ArchiveStatus.Saving)?.archived
}

/** AR1: asked once per tap on the confirm button, when [after] starts writing and [before] was not writing. */
private fun archiveEffect(before: AppState, after: AppState): PersistenceEffect.ArchiveMatch? {
    val archived = archiveOf(after)?.takeIf { archiveOf(before) == null } ?: return null
    val colors = archived.players.map { PlayerPalette.colorOf(it.colorSlot).toArgb() }
    return PersistenceEffect.ArchiveMatch(archived, colors)
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

/**
 * Applies [effects] in order; writes run in the background. The answer of an archive is handed to [onEvent], on the
 * persistence thread: the caller moves it to the main thread.
 */
fun Persistence.applyEffects(effects: List<PersistenceEffect>, onEvent: (AppEvent) -> Unit = {}) =
    effects.forEach { effect ->
        when (effect) {
            is PersistenceEffect.SaveGame -> saveGame(effect.saved)
            PersistenceEffect.DeleteGame -> deleteGame()
            is PersistenceEffect.SaveLastSetup -> saveLastSetup(effect.setups)
            is PersistenceEffect.TouchProfiles -> touchProfiles(effect.names, effect.at)
            PersistenceEffect.LoadProfiles -> loadProfiles { onEvent(AppEvent.ProfilesLoaded(it)) }
            is PersistenceEffect.ArchiveMatch ->
                archiveMatch(effect.archived, effect.colorsArgb) { isArchived ->
                    onEvent(if (isArchived) AppEvent.ArchiveSucceeded else AppEvent.ArchiveFailed)
                }
        }
    }
