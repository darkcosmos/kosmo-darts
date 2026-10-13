package io.github.darkcosmos.kosmodarts.ui.setup

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.InvalidX01ConfigException
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01ConfigError
import kotlin.random.Random

/**
 * Content of the setup screen. Immutable: every change returns a new state, and any change clears the errors of the
 * last [start] attempt.
 *
 * @property names one entry per name field, in throwing order (1 to [X01Config.MAX_PLAYERS] fields).
 * @property nameErrors errors of the last [start] attempt, by field position.
 * @property screenErrors errors of the last [start] attempt that are not about a name.
 */
data class SetupState(
    val startScore: Int = X01Config.DEFAULT_START_SCORE,
    val checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
    val names: List<String> = List(DEFAULT_PLAYER_COUNT) { "" },
    val nameErrors: Map<Int, List<X01ConfigError>> = emptyMap(),
    val screenErrors: List<X01ConfigError> = emptyList(),
) {
    val canAddPlayer: Boolean
        get() = names.size < X01Config.MAX_PLAYERS

    val canRemovePlayer: Boolean
        get() = names.size > MIN_PLAYER_COUNT

    /** Errors to show under the name field at [index], in the engine order. */
    fun errorsFor(index: Int): List<X01ConfigError> = nameErrors[index].orEmpty()

    fun withStartScore(score: Int): SetupState = edited(copy(startScore = score))

    fun withCheckoutRule(rule: CheckoutRule): SetupState = edited(copy(checkoutRule = rule))

    fun withName(index: Int, name: String): SetupState =
        edited(copy(names = names.mapIndexed { i, old -> if (i == index) name else old }))

    fun withPlayerAdded(): SetupState = if (canAddPlayer) edited(copy(names = names + "")) else this

    fun withLastPlayerRemoved(): SetupState = if (canRemovePlayer) edited(copy(names = names.dropLast(1))) else this

    /** Shuffles the throwing order with [random], injected so that tests are deterministic. */
    fun shuffled(random: Random): SetupState = edited(copy(names = names.shuffled(random)))

    /** Validates the setup with the engine rules (rule C5). */
    fun start(): SetupResult = try {
        SetupResult.Valid(X01Config(names, startScore, checkoutRule))
    } catch (e: InvalidX01ConfigException) {
        val byField = e.violations
            .flatMap { violation -> violation.playerIndices.map { it to violation.error } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        val screenLevel = e.violations.filter { it.playerIndices.isEmpty() }.map { it.error }
        SetupResult.Invalid(copy(nameErrors = byField, screenErrors = screenLevel))
    }

    private fun edited(state: SetupState): SetupState =
        state.copy(nameErrors = emptyMap(), screenErrors = emptyList())

    companion object {
        private const val DEFAULT_PLAYER_COUNT = 2
        private const val MIN_PLAYER_COUNT = 1

        /** Default setup with the given [names], or the default setup if there are none. */
        fun prefilled(names: List<String>): SetupState =
            if (names.isEmpty()) SetupState() else SetupState(names = names)
    }
}

/** Outcome of [SetupState.start]. */
sealed interface SetupResult {
    data class Valid(val config: X01Config) : SetupResult

    /** [setup] carries the errors to show. */
    data class Invalid(val setup: SetupState) : SetupResult
}
