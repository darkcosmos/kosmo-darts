package io.github.darkcosmos.kosmodarts.audio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.darkcosmos.kosmodarts.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The sound effects over Robolectric's SoundPool (docs/rules/announcements.md, AN9, AN10; finding T6). */
@RunWith(AndroidJUnit4::class)
class SoundPoolPlayerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val player = SoundPoolPlayer(context)
    private val pool = shadowOf(player.pool)

    private fun loadAll() =
        SoundEffect.entries.forEach { pool.notifyResourceLoaded(SoundPoolPlayer.resourceOf(it), true) }

    @Test
    fun `AN9 each effect is its own raw file`() {
        assertEquals(
            listOf(
                R.raw.sfx_dart_hit,
                R.raw.sfx_dart_miss,
                R.raw.sfx_dart_cancel,
                R.raw.sfx_bust,
                R.raw.sfx_one_eighty,
                R.raw.sfx_big_fish,
                R.raw.sfx_game_shot,
            ),
            SoundEffect.entries.map(SoundPoolPlayer::resourceOf),
        )
    }

    @Test
    fun `AN10 a sound asked for before it is loaded is skipped, not played later`() {
        assertFalse(player.play(SoundEffect.DART_HIT))
        pool.notifyResourceLoaded(R.raw.sfx_dart_hit, true)

        assertFalse(pool.wasResourcePlayed(R.raw.sfx_dart_hit))
    }

    @Test
    fun `AN10 a loaded sound plays at once`() {
        loadAll()

        assertTrue(player.play(SoundEffect.DART_HIT))
        assertTrue(player.play(SoundEffect.DART_HIT))

        assertEquals(2, pool.getResourcePlaybacks(R.raw.sfx_dart_hit).size)
    }

    @Test
    fun `AN10 a sound that failed to load never plays`() {
        pool.notifyResourceLoaded(R.raw.sfx_bust, false)
        assertFalse(player.play(SoundEffect.BUST))
    }

    @Test
    fun `AN23 stop all then play again works`() {
        loadAll()
        player.play(SoundEffect.DART_HIT)
        player.play(SoundEffect.BUST)

        player.stopAll()

        assertTrue(player.play(SoundEffect.DART_CANCEL))
    }

    @Test
    fun `AN21 after release nothing plays`() {
        loadAll()
        player.release()

        assertFalse(player.play(SoundEffect.DART_HIT))
        assertFalse(pool.wasResourcePlayed(R.raw.sfx_dart_hit))
    }
}
