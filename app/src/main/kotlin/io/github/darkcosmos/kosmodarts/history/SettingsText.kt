package io.github.darkcosmos.kosmodarts.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule

/**
 * The versioned settings text of `match_record.settings` (ADR 0012, "Tables"): `v=1;` then `key=value` pairs joined
 * with `;`, in a fixed order. X01: `start`, `out` (`double`/`single`), `legs` (legs per set), `sets`. Cricket:
 * `variant` (`standard`/`cut-throat`), `bull` (`1`/`0`), `legs`, `sets`.
 */
object SettingsText {
    /** Version of the text, also stored in `match_record.settings_version`. */
    const val VERSION: Int = 1

    private const val VERSION_KEY = "v"
    private const val START = "start"
    private const val OUT = "out"
    private const val VARIANT = "variant"
    private const val BULL = "bull"
    private const val LEGS = "legs"
    private const val SETS = "sets"
    private const val PAIR_SEPARATOR = ";"
    private const val KEY_SEPARATOR = "="

    private val outValues = mapOf(CheckoutRule.DOUBLE_OUT to "double", CheckoutRule.SINGLE_OUT to "single")
    private val variantValues = mapOf(CricketVariant.STANDARD to "standard", CricketVariant.CUT_THROAT to "cut-throat")
    private val bullValues = mapOf(true to "1", false to "0")

    /** The settings text of [settings], version [VERSION]. */
    fun encode(settings: ArchivedSettings): String {
        val modePairs = when (settings) {
            is ArchivedSettings.X01 -> listOf(
                START to settings.startScore.toString(),
                OUT to outValues.getValue(settings.checkoutRule),
            )
            is ArchivedSettings.Cricket -> listOf(
                VARIANT to variantValues.getValue(settings.cricketVariant),
                BULL to bullValues.getValue(settings.isBullInPlay),
            )
        }
        val pairs = listOf(VERSION_KEY to VERSION.toString()) + modePairs + listOf(
            LEGS to settings.format.legsPerSet.toString(),
            SETS to settings.format.sets.toString(),
        )
        return pairs.joinToString(PAIR_SEPARATOR) { (key, value) -> key + KEY_SEPARATOR + value }
    }

    /**
     * The settings of [text], a text written by [encode] for a match of [mode].
     *
     * @throws IllegalStateException when [text] is not a settings text of version [VERSION] (a database fault,
     *   mapped to [DbResult.Failed] by [dbResult]).
     */
    fun decode(text: String, mode: ArchivedMode): ArchivedSettings {
        val pairs = text.split(PAIR_SEPARATOR).map { pair ->
            val parts = pair.split(KEY_SEPARATOR)
            check(parts.size == 2) { "Malformed settings pair" }
            parts[0] to parts[1]
        }
        val values = pairs.toMap()
        check(values.size == pairs.size) { "Repeated settings key" }
        check(values[VERSION_KEY] == VERSION.toString()) { "Unsupported settings version" }
        val format = try {
            MatchFormat(legsPerSet = values.number(LEGS), sets = values.number(SETS))
        } catch (invalid: IllegalArgumentException) {
            throw IllegalStateException("Invalid settings format", invalid)
        }
        return if (mode == ArchivedMode.CRICKET) {
            check(values.keys == setOf(VERSION_KEY, VARIANT, BULL, LEGS, SETS)) { "Unexpected settings keys" }
            ArchivedSettings.Cricket(
                cricketVariant = variantValues.keyOf(values.getValue(VARIANT)),
                isBullInPlay = bullValues.keyOf(values.getValue(BULL)),
                format = format,
            )
        } else {
            check(values.keys == setOf(VERSION_KEY, START, OUT, LEGS, SETS)) { "Unexpected settings keys" }
            ArchivedSettings.X01(
                startScore = values.number(START),
                checkoutRule = outValues.keyOf(values.getValue(OUT)),
                format = format,
            )
        }
    }

    private fun Map<String, String>.number(key: String): Int =
        checkNotNull(get(key)?.takeIf { it.all(Char::isDigit) }?.toIntOrNull()) { "Invalid settings number" }

    private fun <K> Map<K, String>.keyOf(value: String): K =
        checkNotNull(entries.firstOrNull { it.value == value }?.key) { "Unknown settings value" }
}
