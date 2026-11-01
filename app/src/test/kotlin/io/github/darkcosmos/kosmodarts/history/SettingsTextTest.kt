package io.github.darkcosmos.kosmodarts.history

import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedMode
import io.github.darkcosmos.kosmodarts.engine.archive.ArchivedSettings
import io.github.darkcosmos.kosmodarts.engine.cricket.CricketVariant
import io.github.darkcosmos.kosmodarts.engine.match.MatchFormat
import io.github.darkcosmos.kosmodarts.engine.x01.CheckoutRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Settings text version 1 of `match_record.settings` (AR5, ADR 0012 "Tables"). */
class SettingsTextTest {
    private val x01 = ArchivedSettings.X01(301, CheckoutRule.SINGLE_OUT, MatchFormat(legsPerSet = 3, sets = 2))
    private val cricket = ArchivedSettings.Cricket(CricketVariant.STANDARD, isBullInPlay = true, MatchFormat())

    @Test
    fun x01TextListsItsKeysInTheFixedOrder() {
        assertEquals("v=1;start=301;out=single;legs=3;sets=2", SettingsText.encode(x01))
        assertEquals(
            "v=1;start=501;out=double;legs=1;sets=1",
            SettingsText.encode(ArchivedSettings.X01(501, CheckoutRule.DOUBLE_OUT, MatchFormat())),
        )
    }

    @Test
    fun cricketTextListsItsKeysInTheFixedOrder() {
        assertEquals("v=1;variant=standard;bull=1;legs=1;sets=1", SettingsText.encode(cricket))
        assertEquals(
            "v=1;variant=cut-throat;bull=0;legs=2;sets=3",
            SettingsText.encode(ArchivedSettings.Cricket(CricketVariant.CUT_THROAT, false, MatchFormat(2, 3))),
        )
    }

    @Test
    fun decodeReturnsTheEncodedSettings() {
        assertEquals(x01, SettingsText.decode(SettingsText.encode(x01), ArchivedMode.X01))
        assertEquals(cricket, SettingsText.decode(SettingsText.encode(cricket), ArchivedMode.CRICKET))
    }

    @Test
    fun malformedTextsAreReadFaults() {
        listOf(
            "v=2;start=301;out=single;legs=3;sets=2" to ArchivedMode.X01,
            "v=1;start=301;out=single;legs=3" to ArchivedMode.X01,
            "v=1;start=301;out=single;legs=3;sets=2;sets=2" to ArchivedMode.X01,
            "v=1;start=+301;out=single;legs=3;sets=2" to ArchivedMode.X01,
            "v=1;start=301;out=triple;legs=3;sets=2" to ArchivedMode.X01,
            "v=1;start=301;out=single;legs=0;sets=2" to ArchivedMode.X01,
            "v=1;start=301;out;legs=3;sets=2" to ArchivedMode.X01,
            "v=1;variant=standard;bull=1;legs=1;sets=1" to ArchivedMode.X01,
            "v=1;start=301;out=single;legs=3;sets=2" to ArchivedMode.CRICKET,
            "v=1;variant=standard;bull=2;legs=1;sets=1" to ArchivedMode.CRICKET,
        ).forEach { (text, mode) ->
            assertThrows(text, IllegalStateException::class.java) { SettingsText.decode(text, mode) }
        }
    }
}
