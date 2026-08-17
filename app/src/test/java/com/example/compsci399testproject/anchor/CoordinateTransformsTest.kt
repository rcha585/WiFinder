package com.example.compsci399testproject.anchor

import com.example.compsci399testproject.anchor.spatial.CoordinateTransforms
import com.example.compsci399testproject.anchor.spatial.FloorPoint
import com.example.compsci399testproject.anchor.spatial.SpatialCoordinate
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateTransformsTest {
    @Test fun worldFloorRoundTripPreservesMetricAxes() {
        val world = SpatialCoordinate(2.5f, 1.2f, -4f)
        val floor = CoordinateTransforms.worldToFloor(world)
        assertEquals(FloorPoint(2.5f, 4f), floor)
        assertEquals(world, CoordinateTransforms.floorToWorld(floor, 1.2f))
    }

    @Test fun floorDistanceIgnoresHeight() {
        val distance = CoordinateTransforms.distanceOnFloor(
            SpatialCoordinate(0f, 1f, 0f),
            SpatialCoordinate(3f, 8f, 4f),
        )
        assertEquals(5f, distance, 0.0001f)
    }
}
