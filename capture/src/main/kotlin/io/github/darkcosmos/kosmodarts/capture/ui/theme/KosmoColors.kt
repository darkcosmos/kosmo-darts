package io.github.darkcosmos.kosmodarts.capture.ui.theme

// Copied from :app (issue #41, decision Q11); not kept in sync.

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
    /** [danger] is too dark for text on [background] (about 2.9:1); this lighter red is used for danger text. */
    val dangerText: Color,
    val onDanger: Color,
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
            dangerText = Color(0xFFFF8A80),
            onDanger = Color(0xFFFFFFFF),
        )
    }
}

/** The colors of the current theme; [KosmoDartsTheme] provides [KosmoColors.Default]. */
val LocalKosmoColors = staticCompositionLocalOf { KosmoColors.Default }
