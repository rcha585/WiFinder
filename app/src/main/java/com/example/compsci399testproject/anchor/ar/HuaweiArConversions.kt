package com.example.compsci399testproject.anchor.ar

import com.example.compsci399testproject.anchor.spatial.SpatialCoordinate
import com.example.compsci399testproject.anchor.spatial.SpatialQuaternion
import com.example.compsci399testproject.anchor.spatial.SpatialTrackingState
import com.huawei.hiar.ARPose
import com.huawei.hiar.ARTrackable

internal fun ARPose.toSpatialCoordinate(): SpatialCoordinate = SpatialCoordinate(tx(), ty(), tz())

internal fun ARPose.toSpatialQuaternion(): SpatialQuaternion =
    SpatialQuaternion(qx(), qy(), qz(), qw())

internal fun ARTrackable.TrackingState.toSpatialTrackingState(): SpatialTrackingState = when (this) {
    ARTrackable.TrackingState.TRACKING -> SpatialTrackingState.TRACKING
    ARTrackable.TrackingState.PAUSED -> SpatialTrackingState.PAUSED
    ARTrackable.TrackingState.STOPPED -> SpatialTrackingState.STOPPED
    else -> SpatialTrackingState.PAUSED
}
