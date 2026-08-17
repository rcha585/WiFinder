package com.example.compsci399testproject.anchor.spatial

/** Keeps AR metric coordinates independent from pixels and legacy Building 302 calibration. */
object CoordinateTransforms {
    fun worldToFloor(point: SpatialCoordinate): FloorPoint =
        FloorPoint(xMeters = point.xMeters, yMeters = -point.zMeters)

    fun floorToWorld(point: FloorPoint, heightMeters: Float = 0f): SpatialCoordinate =
        SpatialCoordinate(xMeters = point.xMeters, yMeters = heightMeters, zMeters = -point.yMeters)

    fun distanceOnFloor(first: SpatialCoordinate, second: SpatialCoordinate): Float {
        val dx = second.xMeters - first.xMeters
        val dz = second.zMeters - first.zMeters
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }
}
