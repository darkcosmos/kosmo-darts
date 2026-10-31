package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVisit

/** TalkBack description of a visit, with the mark `<plurals>` (review U2): "Last turn: 3 marks". */
@Composable
fun visitDescription(visit: CricketVisit): String = when (visit) {
    CricketVisit.NotPlayed -> stringResource(R.string.game_visit_not_played_description)
    is CricketVisit.InProgress ->
        pluralStringResource(R.plurals.cricket_current_visit_marks, visit.marks, visit.marks)
    is CricketVisit.LastVisit -> pluralStringResource(R.plurals.cricket_last_visit_marks, visit.marks, visit.marks)
}
