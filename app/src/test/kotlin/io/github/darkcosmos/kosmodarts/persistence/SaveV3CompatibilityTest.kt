package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.player.ColorSlot
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import io.github.darkcosmos.kosmodarts.engine.x01.X01MatchConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R25 (ADR 0008): the frozen version 3 last setup of `src/test/resources/persistence/v3` (see its README), written
 * by the version 3 encoder, is still read as X01. The version 3 games are withdrawn (ADR 0011, R30).
 */
class SaveV3CompatibilityTest {

    private val setupCodec = LastSetupCodec()

    @Test
    fun `R25 the version 3 last setup decodes to its X01 setup`() {
        val bytes = V3Fixtures.bytes("last-setup.save")

        val decoded = setupCodec.decode(bytes)

        assertEquals(V3Fixtures.LAST_SETUP, decoded?.x01Setup)
        assertNull(decoded?.cricket)
        assertFalse(String(bytes, Charsets.UTF_8).contains("mode="))
    }
}

/** The frozen version 3 files of `src/test/resources/persistence/v3`, with what they hold (see its README). */
internal object V3Fixtures {

    val LAST_SETUP: X01MatchConfig = X01MatchConfig(
        X01Config(listOf("Cid", "Ann", "Zoé"), 301, CheckoutRule.SINGLE_OUT),
        MatchFormat(legsPerSet = 3, sets = 2),
        listOf(ColorSlot.SLOT_2, ColorSlot.SLOT_4, ColorSlot.SLOT_1),
    )

    fun bytes(name: String): ByteArray =
        checkNotNull(V3Fixtures::class.java.getResourceAsStream("/persistence/v3/$name")) { name }.readBytes()
}
