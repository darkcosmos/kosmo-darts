package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketLegFinishedException
import io.github.darkcosmos.kosmodarts.engine.journal.DartSource
import io.github.darkcosmos.kosmodarts.engine.journal.JournalEvent
import io.github.darkcosmos.kosmodarts.engine.journal.LegJournal
import io.github.darkcosmos.kosmodarts.engine.journal.MatchJournal
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.LegFinishedException

/**
 * The journal lines of the saved game, after the body (docs/adr/0011-save-format-v1-journal.md; rules R26, R28, R29):
 * `match-id`, `started-at`, `journals`, then one `journal` line per leg, the current leg last.
 *
 * The codec only maps lines to [MatchJournal]; the checks against the match ([checkJournal]) ask the engine.
 */
internal const val MATCH_ID_KEY = "match-id"
internal const val STARTED_AT_KEY = "started-at"
internal const val JOURNALS_KEY = "journals"
internal const val JOURNAL_KEY = "journal"

/** Longest journal line, in events: darts entered, voided darts and undos (R28, ADR 0011 "Numbers"). */
internal const val MAX_JOURNAL_EVENTS: Int = 30_000

private const val UNDO_TOKEN = "u"
private const val TIME_SEPARATOR = '@'

/** A lower-case UUID in its `java.util.UUID` text form. */
private val UUID_TEXT = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

/** A canonical non-negative integer of at most 19 digits: no sign, no leading zero except `0`. */
private val CANONICAL_MILLIS = Regex("0|[1-9][0-9]{0,18}")

/**
 * The journal lines of [journal], or `null` when they cannot be written so that the decoder reads them back (R28):
 * an id that is not a lower-case UUID, a negative time, a source the format does not store, or a leg journal of more
 * than [MAX_JOURNAL_EVENTS] events.
 */
internal fun writeJournal(journal: MatchJournal, confirmedLegs: Int): List<Pair<String, String>>? {
    val writable = UUID_TEXT.matches(journal.matchId) &&
        journal.startedAt >= 0 &&
        journal.legs.size == confirmedLegs + 1 &&
        journal.legs.all { leg -> leg.events.size <= MAX_JOURNAL_EVENTS && leg.events.all(::isWritable) }
    if (!writable) return null
    return buildList {
        add(MATCH_ID_KEY to journal.matchId)
        add(STARTED_AT_KEY to journal.startedAt.toString())
        add(JOURNALS_KEY to journal.legs.size.toString())
        journal.legs.forEach { leg -> add(JOURNAL_KEY to leg.events.joinToString(" ", transform = ::eventToken)) }
    }
}

private fun isWritable(event: JournalEvent): Boolean =
    event !is JournalEvent.DartEntered || (event.enteredAt >= 0 && event.source == DartSource.MANUAL)

private fun eventToken(event: JournalEvent): String = when (event) {
    JournalEvent.Undone -> UNDO_TOKEN
    is JournalEvent.DartEntered -> "${DartTokens.tokenOf(event.dart)}$TIME_SEPARATOR${event.enteredAt}"
}

/**
 * Reads the journal lines (R29). [confirmedLegs] is the leg count of the body: the number of `journal` lines must be
 * that plus one. Syntax only; the match is not consulted.
 */
internal fun readJournal(reader: BodyReader, confirmedLegs: Int): MatchJournal {
    val matchId = reader.value(MATCH_ID_KEY)
    ensure(UUID_TEXT.matches(matchId), InvalidSaveReason.MALFORMED)
    val startedAt = parseMillis(reader.value(STARTED_AT_KEY))
    val count = SaveFormat.parseInteger(reader.value(JOURNALS_KEY))
    ensure(count == confirmedLegs + 1, InvalidSaveReason.INCONSISTENT_JOURNAL)
    return MatchJournal(matchId, startedAt, List(count) { parseLegJournal(reader.value(JOURNAL_KEY)) })
}

/** Strict: a number that is not canonical, or over [Long.MAX_VALUE], is malformed; nothing is converted before. */
private fun parseMillis(value: String): Long {
    ensure(CANONICAL_MILLIS.matches(value), InvalidSaveReason.MALFORMED)
    return value.toLongOrNull() ?: reject(InvalidSaveReason.MALFORMED)
}

private fun parseLegJournal(value: String): LegJournal {
    if (value.isEmpty()) return LegJournal()
    // Count before splitting, so that an oversized line is rejected without building it.
    ensure(value.count { it == ' ' } < MAX_JOURNAL_EVENTS, InvalidSaveReason.OUT_OF_RANGE)
    return LegJournal(value.split(' ').map(::parseEvent))
}

/** `u`, or a dart token, `@` and its entry time: there is no event without a time (ADR 0011). */
private fun parseEvent(token: String): JournalEvent {
    if (token == UNDO_TOKEN) return JournalEvent.Undone
    val separator = token.indexOf(TIME_SEPARATOR)
    ensure(separator > 0, InvalidSaveReason.MALFORMED)
    val enteredAt = parseMillis(token.substring(separator + 1))
    val dart = DartTokens.dartOf(token.substring(0, separator)) ?: reject(InvalidSaveReason.OUT_OF_RANGE)
    return JournalEvent.DartEntered(dart, enteredAt, DartSource.MANUAL)
}

/** The engine side of a leg check: the winner after these darts, `null` while the leg is open. */
internal fun interface LegEngine {
    /** @throws LegFinishedException or [CricketLegFinishedException] if a dart follows the winning one. */
    fun winnerAfter(darts: List<Dart>, starter: Int): Int?
}

/**
 * Checks [journal] against the match read from the body (R29, DJ4, ADR 0011): every leg journal replays in the engine
 * (an undo with nothing to undo, or a dart after the win, is [InvalidSaveReason.REJECTED_BY_ENGINE]); the confirmed
 * legs replay to a finished leg won by the winner of their `leg` line, and the current leg to the body's `darts` line.
 *
 * @param playerCount the starter of leg `i` is player `i % playerCount`, as the body's current leg is replayed.
 * @param winners the winner of each confirmed leg, in order.
 * @param currentDarts the `darts` line of the body: the darts in play of the current leg.
 */
internal fun checkJournal(
    journal: MatchJournal,
    engine: LegEngine,
    playerCount: Int,
    winners: List<Int>,
    currentDarts: List<Dart>,
) {
    journal.legs.forEachIndexed { index, leg ->
        val replayed = LegCheck(engine, index % playerCount).run(leg)
        if (index < winners.size) {
            ensure(replayed.winner == winners[index], InvalidSaveReason.INCONSISTENT_JOURNAL)
        } else {
            ensure(replayed.dartsInPlay == currentDarts, InvalidSaveReason.INCONSISTENT_JOURNAL)
        }
    }
}

/** The darts in play at the end of a leg journal, and the winner of that leg if it is finished. */
private class CheckedLeg(val dartsInPlay: List<Dart>, val winner: Int?)

/**
 * Replays one leg journal in the engine, in linear time in the number of events (the engine rebuilds a leg from its
 * darts on every call, so calling it per event would be quadratic on a journal of [MAX_JOURNAL_EVENTS] events).
 *
 * The darts in play are a stack. A dart may only follow a stack that is not finished, which is known from the engine
 * only: the stack is handed to it once at each local maximum (a dart followed by an undo), and once at the end. A
 * successful call proves that every shorter prefix of that stack is open, which [openPrefix] remembers. The cost is
 * one engine call per run of darts that ends in an undo and goes beyond the proven prefix: a normal journal has few.
 */
private class LegCheck(private val engine: LegEngine, private val starter: Int) {

    private val stack = ArrayList<Dart>()

    /** Every prefix of [stack] up to this length is known not to be finished. */
    private var openPrefix = 0

    fun run(leg: LegJournal): CheckedLeg {
        var lastWasDart = false
        for (event in leg.events) {
            when (event) {
                is JournalEvent.DartEntered -> push(event.dart)
                JournalEvent.Undone -> undo(lastWasDart)
            }
            lastWasDart = event is JournalEvent.DartEntered
        }
        // The last stack, whole: proves every earlier push and gives the winner.
        return CheckedLeg(stack.toList(), winnerOf(stack))
    }

    private fun push(dart: Dart) {
        ensure(stack.size < MAX_SAVED_DARTS, InvalidSaveReason.OUT_OF_RANGE)
        stack.add(dart)
    }

    private fun undo(lastWasDart: Boolean) {
        // R29, DJ2: an undo with nothing to undo is rejected.
        ensure(stack.isNotEmpty(), InvalidSaveReason.REJECTED_BY_ENGINE)
        if (lastWasDart && stack.size - 1 > openPrefix) {
            winnerOf(stack)
            openPrefix = stack.size - 1
        }
        stack.removeAt(stack.lastIndex)
        openPrefix = minOf(openPrefix, stack.size)
    }

    private fun winnerOf(darts: List<Dart>): Int? = try {
        engine.winnerAfter(darts, starter)
    } catch (e: LegFinishedException) {
        // A dart after the winning one (S1).
        reject(InvalidSaveReason.REJECTED_BY_ENGINE, e)
    } catch (e: CricketLegFinishedException) {
        // A dart after the winning one (CR9).
        reject(InvalidSaveReason.REJECTED_BY_ENGINE, e)
    }
}
