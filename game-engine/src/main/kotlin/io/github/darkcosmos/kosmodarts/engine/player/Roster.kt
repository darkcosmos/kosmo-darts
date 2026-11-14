package io.github.darkcosmos.kosmodarts.engine.player

import kotlin.random.Random

/** A player of the setup: the name as typed (validated later by the mode configuration) and his color. */
data class RosterEntry(val name: String, val color: PlayerColor)

/**
 * The players of a setup, in throwing order, each with his own color (docs/rules/players.md, PC1 to PC4).
 * Immutable: every change returns a new roster. Names are not validated here (X01 rules C4, C5).
 *
 * @throws InvalidPlayerColorsException if [entries] is empty, has more than [MAX_PLAYERS] players, or two entries
 *   share a color.
 */
class Roster(entries: List<RosterEntry>) {

    /** The players, in throwing order. */
    val entries: List<RosterEntry> = entries.toList()

    init {
        if (this.entries.size !in 1..MAX_PLAYERS) throw InvalidPlayerColorsException("A roster has 1 to 4 players")
        requireValidColors(colors, this.entries.size)
    }

    /** Names, in throwing order. */
    val names: List<String>
        get() = entries.map(RosterEntry::name)

    /** Colors, in throwing order. */
    val colors: List<PlayerColor>
        get() = entries.map(RosterEntry::color)

    val size: Int
        get() = entries.size

    /** Whether a player can still be added. */
    val canAdd: Boolean
        get() = entries.size < MAX_PLAYERS

    /** Whether a player can be removed: at least one stays. */
    val canRemove: Boolean
        get() = entries.size > 1

    /**
     * PC2: a new player with an empty name and the first palette color acceptable against the others, else a color
     * generated with [random]; unchanged when full.
     */
    fun withPlayerAdded(random: Random = Random.Default): Roster {
        if (!canAdd) return this
        return Roster(entries + RosterEntry("", ColorChoices.defaultFor(colors, random)))
    }

    /** PC3: removes the player at [index], whose color becomes free; unchanged with one player or a wrong index. */
    fun withPlayerRemoved(index: Int): Roster =
        if (canRemove && index in entries.indices) Roster(entries.filterIndexed { i, _ -> i != index }) else this

    /** The player at [index] renamed [name], with his color. */
    fun withName(index: Int, name: String): Roster =
        Roster(entries.mapIndexed { i, entry -> if (i == index) entry.copy(name = name) else entry })

    /** PC4: a new throwing order drawn with [random]; each player keeps his color. */
    fun shuffled(random: Random): Roster = Roster(entries.shuffled(random))

    override fun equals(other: Any?): Boolean = other is Roster && entries == other.entries

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "Roster(entries=$entries)"

    companion object {
        /** Maximum number of players of a setup. */
        const val MAX_PLAYERS: Int = PlayerListRules.MAX_PLAYERS

        /** PC1: [count] players with empty names and the palette colors 1 to [count]. */
        fun initial(count: Int): Roster = Roster(PlayerColor.byPosition(count).map { RosterEntry("", it) })

        /** [names] in order, with their [colors] (PC8). */
        fun of(names: List<String>, colors: List<PlayerColor>): Roster {
            requireValidColors(colors, names.size)
            return Roster(names.zip(colors, ::RosterEntry))
        }
    }
}
