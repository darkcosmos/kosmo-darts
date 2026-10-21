package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig

/** The saved game as found at launch. */
sealed interface GameLoad {
    data object Absent : GameLoad

    data class Valid(val saved: SavedGame) : GameLoad

    /** The saved game cannot be resumed (rule R9). */
    data class Invalid(val reason: InvalidSaveReason) : GameLoad
}

/** Both files as found at launch. They are independent (rule R8). */
data class LoadResult(val game: GameLoad, val lastSetup: X01MatchConfig?)

/**
 * Reads and decodes both files (rules R8 to R11). A file that cannot be loaded, whatever the reason (a read error
 * included), is deleted before [load] returns, so that the R9 message is shown once (ADR 0002, decision 5).
 */
class SaveLoader(
    private val gameStore: ByteStore,
    private val setupStore: ByteStore,
    private val gameCodec: GameSaveCodec,
    private val setupCodec: LastSetupCodec,
) {

    fun load(): LoadResult = LoadResult(loadGame(), loadSetup())

    private fun loadGame(): GameLoad = when (val read = gameStore.read(GameSaveCodec.MAX_FILE_BYTES)) {
        ReadResult.Absent -> GameLoad.Absent
        ReadResult.Failed -> rejectGame(InvalidSaveReason.READ_FAILED)
        ReadResult.TooLarge -> rejectGame(InvalidSaveReason.TOO_LARGE)
        is ReadResult.Bytes -> when (val decoded = gameCodec.decode(read.bytes)) {
            is GameDecodeResult.Valid -> GameLoad.Valid(decoded.saved)
            is GameDecodeResult.Invalid -> rejectGame(decoded.reason)
        }
    }

    private fun rejectGame(reason: InvalidSaveReason): GameLoad {
        gameStore.delete()
        return GameLoad.Invalid(reason)
    }

    /** A last setup that cannot be loaded is ignored silently and deleted (rule R10). */
    private fun loadSetup(): X01MatchConfig? {
        val read = setupStore.read(LastSetupCodec.MAX_FILE_BYTES)
        val config = (read as? ReadResult.Bytes)?.let { setupCodec.decode(it.bytes) }
        if (config == null && read != ReadResult.Absent) setupStore.delete()
        return config
    }
}
