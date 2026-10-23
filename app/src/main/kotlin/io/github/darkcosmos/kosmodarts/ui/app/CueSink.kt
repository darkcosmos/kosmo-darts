package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketCue
import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue

/** Receives the cues of each game action, once, when it happens (docs/rules/announcements.md, AN7, AN29). */
fun interface CueSink {
    /** The X01 cues of an action ([gameCues]). */
    fun onCues(cues: List<X01Cue>)

    /** The Cricket cues of an action ([cricketGameCues]); ignored unless the sink plays Cricket (AN29). */
    fun onCricketCues(cues: List<CricketCue>) = Unit
}
