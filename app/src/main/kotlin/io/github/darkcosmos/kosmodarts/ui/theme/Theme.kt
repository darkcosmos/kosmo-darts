package io.github.darkcosmos.kosmodarts.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity

/**
 * The Material 3 colors mapped from the tokens (docs/adr/0007-ui-design-tokens.md), so that the remaining Material
 * components (dialogs, text fields, switches) follow the design system: background, surfaces, primary = `accent`,
 * error = `danger`. No dynamic color.
 */
private fun materialColors(colors: KosmoColors) = darkColorScheme(
    primary = colors.accent,
    onPrimary = colors.onAccent,
    primaryContainer = colors.accent,
    onPrimaryContainer = colors.onAccent,
    secondary = colors.surfaceHigh,
    onSecondary = colors.onBackground,
    secondaryContainer = colors.surfaceHigh,
    onSecondaryContainer = colors.onBackground,
    background = colors.background,
    onBackground = colors.onBackground,
    surface = colors.surface,
    onSurface = colors.onBackground,
    surfaceVariant = colors.surfaceHigh,
    onSurfaceVariant = colors.onBackgroundMuted,
    surfaceContainer = colors.surface,
    surfaceContainerHigh = colors.surface,
    surfaceContainerHighest = colors.surfaceHigh,
    outline = colors.outline,
    outlineVariant = colors.outline,
    error = colors.danger,
    onError = colors.onDanger,
)

/** Kosmo Darts theme. The app is always dark, whatever the system setting. */
@Composable
fun KosmoDartsTheme(
    animationSettings: AnimationSettings = AnimationSettings.System,
    content: @Composable () -> Unit,
) {
    val colors = KosmoColors.Default
    val density = LocalDensity.current
    val typography = remember(density) { KosmoTypography.create(density) }
    CompositionLocalProvider(
        LocalKosmoColors provides colors,
        LocalKosmoTypography provides typography,
        LocalAnimationSettings provides animationSettings,
    ) {
        MaterialTheme(
            colorScheme = materialColors(colors),
            shapes = Shapes(
                extraSmall = KosmoShapes.control,
                small = KosmoShapes.control,
                medium = KosmoShapes.card,
                large = KosmoShapes.card,
                extraLarge = KosmoShapes.card,
            ),
            typography = Typography(),
            content = content,
        )
    }
}

/** Access to the design tokens of the current theme. */
object KosmoTheme {
    val colors: KosmoColors
        @Composable
        @ReadOnlyComposable
        get() = LocalKosmoColors.current

    val typography: KosmoTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalKosmoTypography.current
}
