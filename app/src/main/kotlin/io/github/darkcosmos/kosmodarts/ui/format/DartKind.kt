package io.github.darkcosmos.kosmodarts.ui.format

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier

/** How a dart is written on screen; the text itself comes from string resources. */
enum class DartKind { SINGLE, DOUBLE, TRIPLE, OUTER_BULL, BULL, MISS }

val Dart.kind: DartKind
    get() = when (segment) {
        Dart.MISS_SEGMENT -> DartKind.MISS
        Dart.OUTER_BULL_SEGMENT -> DartKind.OUTER_BULL
        Dart.BULL_SEGMENT -> DartKind.BULL
        else -> when (multiplier) {
            Multiplier.SINGLE -> DartKind.SINGLE
            Multiplier.DOUBLE -> DartKind.DOUBLE
            Multiplier.TRIPLE -> DartKind.TRIPLE
        }
    }
