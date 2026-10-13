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
