package io.github.darkcosmos.kosmodarts.engine.cricket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CricketVisitsTest {

    @Test
    fun `CR14 at the start, the current player has a visit in progress at 0 and the others have not played`() {
        val game = cricketGame("Anna", "Bob", "Cleo", startingPlayerIndex = 1)

        assertEquals(
            listOf(CricketVisit.NotPlayed, CricketVisit.InProgress(0), CricketVisit.NotPlayed),
            CricketVisits.of(game.state),
        )
    }

    @Test
    fun `CR14 the visit in progress shows the CR2 marks of the current turn, surplus included`() {
        val game = cricketGame("Anna", "Bob").throwing("T20", "T20")

        assertEquals(listOf(CricketVisit.InProgress(6), CricketVisit.NotPlayed), CricketVisits.of(game.state))
    }

    @Test
    fun `CR14 after his turn, a player shows the marks of his last completed visit`() {
        val game = cricketGame("Anna", "Bob").turns("T20 BULL 14", "19 MISS MISS").throwing("18")

        assertEquals(listOf(CricketVisit.InProgress(1), CricketVisit.LastVisit(1)), CricketVisits.of(game.state))
        assertEquals(CricketVisit.LastVisit(5), CricketVisits.lastCompleted(game.state, 0))
    }

    @Test
    fun `CR14 the last completed visit ignores the turn in progress and is not played before the first turn`() {
        val game = cricketGame("Anna", "Bob").turns("T20 MISS MISS")

        assertEquals(CricketVisit.LastVisit(3), CricketVisits.lastCompleted(game.state, 0))
        assertEquals(CricketVisit.NotPlayed, CricketVisits.lastCompleted(game.state, 1))
    }

    @Test
    fun `CR14 the winning turn is the winner's last visit, also when another player won`() {
        val soloWon = cricketGame().turns(CLOSE_20_19_18, CLOSE_17_16_15).throwing("BULL", "25")
        assertEquals(listOf(CricketVisit.LastVisit(3)), CricketVisits.of(soloWon.state))

        val nonThrowerWon = cricketGame("Anna", "Bob", "Cleo", variant = CricketVariant.CUT_THROAT)
            .turns(*CricketWinTest.cutThroatNonThrowerWin)

        assertEquals(
            listOf(CricketVisit.LastVisit(3), CricketVisit.LastVisit(0), CricketVisit.LastVisit(3)),
            CricketVisits.of(nonThrowerWon.state),
        )
    }
}
