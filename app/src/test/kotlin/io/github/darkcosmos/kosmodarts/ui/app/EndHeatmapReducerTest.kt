package io.github.darkcosmos.kosmodarts.ui.app

import io.github.darkcosmos.kosmodarts.engine.board.BoardZone
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.persistence.CricketSaveFixtures
import io.github.darkcosmos.kosmodarts.persistence.GameDecodeResult
import io.github.darkcosmos.kosmodarts.persistence.GameLoad
import io.github.darkcosmos.kosmodarts.persistence.GameSaveCodec
import io.github.darkcosmos.kosmodarts.persistence.LoadResult
import io.github.darkcosmos.kosmodarts.persistence.MatchFixtures
import io.github.darkcosmos.kosmodarts.persistence.StoredGame
import io.github.darkcosmos.kosmodarts.persistence.confirmLegsWonBy
import io.github.darkcosmos.kosmodarts.persistence.playLegWonBy
import io.github.darkcosmos.kosmodarts.testing.MatchJournals
import io.github.darkcosmos.kosmodarts.testing.betweenLegs
import io.github.darkcosmos.kosmodarts.testing.cricketBetweenLegs
import io.github.darkcosmos.kosmodarts.testing.endScreen
import io.github.darkcosmos.kosmodarts.testing.gameScreen
import io.github.darkcosmos.kosmodarts.testing.savedCricketGame
import io.github.darkcosmos.kosmodarts.testing.savedGame
import io.github.darkcosmos.kosmodarts.ui.between.heatmapState
import io.github.darkcosmos.kosmodarts.ui.game.GameScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The tab, player and zone of the between-legs and end screens (HM9, HM10, D7, D9; R9, R26, R29). */
class EndHeatmapReducerTest {

    private val events = MatchEvents()
    private val ann = MatchFixtures.match("Ann", "Bob", legsPerSet = 2)
    private val tens = BoardZone.of(Dart.triple(20))!!

    private fun betweenX01() = AppState(screen = betweenLegs(ann.confirmLegsWonBy(1)))

    private fun betweenCricket() =
        AppState(
            screen = cricketBetweenLegs(
                CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(1),
            ),
        )

    private fun endX01() = AppState(screen = endScreen(MatchFixtures.match("Ann", "Bob").playLegWonBy(1)))

    private fun endCricket(): AppState {
        val match = CricketSaveFixtures.match("Ann", "Bob").playLegWonBy(1)
        return AppState(screen = Screen.CricketEnd(match, MatchJournals.of(match)))
    }

    private val screens = mapOf(
        "X01 between legs" to ::betweenX01,
        "Cricket between legs" to ::betweenCricket,
        "X01 end" to ::endX01,
        "Cricket end" to ::endCricket,
    )

    private fun AppState.heatmap() = screen as EndHeatmapScreen

    /** HM9: every screen opens on "Result" with the winner selected (N2 A). */
    @Test
    fun `HM9 every screen opens on Result with the winner selected`() {
        screens.forEach { (name, build) ->
            val screen = build().heatmap()
            assertEquals(name, EndTab.RESULT, screen.endTab)
            assertEquals(name, 1, screen.heatmapPlayer)
            assertEquals(name, null, screen.heatmapZone)
        }
    }

    /** HM9: the tab, the player and the zone events change the screen fields only, on all four screens. */
    @Test
    fun `HM9 tab player and zone events update the screen`() {
        screens.forEach { (name, build) ->
            val state = events.after(
                build(),
                AppEvent.EndTabSelected(EndTab.LEG_HEATMAP),
                AppEvent.HeatmapPlayerSelected(0),
                AppEvent.HeatmapZoneTapped(tens),
            ).heatmap()
            assertEquals(name, EndTab.LEG_HEATMAP, state.endTab)
            assertEquals(name, 0, state.heatmapPlayer)
            assertEquals(name, tens, state.heatmapZone)

            val replaced = events.after(AppState(screen = state), AppEvent.HeatmapZoneTapped(BoardZone.of(Dart.BULL)!!))
            assertEquals(name, BoardZone.of(Dart.BULL), replaced.heatmap().heatmapZone)
            val back = events.after(replaced, AppEvent.EndTabSelected(EndTab.RESULT))
            assertEquals(name, EndTab.RESULT, back.heatmap().endTab)
        }
    }

    /** HM9: the selection changes the values; an unknown player is ignored. */
    @Test
    fun `HM9 the player selection changes the values and ignores an unknown player`() {
        val state = betweenX01()
        val winner = state.heatmap().heatmapState().section.heatmap
        val other = events.after(state, AppEvent.HeatmapPlayerSelected(0)).heatmap().heatmapState().section.heatmap

        assertTrue(winner != other)
        assertEquals(state, events.after(state, AppEvent.HeatmapPlayerSelected(2)))
        assertEquals(state, events.after(state, AppEvent.HeatmapPlayerSelected(-1)))
    }

    /** HM10: the dialog of "Abandon the game?" is modal, the tab events wait for it. */
    @Test
    fun `HM10 the abandon dialog ignores the tab events`() {
        val asking = events.after(betweenX01(), AppEvent.Back)

        assertEquals(asking, events.after(asking, AppEvent.EndTabSelected(EndTab.LEG_HEATMAP)))
    }

    /** HM10, D7: "Next leg" from the Leg heatmap tab, then the next between-legs screen opens on Result. */
    @Test
    fun `HM10 the next between-legs screen opens on Result`() {
        val match = MatchFixtures.match("Ann", "Bob", legsPerSet = 3)
        val first = events.after(
            AppState(screen = betweenLegs(match.confirmLegsWonBy(0))),
            AppEvent.EndTabSelected(EndTab.LEG_HEATMAP),
            AppEvent.HeatmapZoneTapped(tens),
            AppEvent.NextLegTapped,
        )
        assertTrue(first.screen is Screen.Game)

        val won = events.playLegWonBy(first, winner = 1)
        val second = events.after(won, AppEvent.FinishGameTapped).heatmap()

        assertEquals(EndTab.RESULT, second.endTab)
        assertEquals(1, second.heatmapPlayer)
        assertEquals(null, second.heatmapZone)
    }

    /** D7: the end screen of an X01 match is reached with the match winner selected, on Result. */
    @Test
    fun `HM9 Finish game opens the end screen on Result with the match winner`() {
        val start = AppState(screen = gameScreen(GameScreenState(MatchFixtures.match("Ann", "Bob"))))
        val won = events.playLegWonBy(start, winner = 1)

        val end = events.after(won, AppEvent.FinishGameTapped, AppEvent.ArchiveSucceeded).heatmap()

        assertTrue(end is Screen.End)
        assertEquals(EndTab.RESULT, end.endTab)
        assertEquals(1, end.heatmapPlayer)
    }

    /** D7: the default player is one injected function; a fake changes the choice for every screen. */
    @Test
    fun `D7 the default player comes from one injectable function`() {
        val fake = AppReducer(Random(0), heatmapPlayer = { _, _, _ -> 0 })
        val fakeEvents = MatchEvents(fake)
        val start = AppState(screen = gameScreen(GameScreenState(ann)))

        val between = fakeEvents.after(fakeEvents.playLegWonBy(start, winner = 1), AppEvent.FinishGameTapped)

        assertEquals(0, between.heatmap().heatmapPlayer)
        assertEquals(1, defaultHeatmapPlayer(EndScreenKind.BETWEEN_LEGS, lastLegWinner = 1, matchWinner = null))
        assertEquals(2, defaultHeatmapPlayer(EndScreenKind.MATCH_END, lastLegWinner = 1, matchWinner = 2))
        assertEquals(0, defaultHeatmapPlayer(EndScreenKind.MATCH_END, lastLegWinner = null, matchWinner = null))
    }

    /** HM10, R26: a rematch and a new game start a fresh match, without the heatmap of the old one. */
    @Test
    fun `HM10 rematch and new game leave the heatmap`() {
        val tabbed = events.after(
            endX01(),
            AppEvent.EndTabSelected(EndTab.LEG_HEATMAP),
            AppEvent.HeatmapZoneTapped(tens),
        )

        val rematch = events.after(tabbed, AppEvent.Rematch)
        assertTrue(rematch.screen is Screen.Game)
        assertTrue((rematch.screen as Screen.Game).journal.legs.single().events.isEmpty())
        assertTrue(events.after(tabbed, AppEvent.NewGame(GameMode.X01)).screen is Screen.Setup)

        val cricketTabbed = events.after(endCricket(), AppEvent.EndTabSelected(EndTab.LEG_HEATMAP))
        assertTrue(events.after(cricketTabbed, AppEvent.Rematch).screen is Screen.CricketGame)
    }

    /** HM10: a rotation keeps the AppState, so the three fields survive; Back keeps today's behavior on both tabs. */
    @Test
    fun `HM10 the selection is part of the state and Back is unchanged`() {
        val state = events.after(
            betweenX01(),
            AppEvent.EndTabSelected(EndTab.LEG_HEATMAP),
            AppEvent.HeatmapPlayerSelected(0),
            AppEvent.HeatmapZoneTapped(tens),
        )
        val screen = state.heatmap()

        assertEquals(screen, (state.copy()).screen)
        assertTrue((events.after(state, AppEvent.Back).screen as Screen.BetweenLegs).isAbandonDialogShown)
        val tabbed = events.after(endX01(), AppEvent.EndTabSelected(EndTab.LEG_HEATMAP))
        val onTab = events.after(tabbed, AppEvent.Back).screen
        assertTrue(onTab is Screen.Home)
    }

    /** HM10, R26, R29, R9: after a process death the saved journal comes back and the screen opens on Result. */
    @Test
    fun `HM10 after a process death the screen opens on Result with the default player and the journal`() {
        val codec = GameSaveCodec()
        val saved: List<StoredGame> = listOf(
            savedGame(ann.confirmLegsWonBy(1), isAwaitingNextPlayer = false, isBetweenLegs = true),
            savedCricketGame(
                CricketSaveFixtures.match("Ann", "Bob", legsPerSet = 2).confirmLegsWonBy(1),
                isAwaitingNextPlayer = false,
                isBetweenLegs = true,
            ),
        )
        saved.forEach { game ->
            val decoded = codec.decode(checkNotNull(codec.encode(game)))
            assertEquals(GameDecodeResult.Valid(game), decoded) // R9, R29: the loader accepts it
            val loaded = (decoded as GameDecodeResult.Valid).saved

            val state = initialState(LoadResult(GameLoad.Valid(loaded), lastSetup = null), wasOnGameScreen = true)

            val screen = state.heatmap()
            assertEquals(EndTab.RESULT, screen.endTab)
            assertEquals(1, screen.heatmapPlayer)
            assertEquals(null, screen.heatmapZone)
            assertEquals(loaded.journal, screen.journal) // R26
            assertNotNull(screen.heatmapState().section.heatmap.total.takeIf { it > 0 })
        }
    }
}
