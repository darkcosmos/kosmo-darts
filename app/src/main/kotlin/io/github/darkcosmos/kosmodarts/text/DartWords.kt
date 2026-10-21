package io.github.darkcosmos.kosmodarts.text

import android.content.res.Resources
import io.github.darkcosmos.kosmodarts.R
import io.github.darkcosmos.kosmodarts.engine.x01.Dart
import io.github.darkcosmos.kosmodarts.engine.x01.Multiplier

/**
 * Spoken words of [dart] in the app language: "20", "Double 16", "Triple 20", "25", "Bull", or `null` for a miss.
 * Shared by the voice announcements (docs/rules/announcements.md) and the TalkBack description of the checkout
 * suggestion (docs/rules/checkout-suggestions.md, CS9), so both always use the same words. Lives outside `audio` and
 * `ui` so that neither package depends on the other for it.
 */
fun dartWords(resources: Resources, dart: Dart): String? = when {
    dart.value == 0 -> null
    dart == Dart.BULL -> resources.getString(R.string.announce_dart_bull)
    dart == Dart.OUTER_BULL -> resources.getString(R.string.announce_dart_outer_bull)
    dart.multiplier == Multiplier.DOUBLE -> resources.getString(R.string.announce_dart_double, dart.segment)
    dart.multiplier == Multiplier.TRIPLE -> resources.getString(R.string.announce_dart_triple, dart.segment)
    else -> resources.getString(R.string.announce_dart_single, dart.segment)
}
