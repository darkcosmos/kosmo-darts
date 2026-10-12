package io.github.darkcosmos.kosmodarts.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GameEngineTest {

    @Test
    fun `engine exposes its name`() {
        assertEquals("Kosmo Darts game engine", GameEngine.NAME)
    }
}
