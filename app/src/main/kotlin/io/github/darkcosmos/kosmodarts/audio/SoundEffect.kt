package io.github.darkcosmos.kosmodarts.audio

/** The seven sound effects of `res/raw` (docs/rules/announcements.md, AN9; credits in docs/sounds/CREDITS.md). */
enum class SoundEffect {
    DART_HIT,
    DART_MISS,
    DART_CANCEL,
    BUST,
    ONE_EIGHTY,

    /** AN30: a Cricket dart with marks on a target already closed by every player (CR15). */
    CLOSED_SECTOR,

    /** Any win (A.2): replaced the game shot and big fish sounds. */
    VICTORY,
}
