package io.github.darkcosmos.kosmodarts.ui.format

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.text.dartWords

/** Short text of a dart: 20, D20, T20, 25, Bull or Miss. */
@Composable
fun dartText(dart: Dart): String = dartText(LocalResources.current, dart)

/** Short text of a dart from [resources], for use outside composition (measurement, tests). */
fun dartText(resources: Resources, dart: Dart): String = when (dart.kind) {
    DartKind.SINGLE -> resources.getString(R.string.dart_single, dart.segment)
    DartKind.DOUBLE -> resources.getString(R.string.dart_double, dart.segment)
    DartKind.TRIPLE -> resources.getString(R.string.dart_triple, dart.segment)
    DartKind.OUTER_BULL -> resources.getString(R.string.dart_outer_bull)
    DartKind.BULL -> resources.getString(R.string.dart_bull)
    DartKind.MISS -> resources.getString(R.string.dart_miss)
}

/** Darts separated by spaces, for example "T20 T20 D5". */
@Composable
fun dartsText(darts: List<Dart>): String = darts.map { dartText(it) }.joinToString(" ")

/** A checkout route as shown, for example "T20 · T20 · Bull" (docs/rules/checkout-suggestions.md, CS7). */
@Composable
fun checkoutRouteText(route: List<Dart>): String = checkoutRouteText(LocalResources.current, route)

/** A checkout route as shown, from [resources], for use outside composition (measurement, tests). */
fun checkoutRouteText(resources: Resources, route: List<Dart>): String =
    route.joinToString(resources.getString(R.string.checkout_route_separator)) { dartText(resources, it) }

/** A checkout route as read by TalkBack, for example "Triple 20, Triple 20, Bull" (CS9). */
@Composable
fun checkoutSpokenText(route: List<Dart>): String {
    val resources = LocalResources.current
    val separator = stringResource(R.string.checkout_spoken_separator)
    return route.mapNotNull { dartWords(resources, it) }.joinToString(separator)
}

/** Three-dart average in the current language, or a dash if no dart was thrown. */
@Composable
fun averageText(average: Double?): String = if (average == null) {
    stringResource(R.string.game_average_none)
} else {
    formatAverage(average, LocalConfiguration.current.locales[0])
}
