package com.example.compsci399testproject.anchor.ui

import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.compsci399testproject.anchor.ar.AnchorArRuntime
import com.example.compsci399testproject.anchor.ar.ArCoreSurfaceView
import com.example.compsci399testproject.anchor.ar.GoogleArRuntime
import com.example.compsci399testproject.anchor.ar.HuaweiArRuntime
import com.example.compsci399testproject.anchor.ar.HuaweiArSurfaceView
import com.example.compsci399testproject.anchor.export.ExportedMapping
import java.util.Locale

private val defaultPresetWidths = mapOf("A" to "14.0", "B" to "5.4", "C" to "16.0")

@Composable
fun AnchorMappingScreen(
    viewModel: AnchorMappingViewModel,
    runtime: AnchorArRuntime?,
    onBeginCalibration: (String, Map<String, Float>) -> Unit,
    onRetryAr: () -> Unit,
    onFinishMapping: () -> Unit,
    onReset: () -> Unit,
    onShare: (ExportedMapping) -> Unit,
    onSurfaceReady: (GLSurfaceView) -> Unit,
    onSurfaceReleased: (GLSurfaceView) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF07111F))) {
        when (state.phase) {
            MappingPhase.SETUP -> SetupScreen(onBeginCalibration)
            MappingPhase.CALIBRATION -> ArMappingSurface(
                state = state,
                runtime = runtime,
                viewModel = viewModel,
                onPrimary = viewModel::startMapping,
                primaryLabel = "Start Mapping",
                primaryEnabled = state.canStartMapping,
                onRetryAr = onRetryAr,
                onReset = onReset,
                onSurfaceReady = onSurfaceReady,
                onSurfaceReleased = onSurfaceReleased,
            )
            MappingPhase.MAPPING -> ArMappingSurface(
                state = state,
                runtime = runtime,
                viewModel = viewModel,
                onPrimary = onFinishMapping,
                primaryLabel = "Finish Scan",
                primaryEnabled = state.samplesCollected > 0,
                onRetryAr = onRetryAr,
                onReset = onReset,
                onSurfaceReady = onSurfaceReady,
                onSurfaceReleased = onSurfaceReleased,
            )
            MappingPhase.PROCESSING -> ProcessingScreen()
            MappingPhase.PREVIEW -> PreviewScreen(state, onShare, onReset)
        }
        state.errorMessage?.let { message ->
            Card(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
            ) {
                Text(message, color = Color.White, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun SetupScreen(onBeginCalibration: (String, Map<String, Float>) -> Unit) {
    var sessionId by remember { mutableStateOf("") }
    var markerWidths by remember { mutableStateOf(defaultPresetWidths) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("WiFinder Anchor", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
        Text("MVP 1 experimental metric room mapping", color = Color(0xFF72E7C5))
        Spacer(Modifier.height(24.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF10243A))) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("New Mapping Session", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "Preset sizes: A = 14 inch laptop Chrome, B = iPhone 14 Pro Safari, C = 13 inch iPad Pro Safari. Adjust only if the web page shows a different preset.",
                    color = Color(0xFFB8C8D8),
                )
                OutlinedTextField(
                    value = sessionId,
                    onValueChange = { sessionId = it.uppercase().filter(Char::isLetterOrDigit).take(16) },
                    label = { Text("Session ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                MarkerWidthInput("Anchor A width", "14 inch laptop", markerWidths.getValue("A")) {
                    markerWidths = markerWidths + ("A" to it)
                }
                MarkerWidthInput("Anchor B width", "iPhone 14 Pro", markerWidths.getValue("B")) {
                    markerWidths = markerWidths + ("B" to it)
                }
                MarkerWidthInput("Anchor C width", "13 inch iPad Pro", markerWidths.getValue("C")) {
                    markerWidths = markerWidths + ("C" to it)
                }
                Button(
                    onClick = {
                        onBeginCalibration(
                            sessionId,
                            markerWidths.mapValues { (_, value) -> value.toFloatOrNull() ?: 0f },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Step 1 - Calibrate Anchors") }
                Text(
                    "Huawei phones use Huawei AR Engine first. Other Android devices fall back to Google ARCore. No WiFinder cloud service is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF91A7BA),
                )
            }
        }
    }
}

@Composable
private fun MarkerWidthInput(label: String, helper: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { character -> character.isDigit() || character == '.' }.take(5)) },
        label = { Text(label) },
        supportingText = { Text(helper) },
        suffix = { Text("cm") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArMappingSurface(
    state: MappingUiState,
    runtime: AnchorArRuntime?,
    viewModel: AnchorMappingViewModel,
    onPrimary: () -> Unit,
    primaryLabel: String,
    primaryEnabled: Boolean,
    onRetryAr: () -> Unit,
    onReset: () -> Unit,
    onSurfaceReady: (GLSurfaceView) -> Unit,
    onSurfaceReleased: (GLSurfaceView) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (runtime != null) {
            ArCameraView(runtime, viewModel, onSurfaceReady, onSurfaceReleased)
        } else {
            ArWaitingView(hasError = state.errorMessage != null, onRetryAr = onRetryAr, onReset = onReset)
        }
        Card(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xDD07111F)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (state.phase == MappingPhase.CALIBRATION) "Step 1 - Calibrate Anchors" else "Mapping in progress",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                if (state.phase == MappingPhase.CALIBRATION) {
                    Text("Point the camera at each stationary marker until it is tracked.", color = Color(0xFFB8C8D8))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.requiredAnchorIds.forEach { anchorId -> AnchorStatus(anchorId, anchorId in state.detectedAnchorIds) }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("Tracking", state.trackingState.name)
                        Metric("Samples", state.samplesCollected.toString())
                        Metric("Time", formatDuration(state.mappingTimeMillis))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Metric("Distance", String.format(Locale.US, "%.1f m", state.walkedDistanceMeters))
                        Metric("Depth pts", state.depthObservations.toString())
                        Metric("Wi-Fi", state.wifiSamples.toString())
                    }
                }
                val depthText = when (state.depthSupported) {
                    true -> "Depth API enabled"
                    false -> "${runtime?.engineLabel ?: "AR"} plane tracking enabled; depth points unavailable"
                    null -> "Checking AR runtime support..."
                }
                Text(depthText, color = if (state.depthSupported == false) Color(0xFFFFC66D) else Color(0xFF72E7C5), style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(
            onClick = onPrimary,
            enabled = runtime != null && primaryEnabled,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp).height(54.dp),
        ) { Text(primaryLabel) }
    }
}

@Composable
private fun ArWaitingView(hasError: Boolean, onRetryAr: () -> Unit, onReset: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (!hasError) {
            CircularProgressIndicator(color = Color(0xFF72E7C5))
            Spacer(Modifier.height(12.dp))
            Text("Preparing AR camera...", color = Color.White)
        } else {
            Text("AR camera did not start.", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetryAr) { Text("Retry AR camera") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onReset) { Text("Back to setup") }
        }
    }
}

@Composable
private fun ArCameraView(
    runtime: AnchorArRuntime,
    viewModel: AnchorMappingViewModel,
    onSurfaceReady: (GLSurfaceView) -> Unit,
    onSurfaceReleased: (GLSurfaceView) -> Unit,
) {
    val context = LocalContext.current
    var view by remember(runtime) { mutableStateOf<GLSurfaceView?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            when (runtime) {
                is GoogleArRuntime -> ArCoreSurfaceView(
                    context = context,
                    session = runtime.session,
                    depthSupported = runtime.depthSupported,
                    onFrame = viewModel::onArFrame,
                    onError = viewModel::reportError,
                )
                is HuaweiArRuntime -> HuaweiArSurfaceView(
                    context = context,
                    session = runtime.session,
                    onFrame = viewModel::onArFrame,
                    onError = viewModel::reportError,
                )
            }.also { surface -> view = surface; onSurfaceReady(surface) }
        },
    )
    DisposableEffect(runtime) {
        onDispose { view?.let(onSurfaceReleased) }
    }
}

@Composable
private fun AnchorStatus(anchorId: String, detected: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = if (detected) Color(0xFF0B6E58) else Color(0xFF26394C))) {
        Text(
            "$anchorId: ${if (detected) "detected" else "waiting"}",
            color = Color.White,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, color = Color(0xFF91A7BA), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White)
    }
}

@Composable
private fun ProcessingScreen() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = Color(0xFF72E7C5))
        Spacer(Modifier.height(14.dp))
        Text("Fitting walls and exporting...", color = Color.White)
    }
}

@Composable
private fun PreviewScreen(state: MappingUiState, onShare: (ExportedMapping) -> Unit, onReset: () -> Unit) {
    val floorPlan = state.floorPlan ?: return
    val snapshot = state.snapshot ?: return
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("2D Preview", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            "${floorPlan.walls.size} walls | ${floorPlan.corners.size} corners | ${snapshot.trajectory.size} poses",
            color = Color(0xFFB8C8D8),
        )
        FloorPlanPreview(snapshot, floorPlan, Modifier.weight(1f).fillMaxWidth())
        floorPlan.reconstructionNotes.forEach { Text(it, color = Color(0xFFFFC66D), style = MaterialTheme.typography.bodySmall) }
        state.exportedMapping?.let { exported ->
            Text("Saved: ${exported.directory.absolutePath}", color = Color(0xFF91A7BA), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onShare(exported) }, modifier = Modifier.fillMaxWidth()) { Text("Share mapping.json + floorplan.svg") }
        }
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("New Mapping Session") }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1_000L
    return "%d:%02d".format(Locale.US, totalSeconds / 60L, totalSeconds % 60L)
}
