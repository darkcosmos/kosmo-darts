package io.github.darkcosmos.kosmodarts.engine.x01

/**
 * Short dart notation used by the X01 tests: "T20", "D16", "S5" (or "5"), "25" (outer bull), "BULL", "MISS".
 */
internal fun dart(code: String): Dart = when (code) {
    "MISS" -> Dart.MISS
    "25" -> Dart.OUTER_BULL
    "BULL" -> Dart.BULL
    else -> when (code.first()) {
        'T' -> Dart.triple(code.drop(1).toInt())
        'D' -> Dart.double(code.drop(1).toInt())
        'S' -> Dart.single(code.drop(1).toInt())
        else -> Dart.single(code.toInt())
    }
}

internal fun X01Game.throwing(vararg codes: String): X01Game =
    codes.fold(this) { game, code -> game.throwDart(dart(code)) }

internal fun newGame(
    vararg players: String = arrayOf("Alice"),
    startScore: Int = X01Config.DEFAULT_START_SCORE,
    checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
): X01Game = X01Game(X01Config(players.toList(), startScore, checkoutRule))

internal fun X01Game.player(index: Int): X01PlayerState = state.players[index]

/**
 * A one-player game at the start of a turn, with [remaining] left. Scores full turns of triple 20 then singles, and
 * pads the last turn with misses, so no dart busts on the way.
 */
internal fun gameAtRemaining(
    remaining: Int,
    name: String = "Alice",
    startScore: Int = 301,
    checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
): X01Game {
    val toScore = startScore - remaining
    val rest = toScore % 60
    val codes = List(toScore / 60) { "T20" } + when {
        rest == 0 -> emptyList()
        rest <= 20 -> listOf("$rest")
        rest <= 40 -> listOf("20", "${rest - 20}")
        else -> listOf("20", "20", "${rest - 40}")
    }
    val padded = codes + List((3 - codes.size % 3) % 3) { "MISS" }
    return newGame(name, startScore = startScore, checkoutRule = checkoutRule).throwing(*padded.toTypedArray())
}
