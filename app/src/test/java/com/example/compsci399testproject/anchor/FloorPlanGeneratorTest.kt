package com.example.compsci399testproject.anchor

import com.example.compsci399testproject.anchor.floorplan.FloorPlanGenerator
import com.example.compsci399testproject.anchor.floorplan.fitLine
import com.example.compsci399testproject.anchor.spatial.FloorPoint
import com.example.compsci399testproject.anchor.spatial.WallSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FloorPlanGeneratorTest {
    private val generator = FloorPlanGenerator()

    @Test fun lineFittingRecoversWallFromNoisyPoints() {
        val points = (0..20).map { index ->
            FloorPoint(index * 0.2f, if (index % 2 == 0) 0.025f else -0.025f)
        }
        val fitted = fitLine(points)
        assertNotNull(fitted)
        assertEquals(21, fitted!!.supportCount)
        assertTrue(fitted.rmsErrorMeters < 0.04f)
        assertTrue(kotlin.math.abs(fitted.end.xMeters - fitted.start.xMeters) > 3.9f)
    }

    @Test fun mergeCombinesCollinearTouchingSegments() {
        val merged = generator.mergeWalls(
            listOf(
                wall(0f, 0f, 2f, 0f),
                wall(2.1f, 0.03f, 4f, 0.03f),
            ),
        )
        assertEquals(1, merged.size)
        assertTrue(kotlin.math.abs(merged.single().end.xMeters - merged.single().start.xMeters) > 3.9f)
    }

    @Test fun closedRectangleProducesOutlineAndCorners() {
        val walls = listOf(
            wall(0f, 0f, 4f, 0f),
            wall(4f, 0f, 4f, 3f),
            wall(4f, 3f, 0f, 3f),
            wall(0f, 3f, 0f, 0f),
        )
        assertEquals(4, generator.findCorners(walls).size)
        val outline = generator.buildClosedOutline(walls)
        assertNotNull(outline)
        assertEquals(5, outline!!.size)
        assertEquals(outline.first(), outline.last())
    }

    @Test fun openSegmentsDoNotFabricateRoomPolygon() {
        val walls = listOf(wall(0f, 0f, 4f, 0f), wall(4f, 0f, 4f, 3f))
        assertEquals(null, generator.buildClosedOutline(walls))
    }

    private fun wall(x1: Float, y1: Float, x2: Float, y2: Float) = WallSegment(
        start = FloorPoint(x1, y1),
        end = FloorPoint(x2, y2),
        source = "test",
        supportCount = 10,
    )
}
