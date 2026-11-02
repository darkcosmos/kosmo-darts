package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketLegSummary
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.cricket.InvalidCricketConfigException
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/**
 * The body of a Cricket saved game, after its mode line (docs/adr/0008-save-format-v4-modes.md; rules R21,
 * R24). Its lines, in this order, each once:
 *
 * `variant`, `bull`, `legs-per-set`, `sets`, `players`, one `player` per player, `colors`, `legs`, one `leg` per
 * confirmed leg, `darts`, `awaiting-next-player`, `between-legs`.
 */
internal data class CricketSaveBody(
    val config: CricketConfig,
    val format: MatchFormat,
    val colorSlots: List<ColorSlot>,
    val legs: List<CricketLegSummary>,
    val darts: List<Dart>,
    val isAwaitingNextPlayer: Boolean,
    val isBetweenLegs: Boolean,
)

internal const val VARIANT_KEY = "variant"
internal const val BULL_KEY = "bull"

/** R24: the variant tokens, a closed set. */
internal val CRICKET_VARIANT_TOKENS: Map<CricketVariant, String> =
    mapOf(CricketVariant.STANDARD to "standard", CricketVariant.CUT_THROAT to "cut-throat")

/** R24: the 25-Bull tokens, a closed set: `1` when the Bull is in play, `0` without it. */
internal val CRICKET_BULL_TOKENS: Map<Boolean, String> = mapOf(true to "1", false to "0")

/** Values per player in a `leg` line: marks hit, darts, points, board marks. */
private const val LEG_FIGURES = 4

/** Whether every figure of [body] can be written (R12, R24): no leg over the darts limit or over 99,999 points. */
internal fun isWritable(body: CricketSaveBody): Boolean = body.darts.size <= MAX_SAVED_DARTS &&
    body.legs.all { leg ->
        leg.dartsThrown.all { it <= MAX_SAVED_DARTS } && leg.points.all { it <= SaveFormat.MAX_INTEGER }
    }

/** The lines of [body], in the order of [CricketSaveBody]; the caller writes the mode line before them. */
internal fun writeCricketBody(body: CricketSaveBody): List<Pair<String, String>> = buildList {
    add(VARIANT_KEY to CRICKET_VARIANT_TOKENS.getValue(body.config.variant))
    add(BULL_KEY to CRICKET_BULL_TOKENS.getValue(body.config.isBullInPlay))
    add(SaveKey.LEGS_PER_SET.text to body.format.legsPerSet.toString())
    add(SaveKey.SETS.text to body.format.sets.toString())
    add(SaveKey.PLAYERS.text to body.config.players.size.toString())
    body.config.players.forEach { add(SaveKey.PLAYER.text to SaveFormat.encodeName(it)) }
    add(SaveKey.COLORS.text to body.colorSlots.joinToString(" ") { it.number.toString() })
    add(SaveKey.LEGS.text to body.legs.size.toString())
    body.legs.forEach { add(SaveKey.LEG.text to cricketLegToken(it)) }
    add(SaveKey.DARTS.text to body.darts.joinToString(" ") { DartTokens.tokenOf(it) })
    add(SaveKey.AWAITING.text to body.isAwaitingNextPlayer.toString())
    add(SaveKey.BETWEEN_LEGS.text to body.isBetweenLegs.toString())
}

/**
 * Reads the Cricket body lines in order and checks each value as it is read, as [readBody] does for X01: syntax and
 * range ([InvalidSaveReason.OUT_OF_RANGE]), the configuration as soon as the names are read
 * ([InvalidSaveReason.INVALID_CONFIG], an [InvalidCricketConfigException] never escapes), then each confirmed leg with
 * the engine ranges of [CricketLegSummary]. Does not check that every line was read: the caller calls
 * [BodyReader.finish].
 */
internal fun readCricketBody(reader: BodyReader): CricketSaveBody {
    val variant = parseToken(reader.value(VARIANT_KEY), CRICKET_VARIANT_TOKENS)
    val isBullInPlay = parseToken(reader.value(BULL_KEY), CRICKET_BULL_TOKENS)
    val legsPerSet = parseFormatValue(reader.value(SaveKey.LEGS_PER_SET.text))
    val sets = parseFormatValue(reader.value(SaveKey.SETS.text))
    val format = MatchFormat(legsPerSet, sets)
    val playerCount = parsePlayerCount(reader.value(SaveKey.PLAYERS.text))
    val names = List(playerCount) { SaveFormat.decodeName(reader.value(SaveKey.PLAYER.text)) }
    val config = try {
        CricketConfig(names, variant, isBullInPlay)
    } catch (e: InvalidCricketConfigException) {
        reject(InvalidSaveReason.INVALID_CONFIG, e)
    }
    val colorSlots = parseColorSlots(reader.value(SaveKey.COLORS.text), playerCount)
    val legCount = parseLegCount(reader.value(SaveKey.LEGS.text), format, playerCount)
    val legs = List(legCount) { parseCricketLeg(reader.value(SaveKey.LEG.text), config) }
    return CricketSaveBody(
        config = config,
        format = format,
        colorSlots = colorSlots,
        legs = legs,
        darts = parseDarts(reader.value(SaveKey.DARTS.text)),
        isAwaitingNextPlayer = parseBoolean(reader.value(SaveKey.AWAITING.text)),
        isBetweenLegs = parseBoolean(reader.value(SaveKey.BETWEEN_LEGS.text)),
    )
}

/** The value whose token is [token], or [InvalidSaveReason.OUT_OF_RANGE] for any other text. */
internal fun <T> parseToken(token: String, tokens: Map<T, String>): T =
    tokens.entries.firstOrNull { it.value == token }?.key ?: reject(InvalidSaveReason.OUT_OF_RANGE)

/**
 * A Cricket `leg` value: the winner index, then `marks/darts/points/board` for each player in configuration order.
 * Each figure is a canonical integer of at most five digits; the darts are at most the limit of R12, and every other
 * range is the engine's ([CricketLegSummary]: winner index, marks at most 3 per dart, board marks; R24).
 */
private fun parseCricketLeg(value: String, config: CricketConfig): CricketLegSummary {
    val tokens = value.split(' ')
    ensure(tokens.size == config.players.size + 1, InvalidSaveReason.MALFORMED)
    val winner = SaveFormat.parseInteger(tokens.first())
    val figures = tokens.drop(1).map { token ->
        val parts = token.split(FIGURE_SEPARATOR)
        ensure(parts.size == LEG_FIGURES, InvalidSaveReason.MALFORMED)
        parts.map(SaveFormat::parseInteger)
    }
    ensure(figures.all { it[DARTS_FIGURE] <= MAX_SAVED_DARTS }, InvalidSaveReason.OUT_OF_RANGE)
    return try {
        CricketLegSummary(
            config = config,
            winnerIndex = winner,
            marksHit = figures.map { it[MARKS_FIGURE] },
            dartsThrown = figures.map { it[DARTS_FIGURE] },
            points = figures.map { it[POINTS_FIGURE] },
            boardMarks = figures.map { it[BOARD_FIGURE] },
        )
    } catch (e: IllegalArgumentException) {
        reject(InvalidSaveReason.OUT_OF_RANGE, e)
    }
}

private fun cricketLegToken(leg: CricketLegSummary): String =
    (listOf(leg.winnerIndex.toString()) + leg.marksHit.indices.map { player ->
        listOf(leg.marksHit, leg.dartsThrown, leg.points, leg.boardMarks)
            .joinToString(FIGURE_SEPARATOR.toString()) { it[player].toString() }
    }).joinToString(" ")

private const val MARKS_FIGURE = 0
private const val DARTS_FIGURE = 1
private const val POINTS_FIGURE = 2
private const val BOARD_FIGURE = 3
