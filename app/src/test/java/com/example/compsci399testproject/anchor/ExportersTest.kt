package com.example.compsci399testproject.anchor

import com.example.compsci399testproject.anchor.export.MappingJsonExporter
import com.example.compsci399testproject.anchor.export.SvgFloorPlanExporter
import com.example.compsci399testproject.anchor.spatial.AnchorObservation
import com.example.compsci399testproject.anchor.spatial.FloorPlanResult
import com.example.compsci399testproject.anchor.spatial.FloorPoint
import com.example.compsci399testproject.anchor.spatial.MappingSessionMetadata
import com.example.compsci399testproject.anchor.spatial.MappingSnapshot
import com.example.compsci399testproject.anchor.spatial.SpatialCoordinate
import com.example.compsci399testproject.anchor.spatial.SpatialQuaternion
import com.example.compsci399testproject.anchor.spatial.SpatialTrackingState
import com.example.compsci399testproject.anchor.spatial.WallSegment
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportersTest {
    @Test fun jsonContainsRequiredMappingSections() {
        val root = JsonParser.parseString(MappingJsonExporter.toJson(snapshot(), floorPlan())).asJsonObject
        assertEquals(1, root["schemaVersion"].asInt)
        assertEquals("TEST01", root.getAsJsonObject("session")["sessionId"].asString)
        assertTrue(root.has("trajectory"))
        assertTrue(root.has("depthObservations"))
        assertTrue(root.has("wifiSamples"))
        assertTrue(root.has("generatedWalls"))
        assertTrue(root.has("generatedCorners"))
    }

    @Test fun svgUsesMetricScaleAndRealGeometry() {
        val svg = SvgFloorPlanExporter.toSvg(snapshot(), floorPlan(), pixelsPerMeter = 100f)
        assertTrue(svg.startsWith("<?xml"))
        assertTrue(svg.contains("1 metre"))
        assertTrue(svg.contains("Anchor A"))
        assertTrue(svg.contains("<line"))
        assertTrue(svg.contains("<polygon"))
    }

    private fun snapshot() = MappingSnapshot(
        metadata = MappingSessionMetadata("TEST01", 1000L, 2000L, 0.15f, listOf("A", "B", "C"), true, "test"),
        anchors = listOf(
            AnchorObservation("A", 1000L, SpatialCoordinate(0f, 1f, 0f), SpatialQuaternion(0f, 0f, 0f, 1f), SpatialTrackingState.TRACKING, "FULL_TRACKING"),
        ),
        trajectory = emptyList(),
        depthObservations = emptyList(),
        wifiSamples = emptyList(),
        planeWallCandidates = emptyList(),
    )

    private fun floorPlan(): FloorPlanResult {
        val outline = listOf(FloorPoint(0f, 0f), FloorPoint(2f, 0f), FloorPoint(2f, 2f), FloorPoint(0f, 2f), FloorPoint(0f, 0f))
        return FloorPlanResult(
            walls = outline.zipWithNext().map { (start, end) -> WallSegment(start, end, "test", 4) },
            corners = outline.dropLast(1),
            roomOutline = outline,
            reconstructionNotes = emptyList(),
        )
    }
}
