package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatch
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.end.EndContent
import io.github.darkcosmos.kosmodarts.ui.end.endRows

/**
 * End of a Cricket match, a single game included (CR27, M17): the layout of the X01 end screen with a PTS column (the
 * points of the last leg, none in a solo game), "MPR MATCH" in place of the average, and the darts thrown.
 */
@Composable
fun CricketEndScreen(match: CricketMatch, onEvent: (AppEvent) -> Unit, modifier: Modifier = Modifier) {
    EndContent(
        rows = endRows(match),
        formatLine = cricketFormatText(LocalResources.current, match.config, match.format),
        mode = GameMode.CRICKET,
        onEvent = onEvent,
        modifier = modifier,
    )
}
