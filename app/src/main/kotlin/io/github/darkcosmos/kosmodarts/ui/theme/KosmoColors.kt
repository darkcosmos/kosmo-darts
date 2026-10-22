package io.github.darkcosmos.kosmodarts.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every color token of the design system (docs/design/design-system.md, section 2). The palette is fixed: no dynamic
 * color, always dark. Composables read it through [KosmoTheme.colors].
 */
@Immutable
data class KosmoColors(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val surfaceHigher: Color,
    val outline: Color,
    val onBackground: Color,
    val onBackgroundMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentDim: Color,
    val danger: Color,
    val onDanger: Color,
    val player1: Color,
    val player2: Color,
    val player3: Color,
    val player4: Color,
    val onPlayer1: Color,
    val onPlayer2: Color,
    val onPlayer3: Color,
    val onPlayer4: Color,
) {
    companion object {
        /** The values of design-system.md, section 2. */
        val Default: KosmoColors = KosmoColors(
            background = Color(0xFF0A0F1E),
            surface = Color(0xFF121A33),
            surfaceHigh = Color(0xFF18213F),
            surfaceHigher = Color(0xFF1B2546),
            outline = Color(0xFF2E3A63),
            onBackground = Color(0xFFFFFFFF),
            onBackgroundMuted = Color(0xFFAEB8D6),
            accent = Color(0xFFFFD23F),
            onAccent = Color(0xFF0A0F1E),
            accentDim = Color(0xFF2A2410),
            danger = Color(0xFFB3261E),
            onDanger = Color(0xFFFFFFFF),
            player1 = Color(0xFF1F63D6),
            player2 = Color(0xFFE07020),
            player3 = Color(0xFF0C7C6A),
            player4 = Color(0xFFC9A7FF),
            onPlayer1 = Color(0xFFFFFFFF),
            onPlayer2 = Color(0xFF0A0F1E),
            onPlayer3 = Color(0xFFFFFFFF),
            onPlayer4 = Color(0xFF0A0F1E),
        )
    }
}

/** The colors of the current theme; [KosmoDartsTheme] provides [KosmoColors.Default]. */
val LocalKosmoColors = staticCompositionLocalOf { KosmoColors.Default }
