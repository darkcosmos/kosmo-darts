package io.github.darkcosmos.kosmodarts.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/** Short text of a dart: 20, D20, T20, 25, Bull or Miss. */
@Composable
fun dartText(dart: Dart): String = when (dart.kind) {
    DartKind.SINGLE -> stringResource(R.string.dart_single, dart.segment)
    DartKind.DOUBLE -> stringResource(R.string.dart_double, dart.segment)
    DartKind.TRIPLE -> stringResource(R.string.dart_triple, dart.segment)
    DartKind.OUTER_BULL -> stringResource(R.string.dart_outer_bull)
    DartKind.BULL -> stringResource(R.string.dart_bull)
    DartKind.MISS -> stringResource(R.string.dart_miss)
}

/** Darts separated by spaces, for example "T20 T20 D5". */
@Composable
fun dartsText(darts: List<Dart>): String = darts.map { dartText(it) }.joinToString(" ")

/** Three-dart average in the current language, or a dash if no dart was thrown. */
@Composable
fun averageText(average: Double?): String = if (average == null) {
    stringResource(R.string.game_average_none)
} else {
    formatAverage(average, LocalConfiguration.current.locales[0])
}
