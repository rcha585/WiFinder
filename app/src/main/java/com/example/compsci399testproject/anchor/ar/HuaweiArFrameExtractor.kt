package com.example.compsci399testproject.anchor.ar

import com.example.compsci399testproject.anchor.spatial.AnchorObservation
import com.example.compsci399testproject.anchor.spatial.CoordinateTransforms
import com.example.compsci399testproject.anchor.spatial.DepthObservation
import com.example.compsci399testproject.anchor.spatial.DepthObservationSource
import com.example.compsci399testproject.anchor.spatial.PoseSample
import com.example.compsci399testproject.anchor.spatial.SpatialTrackingState
import com.example.compsci399testproject.anchor.spatial.WallSegment
import com.huawei.hiar.ARAugmentedImage
import com.huawei.hiar.ARFrame
import com.huawei.hiar.ARHitResult
import com.huawei.hiar.ARPlane
import com.huawei.hiar.ARSession
import com.huawei.hiar.ARTrackable

class HuaweiArFrameExtractor(private val intervalNanos: Long = 450_000_000L) {
    private var lastSampleTimestampNanos = 0L

    fun extract(
        session: ARSession,
        frame: ARFrame,
        viewportWidth: Int,
        viewportHeight: Int,
    ): ArFramePacket {
        val now = System.currentTimeMillis()
        val camera = frame.camera
        val cameraPose = PoseSample(
            timestampEpochMillis = now,
            frameTimestampNanos = frame.timestampNs,
            position = camera.pose.toSpatialCoordinate(),
            rotation = camera.pose.toSpatialQuaternion(),
            trackingState = camera.trackingState.toSpatialTrackingState(),
        )
        val anchors = frame.getUpdatedTrackables(ARAugmentedImage::class.java)
            .filter { it.trackingState == ARTrackable.TrackingState.TRACKING }
            .mapNotNull { image ->
                val anchorId = image.name.removePrefix("Anchor ").trim()
                if (anchorId.length != 1) return@mapNotNull null
                AnchorObservation(
                    anchorId = anchorId,
                    timestampEpochMillis = now,
                    position = image.centerPose.toSpatialCoordinate(),
                    rotation = image.centerPose.toSpatialQuaternion(),
                    trackingState = image.trackingState.toSpatialTrackingState(),
                    trackingMethod = "HUAWEI_${image.trackingState.name}",
                    confidence = null,
                )
            }
        val (depth, planeWalls) = if (cameraPose.trackingState == SpatialTrackingState.TRACKING) {
            samplePlanes(session, frame, viewportWidth, viewportHeight, cameraPose, now)
        } else {
            emptyList<DepthObservation>() to emptyList()
        }
        return ArFramePacket(cameraPose, anchors, depth, planeWalls)
    }

    private fun samplePlanes(
        session: ARSession,
        frame: ARFrame,
        viewportWidth: Int,
        viewportHeight: Int,
        sourcePose: PoseSample,
        timestampEpochMillis: Long,
    ): Pair<List<DepthObservation>, List<PlaneWallObservation>> {
        val planeWalls = collectVerticalPlanes(session)
        if (frame.timestampNs - lastSampleTimestampNanos < intervalNanos || viewportWidth <= 0 || viewportHeight <= 0) {
            return emptyList<DepthObservation>() to planeWalls
        }
        lastSampleTimestampNanos = frame.timestampNs
        val observations = mutableListOf<DepthObservation>()
        val columns = 4
        val rows = 3
        for (row in 1..rows) {
            for (column in 1..columns) {
                val x = viewportWidth * column.toFloat() / (columns + 1)
                val y = viewportHeight * row.toFloat() / (rows + 1)
                val hit = frame.hitTest(x, y).firstOrNull { it.trackable is ARPlane } ?: continue
                val plane = hit.trackable as? ARPlane ?: continue
                observations += planeHitObservation(hit, plane, sourcePose, timestampEpochMillis)
            }
        }
        return observations to planeWalls
    }

    private fun planeHitObservation(
        hit: ARHitResult,
        plane: ARPlane,
        sourcePose: PoseSample,
        timestampEpochMillis: Long,
    ): DepthObservation = DepthObservation(
        timestampEpochMillis = timestampEpochMillis,
        worldCoordinate = hit.hitPose.toSpatialCoordinate(),
        sourceCameraPose = sourcePose,
        source = DepthObservationSource.PLANE_HIT,
        confidence = null,
        associatedPlaneId = planeId(plane),
        wallCandidate = plane.type == ARPlane.PlaneType.VERTICAL_FACING,
    )

    private fun collectVerticalPlanes(session: ARSession): List<PlaneWallObservation> =
        session.getAllTrackables(ARPlane::class.java)
            .asSequence()
            .filter {
                it.trackingState == ARTrackable.TrackingState.TRACKING &&
                    it.type == ARPlane.PlaneType.VERTICAL_FACING &&
                    it.subsumedBy == null
            }
            .mapNotNull { plane ->
                if (plane.extentX < 0.25f) return@mapNotNull null
                val axis = FloatArray(3)
                plane.centerPose.getTransformedAxis(0, 1.0f, axis, 0)
                val halfWidth = plane.extentX / 2f
                val centre = plane.centerPose.toSpatialCoordinate()
                val startWorld = centre.copy(
                    xMeters = centre.xMeters - axis[0] * halfWidth,
                    zMeters = centre.zMeters - axis[2] * halfWidth,
                )
                val endWorld = centre.copy(
                    xMeters = centre.xMeters + axis[0] * halfWidth,
                    zMeters = centre.zMeters + axis[2] * halfWidth,
                )
                val id = planeId(plane)
                val polygonSupport = (plane.planePolygon?.remaining() ?: 0) / 2
                PlaneWallObservation(
                    planeId = id,
                    segment = WallSegment(
                        start = CoordinateTransforms.worldToFloor(startWorld),
                        end = CoordinateTransforms.worldToFloor(endWorld),
                        source = "huawei-vertical-plane",
                        supportCount = polygonSupport.coerceAtLeast(2),
                    ),
                )
            }
            .toList()

    private fun planeId(plane: ARPlane): String = "huawei-plane-${plane.hashCode().toUInt().toString(16)}"
}
