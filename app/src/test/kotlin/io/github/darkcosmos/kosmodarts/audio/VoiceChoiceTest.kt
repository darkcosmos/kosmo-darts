package io.github.darkcosmos.kosmodarts.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Embedded voice only (docs/rules/announcements.md, AN11, AN12; ADR 0003). Protected by a canary. */
class VoiceChoiceTest {

    private fun voice(
        name: String,
        language: String = "fra",
        country: String = "FRA",
        quality: Int = 300,
        network: Boolean = false,
        notInstalled: Boolean = false,
        networkSynthesis: Boolean = false,
    ) = VoiceCandidate(name, language, country, quality, network, notInstalled, networkSynthesis)

    private fun choose(vararg voices: VoiceCandidate, country: String = "FRA") =
        VoiceChoice.choose(voices.toList(), "fra", country)

    @Test
    fun `AN12 a network voice is never chosen, even alone`() {
        assertNull(choose(voice("fr-network", network = true)))
    }

    @Test
    fun `AN12 a voice that is not installed is never chosen`() {
        assertNull(choose(voice("fr-missing", notInstalled = true)))
    }

    @Test
    fun `AN12 a network synthesis voice is never chosen`() {
        assertNull(choose(voice("fr-networkTts", networkSynthesis = true)))
    }

    @Test
    fun `AN11 a usable voice of another language is never chosen`() {
        assertNull(choose(voice("en-local", language = "eng", country = "USA")))
    }

    @Test
    fun `AN12 no voice at all gives none`() {
        assertNull(choose())
    }

    @Test
    fun `AN11 the phone's country is preferred among usable voices`() {
        val france = voice("fr-FR", country = "FRA", quality = 400)
        val canada = voice("fr-CA", country = "CAN", quality = 300)
        assertEquals(canada, choose(france, canada, country = "CAN"))
        assertEquals(france, choose(france, canada, country = "FRA"))
    }

    @Test
    fun `AN11 without a voice of the country, the best quality, then the name`() {
        val low = voice("a-low", country = "FRA", quality = 200)
        val highB = voice("b-high", country = "CAN", quality = 400)
        val highA = voice("a-high", country = "CHE", quality = 400)
        assertEquals(highA, choose(low, highB, highA, country = "BEL"))
    }

    @Test
    fun `AN12 a usable voice of another country wins over a network voice of the right country`() {
        val networkFrance = voice("fr-FR-network", country = "FRA", quality = 500, network = true)
        val localCanada = voice("fr-CA-local", country = "CAN")
        assertEquals(localCanada, choose(networkFrance, localCanada, country = "FRA"))
    }

    @Test
    fun `AN11 language codes compare without case`() {
        val upper = voice("upper", language = "FRA")
        assertEquals(upper, choose(upper))
    }
}
