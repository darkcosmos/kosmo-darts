package io.github.darkcosmos.kosmodarts.capture.ui.theme

// Copied from :app (issue #41, decision Q11); not kept in sync.

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.darkcosmos.kosmodarts.capture.R

/** Width axis of every display style: condensed (design system, section 3). */
const val SAIRA_DISPLAY_WIDTH: Float = 75f

private const val WEIGHT_BOLD = 700
private const val WEIGHT_EXTRA_BOLD = 800

/** The embedded Saira variable font (res/font, OFL 1.1), condensed, in [weight]. Never downloaded. */
@OptIn(ExperimentalTextApi::class)
fun sairaFont(weight: Int, width: Float = SAIRA_DISPLAY_WIDTH): Font = Font(
    R.font.saira_variable,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

/** Saira condensed, in the two weights the design system uses (700 and 800). */
val SairaCondensed: FontFamily = FontFamily(sairaFont(WEIGHT_BOLD), sairaFont(WEIGHT_EXTRA_BOLD))

/** Tabular figures on every number (design system, section 3). */
private const val TABULAR_FIGURES = "tnum"

/**
 * Tabular figures with the distinct zero of Saira (its `zero` feature), for the short labels where a 0 sits next to
 * letters, "S0 · M0", so it never reads as the letter O (U14).
 */
const val LABEL_FIGURES: String = "tnum, zero"

/** Line height of the display styles, relative to their size: one line takes 1.2 times the size. */
const val DISPLAY_LINE_HEIGHT: Float = 1.2f

/**
 * Text styles of the design system (section 3). Display styles use [SairaCondensed] with tabular figures; body text
 * uses the system font. The "fixed" styles ([scoreHuge], [keypad], [dart], [visit], [tileScore]) are built with
 * [fixedSp] for the given density, so they ignore the font size setting; the others follow it.
 */
@Immutable
data class KosmoTypography(
    /** Remaining score of the current player; its size is set by the layout policy (220 portrait, 160 landscape). */
    val scoreHuge: TextStyle,
    val keypad: TextStyle,
    val keypadLabel: TextStyle,
    val dart: TextStyle,
    val visit: TextStyle,
    val tileScore: TextStyle,
    val tileScoreSmall: TextStyle,
    val route: TextStyle,
    val playerNameZone: TextStyle,
    val playerNameTile: TextStyle,
    val label: TextStyle,
    val labelLarge: TextStyle,
    val button: TextStyle,
    val title: TextStyle,
    val banner: TextStyle,
    val winnerName: TextStyle,
    val body: TextStyle,
    val bodyLarge: TextStyle,
) {
    companion object {
        /** Sizes in dp of the fixed styles (design system, section 3). */
        const val SCORE_PORTRAIT: Float = 220f
        const val SCORE_PORTRAIT_FOUR_PLAYERS: Float = 204f
        const val SCORE_LANDSCAPE: Float = 160f
        private const val KEYPAD = 28f
        private const val KEYPAD_LABEL = 20f
        private const val DART = 26f
        private const val VISIT = 28f
        private const val TILE_SCORE = 32f
        private const val TILE_SCORE_FOUR_PLAYERS = 28f

        /** The styles for [density]: the fixed ones in dp whatever its font scale. */
        fun create(density: Density): KosmoTypography = KosmoTypography(
            scoreHuge = display(density.fixedSp(SCORE_PORTRAIT), WEIGHT_EXTRA_BOLD).copy(
                lineHeight = 1.em,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            keypad = display(density.fixedSp(KEYPAD), WEIGHT_EXTRA_BOLD),
            keypadLabel = display(density.fixedSp(KEYPAD_LABEL), WEIGHT_EXTRA_BOLD, letterSpacing = 0.04.em),
            dart = display(density.fixedSp(DART), WEIGHT_BOLD),
            visit = display(density.fixedSp(VISIT), WEIGHT_EXTRA_BOLD),
            tileScore = display(density.fixedSp(TILE_SCORE), WEIGHT_EXTRA_BOLD),
            tileScoreSmall = display(density.fixedSp(TILE_SCORE_FOUR_PLAYERS), WEIGHT_EXTRA_BOLD),
            route = display(28.sp, WEIGHT_BOLD, letterSpacing = 0.04.em),
            playerNameZone = display(28.sp, WEIGHT_EXTRA_BOLD, letterSpacing = 0.04.em),
            playerNameTile = display(18.sp, WEIGHT_BOLD, letterSpacing = 0.04.em),
            label = display(14.sp, WEIGHT_BOLD, letterSpacing = 0.1.em),
            labelLarge = display(18.sp, WEIGHT_BOLD, letterSpacing = 0.06.em),
            button = display(20.sp, WEIGHT_EXTRA_BOLD, letterSpacing = 0.06.em),
            title = display(32.sp, WEIGHT_EXTRA_BOLD, letterSpacing = 0.04.em),
            banner = display(64.sp, WEIGHT_EXTRA_BOLD, letterSpacing = 0.04.em),
            winnerName = display(96.sp, WEIGHT_EXTRA_BOLD, letterSpacing = 0.02.em),
            body = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
            bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        )

        private fun display(size: TextUnit, weight: Int, letterSpacing: TextUnit = 0.em): TextStyle = TextStyle(
            fontFamily = SairaCondensed,
            fontWeight = FontWeight(weight),
            fontSize = size,
            lineHeight = DISPLAY_LINE_HEIGHT.em,
            letterSpacing = letterSpacing,
            fontFeatureSettings = TABULAR_FIGURES,
        )
    }
}

/** The typography of the current theme; [KosmoDartsTheme] provides it for the current density. */
val LocalKosmoTypography = staticCompositionLocalOf { KosmoTypography.create(Density(1f)) }
