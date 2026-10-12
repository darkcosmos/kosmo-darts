package io.github.darkcosmos.kosmodarts.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KosmoDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB4A9),
    onPrimary = Color(0xFF690004),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFF8CD5A0),
    onSecondary = Color(0xFF003919),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E1E0),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE6E1E0),
    onSurfaceVariant = Color(0xFFC8C2C0),
)

/** Kosmo Darts theme. The app is always dark, whatever the system setting. */
@Composable
fun KosmoDartsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KosmoDarkColorScheme,
        content = content,
    )
}
