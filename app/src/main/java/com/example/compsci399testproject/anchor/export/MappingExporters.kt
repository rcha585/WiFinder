package com.example.compsci399testproject.anchor.export

import com.example.compsci399testproject.anchor.spatial.CoordinateTransforms
import com.example.compsci399testproject.anchor.spatial.FloorPlanResult
import com.example.compsci399testproject.anchor.spatial.FloorPoint
import com.example.compsci399testproject.anchor.spatial.MappingSnapshot
import com.google.gson.GsonBuilder
import kotlin.math.max
import kotlin.math.min

data class MappingExportDocument(
    val schemaVersion: Int = 1,
    val session: Any,
    val anchors: Any,
    val trajectory: Any,
    val depthObservations: Any,
    val wifiSamples: Any,
    val planeWallCandidates: Any,
    val generatedWalls: Any,
    val generatedCorners: Any,
    val roomOutline: Any?,
    val reconstructionNotes: Any,
)

object MappingJsonExporter {
    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().create()

    fun toJson(snapshot: MappingSnapshot, floorPlan: FloorPlanResult): String = gson.toJson(
        MappingExportDocument(
            session = snapshot.metadata,
            anchors = snapshot.anchors,
            trajectory = snapshot.trajectory,
            depthObservations = snapshot.depthObservations,
            wifiSamples = snapshot.wifiSamples,
            planeWallCandidates = snapshot.planeWallCandidates,
            generatedWalls = floorPlan.walls,
            generatedCorners = floorPlan.corners,
            roomOutline = floorPlan.roomOutline,
            reconstructionNotes = floorPlan.reconstructionNotes,
        ),
    )
}

object SvgFloorPlanExporter {
    fun toSvg(snapshot: MappingSnapshot, floorPlan: FloorPlanResult, pixelsPerMeter: Float = 100f): String {
        val trajectory = snapshot.trajectory.map { CoordinateTransforms.worldToFloor(it.position) }
        val anchors = snapshot.anchors.map { it.anchorId to CoordinateTransforms.worldToFloor(it.position) }
        val allPoints = buildList {
            addAll(trajectory)
            addAll(anchors.map { it.second })
            floorPlan.walls.forEach { add(it.start); add(it.end) }
            addAll(floorPlan.corners)
        }
        val bounds = bounds(allPoints.ifEmpty { listOf(FloorPoint(0f, 0f), FloorPoint(1f, 1f)) })
        val margin = 40f
        val width = max(200f, (bounds.maxX - bounds.minX) * pixelsPerMeter + margin * 2f)
        val height = max(200f, (bounds.maxY - bounds.minY) * pixelsPerMeter + margin * 2f)
        fun x(point: FloorPoint) = margin + (point.xMeters - bounds.minX) * pixelsPerMeter
        fun y(point: FloorPoint) = margin + (bounds.maxY - point.yMeters) * pixelsPerMeter
        fun point(point: FloorPoint) = "${format(x(point))},${format(y(point))}"

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${format(width)}\" height=\"${format(height)}\" viewBox=\"0 0 ${format(width)} ${format(height)}\">")
            appendLine("  <title>WiFinder Anchor ${xml(snapshot.metadata.sessionId)}</title>")
            appendLine("  <desc>Metric floor plan. Scale: ${format(pixelsPerMeter)} SVG units per metre.</desc>")
            appendLine("  <rect width=\"100%\" height=\"100%\" fill=\"#f8fafc\"/>")
            floorPlan.roomOutline?.let { outline ->
                appendLine("  <polygon points=\"${outline.joinToString(" ") { point(it) }}\" fill=\"#dbeafe\" stroke=\"none\" opacity=\"0.65\"/>")
            }
            if (trajectory.size >= 2) {
                appendLine("  <polyline points=\"${trajectory.joinToString(" ") { point(it) }}\" fill=\"none\" stroke=\"#f97316\" stroke-width=\"3\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>")
            }
            for (wall in floorPlan.walls) {
                appendLine("  <line x1=\"${format(x(wall.start))}\" y1=\"${format(y(wall.start))}\" x2=\"${format(x(wall.end))}\" y2=\"${format(y(wall.end))}\" stroke=\"#102a43\" stroke-width=\"7\" stroke-linecap=\"round\"/>")
            }
            for (corner in floorPlan.corners) {
                appendLine("  <circle cx=\"${format(x(corner))}\" cy=\"${format(y(corner))}\" r=\"5\" fill=\"#7c3aed\"/>")
            }
            for ((anchorId, anchor) in anchors) {
                appendLine("  <circle cx=\"${format(x(anchor))}\" cy=\"${format(y(anchor))}\" r=\"9\" fill=\"#0fbb9b\" stroke=\"#064e3b\" stroke-width=\"2\"/>")
                appendLine("  <text x=\"${format(x(anchor) + 12f)}\" y=\"${format(y(anchor) - 10f)}\" font-family=\"sans-serif\" font-size=\"16\" fill=\"#064e3b\">Anchor ${xml(anchorId)}</text>")
            }
            appendLine("  <g transform=\"translate(${format(margin)},${format(height - 20f)})\"><line x1=\"0\" y1=\"0\" x2=\"${format(pixelsPerMeter)}\" y2=\"0\" stroke=\"#0f172a\" stroke-width=\"3\"/><text x=\"0\" y=\"-7\" font-family=\"sans-serif\" font-size=\"13\">1 metre</text></g>")
            appendLine("</svg>")
        }
    }

    private data class Bounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

    private fun bounds(points: List<FloorPoint>): Bounds {
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (point in points) {
            minX = min(minX, point.xMeters)
            minY = min(minY, point.yMeters)
            maxX = max(maxX, point.xMeters)
            maxY = max(maxY, point.yMeters)
        }
        if (maxX - minX < 1f) { minX -= 0.5f; maxX += 0.5f }
        if (maxY - minY < 1f) { minY -= 0.5f; maxY += 0.5f }
        return Bounds(minX, minY, maxX, maxY)
    }

    private fun format(value: Float): String = "%.2f".format(java.util.Locale.US, value)

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
