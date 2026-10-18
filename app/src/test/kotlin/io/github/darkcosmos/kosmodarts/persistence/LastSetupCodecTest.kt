package io.github.darkcosmos.kosmodarts.persistence

import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import io.github.darkcosmos.kosmodarts.engine.x01.X01Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The last setup file (docs/adr/0001-save-format.md; rules R7, R10). */
class LastSetupCodecTest {

    private val codec = LastSetupCodec()

    private val valid = String(codec.encode(X01Config(listOf("Ann", "Bob"))), Charsets.UTF_8)

    @Test
    fun `R7 every setup round trips`() {
        for (players in 1..X01Config.MAX_PLAYERS) {
            for (startScore in X01Config.START_SCORES) {
                for (rule in CheckoutRule.entries) {
                    val config = SaveFixtures.config(players, startScore, rule)
                    assertEquals(config, codec.decode(codec.encode(config)))
                }
            }
        }
    }

    @Test
    fun `R7 the order of the names is kept`() {
        val config = X01Config(listOf("Dan", "Ann", "Cid"))

        assertEquals(listOf("Dan", "Ann", "Cid"), codec.decode(codec.encode(config))?.players)
    }

    @Test
    fun `R10 invalid content decodes to nothing`() {
        listOf(
            "",
            valid.dropLast(1),
            valid.replace("version=1", "version=2"),
            valid.replace("start-score=501", "start-score=401"),
            valid.replace("kosmodarts=last-setup", "kosmodarts=saved-game"),
            valid.replace("players=2", "players=3"),
            valid.replace("\n", "\r\n"),
        ).forEach { assertNull(it, codec.decode(it.toByteArray(Charsets.UTF_8))) }
    }

    @Test
    fun `R10 a duplicate name is an invalid setup`() {
        val bob = SaveFormat.encodeName("Bob")
        val duplicate = valid.replace("player=$bob", "player=${SaveFormat.encodeName("ann")}")

        // The checksum no longer matches either: whatever the first broken check, nothing is accepted.
        assertNull(codec.decode(duplicate.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `R10 a file over the size limit is rejected`() {
        assertNull(codec.decode(ByteArray(LastSetupCodec.MAX_FILE_BYTES + 1)))
    }
}
