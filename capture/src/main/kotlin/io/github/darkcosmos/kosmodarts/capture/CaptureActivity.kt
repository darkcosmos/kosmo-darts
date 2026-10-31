package io.github.darkcosmos.kosmodarts.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.darkcosmos.kosmodarts.capture.camera.CameraXCaptureCamera
import io.github.darkcosmos.kosmodarts.capture.camera.CaptureController
import io.github.darkcosmos.kosmodarts.capture.camera.GravityReader
import io.github.darkcosmos.kosmodarts.capture.session.SystemCaptureClock
import io.github.darkcosmos.kosmodarts.capture.storage.CaptureSettings
import io.github.darkcosmos.kosmodarts.capture.storage.MediaStoreCaptureStorage
import io.github.darkcosmos.kosmodarts.capture.truth.AppInfo
import io.github.darkcosmos.kosmodarts.capture.truth.DeviceInfo
import io.github.darkcosmos.kosmodarts.capture.truth.VideoQuality
import io.github.darkcosmos.kosmodarts.capture.ui.CameraPermission
import io.github.darkcosmos.kosmodarts.capture.ui.CaptureActions
import io.github.darkcosmos.kosmodarts.capture.ui.CaptureScreen
import io.github.darkcosmos.kosmodarts.capture.ui.CaptureViewModel
import io.github.darkcosmos.kosmodarts.capture.ui.theme.KosmoDartsTheme
import java.io.File
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Single activity of Kosmo Capture. It owns the camera, the gravity sensor and the view model wiring (rules CP1, CP9):
 * the camera permission, the preview, the screen-on flag while a session runs and the interruption when the screen
 * really stops. Rotation never recreates it (see the manifest); the use cases just follow the display rotation.
 */
class CaptureActivity : ComponentActivity() {
    private lateinit var camera: CameraXCaptureCamera
    private lateinit var controller: CaptureController
    private lateinit var viewModel: CaptureViewModel
    private lateinit var gravity: GravityReader
    private var previewView: PreviewView? = null
    private var boundKey: Pair<VideoQuality, PreviewView>? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onPermission(
                when {
                    granted -> CameraPermission.GRANTED
                    shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> CameraPermission.ASK
                    else -> CameraPermission.PERMANENTLY_DENIED
                },
            )
        }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) = camera.setRotation(displayRotation())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val storage = MediaStoreCaptureStorage(contentResolver)
        camera = CameraXCaptureCamera(this, storage)
        controller = CaptureController(camera)
        gravity = GravityReader(getSystemService(Context.SENSOR_SERVICE) as SensorManager?)
        val settings = CaptureSettings(File(filesDir, SETTINGS_FILE))
        viewModel = ViewModelProvider.create(
            this,
            viewModelFactory {
                initializer {
                    CaptureViewModel(SystemCaptureClock, storage, settings, controller) {
                        controller.setup(appInfo(), deviceInfo(), viewModel.state.value.quality)
                    }
                }
            },
        )[CaptureViewModel::class]
        controller.attach(viewModel)
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, null)

        val actions = CaptureActions.of(viewModel, ::requestCamera, ::openAppSettings)
            .copy(onDart = { viewModel.onDart(it, gravity.latest()) })
        setContent {
            KosmoDartsTheme {
                val state by viewModel.state.collectAsState()
                CaptureScreen(
                    state = state,
                    actions = actions,
                    preview = {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                PreviewView(context).apply {
                                    scaleType = PreviewView.ScaleType.FIT_CENTER
                                    previewView = this
                                    bindCameraIfPossible()
                                }
                            },
                        )
                    },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        }
        observeState()
    }

    override fun onStart() {
        super.onStart()
        gravity.start()
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            viewModel.onPermission(CameraPermission.GRANTED)
        }
    }

    override fun onStop() {
        // Rule CP9: a real stop ends the session; a configuration change never does.
        viewModel.onScreenStopped(isChangingConfigurations)
        viewModel.onCameraReady(false)
        boundKey = null
        gravity.stop()
        super.onStop()
    }

    override fun onDestroy() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        super.onDestroy()
    }

    /** Binds the camera on permission, quality and preview changes, and keeps the screen on while a session runs. */
    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.map { it.permission to it.quality }.distinctUntilChanged()
                        .collect { bindCameraIfPossible() }
                }
                launch {
                    viewModel.state.map { it.keepScreenOn }.distinctUntilChanged().collect { keep ->
                        if (keep) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
            }
        }
    }

    private fun bindCameraIfPossible() {
        val view = previewView ?: return
        val state = viewModel.state.value
        if (state.permission != CameraPermission.GRANTED || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return
        }
        val key = state.quality to view
        if (key == boundKey) return
        boundKey = key
        viewModel.onCameraReady(false)
        camera.bind(this, view, state.quality, displayRotation()) { bound ->
            if (boundKey == key) viewModel.onCameraReady(bound)
        }
    }

    private fun displayRotation(): Int = display?.rotation ?: Surface.ROTATION_90

    private fun requestCamera() = permissionLauncher.launch(Manifest.permission.CAMERA)

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun appInfo(): AppInfo {
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
        return AppInfo(packageName, version)
    }

    private fun deviceInfo(): DeviceInfo = DeviceInfo(Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT)

    private companion object {
        const val SETTINGS_FILE = "capture-settings.txt"
    }
}
