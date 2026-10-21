package io.github.darkcosmos.kosmodarts.ui.setup

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.InvalidX01ConfigException
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01ConfigError
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import kotlin.random.Random

/**
 * Content of the setup screen. Immutable: every change returns a new state, and any change clears the errors of the
 * last [start] attempt.
 *
 * @property names one entry per name field, in throwing order (1 to [X01Config.MAX_PLAYERS] fields).
 * @property nameErrors errors of the last [start] attempt, by field position.
 * @property screenErrors errors of the last [start] attempt that are not about a name.
 * @property legsPerSet legs a player must win to win a set, from [MatchFormat.MIN] to [MatchFormat.MAX] (M13).
 * @property sets sets a player must win to win the match, from [MatchFormat.MIN] to [MatchFormat.MAX] (M13).
 */
data class SetupState(
    val startScore: Int = X01Config.DEFAULT_START_SCORE,
    val checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
    val names: List<String> = List(DEFAULT_PLAYER_COUNT) { "" },
    val nameErrors: Map<Int, List<X01ConfigError>> = emptyMap(),
    val screenErrors: List<X01ConfigError> = emptyList(),
    val legsPerSet: Int = MatchFormat.MIN,
    val sets: Int = MatchFormat.MIN,
) {
    /** M13: − of "Legs per set" is disabled at [MatchFormat.MIN]. */
    val canDecreaseLegs: Boolean
        get() = legsPerSet > MatchFormat.MIN

    /** M13: + of "Legs per set" is disabled at [MatchFormat.MAX]. */
    val canIncreaseLegs: Boolean
        get() = legsPerSet < MatchFormat.MAX

    /** M13: − of "Sets" is disabled at [MatchFormat.MIN]. */
    val canDecreaseSets: Boolean
        get() = sets > MatchFormat.MIN

    /** M13: + of "Sets" is disabled at [MatchFormat.MAX]. */
    val canIncreaseSets: Boolean
        get() = sets < MatchFormat.MAX

    /** M13: with one set, "Legs per set" reads "Legs" (owner Q10). */
    val hasSets: Boolean
        get() = sets > MatchFormat.MIN

    /** M13: [value] clamped to [MatchFormat.MIN] to [MatchFormat.MAX]. */
    fun withLegsPerSet(value: Int): SetupState = edited(copy(legsPerSet = value.coerceIn(FORMAT_RANGE)))

    /** M13: [value] clamped to [MatchFormat.MIN] to [MatchFormat.MAX]. */
    fun withSets(value: Int): SetupState = edited(copy(sets = value.coerceIn(FORMAT_RANGE)))
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

    /** Validates the setup with the engine rules (rules C5, M1). */
    fun start(): SetupResult = try {
        SetupResult.Valid(X01MatchConfig(X01Config(names, startScore, checkoutRule), MatchFormat(legsPerSet, sets)))
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
        private val FORMAT_RANGE = MatchFormat.MIN..MatchFormat.MAX

        /**
         * The setup of [lastSetup] (names in order, start score, checkout rule, match format), or the default setup
         * (rules R7, R13).
         */
        fun prefilled(lastSetup: X01MatchConfig?): SetupState = lastSetup
            ?.let {
                SetupState(
                    startScore = it.config.startScore,
                    checkoutRule = it.config.checkoutRule,
                    names = it.config.players,
                    legsPerSet = it.format.legsPerSet,
                    sets = it.format.sets,
                )
            }
            ?: SetupState()
    }
}

/** Outcome of [SetupState.start]. */
sealed interface SetupResult {
    data class Valid(val config: X01MatchConfig) : SetupResult

    /** [setup] carries the errors to show. */
    data class Invalid(val setup: SetupState) : SetupResult
}
