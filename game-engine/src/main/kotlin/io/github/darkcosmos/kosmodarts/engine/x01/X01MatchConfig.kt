package io.github.darkcosmos.kosmodarts.engine.x01

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat

/**
 * What the setup produces and the last setup stores: the configuration of every leg and the match format
 * (docs/rules/match.md, M1, M13). The default format is a single game (M5).
 */
data class X01MatchConfig(val config: X01Config, val format: MatchFormat = MatchFormat())
