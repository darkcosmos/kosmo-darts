package io.github.darkcosmos.kosmodarts.capture.ui

import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import io.github.darkcosmos.kosmodarts.engine.x01.Dart

/** Everything the capture screen can ask for; the screen only forwards these events. */
data class CaptureActions(
    val onStart: () -> Unit = {},
    val onDart: (Dart) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onNextVolley: () -> Unit = {},
    val onStopRequested: () -> Unit = {},
    val onStopCancelled: () -> Unit = {},
    val onStopConfirmed: () -> Unit = {},
    val onDeleteAllRequested: () -> Unit = {},
    val onDeleteAllCancelled: () -> Unit = {},
    val onDeleteAllConfirmed: () -> Unit = {},
    val onQuality: (VideoQuality) -> Unit = {},
    val onErrorDismissed: () -> Unit = {},
    val onPhotoMessageShown: () -> Unit = {},
    val onAllowCamera: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
) {
    companion object {
        /** Actions backed by [viewModel]; the permission requests are supplied by the activity. */
        fun of(
            viewModel: CaptureViewModel,
            onAllowCamera: () -> Unit,
            onOpenSettings: () -> Unit,
        ): CaptureActions = CaptureActions(
            onStart = viewModel::onStart,
            onDart = { viewModel.onDart(it) },
            onUndo = viewModel::onUndo,
            onNextVolley = viewModel::onNextVolley,
            onStopRequested = viewModel::onStopRequested,
            onStopCancelled = viewModel::onStopCancelled,
            onStopConfirmed = viewModel::onStopConfirmed,
            onDeleteAllRequested = viewModel::onDeleteAllRequested,
            onDeleteAllCancelled = viewModel::onDeleteAllCancelled,
            onDeleteAllConfirmed = viewModel::onDeleteAllConfirmed,
            onQuality = viewModel::onQuality,
            onErrorDismissed = viewModel::onErrorDismissed,
            onPhotoMessageShown = viewModel::onPhotoMessageShown,
            onAllowCamera = onAllowCamera,
            onOpenSettings = onOpenSettings,
        )
    }
}
