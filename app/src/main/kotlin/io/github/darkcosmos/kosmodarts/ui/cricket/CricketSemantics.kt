package io.github.darkcosmos.kosmodarts.ui.cricket

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

/** Custom semantics of the Cricket board (CR23, review T3). */
object CricketSemantics {
    /** Whether a board row is dead: closed by every player, so it no longer scores (CR15). Set on every row. */
    val RowDead: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("CricketRowDead")
}

/** Marks a board row as dead or live for the semantics tree. */
var SemanticsPropertyReceiver.rowDead: Boolean
    get() = throw UnsupportedOperationException("RowDead is write-only")
    set(value) {
        this[CricketSemantics.RowDead] = value
    }
