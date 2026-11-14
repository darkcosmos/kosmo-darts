package io.github.darkcosmos.kosmodarts.ui.setup

import io.github.darkcosmos.kosmodarts.engine.player.PlayerColor
import io.github.darkcosmos.kosmodarts.engine.player.PlayerColorRules
import io.github.darkcosmos.kosmodarts.persistence.GameDecodeResult
import io.github.darkcosmos.kosmodarts.persistence.GameSaveCodec
import io.github.darkcosmos.kosmodarts.persistence.cricket
import io.github.darkcosmos.kosmodarts.persistence.x01
import io.github.darkcosmos.kosmodarts.testing.reduce
import io.github.darkcosmos.kosmodarts.testing.savedCricketGame
import io.github.darkcosmos.kosmodarts.testing.savedGame
import io.github.darkcosmos.kosmodarts.ui.app.AppEvent
import io.github.darkcosmos.kosmodarts.ui.app.AppReducer
import io.github.darkcosmos.kosmodarts.ui.app.AppState
import io.github.darkcosmos.kosmodarts.ui.app.GameMode
import io.github.darkcosmos.kosmodarts.ui.app.Screen
import io.github.darkcosmos.kosmodarts.ui.app.cricketRematchSetup
import io.github.darkcosmos.kosmodarts.ui.app.rematchSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The color swatches of the setup (docs/rules/players.md, PC1 to PC4, PC9, PC12 to PC16), without a device. */
class SetupStateColorTest {

    private val setup = SetupState(names = listOf("Ann", "Bob"))

    private fun SetupState.row(): SwatchRow = checkNotNull(openSwatches)

    private fun SetupState.generatedColor(): PlayerColor =
        row().colors.first { it !in PlayerColor.PALETTE }

    @Test
    fun `PC14 a tap opens the row of one player and a second tap closes it`() {
        val opened = setup.withSwatchesToggled(0, Random(1))

        assertEquals(0, opened.row().index)
        assertEquals(setup.roster.colors[0], opened.row().colors.first())
        assertNull(opened.withSwatchesToggled(0, Random(2)).openSwatches)
    }

    @Test
    fun `PC14 opening another player's row moves it`() {
        val moved = setup.withSwatchesToggled(0, Random(1)).withSwatchesToggled(1, Random(1))

        assertEquals(1, moved.row().index)
        assertEquals(setup.roster.colors[1], moved.row().colors.first())
    }

    @Test
    fun `PC14 picking applies the color, closes the row and leaves the others`() {
        val opened = setup.withSwatchesToggled(0, Random(1))
        val picked = opened.row().colors[1]

        val after = opened.withColorPicked(picked)

        assertEquals(listOf(picked, setup.roster.colors[1]), after.roster.colors)
        assertEquals(setup.names, after.names)
        assertNull(after.openSwatches)
    }

    @Test
    fun `PC14 the color of another player is absent from the row, and a refused pick changes nothing`() {
        val opened = setup.withSwatchesToggled(0, Random(1))
        val other = setup.roster.colors[1]

        assertFalse(other in opened.row().colors)
        assertEquals(opened, opened.withColorPicked(other))
        assertEquals(setup, setup.withColorPicked(PlayerColor.TEAL))
    }

    @Test
    fun `PC15 the row has at least four swatches and keeps them while open, a new opening draws again`() {
        val opened = setup.withSwatchesToggled(0, Random(1))

        assertTrue(opened.row().colors.size >= PlayerColorRules.MIN_CHOICES)
        assertEquals(opened.row(), opened.withName(0, "Zed").row())
        val reopened = opened.withSwatchesToggled(0, Random(1)).withSwatchesToggled(0, Random(99))
        assertNotEquals(opened.row().colors.last(), reopened.row().colors.last())
    }

    @Test
    fun `PC3 removing the player whose row is open closes it, removing another keeps it open`() {
        val three = SetupState(names = listOf("Ann", "Bob", "Cid"))
        val open = three.withSwatchesToggled(1, Random(1))

        assertNull(open.withPlayerRemoved(1).openSwatches)
        assertEquals(0, open.withPlayerRemoved(0).row().index)
        assertEquals(open.row(), open.withPlayerRemoved(2).row())
    }

    @Test
    fun `PC2 to PC4 adding and shuffling keep distinct colors and close the row`() {
        val open = setup.withSwatchesToggled(0, Random(1))
        val added = open.withPlayerAdded(Random(3))
        val shuffled = added.shuffled(Random(4))
        val reopened = shuffled.withSwatchesToggled(0, Random(1))

        assertNull(added.openSwatches)
        assertEquals(3, added.roster.colors.distinct().size)
        assertEquals(added.roster.colors.toSet(), shuffled.roster.colors.toSet())
        assertEquals(setOf(shuffled.roster.colors[0]), setOf(reopened.row().colors.first()))
        assertNull(shuffled.openSwatches)
    }

    @Test
    fun `PC2 adding a player next to a generated color gives an acceptable color`() {
        val generated = setup.withSwatchesToggled(0, Random(5)).let { it.withColorPicked(it.generatedColor()) }
        val colors = generated.withPlayerAdded(Random(6)).withPlayerAdded(Random(7)).roster.colors

        assertEquals(4, colors.distinct().size)
        colors.forEachIndexed { i, color ->
            assertTrue(PlayerColorRules.isAcceptable(color, colors.filterIndexed { j, _ -> j != i }))
        }
    }

    // ---- PC7 (review T3): a generated color survives the match flows, in both modes. ----

    private val reducer = AppReducer(Random(0))

    private fun AppState.after(vararg events: AppEvent): AppState = 
        events.fold(this) { state, event -> reducer.reduce(state, event) }

    private fun startedWithGenerated(mode: GameMode): Pair<AppState, PlayerColor> {
        val opened = AppState().after(AppEvent.NewGame(mode), AppEvent.ColorSwatchTapped(0))
        val row = ((opened.screen as Screen.Setup).setup).row()
        val generated = row.colors.first { it !in PlayerColor.PALETTE }
        val started = opened.after(
            AppEvent.ColorPicked(generated),
            AppEvent.NameChanged(0, "Ann"),
            AppEvent.NameChanged(1, "Bob"),
            AppEvent.StartRequested,
        )
        return started to generated
    }

    @Test
    fun `PC7 X01 keeps a generated color through undo, rematch and a resume then rematch`() {
        val (started, generated) = startedWithGenerated(GameMode.X01)
        val game = (started.screen as Screen.Game).game

        assertEquals(generated, game.match.colors[0])
        val undone = started.after(AppEvent.MissTapped, AppEvent.UndoTapped).screen as Screen.Game
        assertEquals(generated, undone.game.match.colors[0])
        assertEquals(generated, rematchSetup(game.match).colors.last())

        val codec = GameSaveCodec()
        val saved = savedGame(game.match, isAwaitingNextPlayer = false)
        val resumed = (codec.decode(checkNotNull(codec.encode(saved))) as GameDecodeResult.Valid).x01
        assertEquals(generated, resumed.match.colors[0])
        assertEquals(generated, rematchSetup(resumed.match).colors.last())
    }

    @Test
    fun `PC7 Cricket keeps a generated color through a start, a resume then rematch`() {
        val (started, generated) = startedWithGenerated(GameMode.CRICKET)
        val match = (started.screen as Screen.CricketGame).game.match

        assertEquals(generated, match.colors[0])
        assertEquals(generated, cricketRematchSetup(match).colors.last())

        val codec = GameSaveCodec()
        val saved = savedCricketGame(match, isAwaitingNextPlayer = false)
        val resumed = (codec.decode(checkNotNull(codec.encode(saved))) as GameDecodeResult.Valid).cricket
        assertEquals(generated, resumed.match.colors[0])
        assertEquals(generated, cricketRematchSetup(resumed.match).colors.last())
    }
}
