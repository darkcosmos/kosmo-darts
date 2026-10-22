package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.InvalidX01ConfigException
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01LegSummary

/**
 * A body key of the save files (docs/adr/0001-save-format.md, docs/adr/0005-save-format-v2-match.md,
 * docs/adr/0006-save-format-v3-colors.md). Each codec
 * defines, once per version, the ordered list of keys of its files (its layout); [writeBody] and [readBody] follow
 * that list, so any missing, extra, duplicated or out-of-order key is [InvalidSaveReason.MALFORMED] (S2).
 *
 * [PLAYER] and [LEG] are written once per item: as many lines as the [PLAYERS] and [LEGS] values just before them.
 */
internal enum class SaveKey(val text: String) {
    GAME("game"),
    START_SCORE("start-score"),
    CHECKOUT("checkout"),
    LEGS_PER_SET("legs-per-set"),
    SETS("sets"),
    PLAYERS("players"),
    PLAYER("player"),
    COLORS("colors"),
    LEGS("legs"),
    LEG("leg"),
    DARTS("darts"),
    AWAITING("awaiting-next-player"),
    BETWEEN_LEGS("between-legs"),
}

/**
 * Everything a save body can hold. A key absent from a layout keeps its default value: a version 1 file is a single
 * game (format 1 and 1, no confirmed leg, not between legs; rule R16), and a file before version 3 gives each player
 * the color slot of his position (R18).
 */
internal data class SaveBody(
    val config: X01Config,
    val format: MatchFormat = MatchFormat(),
    val legs: List<X01LegSummary> = emptyList(),
    val darts: List<Dart> = emptyList(),
    val isAwaitingNextPlayer: Boolean = false,
    val isBetweenLegs: Boolean = false,
    val colorSlots: List<ColorSlot> = ColorSlot.byPosition(config.players.size),
)

/** Largest number of darts that is saved, in the current leg and in each confirmed leg (rule R12). */
internal const val MAX_SAVED_DARTS: Int = 10_000

private const val GAME_X01 = "x01"
private const val POINTS_DARTS_SEPARATOR = '/'

private val CHECKOUT_TOKENS = mapOf(CheckoutRule.DOUBLE_OUT to "double-out", CheckoutRule.SINGLE_OUT to "single-out")

/** The lines of [body], in the order of [layout]. */
internal fun writeBody(layout: List<SaveKey>, body: SaveBody): List<Pair<String, String>> = layout.flatMap { key ->
    val values = when (key) {
        SaveKey.GAME -> listOf(GAME_X01)
        SaveKey.START_SCORE -> listOf(body.config.startScore.toString())
        SaveKey.CHECKOUT -> listOf(CHECKOUT_TOKENS.getValue(body.config.checkoutRule))
        SaveKey.LEGS_PER_SET -> listOf(body.format.legsPerSet.toString())
        SaveKey.SETS -> listOf(body.format.sets.toString())
        SaveKey.PLAYERS -> listOf(body.config.players.size.toString())
        SaveKey.PLAYER -> body.config.players.map(SaveFormat::encodeName)
        SaveKey.COLORS -> listOf(body.colorSlots.joinToString(" ") { it.number.toString() })
        SaveKey.LEGS -> listOf(body.legs.size.toString())
        SaveKey.LEG -> body.legs.map(::legToken)
        SaveKey.DARTS -> listOf(body.darts.joinToString(" ") { DartTokens.tokenOf(it) })
        SaveKey.AWAITING -> listOf(body.isAwaitingNextPlayer.toString())
        SaveKey.BETWEEN_LEGS -> listOf(body.isBetweenLegs.toString())
    }
    values.map { key.text to it }
}

/**
 * Reads the lines of [layout] in order and checks each value as it is read (ADR 0001, "Validation order", steps 6
 * and 7): syntax and range ([InvalidSaveReason.OUT_OF_RANGE]), then the configuration with the engine rules
 * ([InvalidSaveReason.INVALID_CONFIG]) as soon as the names are read, then each confirmed leg with the engine
 * ([InvalidSaveReason.OUT_OF_RANGE]). Does not check that every line was read: the caller calls [BodyReader.finish].
 */
internal fun readBody(reader: BodyReader, layout: List<SaveKey>): SaveBody {
    val fields = BodyFields()
    layout.forEach { key -> fields.read(key, reader) }
    return fields.toBody()
}

/** The values read so far, in layout order. */
private class BodyFields {
    private var startScore = X01Config.DEFAULT_START_SCORE
    private var checkoutRule = CheckoutRule.DOUBLE_OUT
    private var legsPerSet = MatchFormat.MIN
    private var sets = MatchFormat.MIN
    private var playerCount = 0
    private var config: X01Config? = null
    private var colorSlots: List<ColorSlot>? = null
    private var legCount = 0
    private var legs: List<X01LegSummary> = emptyList()
    private var darts: List<Dart> = emptyList()
    private var isAwaiting = false
    private var isBetweenLegs = false

    fun read(key: SaveKey, reader: BodyReader) {
        val value = { reader.value(key.text) }
        when (key) {
            SaveKey.GAME -> ensure(value() == GAME_X01, InvalidSaveReason.OUT_OF_RANGE)
            SaveKey.START_SCORE -> startScore = parseStartScore(value())
            SaveKey.CHECKOUT -> checkoutRule = parseCheckout(value())
            SaveKey.LEGS_PER_SET -> legsPerSet = parseFormatValue(value())
            SaveKey.SETS -> sets = parseFormatValue(value())
            SaveKey.PLAYERS -> playerCount = parsePlayerCount(value())
            SaveKey.PLAYER -> config = buildConfig(List(playerCount) { SaveFormat.decodeName(value()) })
            SaveKey.COLORS -> colorSlots = parseColorSlots(value(), playerCount)
            SaveKey.LEGS -> legCount = parseLegCount(value())
            SaveKey.LEG -> legs = List(legCount) { parseLeg(value()) }
            SaveKey.DARTS -> darts = parseDarts(value())
            SaveKey.AWAITING -> isAwaiting = parseBoolean(value())
            SaveKey.BETWEEN_LEGS -> isBetweenLegs = parseBoolean(value())
        }
    }

    fun toBody(): SaveBody {
        val players = checkNotNull(config) { "Every layout has player lines" }
        return SaveBody(
            config = players,
            format = MatchFormat(legsPerSet, sets),
            legs = legs,
            darts = darts,
            isAwaitingNextPlayer = isAwaiting,
            isBetweenLegs = isBetweenLegs,
            // R18: a file without a colors line gives each player the slot of his position.
            colorSlots = colorSlots ?: ColorSlot.byPosition(players.players.size),
        )
    }

    private fun buildConfig(names: List<String>): X01Config = try {
        X01Config(names, startScore, checkoutRule)
    } catch (e: InvalidX01ConfigException) {
        reject(InvalidSaveReason.INVALID_CONFIG, e)
    }

    /** M12: at most the number of legs a match of this format can have. Read after the format and the players. */
    private fun parseLegCount(value: String): Int {
        val count = SaveFormat.parseInteger(value)
        ensure(count <= MatchFormat(legsPerSet, sets).maxLegs(playerCount), InvalidSaveReason.OUT_OF_RANGE)
        return count
    }

    /**
     * A `leg` value: the winner index, then `points/darts` for each player in configuration order. The ranges are
     * the engine's ([X01LegSummary]); the codec only adds the darts limit of rule R12.
     */
    private fun parseLeg(value: String): X01LegSummary {
        val leg = checkNotNull(config) { "Player lines come before leg lines" }
        val tokens = value.split(' ')
        ensure(tokens.size == leg.players.size + 1, InvalidSaveReason.MALFORMED)
        val winner = SaveFormat.parseInteger(tokens.first())
        val figures = tokens.drop(1).map { token ->
            val parts = token.split(POINTS_DARTS_SEPARATOR)
            ensure(parts.size == 2, InvalidSaveReason.MALFORMED)
            SaveFormat.parseInteger(parts[0]) to SaveFormat.parseInteger(parts[1])
        }
        ensure(figures.all { (_, darts) -> darts <= MAX_SAVED_DARTS }, InvalidSaveReason.OUT_OF_RANGE)
        return try {
            X01LegSummary(leg, winner, figures.map { it.first }, figures.map { it.second })
        } catch (e: IllegalArgumentException) {
            reject(InvalidSaveReason.OUT_OF_RANGE, e)
        }
    }
}

private fun legToken(leg: X01LegSummary): String =
    (listOf(leg.winnerIndex.toString()) + leg.pointsScored.indices.map { player ->
        "${leg.pointsScored[player]}$POINTS_DARTS_SEPARATOR${leg.dartsThrown[player]}"
    }).joinToString(" ")

private fun parseStartScore(value: String): Int {
    val startScore = SaveFormat.parseInteger(value)
    ensure(startScore in X01Config.START_SCORES, InvalidSaveReason.OUT_OF_RANGE)
    return startScore
}

private fun parseCheckout(value: String): CheckoutRule =
    CHECKOUT_TOKENS.entries.firstOrNull { it.value == value }?.key ?: reject(InvalidSaveReason.OUT_OF_RANGE)

/** M1: legs per set and sets are each from [MatchFormat.MIN] to [MatchFormat.MAX]. */
private fun parseFormatValue(value: String): Int {
    val number = SaveFormat.parseInteger(value)
    ensure(number in MatchFormat.MIN..MatchFormat.MAX, InvalidSaveReason.OUT_OF_RANGE)
    return number
}

private fun parsePlayerCount(value: String): Int {
    val count = SaveFormat.parseInteger(value)
    ensure(count in 1..X01Config.MAX_PLAYERS, InvalidSaveReason.OUT_OF_RANGE)
    return count
}

/**
 * A `colors` value (R17, ADR 0006): one slot number per player, 1 to 4, separated by single spaces, all distinct
 * (PC1). A wrong count or an empty token is [InvalidSaveReason.MALFORMED]; a value outside 1 to 4 or a repeated slot
 * is [InvalidSaveReason.OUT_OF_RANGE].
 */
private fun parseColorSlots(value: String, playerCount: Int): List<ColorSlot> {
    val tokens = value.split(' ')
    ensure(tokens.size == playerCount && tokens.none(String::isEmpty), InvalidSaveReason.MALFORMED)
    val slots = tokens.map { ColorSlot.ofNumber(SaveFormat.parseInteger(it)) ?: reject(InvalidSaveReason.OUT_OF_RANGE) }
    ensure(slots.toSet().size == slots.size, InvalidSaveReason.OUT_OF_RANGE)
    return slots
}

private fun parseDarts(value: String): List<Dart> {
    if (value.isEmpty()) return emptyList()
    // Count before splitting, so that an oversized list is rejected without building it.
    ensure(value.count { it == ' ' } < MAX_SAVED_DARTS, InvalidSaveReason.OUT_OF_RANGE)
    return value.split(' ').map { token ->
        ensure(token.isNotEmpty(), InvalidSaveReason.MALFORMED)
        DartTokens.dartOf(token) ?: reject(InvalidSaveReason.OUT_OF_RANGE)
    }
}

private fun parseBoolean(value: String): Boolean = when (value) {
    "true" -> true
    "false" -> false
    else -> reject(InvalidSaveReason.OUT_OF_RANGE)
}
