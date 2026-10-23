package io.github.darkcosmos.kosmodarts.ui.components

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * A centred label that never breaks inside a word (U11): it is drawn at the largest size, from its style's size down to
 * [minFontSize], at which it fits its space on at most [maxLines] lines with every line break at a space. Words are
 * never cut ("ANNUL / ER", "DOUB"); the label only gets smaller.
 */
@Composable
internal fun WordSafeText(
    text: String,
    style: TextStyle,
    maxLines: Int = Int.MAX_VALUE,
    minFontSize: TextUnit = WORD_SAFE_MIN_FONT,
) {
    val autoSize = remember(style.fontSize, minFontSize) { WordSafeAutoSize(minFontSize, style.fontSize) }
    Text(text = text, style = style, textAlign = TextAlign.Center, maxLines = maxLines, autoSize = autoSize)
}

/** Whether every line break of [text] at the given line ends falls at whitespace (U11). */
internal fun breaksOnlyBetweenWords(text: CharSequence, lineEnds: List<Int>): Boolean =
    lineEnds.dropLast(1).all { end ->
        end in 1 until text.length && (text[end - 1].isWhitespace() || text[end].isWhitespace())
    }

/** The number of words of a label (split at spaces): as many lines as it may need without breaking a word. */
internal fun wordCount(text: String): Int = text.split(' ').count { it.isNotEmpty() }.coerceAtLeast(1)

/** The line ends of a laid-out text, for [breaksOnlyBetweenWords]. */
internal fun TextLayoutResult.lineEnds(): List<Int> = (0 until lineCount).map { getLineEnd(it) }

private val WORD_SAFE_MIN_FONT = 8.sp
private val WORD_SAFE_STEP = 0.5.sp

/** Steps down from [max] to [min] until the text fits without overflow and without a break inside a word. */
private class WordSafeAutoSize(private val min: TextUnit, private val max: TextUnit) : TextAutoSize {

    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val minPx = min.toPx()
        val stepPx = WORD_SAFE_STEP.toPx()
        var sizePx = max.toPx()
        while (sizePx > minPx) {
            val layout = performLayout(constraints, text, sizePx.toSp())
            val fits = !layout.didOverflowHeight && !layout.didOverflowWidth &&
                breaksOnlyBetweenWords(text.text, layout.lineEnds())
            if (fits) return sizePx.toSp()
            sizePx -= stepPx
        }
        return min
    }

    override fun equals(other: Any?): Boolean = other is WordSafeAutoSize && other.min == min && other.max == max

    override fun hashCode(): Int = 31 * min.hashCode() + max.hashCode()
}
