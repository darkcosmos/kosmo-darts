package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.profile.withProfileDeleted
import io.github.darkcosmos.kosmodarts.engine.profile.withProfileMerged
import io.github.darkcosmos.kosmodarts.engine.profile.withProfileRenamed
import io.github.darkcosmos.kosmodarts.history.DbResult
import io.github.darkcosmos.kosmodarts.history.MergeResult
import io.github.darkcosmos.kosmodarts.history.RenameResult
import io.github.darkcosmos.kosmodarts.persistence.LastSetups
import io.github.darkcosmos.kosmodarts.persistence.SavedCricketGame
import io.github.darkcosmos.kosmodarts.persistence.SavedGame
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileAnswer
import io.github.darkcosmos.kosmodarts.ui.profile.ProfileRequest

internal fun isMerged(answer: ProfileAnswer.Merged): Boolean =
    (answer.result as? DbResult.Ok)?.value == MergeResult.MERGED

/** PM9: the last setup after a successful [request]; a failure, a refusal or any other request changes nothing. */
internal fun LastSetups.afterAnswer(request: ProfileRequest, answer: ProfileAnswer): LastSetups = when {
    request is ProfileRequest.Rename && answer is ProfileAnswer.Renamed ->
        (answer.result as? DbResult.Ok)?.value.let { result ->
            if (result is RenameResult.Renamed) {
                withRosterUpdated { it.withProfileRenamed(request.profile.nameKey, result.name) }
            } else {
                this
            }
        }
    request is ProfileRequest.Merge && answer is ProfileAnswer.Merged ->
        if (isMerged(answer)) {
            withRosterUpdated { it.withProfileMerged(request.source.nameKey, request.target.name) }
        } else {
            this
        }
    request is ProfileRequest.Delete && answer is ProfileAnswer.Deleted ->
        if (answer.result is DbResult.Ok) withRosterUpdated { it.withProfileDeleted(request.profile.nameKey) } else this
    else -> this
}

/** The names of the players of the saved game, whatever its mode (PM8). */
internal fun StoredGame.playerNames(): List<String> = when (this) {
    is SavedGame -> match.matchConfig.config.players
    is SavedCricketGame -> match.matchConfig.config.players
}
