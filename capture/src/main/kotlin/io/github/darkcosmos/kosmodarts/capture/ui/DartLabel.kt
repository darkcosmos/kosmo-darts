package io.github.darkcosmos.kosmodarts.capture.ui

import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier

/** What a dart slot of the volley readout shows: a fixed text (`T20`, `D5`, `5`, `25`) or a translated name. */
sealed interface DartLabel {
    /** Text that is the same in every language. */
    data class Text(val value: String) : DartLabel

    /** The bull, shown with the translated name resource. */
    data object Bull : DartLabel

    /** A miss, shown with the translated name resource. */
    data object Miss : DartLabel

    companion object {
        /** Label of [dart] for the readout (rule CP3). */
        fun of(dart: Dart): DartLabel = when {
            dart.segment == Dart.MISS_SEGMENT -> Miss
            dart.segment == Dart.BULL_SEGMENT -> Bull
            dart.segment == Dart.OUTER_BULL_SEGMENT -> Text(dart.segment.toString())
            dart.multiplier == Multiplier.TRIPLE -> Text("T${dart.segment}")
            dart.multiplier == Multiplier.DOUBLE -> Text("D${dart.segment}")
            else -> Text(dart.segment.toString())
        }
    }
}
