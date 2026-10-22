package io.github.darkcosmos.kosmodarts.engine.player

import kotlin.random.Random

/** A player of the setup: the name as typed (validated later by the mode configuration) and his color slot. */
data class RosterEntry(val name: String, val slot: ColorSlot)

/**
 * The players of a setup, in throwing order, each with his own color slot (docs/rules/players.md, PC1 to PC4).
 * Immutable: every change returns a new roster. Names are not validated here (X01 rules C4, C5).
 *
 * @throws InvalidColorSlotsException if [entries] is empty, has more players than slots, or two entries share a slot.
 */
class Roster(entries: List<RosterEntry>) {

    /** The players, in throwing order. */
    val entries: List<RosterEntry> = entries.toList()

    init {
        if (this.entries.size !in 1..MAX_PLAYERS) throw InvalidColorSlotsException("A roster has 1 to 4 players")
        requireValidColorSlots(slots, this.entries.size)
    }

    /** Names, in throwing order. */
    val names: List<String>
        get() = entries.map(RosterEntry::name)

    /** Color slots, in throwing order. */
    val slots: List<ColorSlot>
        get() = entries.map(RosterEntry::slot)

    val size: Int
        get() = entries.size

    /** Whether a slot is still free. */
    val canAdd: Boolean
        get() = entries.size < MAX_PLAYERS

    /** Whether a player can be removed: at least one stays. */
    val canRemove: Boolean
        get() = entries.size > 1

    /** PC2: a new player with an empty name and the first free slot in slot order; unchanged when full. */
    fun withPlayerAdded(): Roster {
        if (!canAdd) return this
        val free = ColorSlot.entries.first { it !in slots }
        return Roster(entries + RosterEntry("", free))
    }

    /** PC3: removes the player at [index], whose slot becomes free; unchanged with one player or a wrong index. */
    fun withPlayerRemoved(index: Int): Roster =
        if (canRemove && index in entries.indices) Roster(entries.filterIndexed { i, _ -> i != index }) else this

    /** The player at [index] renamed [name], with his slot. */
    fun withName(index: Int, name: String): Roster =
        Roster(entries.mapIndexed { i, entry -> if (i == index) entry.copy(name = name) else entry })

    /** PC4: a new throwing order drawn with [random]; each player keeps his slot. */
    fun shuffled(random: Random): Roster = Roster(entries.shuffled(random))

    override fun equals(other: Any?): Boolean = other is Roster && entries == other.entries

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "Roster(entries=$entries)"

    companion object {
        /** Number of color slots, hence of players. */
        val MAX_PLAYERS: Int = ColorSlot.entries.size

        /** PC1: [count] players with empty names and slots 1 to [count]. */
        fun initial(count: Int): Roster = Roster(ColorSlot.byPosition(count).map { RosterEntry("", it) })

        /** [names] in order, with their [slots] (PC8). */
        fun of(names: List<String>, slots: List<ColorSlot>): Roster {
            requireValidColorSlots(slots, names.size)
            return Roster(names.zip(slots, ::RosterEntry))
        }
    }
}
