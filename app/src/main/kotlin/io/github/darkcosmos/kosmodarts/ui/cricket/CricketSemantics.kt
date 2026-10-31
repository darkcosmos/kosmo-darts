package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/** Custom semantics of the Cricket board (CR23, review T3). */
object CricketSemantics {
    /** Whether a board row is dead: closed by every player, so it no longer scores (CR15). Set on every row. */
    val RowDead: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("CricketRowDead")

    /** The number of dots drawn under the number of a target button: 0, 2 or 3 (CR24). Set on every button. */
    val DotCount: SemanticsPropertyKey<Int> = SemanticsPropertyKey("CricketDotCount")
}

/** Sets [CricketSemantics.DotCount] on the dots of a target button. */
var SemanticsPropertyReceiver.dotCount: Int
    get() = throw UnsupportedOperationException("DotCount is write-only")
    set(value) {
        this[CricketSemantics.DotCount] = value
    }

/** Marks a board row as dead or live for the semantics tree. */
var SemanticsPropertyReceiver.rowDead: Boolean
    get() = throw UnsupportedOperationException("RowDead is write-only")
    set(value) {
        this[CricketSemantics.RowDead] = value
    }
