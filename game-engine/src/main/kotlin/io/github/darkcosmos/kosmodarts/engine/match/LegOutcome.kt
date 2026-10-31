package io.github.darkcosmos.kosmodarts.engine.match

/** What confirming a won leg decides (docs/rules/match.md, M7). */
enum class LegOutcome {
    /** The leg only: neither a set nor the match. */
    LEG,

    /** A set, but not the match. */
    SET,

    /** The match. A single game always decides the match (M5). */
    MATCH,
    ;

    /**
     * Whether a won leg with this outcome is confirmed into the match (M7, M8): a [LEG] or a [SET] is, while the leg
     * that decides the [MATCH] stays the current leg, whose figures break ties (M10).
     */
    val isConfirmable: Boolean
        get() = this != MATCH
}
