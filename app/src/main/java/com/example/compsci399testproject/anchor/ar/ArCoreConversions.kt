package com.example.compsci399testproject.anchor.ar

import com.example.compsci399testproject.anchor.spatial.SpatialCoordinate
import com.example.compsci399testproject.anchor.spatial.SpatialQuaternion
import com.example.compsci399testproject.anchor.spatial.SpatialTrackingState
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState

internal fun Pose.toSpatialCoordinate(): SpatialCoordinate = SpatialCoordinate(tx(), ty(), tz())

internal fun Pose.toSpatialQuaternion(): SpatialQuaternion {
    val values = FloatArray(4)
    getRotationQuaternion(values, 0)
    return SpatialQuaternion(values[0], values[1], values[2], values[3])
}

internal fun TrackingState.toSpatialTrackingState(): SpatialTrackingState = when (this) {
    TrackingState.TRACKING -> SpatialTrackingState.TRACKING
    TrackingState.PAUSED -> SpatialTrackingState.PAUSED
    TrackingState.STOPPED -> SpatialTrackingState.STOPPED
}
