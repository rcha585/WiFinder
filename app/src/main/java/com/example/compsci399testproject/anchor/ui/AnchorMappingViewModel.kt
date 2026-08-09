package com.example.compsci399testproject.anchor.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.compsci399testproject.anchor.ar.ArFramePacket
import com.example.compsci399testproject.anchor.export.ExportRepository
import com.example.compsci399testproject.anchor.export.ExportedMapping
import com.example.compsci399testproject.anchor.floorplan.FloorPlanGenerator
import com.example.compsci399testproject.anchor.session.AnchorSessionRepository
import com.example.compsci399testproject.anchor.session.MarkerPattern
import com.example.compsci399testproject.anchor.spatial.FloorPlanResult
import com.example.compsci399testproject.anchor.spatial.MappingSessionMetadata
import com.example.compsci399testproject.anchor.spatial.MappingSnapshot
import com.example.compsci399testproject.anchor.spatial.SpatialTrackingState
import com.example.compsci399testproject.anchor.wifi.SpatialWifiCollector
import com.example.compsci399testproject.viewmodel.WifiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MappingPhase { SETUP, CALIBRATION, MAPPING, PROCESSING, PREVIEW }

data class MappingUiState(
    val phase: MappingPhase = MappingPhase.SETUP,
    val sessionId: String = "",
    val markerWidthCentimetres: Float = 14f,
    val requiredAnchorIds: List<String> = listOf("A", "B", "C"),
    val detectedAnchorIds: Set<String> = emptySet(),
    val trackingState: SpatialTrackingState = SpatialTrackingState.PAUSED,
    val depthSupported: Boolean? = null,
    val samplesCollected: Int = 0,
    val depthObservations: Int = 0,
    val wifiSamples: Int = 0,
    val walkedDistanceMeters: Float = 0f,
    val mappingStartedAtEpochMillis: Long? = null,
    val mappingTimeMillis: Long = 0L,
    val errorMessage: String? = null,
    val snapshot: MappingSnapshot? = null,
    val floorPlan: FloorPlanResult? = null,
    val exportedMapping: ExportedMapping? = null,
) {
    val canStartMapping: Boolean get() = detectedAnchorIds.containsAll(requiredAnchorIds)
}

class AnchorMappingViewModel(
    application: Application,
    wifiViewModel: WifiViewModel,
) : AndroidViewModel(application) {
    private val repository = AnchorSessionRepository()
    private val wifiCollector = SpatialWifiCollector(wifiViewModel)
    private val floorPlanGenerator = FloorPlanGenerator()
    private val exportRepository = ExportRepository(application)
    private val _state = MutableStateFlow(MappingUiState())
    val state: StateFlow<MappingUiState> = _state.asStateFlow()
    private var lastUiUpdateAt = 0L

    fun prepareSession(sessionIdInput: String, markerWidthCentimetres: Float): Boolean {
        val sessionId = MarkerPattern.normalizeSessionId(sessionIdInput)
        if (sessionId.length < 4 || markerWidthCentimetres !in 5f..40f) {
            _state.update { it.copy(errorMessage = "Use a 4–16 character session ID and a measured marker width of 5–40 cm.") }
            return false
        }
        val required = listOf("A", "B", "C")
        repository.start(
            MappingSessionMetadata(
                sessionId = sessionId,
                startedAtEpochMillis = System.currentTimeMillis(),
                markerWidthMeters = markerWidthCentimetres / 100f,
                requiredAnchorIds = required,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            ),
        )
        _state.value = MappingUiState(
            phase = MappingPhase.CALIBRATION,
            sessionId = sessionId,
            markerWidthCentimetres = markerWidthCentimetres,
            requiredAnchorIds = required,
        )
        return true
    }

    fun setDepthSupported(supported: Boolean) {
        runCatching { repository.setDepthSupported(supported) }
        _state.update { it.copy(depthSupported = supported) }
    }

    fun onArFrame(packet: ArFramePacket) {
        val phase = _state.value.phase
        if (phase !in setOf(MappingPhase.CALIBRATION, MappingPhase.MAPPING)) return
        packet.anchors.forEach(repository::recordAnchor)
        if (phase == MappingPhase.MAPPING) {
            if (packet.cameraPose.trackingState == SpatialTrackingState.TRACKING) repository.recordPose(packet.cameraPose)
            repository.recordDepth(packet.depthObservations)
            packet.planeWalls.forEach { repository.recordPlaneWall(it.planeId, it.segment) }
        }
        val now = System.currentTimeMillis()
        if (now - lastUiUpdateAt >= 160L) {
            lastUiUpdateAt = now
            val startedAt = _state.value.mappingStartedAtEpochMillis
            _state.update {
                it.copy(
                    detectedAnchorIds = repository.detectedAnchorIds(),
                    trackingState = packet.cameraPose.trackingState,
                    samplesCollected = repository.sampleCount(),
                    depthObservations = repository.depthCount(),
                    wifiSamples = repository.wifiCount(),
                    walkedDistanceMeters = repository.walkedDistanceMeters(),
                    mappingTimeMillis = startedAt?.let { start -> now - start } ?: 0L,
                )
            }
        }
    }

    fun startMapping() {
        if (!_state.value.canStartMapping) return
        val startedAt = System.currentTimeMillis()
        _state.update { it.copy(phase = MappingPhase.MAPPING, mappingStartedAtEpochMillis = startedAt, errorMessage = null) }
        wifiCollector.start(
            scope = viewModelScope,
            poseProvider = repository::latestPose,
            onSamples = { samples ->
                if (_state.value.phase == MappingPhase.MAPPING) repository.recordWifi(samples)
            },
        )
    }

    fun finishMapping() {
        if (_state.value.phase != MappingPhase.MAPPING) return
        wifiCollector.stop()
        _state.update { it.copy(phase = MappingPhase.PROCESSING) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val snapshot = repository.snapshot(System.currentTimeMillis())
                    val floorPlan = floorPlanGenerator.generate(snapshot)
                    val exported = exportRepository.export(snapshot, floorPlan)
                    Triple(snapshot, floorPlan, exported)
                }
            }.onSuccess { (snapshot, floorPlan, exported) ->
                _state.update {
                    it.copy(
                        phase = MappingPhase.PREVIEW,
                        snapshot = snapshot,
                        floorPlan = floorPlan,
                        exportedMapping = exported,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(phase = MappingPhase.MAPPING, errorMessage = "Export failed: ${error.message}") }
            }
        }
    }

    fun reportError(message: String) {
        _state.update { it.copy(errorMessage = message) }
    }

    fun reset() {
        wifiCollector.stop()
        _state.value = MappingUiState()
    }

    override fun onCleared() {
        wifiCollector.stop()
        super.onCleared()
    }
}

class AnchorMappingViewModelFactory(
    private val application: Application,
    private val wifiViewModel: WifiViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AnchorMappingViewModel(application, wifiViewModel) as T
}
