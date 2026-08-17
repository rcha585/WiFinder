package com.example.compsci399testproject.anchor.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.example.compsci399testproject.anchor.ar.AnchorArRuntime
import com.example.compsci399testproject.anchor.ar.GoogleArRuntime
import com.example.compsci399testproject.anchor.ar.HuaweiArRuntime
import com.example.compsci399testproject.anchor.ar.MarkerBitmapFactory
import com.example.compsci399testproject.anchor.export.ExportedMapping
import com.example.compsci399testproject.ui.theme.COMPSCI399TestProjectTheme
import com.example.compsci399testproject.viewmodel.WifiScannerViewModelFactory
import com.example.compsci399testproject.viewmodel.WifiViewModel
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config as GoogleConfig
import com.google.ar.core.Session as GoogleSession
import com.huawei.hiar.ARConfigBase
import com.huawei.hiar.AREnginesApk
import com.huawei.hiar.ARSession as HuaweiSession
import com.huawei.hiar.ARWorldTrackingConfig

private data class PendingArConfiguration(
    val sessionId: String,
    val markerWidthsMetersByAnchor: Map<String, Float>,
)

class AnchorMappingActivity : ComponentActivity() {
    private lateinit var viewModel: AnchorMappingViewModel
    private var runtime by mutableStateOf<AnchorArRuntime?>(null)
    private var surfaceView: GLSurfaceView? = null
    private var pendingConfiguration: PendingArConfiguration? = null
    private var installRequested = false
    private var activityResumed = false
    private var sessionResumed = false

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) ensureArRuntime() else viewModel.reportError("Camera permission is required for Anchor Mapping.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wifiViewModel = ViewModelProvider(this, WifiScannerViewModelFactory(application))[WifiViewModel::class.java]
        viewModel = ViewModelProvider(
            this,
            AnchorMappingViewModelFactory(application, wifiViewModel),
        )[AnchorMappingViewModel::class.java]
        setContent {
            COMPSCI399TestProjectTheme {
                AnchorMappingScreen(
                    viewModel = viewModel,
                    runtime = runtime,
                    onBeginCalibration = ::beginCalibration,
                    onRetryAr = ::ensureArRuntime,
                    onFinishMapping = ::finishMapping,
                    onReset = ::reset,
                    onShare = ::share,
                    onSurfaceReady = ::attachSurface,
                    onSurfaceReleased = ::releaseSurface,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if (pendingConfiguration != null && runtime == null) ensureArRuntime() else resumeAr()
    }

    override fun onPause() {
        activityResumed = false
        pauseAr()
        super.onPause()
    }

    override fun onDestroy() {
        closeAr()
        super.onDestroy()
    }

    private fun beginCalibration(sessionId: String, markerWidthsCentimetresByAnchor: Map<String, Float>) {
        if (!viewModel.prepareSession(sessionId, markerWidthsCentimetresByAnchor)) return
        pendingConfiguration = PendingArConfiguration(
            sessionId = viewModel.state.value.sessionId,
            markerWidthsMetersByAnchor = viewModel.state.value.markerWidthsCentimetresByAnchor.mapValues { it.value / 100f },
        )
        ensureArRuntime()
    }

    private fun ensureArRuntime() {
        val configuration = pendingConfiguration ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        try {
            closeAr()
            val nextRuntime = if (isHuaweiDevice()) {
                createHuaweiRuntime(configuration)
            } else {
                createGoogleRuntime(configuration)
            }
            runtime = nextRuntime
            viewModel.setArRuntime(nextRuntime.engineLabel, nextRuntime.depthSupported)
            if (activityResumed) resumeAr()
        } catch (error: Exception) {
            val engineLabel = if (isHuaweiDevice()) "Huawei AR Engine" else "Google ARCore"
            viewModel.reportError("$engineLabel could not start: ${error.userMessage()}")
        }
    }

    private fun createHuaweiRuntime(configuration: PendingArConfiguration): HuaweiArRuntime {
        if (!runCatching { AREnginesApk.isAREngineApkReady(this) }.getOrDefault(false)) {
            throw IllegalStateException("Huawei AR Engine service is not ready. Install or update Huawei AR Engine from AppGallery.")
        }
        val session = HuaweiSession(applicationContext)
        val config = ARWorldTrackingConfig(session).apply {
            focusMode = ARConfigBase.FocusMode.AUTO_FOCUS
            planeFindingMode = ARConfigBase.PlaneFindingMode.ENABLE
            updateMode = ARConfigBase.UpdateMode.LATEST_CAMERA_IMAGE
            augmentedImageDatabase = MarkerBitmapFactory.createHuaweiDatabase(
                session = session,
                sessionId = configuration.sessionId,
                markerWidthsMetersByAnchor = configuration.markerWidthsMetersByAnchor,
            )
        }
        if (!session.isSupported(config)) {
            session.stop()
            throw IllegalStateException("Huawei AR Engine does not support this tracking configuration on this device.")
        }
        session.configure(config)
        return HuaweiArRuntime(session)
    }

    private fun createGoogleRuntime(configuration: PendingArConfiguration): GoogleArRuntime {
        when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                installRequested = true
                throw IllegalStateException("Google Play Services for AR installation was requested.")
            }
            ArCoreApk.InstallStatus.INSTALLED -> Unit
        }
        val session = GoogleSession(this)
        val supportsDepth = session.isDepthModeSupported(GoogleConfig.DepthMode.AUTOMATIC)
        val config = session.config.apply {
            planeFindingMode = GoogleConfig.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            updateMode = GoogleConfig.UpdateMode.LATEST_CAMERA_IMAGE
            augmentedImageDatabase = MarkerBitmapFactory.createGoogleDatabase(
                session = session,
                sessionId = configuration.sessionId,
                markerWidthsMetersByAnchor = configuration.markerWidthsMetersByAnchor,
            )
            depthMode = if (supportsDepth) GoogleConfig.DepthMode.AUTOMATIC else GoogleConfig.DepthMode.DISABLED
        }
        session.configure(config)
        return GoogleArRuntime(session, supportsDepth)
    }

    private fun attachSurface(view: GLSurfaceView) {
        surfaceView = view
        if (sessionResumed) view.onResume()
    }

    private fun releaseSurface(view: GLSurfaceView) {
        if (surfaceView === view) {
            view.onPause()
            surfaceView = null
        }
    }

    private fun resumeAr() {
        val currentRuntime = runtime ?: return
        if (!activityResumed || sessionResumed) return
        try {
            when (currentRuntime) {
                is GoogleArRuntime -> currentRuntime.session.resume()
                is HuaweiArRuntime -> currentRuntime.session.resume()
            }
            sessionResumed = true
            surfaceView?.onResume()
        } catch (error: Exception) {
            viewModel.reportError("${currentRuntime.engineLabel} camera unavailable: ${error.userMessage()}")
        }
    }

    private fun pauseAr() {
        if (!sessionResumed) return
        surfaceView?.onPause()
        when (val currentRuntime = runtime) {
            is GoogleArRuntime -> currentRuntime.session.pause()
            is HuaweiArRuntime -> currentRuntime.session.pause()
            null -> Unit
        }
        sessionResumed = false
    }

    private fun closeAr() {
        pauseAr()
        when (val currentRuntime = runtime) {
            is GoogleArRuntime -> currentRuntime.session.close()
            is HuaweiArRuntime -> currentRuntime.session.stop()
            null -> Unit
        }
        runtime = null
    }

    private fun finishMapping() {
        viewModel.finishMapping()
        closeAr()
        pendingConfiguration = null
    }

    private fun reset() {
        closeAr()
        pendingConfiguration = null
        viewModel.reset()
    }

    private fun share(exported: ExportedMapping) {
        val authority = "$packageName.fileprovider"
        val uris = arrayListOf(
            FileProvider.getUriForFile(this, authority, exported.mappingJson),
            FileProvider.getUriForFile(this, authority, exported.floorPlanSvg),
        )
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, "WiFinder Anchor ${viewModel.state.value.sessionId}")
        }
        startActivity(Intent.createChooser(intent, "Share mapping exports"))
    }

    private fun isHuaweiDevice(): Boolean =
        Build.MANUFACTURER.equals("HUAWEI", ignoreCase = true) ||
            Build.BRAND.equals("HUAWEI", ignoreCase = true)

    private fun Exception.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
