package com.example.compsci399testproject.anchor.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import com.example.compsci399testproject.anchor.ar.ArCoreSurfaceView
import com.example.compsci399testproject.anchor.ar.MarkerBitmapFactory
import com.example.compsci399testproject.anchor.export.ExportedMapping
import com.example.compsci399testproject.ui.theme.COMPSCI399TestProjectTheme
import com.example.compsci399testproject.viewmodel.WifiScannerViewModelFactory
import com.example.compsci399testproject.viewmodel.WifiViewModel
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session

data class ArRuntime(val session: Session, val depthSupported: Boolean)

class AnchorMappingActivity : ComponentActivity() {
    private lateinit var viewModel: AnchorMappingViewModel
    private var runtime by mutableStateOf<ArRuntime?>(null)
    private var surfaceView: ArCoreSurfaceView? = null
    private var pendingConfiguration: Pair<String, Float>? = null
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

    private fun beginCalibration(sessionId: String, markerWidthCentimetres: Float) {
        if (!viewModel.prepareSession(sessionId, markerWidthCentimetres)) return
        pendingConfiguration = viewModel.state.value.sessionId to markerWidthCentimetres / 100f
        ensureArRuntime()
    }

    private fun ensureArRuntime() {
        val (sessionId, markerWidthMeters) = pendingConfiguration ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }
            closeAr()
            val session = Session(this)
            val supportsDepth = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            val config = session.config.apply {
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                augmentedImageDatabase = MarkerBitmapFactory.createDatabase(session, sessionId, markerWidthMeters)
                depthMode = if (supportsDepth) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
            }
            session.configure(config)
            runtime = ArRuntime(session, supportsDepth)
            viewModel.setDepthSupported(supportsDepth)
            if (activityResumed) resumeAr()
        } catch (error: Exception) {
            viewModel.reportError("ARCore could not start: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun attachSurface(view: ArCoreSurfaceView) {
        surfaceView = view
        if (sessionResumed) view.onResume()
    }

    private fun releaseSurface(view: ArCoreSurfaceView) {
        if (surfaceView === view) {
            view.onPause()
            surfaceView = null
        }
    }

    private fun resumeAr() {
        val session = runtime?.session ?: return
        if (!activityResumed || sessionResumed) return
        try {
            session.resume()
            sessionResumed = true
            surfaceView?.onResume()
        } catch (error: Exception) {
            viewModel.reportError("ARCore camera unavailable: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun pauseAr() {
        if (!sessionResumed) return
        surfaceView?.onPause()
        runtime?.session?.pause()
        sessionResumed = false
    }

    private fun closeAr() {
        pauseAr()
        runtime?.session?.close()
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
}
