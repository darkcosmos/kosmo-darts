package io.github.darkcosmos.kosmodarts.engine.match

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** M7, M8 (docs/rules/match.md): which won legs are confirmed into the match, for every mode. */
class LegOutcomeTest {

    /** M7, M8: a LEG or a SET is confirmed; the leg that decides the MATCH stays the current leg (M10). */
    @Test
    fun `M8 only LEG and SET are confirmable`() {
        assertEquals(
            mapOf(LegOutcome.LEG to true, LegOutcome.SET to true, LegOutcome.MATCH to false),
            LegOutcome.entries.associateWith(LegOutcome::isConfirmable),
        )
    }
}
