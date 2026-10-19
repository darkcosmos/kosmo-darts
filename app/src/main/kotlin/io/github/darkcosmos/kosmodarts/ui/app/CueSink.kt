package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.x01.X01Cue

/** Receives the cues of each game action, once, when it happens (docs/rules/announcements.md, AN7). */
fun interface CueSink {
    fun onCues(cues: List<X01Cue>)
}
