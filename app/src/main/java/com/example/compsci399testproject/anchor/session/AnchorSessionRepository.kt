package com.example.compsci399testproject.anchor.session

import com.example.compsci399testproject.anchor.spatial.AnchorObservation
import com.example.compsci399testproject.anchor.spatial.CoordinateTransforms
import com.example.compsci399testproject.anchor.spatial.DepthObservation
import com.example.compsci399testproject.anchor.spatial.MappingSessionMetadata
import com.example.compsci399testproject.anchor.spatial.MappingSnapshot
import com.example.compsci399testproject.anchor.spatial.PoseSample
import com.example.compsci399testproject.anchor.spatial.SpatialWifiSample
import com.example.compsci399testproject.anchor.spatial.WallSegment

class AnchorSessionRepository {
    private var metadata: MappingSessionMetadata? = null
    private val anchors = linkedMapOf<String, AnchorObservation>()
    private val trajectory = mutableListOf<PoseSample>()
    private val depthObservations = mutableListOf<DepthObservation>()
    private val wifiSamples = mutableListOf<SpatialWifiSample>()
    private val planeWallCandidates = linkedMapOf<String, WallSegment>()

    @Synchronized
    fun start(metadata: MappingSessionMetadata) {
        this.metadata = metadata
        anchors.clear()
        trajectory.clear()
        depthObservations.clear()
        wifiSamples.clear()
        planeWallCandidates.clear()
    }

    @Synchronized fun setDepthSupported(supported: Boolean) {
        metadata = requireMetadata().copy(depthSupported = supported)
    }

    @Synchronized fun recordAnchor(observation: AnchorObservation) {
        anchors[observation.anchorId] = observation
    }

    @Synchronized fun recordPose(sample: PoseSample) {
        val previous = trajectory.lastOrNull()
        if (previous == null || sample.timestampEpochMillis - previous.timestampEpochMillis >= 80L) {
            trajectory += sample
        }
    }

    @Synchronized fun recordDepth(observations: List<DepthObservation>) {
        depthObservations += observations
    }

    @Synchronized fun recordWifi(samples: List<SpatialWifiSample>) {
        wifiSamples += samples
    }

    @Synchronized fun recordPlaneWall(id: String, segment: WallSegment) {
        planeWallCandidates[id] = segment
    }

    @Synchronized fun detectedAnchorIds(): Set<String> = anchors.keys.toSet()

    @Synchronized fun latestPose(): PoseSample? = trajectory.lastOrNull()

    @Synchronized fun sampleCount(): Int = trajectory.size

    @Synchronized fun depthCount(): Int = depthObservations.size

    @Synchronized fun wifiCount(): Int = wifiSamples.size

    @Synchronized fun walkedDistanceMeters(): Float = trajectory.zipWithNext().sumOf { (first, second) ->
        CoordinateTransforms.distanceOnFloor(first.position, second.position).toDouble()
    }.toFloat()

    @Synchronized
    fun snapshot(finishedAtEpochMillis: Long? = null): MappingSnapshot {
        val finalMetadata = requireMetadata().copy(
            finishedAtEpochMillis = finishedAtEpochMillis ?: metadata?.finishedAtEpochMillis,
        )
        if (finishedAtEpochMillis != null) metadata = finalMetadata
        return MappingSnapshot(
            metadata = finalMetadata,
            anchors = anchors.values.toList(),
            trajectory = trajectory.toList(),
            depthObservations = depthObservations.toList(),
            wifiSamples = wifiSamples.toList(),
            planeWallCandidates = planeWallCandidates.values.toList(),
        )
    }

    private fun requireMetadata(): MappingSessionMetadata =
        checkNotNull(metadata) { "A mapping session has not been started" }
}
