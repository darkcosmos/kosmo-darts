package io.github.darkcosmos.kosmodarts.engine.player

/**
 * Color slot of a player (docs/rules/players.md, PC1). The engine knows nothing of colors: the UI maps slot n to its
 * n-th player color (PC9). Declared in slot order, which is the order free slots are given in (PC2).
 */
enum class ColorSlot {
    SLOT_1,
    SLOT_2,
    SLOT_3,
    SLOT_4,
    ;

    /** 1 to 4, as written in the save files. */
    val number: Int
        get() = ordinal + 1

    companion object {
        /** The slot written [number] (1 to 4), or `null`. */
        fun ofNumber(number: Int): ColorSlot? = entries.getOrNull(number - 1)

        /**
         * Slots 1 to [count], one per player by position: the default of a match and of older saves (PC5, R18).
         *
         * @throws IllegalArgumentException if [count] is not from 1 to the number of slots.
         */
        fun byPosition(count: Int): List<ColorSlot> {
            require(count in 1..entries.size) { "count must be from 1 to ${entries.size}" }
            return entries.take(count)
        }
    }
}

/** Thrown when a list of color slots does not give each player its own slot (PC1, PC5). */
class InvalidColorSlotsException(message: String) : IllegalArgumentException(message)

/**
 * Checks PC1 and PC5: exactly [playerCount] slots, all distinct.
 *
 * @throws InvalidColorSlotsException otherwise.
 */
fun requireValidColorSlots(slots: List<ColorSlot>, playerCount: Int) {
    if (slots.size != playerCount) throw InvalidColorSlotsException("Expected $playerCount color slots")
    if (slots.toSet().size != slots.size) throw InvalidColorSlotsException("Two players share a color slot")
}
