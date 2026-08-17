package com.example.compsci399testproject.anchor.ar

import com.example.compsci399testproject.anchor.depth.DepthSampler
import com.example.compsci399testproject.anchor.spatial.AnchorObservation
import com.example.compsci399testproject.anchor.spatial.PoseSample
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState

class ArFrameExtractor(private val depthSampler: DepthSampler = DepthSampler()) {
    fun extract(
        session: Session,
        frame: Frame,
        viewportWidth: Int,
        viewportHeight: Int,
        depthSupported: Boolean,
    ): ArFramePacket {
        val now = System.currentTimeMillis()
        val camera = frame.camera
        val cameraPose = PoseSample(
            timestampEpochMillis = now,
            frameTimestampNanos = frame.timestamp,
            position = camera.pose.toSpatialCoordinate(),
            rotation = camera.pose.toSpatialQuaternion(),
            trackingState = camera.trackingState.toSpatialTrackingState(),
        )
        val anchors = frame.getUpdatedTrackables(AugmentedImage::class.java)
            .filter { it.trackingState == TrackingState.TRACKING }
            .mapNotNull { image ->
                val anchorId = image.name.removePrefix("Anchor ").trim()
                if (anchorId.length != 1) return@mapNotNull null
                AnchorObservation(
                    anchorId = anchorId,
                    timestampEpochMillis = now,
                    position = image.centerPose.toSpatialCoordinate(),
                    rotation = image.centerPose.toSpatialQuaternion(),
                    trackingState = image.trackingState.toSpatialTrackingState(),
                    trackingMethod = image.trackingMethod.name,
                    confidence = null,
                )
            }
        val (depth, planeWalls) = if (camera.trackingState == TrackingState.TRACKING) {
            depthSampler.sample(session, frame, viewportWidth, viewportHeight, cameraPose, depthSupported, now)
        } else {
            emptyList<com.example.compsci399testproject.anchor.spatial.DepthObservation>() to emptyList()
        }
        return ArFramePacket(cameraPose, anchors, depth, planeWalls)
    }
}
