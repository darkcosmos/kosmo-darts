package io.github.darkcosmos.kosmodarts.ui.setup

import io.github.darkcosmos.kosmodarts.engine.cricket.CricketConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketMatchConfig
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.cricket.InvalidCricketConfigException
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.player.PlayerListError
import io.github.darkcosmos.kosmodarts.engine.player.Roster
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.InvalidX01ConfigException
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01ConfigError
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import io.github.darkcosmos.kosmodarts.persistence.LastSetups
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import kotlin.random.Random

/**
 * Content of the setup screen. Immutable: every change returns a new state, and any change clears the errors of the
 * last [start] attempt, so errors never point at a field that moved.
 *
 * @property roster one entry per name field, in throwing order, each with its color slot (1 to
 *   [X01Config.MAX_PLAYERS] fields; docs/rules/players.md, PC1 to PC4).
 * @property nameErrors errors of the last [start] attempt, by field position.
 * @property screenErrors errors of the last [start] attempt that are not about a name.
 * @property legsPerSet legs a player must win to win a set, from [MatchFormat.MIN] to [MatchFormat.MAX] (M13).
 * @property sets sets a player must win to win the match, from [MatchFormat.MIN] to [MatchFormat.MAX] (M13).
 * @property mode the mode of the "New game" that opened the setup (CR20): it decides the settings shown and the config
 *   [start] returns. The start score and the checkout rule are X01 settings.
 * @property cricketVariant Standard or Cut-throat (CR21), a Cricket setting.
 * @property isBullInPlay whether the Bull is a target (CR21, "25-Bull"), a Cricket setting.
 */
data class SetupState(
    val startScore: Int = X01Config.DEFAULT_START_SCORE,
    val checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
    val roster: Roster = Roster.initial(DEFAULT_PLAYER_COUNT),
    val nameErrors: Map<Int, List<X01ConfigError>> = emptyMap(),
    val screenErrors: List<X01ConfigError> = emptyList(),
    val legsPerSet: Int = MatchFormat.MIN,
    val sets: Int = MatchFormat.MIN,
    val mode: GameMode = GameMode.X01,
    val cricketVariant: CricketVariant = CricketVariant.STANDARD,
    val isBullInPlay: Boolean = true,
) {
    /** [names] with the color slots of their positions (PC5), for a setup typed in one go. */
    constructor(
        startScore: Int = X01Config.DEFAULT_START_SCORE,
        checkoutRule: CheckoutRule = CheckoutRule.DOUBLE_OUT,
        names: List<String>,
        legsPerSet: Int = MatchFormat.MIN,
        sets: Int = MatchFormat.MIN,
    ) : this(
        startScore = startScore,
        checkoutRule = checkoutRule,
        roster = Roster.of(names, ColorSlot.byPosition(names.size)),
        legsPerSet = legsPerSet,
        sets = sets,
    )

    /** Names, in throwing order. */
    val names: List<String>
        get() = roster.names

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

    /** Whether the match has more than one set. */
    val hasSets: Boolean
        get() = sets > MatchFormat.MIN

    /** M13: [value] clamped to [MatchFormat.MIN] to [MatchFormat.MAX]. */
    fun withLegsPerSet(value: Int): SetupState = edited(copy(legsPerSet = value.coerceIn(FORMAT_RANGE)))

    /** M13: [value] clamped to [MatchFormat.MIN] to [MatchFormat.MAX]. */
    fun withSets(value: Int): SetupState = edited(copy(sets = value.coerceIn(FORMAT_RANGE)))

    /** M13: "+ Add a player" is shown while a color slot is free. */
    val canAddPlayer: Boolean
        get() = roster.canAdd

    /** M13: each row's remove cross is shown while there is more than one player. */
    val canRemovePlayer: Boolean
        get() = roster.canRemove

    /** Errors to show under the name field at [index], in the engine order. */
    fun errorsFor(index: Int): List<X01ConfigError> = nameErrors[index].orEmpty()

    fun withStartScore(score: Int): SetupState = edited(copy(startScore = score))

    fun withCheckoutRule(rule: CheckoutRule): SetupState = edited(copy(checkoutRule = rule))

    fun withName(index: Int, name: String): SetupState = edited(copy(roster = roster.withName(index, name)))

    /** PC2: a new empty row with the first free color slot. */
    fun withPlayerAdded(): SetupState = if (canAddPlayer) edited(copy(roster = roster.withPlayerAdded())) else this

    /** PC3, M13: removes the row at [index] and its typed name at once; its color slot becomes free. */
    fun withPlayerRemoved(index: Int): SetupState =
        if (canRemovePlayer && index in roster.entries.indices) {
            edited(copy(roster = roster.withPlayerRemoved(index)))
        } else {
            this
        }

    /** PC4: shuffles the throwing order with [random], injected so that tests are deterministic. */
    fun shuffled(random: Random): SetupState = edited(copy(roster = roster.shuffled(random)))

    /** CR21: Standard or Cut-throat. */
    fun withCricketVariant(value: CricketVariant): SetupState = edited(copy(cricketVariant = value))

    /** CR21: whether the Bull is a target. */
    fun withBullInPlay(value: Boolean): SetupState = edited(copy(isBullInPlay = value))

    /**
     * Validates the setup of its [mode] with the engine rules (rules C5, M1, CR1); the slots go with the names (PC5).
     */
    fun start(): SetupResult = try {
        val format = MatchFormat(legsPerSet, sets)
        when (mode) {
            GameMode.X01 ->
                SetupResult.Valid(X01MatchConfig(X01Config(roster.names, startScore, checkoutRule), format, roster.slots))
            GameMode.CRICKET -> SetupResult.ValidCricket(
                CricketMatchConfig(CricketConfig(roster.names, cricketVariant, isBullInPlay), format, roster.slots),
            )
        }
    } catch (e: InvalidX01ConfigException) {
        invalid(e.violations.map { it.error to it.playerIndices })
    } catch (e: InvalidCricketConfigException) {
        invalid(e.violations.map { it.error.toX01ConfigError() to it.playerIndices })
    }

    private fun invalid(errors: List<Pair<X01ConfigError, List<Int>>>): SetupResult.Invalid {
        val byField = errors
            .flatMap { (error, indices) -> indices.map { it to error } }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
        val screenLevel = errors.filter { it.second.isEmpty() }.map { it.first }
        return SetupResult.Invalid(copy(nameErrors = byField, screenErrors = screenLevel))
    }

    private fun edited(state: SetupState): SetupState =
        state.copy(nameErrors = emptyMap(), screenErrors = emptyList())

    companion object {
        private const val DEFAULT_PLAYER_COUNT = 2
        private val FORMAT_RANGE = MatchFormat.MIN..MatchFormat.MAX

        /**
         * The setup of [lastSetup] (names in order with their color slots, start score, checkout rule, match format),
         * or the default setup (rules R7, R13; PC8).
         */
        fun prefilled(lastSetup: X01MatchConfig?): SetupState = lastSetup
            ?.let {
                SetupState(
                    startScore = it.config.startScore,
                    checkoutRule = it.config.checkoutRule,
                    roster = Roster.of(it.config.players, it.colorSlots),
                    legsPerSet = it.format.legsPerSet,
                    sets = it.format.sets,
                )
            }
            ?: SetupState()

        /**
         * The setup of [mode] prefilled with [lastSetups]: the shared roster with the last settings of that mode, or
         * the defaults of that mode (rules R7, R13, R22, CR21).
         */
        fun prefilled(lastSetups: LastSetups?, mode: GameMode): SetupState = when (mode) {
            GameMode.X01 -> prefilled(lastSetups?.x01Setup).copy(mode = mode)
            GameMode.CRICKET -> lastSetups
                ?.cricketSetup
                ?.let {
                    SetupState(
                        roster = Roster.of(it.config.players, it.colorSlots),
                        legsPerSet = it.format.legsPerSet,
                        sets = it.format.sets,
                        mode = mode,
                        cricketVariant = it.config.variant,
                        isBullInPlay = it.config.isBullInPlay,
                    )
                }
                ?: SetupState(mode = mode)
        }
    }
}

/** Outcome of [SetupState.start]. */
sealed interface SetupResult {
    /** A valid X01 setup. */
    data class Valid(val config: X01MatchConfig) : SetupResult

    /** A valid Cricket setup (CR21). */
    data class ValidCricket(val config: CricketMatchConfig) : SetupResult

    /** [setup] carries the errors to show. */
    data class Invalid(val setup: SetupState) : SetupResult
}

/** The setup error of a player list rule (rules C5, CR1: both modes share the list rules). */
private fun PlayerListError.toX01ConfigError(): X01ConfigError = when (this) {
    PlayerListError.NO_PLAYERS -> X01ConfigError.NO_PLAYERS
    PlayerListError.TOO_MANY_PLAYERS -> X01ConfigError.TOO_MANY_PLAYERS
    PlayerListError.BLANK_NAME -> X01ConfigError.BLANK_NAME
    PlayerListError.NAME_TOO_LONG -> X01ConfigError.NAME_TOO_LONG
    PlayerListError.DUPLICATE_NAME -> X01ConfigError.DUPLICATE_NAME
}
