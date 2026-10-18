package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01Game
import java.io.IOException

/** Games used by the persistence tests. Names are made up; nothing here is ever logged. */
internal object SaveFixtures {

    val NAMES = listOf("Ann", "Bob", "Cid", "Dan")

    fun config(
        players: Int = 2,
        startScore: Int = 501,
        rule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
    ): X01Config = X01Config(NAMES.take(players), startScore, rule)

    /**
     * The saved game after [darts] are entered with the keypad, every turn but the last one confirmed with "Next
     * player": it waits only if its last dart completed a turn. The flag is read from the engine's completed turns,
     * independently of [SavedGame.isConsistent], which the tests check.
     */
    fun savedAfter(config: X01Config, darts: List<Dart>): SavedGame {
        val game = X01Game(config, darts)
        val dartsInCompletedTurns = game.state.completedTurns.sumOf { it.darts.size }
        return SavedGame(game, isAwaitingNextPlayer = darts.isNotEmpty() && dartsInCompletedTurns == darts.size)
    }

    private val T20 = Dart.triple(20)

    val EMPTY: SavedGame = SavedGame(X01Game(config()), isAwaitingNextPlayer = false)

    /** Ann has thrown two darts of her first turn. */
    val MID_TURN: SavedGame = savedAfter(config(), listOf(T20, Dart.single(5)))

    /** Ann's T20 T20 20 waits for "Next player". */
    val WAITING_NORMAL: SavedGame = savedAfter(config(), listOf(T20, T20, Dart.single(20)))

    /** Same darts, after "Next player": Bob to throw. */
    val AFTER_NEXT_PLAYER: SavedGame = WAITING_NORMAL.copy(isAwaitingNextPlayer = false)

    /** 301 double out: Ann leaves 121, Bob misses, then Ann's T20 T20 leaves 1: a bust on the second dart. */
    val WAITING_BUST: SavedGame =
        savedAfter(config(startScore = 301), listOf(T20, T20, T20) + List(3) { Dart.MISS } + listOf(T20, T20))

    /** 301 double out: Ann leaves 121, then 41; her T20 on 41 busts on the first dart of her third turn. */
    val WAITING_BUST_FIRST_DART: SavedGame = savedAfter(
        config(startScore = 301),
        listOf(T20, T20, T20) + misses(3) + listOf(T20, Dart.single(20), Dart.MISS) + misses(3) + T20,
    )

    private fun misses(count: Int): List<Dart> = List(count) { Dart.MISS }

    /** 301 single out: Ann leaves 121, Bob misses, then Ann finishes with T20 T20 1, waiting in the dialog. */
    val WAITING_CHECKOUT: SavedGame =
        savedAfter(
            config(startScore = 301, rule = CheckoutRule.SINGLE_OUT),
            listOf(T20, T20, T20) + List(3) { Dart.MISS } + listOf(T20, T20, Dart.single(1)),
        )

    /** One player and exactly [GameSaveCodec.MAX_DARTS] misses. */
    val LONGEST: SavedGame = SavedGame(X01Game(config(players = 1), List(GameSaveCodec.MAX_DARTS) { Dart.MISS }), false)
}

/** A [ByteStore] in memory, with counters and an optional failure on read or write. */
internal class MemoryStore(var content: ByteArray? = null) : ByteStore {
    var failOnRead = false
    var failOnWrite = false
    @Volatile var reads = 0
        private set
    var writes = 0
        private set
    var deletes = 0
        private set

    override fun read(maxBytes: Int): ReadResult {
        reads++
        val bytes = content
        return when {
            bytes == null -> ReadResult.Absent
            failOnRead -> ReadResult.Failed
            bytes.size > maxBytes -> ReadResult.TooLarge
            else -> ReadResult.Bytes(bytes.copyOf())
        }
    }

    override fun write(bytes: ByteArray) {
        if (failOnWrite) throw IOException("test failure")
        writes++
        content = bytes.copyOf()
    }

    override fun delete() {
        deletes++
        content = null
    }
}

/** An executor that runs tasks only when the test says so. */
internal class ManualExecutor : java.util.concurrent.Executor {
    private val tasks = ArrayDeque<Runnable>()

    val pendingTasks: Int
        get() = tasks.size

    override fun execute(command: Runnable) {
        tasks.addLast(command)
    }

    fun runAll() {
        while (tasks.isNotEmpty()) tasks.removeFirst().run()
    }
}
