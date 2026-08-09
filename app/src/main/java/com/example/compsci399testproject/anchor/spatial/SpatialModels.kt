package com.example.compsci399testproject.anchor.spatial

data class SpatialCoordinate(
    val xMeters: Float,
    val yMeters: Float,
    val zMeters: Float,
)

data class SpatialQuaternion(
    val x: Float,
    val y: Float,
    val z: Float,
    val w: Float,
)

data class FloorPoint(
    val xMeters: Float,
    val yMeters: Float,
)

enum class SpatialTrackingState { TRACKING, PAUSED, STOPPED }

data class PoseSample(
    val timestampEpochMillis: Long,
    val frameTimestampNanos: Long,
    val position: SpatialCoordinate,
    val rotation: SpatialQuaternion,
    val trackingState: SpatialTrackingState,
)

data class AnchorObservation(
    val anchorId: String,
    val timestampEpochMillis: Long,
    val position: SpatialCoordinate,
    val rotation: SpatialQuaternion,
    val trackingState: SpatialTrackingState,
    val trackingMethod: String,
    val confidence: Float? = null,
)

enum class DepthObservationSource { DEPTH_HIT, PLANE_HIT }

data class DepthObservation(
    val timestampEpochMillis: Long,
    val worldCoordinate: SpatialCoordinate,
    val sourceCameraPose: PoseSample,
    val source: DepthObservationSource,
    val confidence: Float? = null,
    val associatedPlaneId: String? = null,
    val wallCandidate: Boolean = false,
)

data class SpatialWifiSample(
    val timestampEpochMillis: Long,
    val pose: SpatialCoordinate,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
)

data class WallSegment(
    val start: FloorPoint,
    val end: FloorPoint,
    val source: String,
    val supportCount: Int,
    val rmsErrorMeters: Float? = null,
)

data class FloorPlanResult(
    val walls: List<WallSegment>,
    val corners: List<FloorPoint>,
    val roomOutline: List<FloorPoint>?,
    val reconstructionNotes: List<String>,
)

data class MappingSessionMetadata(
    val sessionId: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long? = null,
    val markerWidthMeters: Float,
    val requiredAnchorIds: List<String>,
    val depthSupported: Boolean? = null,
    val deviceModel: String,
    val coordinateSystem: String = "ARCore world metres; floor=(world.x, -world.z); world.y is up",
)

data class MappingSnapshot(
    val metadata: MappingSessionMetadata,
    val anchors: List<AnchorObservation>,
    val trajectory: List<PoseSample>,
    val depthObservations: List<DepthObservation>,
    val wifiSamples: List<SpatialWifiSample>,
    val planeWallCandidates: List<WallSegment>,
)
