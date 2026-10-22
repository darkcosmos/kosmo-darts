package io.github.darkcosmos.kosmodarts.ui.components

// The small models the components take, apart from their composables.

/** The two button kinds of the design system: principal (`accent`) and secondary (`surfaceHigh`), plus outlined. */
enum class ButtonKind { PRIMARY, SECONDARY, OUTLINED_ACCENT }

/** One button of an [ActionBar]. */
class BarAction(val text: String, val tag: String, val isEnabled: Boolean, val onClick: () -> Unit)

/** A button of a [KosmoDialog]. */
class DialogAction(val text: String, val onClick: () -> Unit)

/** Test tags of a [WinnerZone], given by the screen that shows it. */
class WinnerZoneTags(val chip: String, val name: String, val detail: String)
