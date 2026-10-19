package io.github.darkcosmos.kosmodarts.audio

/**
 * What the app knows about one TTS voice (AN12). [language] and [country] are ISO 639-2 / ISO 3166-1 alpha-3 codes,
 * as `Locale.getISO3Language()` and `Locale.getISO3Country()` give them, so that 2- and 3-letter forms compare equal.
 */
data class VoiceCandidate(
    val name: String,
    val language: String,
    val country: String,
    val quality: Int,
    val isNetworkConnectionRequired: Boolean,
    val isNotInstalled: Boolean,
    val isNetworkSynthesis: Boolean,
)

/** Chooses the voice that speaks the announcements (AN11, AN12, ADR 0003). Pure. */
object VoiceChoice {

    /**
     * The usable voice of [language] to use, or `null` when there is none: no network voice, no voice that is not
     * installed, no other language. Voices of [country] come first, then higher quality, then by name.
     */
    fun choose(candidates: Collection<VoiceCandidate>, language: String, country: String): VoiceCandidate? =
        candidates
            .filter { it.language.equals(language, ignoreCase = true) && it.isUsable }
            .sortedWith(
                compareBy<VoiceCandidate> { !it.country.equals(country, ignoreCase = true) }
                    .thenByDescending { it.quality }
                    .thenBy { it.name },
            )
            .firstOrNull()

    private val VoiceCandidate.isUsable: Boolean
        get() = !isNetworkConnectionRequired && !isNotInstalled && !isNetworkSynthesis
}
