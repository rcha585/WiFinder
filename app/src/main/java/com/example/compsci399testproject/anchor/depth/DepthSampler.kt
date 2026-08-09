package com.example.compsci399testproject.anchor.depth

import com.example.compsci399testproject.anchor.ar.PlaneWallObservation
import com.example.compsci399testproject.anchor.ar.toSpatialCoordinate
import com.example.compsci399testproject.anchor.spatial.CoordinateTransforms
import com.example.compsci399testproject.anchor.spatial.DepthObservation
import com.example.compsci399testproject.anchor.spatial.DepthObservationSource
import com.example.compsci399testproject.anchor.spatial.FloorPoint
import com.example.compsci399testproject.anchor.spatial.PoseSample
import com.example.compsci399testproject.anchor.spatial.WallSegment
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState

class DepthSampler(private val intervalNanos: Long = 450_000_000L) {
    private var lastSampleTimestampNanos = 0L

    fun sample(
        session: Session,
        frame: Frame,
        viewportWidth: Int,
        viewportHeight: Int,
        sourcePose: PoseSample,
        depthSupported: Boolean,
        timestampEpochMillis: Long,
    ): Pair<List<DepthObservation>, List<PlaneWallObservation>> {
        val planeWalls = collectVerticalPlanes(session)
        if (frame.timestamp - lastSampleTimestampNanos < intervalNanos || viewportWidth <= 0 || viewportHeight <= 0) {
            return emptyList<DepthObservation>() to planeWalls
        }
        lastSampleTimestampNanos = frame.timestamp
        val observations = mutableListOf<DepthObservation>()
        val columns = 4
        val rows = 3
        for (row in 1..rows) {
            for (column in 1..columns) {
                val x = viewportWidth * column.toFloat() / (columns + 1)
                val y = viewportHeight * row.toFloat() / (rows + 1)
                val hit = frame.hitTest(x, y).firstOrNull { result ->
                    result.trackable is DepthPoint || result.trackable is Plane
                } ?: continue
                val trackable = hit.trackable
                val plane = trackable as? Plane
                val isVerticalPlane = plane?.type == Plane.Type.VERTICAL
                observations += DepthObservation(
                    timestampEpochMillis = timestampEpochMillis,
                    worldCoordinate = hit.hitPose.toSpatialCoordinate(),
                    sourceCameraPose = sourcePose,
                    source = if (trackable is DepthPoint && depthSupported) DepthObservationSource.DEPTH_HIT else DepthObservationSource.PLANE_HIT,
                    confidence = null,
                    associatedPlaneId = plane?.let { planeId(it) },
                    wallCandidate = trackable is DepthPoint || isVerticalPlane,
                )
            }
        }
        return observations to planeWalls
    }

    private fun collectVerticalPlanes(session: Session): List<PlaneWallObservation> =
        session.getAllTrackables(Plane::class.java)
            .asSequence()
            .filter { it.trackingState == TrackingState.TRACKING && it.type == Plane.Type.VERTICAL && it.subsumedBy == null }
            .mapNotNull { plane ->
                if (plane.extentX < 0.25f) return@mapNotNull null
                val axis = plane.centerPose.xAxis
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
                val polygonSupport = (plane.polygon?.remaining() ?: 0) / 2
                val id = planeId(plane)
                PlaneWallObservation(
                    planeId = id,
                    segment = WallSegment(
                        start = CoordinateTransforms.worldToFloor(startWorld),
                        end = CoordinateTransforms.worldToFloor(endWorld),
                        source = "arcore-vertical-plane",
                        supportCount = polygonSupport.coerceAtLeast(2),
                    ),
                )
            }
            .toList()

    private fun planeId(plane: Plane): String = "plane-${plane.hashCode().toUInt().toString(16)}"
}
