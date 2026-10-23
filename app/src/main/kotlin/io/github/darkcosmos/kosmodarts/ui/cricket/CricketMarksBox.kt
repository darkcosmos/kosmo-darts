package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVisit
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

private val BOX_PADDING = 6.dp

/** The marks of a visit (CR14): at most 9, so one digit. "Not played" shows 0, like the X01 visit box (VT8). */
val CricketVisit.marksCount: Int
    get() = when (this) {
        CricketVisit.NotPlayed -> 0
        is CricketVisit.InProgress -> marks
        is CricketVisit.LastVisit -> marks
    }

/** TalkBack description of a visit, with the mark `<plurals>` (review U2): "Last turn: 3 marks". */
@Composable
fun visitDescription(visit: CricketVisit): String = when (visit) {
    CricketVisit.NotPlayed -> stringResource(R.string.game_visit_not_played_description)
    is CricketVisit.InProgress ->
        pluralStringResource(R.plurals.cricket_current_visit_marks, visit.marks, visit.marks)
    is CricketVisit.LastVisit -> pluralStringResource(R.plurals.cricket_last_visit_marks, visit.marks, visit.marks)
}

/**
 * The marks of a visit in a box (design system, `VisitBox` system of issue #49): filled (white, text `background`)
 * in the zone, outlined (border `onBackgroundMuted`) in a board column header. One TalkBack item with [description].
 */
@Composable
fun CricketMarksBox(
    visit: CricketVisit,
    isFilled: Boolean,
    style: TextStyle,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = KosmoTheme.colors
    val description = visitDescription(visit)
    Surface(
        color = if (isFilled) colors.onBackground else Color.Transparent,
        contentColor = if (isFilled) colors.background else colors.onBackground,
        border = if (isFilled) null else BorderStroke(1.dp, colors.onBackgroundMuted),
        shape = KosmoShapes.control,
        modifier = modifier
            .widthIn(min = BOX_MIN_WIDTH)
            .testTag(tag)
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.game_visit_value, visit.marksCount),
                style = style,
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.fillMaxWidth().padding(horizontal = BOX_PADDING),
            )
        }
    }
}

private val BOX_MIN_WIDTH = 28.dp
