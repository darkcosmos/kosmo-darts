package io.github.darkcosmos.kosmodarts.engine.cricket

/** How surplus marks score in a Cricket leg (docs/rules/cricket.md, CR1, CR4, CR5). */
enum class CricketVariant {
    /** Surplus marks score for the thrower; the highest score wins (CR4, CR8). */
    STANDARD,

    /** Surplus marks score for the opponents who have not closed the target; the lowest score wins (CR5, CR8). */
    CUT_THROAT,
}
