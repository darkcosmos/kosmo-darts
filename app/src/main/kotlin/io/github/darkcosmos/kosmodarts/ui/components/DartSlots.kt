package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.ui.format.dartText
import io.github.darkcosmos.kosmodarts.ui.game.GameTags
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoShapes
import io.github.darkcosmos.kosmodarts.ui.theme.KosmoTheme

private const val DARTS_PER_TURN = 3

/** Height of a dart slot and of the filled visit box next to it (fixed: the dart style ignores the font scale). */
val DART_SLOT_HEIGHT = 46.dp

private val UNDERLINE = 4.dp

/**
 * The three darts of the turn (design system, `DartSlots`): `background` boxes; the slot of the next dart underlined
 * in `accent`; with [isFinalDartHighlighted], the last dart (the winning one) in `accent`. Each slot is one TalkBack
 * item, "Dart 2: T20". The compact Cricket zone passes a lower [slotHeight].
 */
@Composable
fun DartSlots(
    darts: List<Dart>,
    nextDartIndex: Int?,
    isFinalDartHighlighted: Boolean,
    modifier: Modifier = Modifier,
    slotHeight: Dp = DART_SLOT_HEIGHT,
) {
    val colors = KosmoTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        repeat(DARTS_PER_TURN) { index ->
            val dart = darts.getOrNull(index)
            val text = dart?.let { dartText(it) }.orEmpty()
            val description = if (dart == null) {
                stringResource(R.string.game_dart_slot_empty, index + 1)
            } else {
                stringResource(R.string.game_dart_slot, index + 1, text)
            }
            val isFinal = isFinalDartHighlighted && index == darts.lastIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(slotHeight)
                    .clip(KosmoShapes.control)
                    .background(colors.background)
                    .testTag(GameTags.dartSlot(index))
                    .clearAndSetSemantics { contentDescription = description },
            ) {
                Text(
                    text = text,
                    style = KosmoTheme.typography.dart,
                    color = if (isFinal) colors.accent else colors.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                if (index == nextDartIndex) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(UNDERLINE)
                            .background(colors.accent)
                            .testTag(GameTags.NEXT_DART_MARK),
                    )
                }
            }
        }
    }
}
