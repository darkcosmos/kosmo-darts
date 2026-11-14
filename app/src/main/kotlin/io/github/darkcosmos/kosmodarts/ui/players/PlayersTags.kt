package io.github.darkcosmos.kosmodarts.ui.players

/** Test tags of the Players screen. */
object PlayersTags {
    const val BACK = "players_back"
    const val LIST = "players_list"
    const val EMPTY = "players_empty"
    const val FAILED = "players_failed"
    const val RETRY = "players_retry"

    /** The live-region message under the open card (PM4, PM8, PM10). */
    const val MESSAGE = "players_message"

    const val RENAME = "players_rename"
    const val MERGE = "players_merge"
    const val RESET = "players_reset"
    const val DELETE = "players_delete"

    const val RENAME_FIELD = "players_rename_field"
    const val RENAME_ERROR = "players_rename_error"
    const val RENAME_SAVE = "players_rename_save"
    const val MERGE_INTO_TAKEN = "players_merge_into_taken"

    /** The text of a confirmation panel, and its confirm and cancel buttons. */
    const val CONFIRM_TEXT = "players_confirm_text"
    const val CONFIRM = "players_confirm"
    const val CANCEL = "players_cancel"

    /** The merge target list title. */
    const val MERGE_PICK = "players_merge_pick"

    /** The card of a profile (name, count and last match date). */
    fun card(id: Long): String = "players_card_$id"

    /** The line "N matches · Last match: date" of a card. */
    fun summary(id: Long): String = "players_summary_$id"

    /** A row of the merge target list. */
    fun mergeTarget(id: Long): String = "players_merge_target_$id"
}
