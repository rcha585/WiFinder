package com.example.compsci399testproject.anchor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.compsci399testproject.anchor.spatial.CoordinateTransforms
import com.example.compsci399testproject.anchor.spatial.FloorPlanResult
import com.example.compsci399testproject.anchor.spatial.FloorPoint
import com.example.compsci399testproject.anchor.spatial.MappingSnapshot
import kotlin.math.max
import kotlin.math.min

@Composable
fun FloorPlanPreview(snapshot: MappingSnapshot, floorPlan: FloorPlanResult, modifier: Modifier = Modifier) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val trajectory = remember(snapshot) { snapshot.trajectory.map { CoordinateTransforms.worldToFloor(it.position) } }
    val anchors = remember(snapshot) { snapshot.anchors.map { CoordinateTransforms.worldToFloor(it.position) } }
    val allPoints = remember(snapshot, floorPlan) {
        buildList {
            addAll(trajectory)
            addAll(anchors)
            floorPlan.walls.forEach { add(it.start); add(it.end) }
            addAll(floorPlan.corners)
        }
    }
    Box(
        modifier = modifier
            .background(Color(0xFFF8FAFC))
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    zoom = (zoom * zoomChange).coerceIn(0.5f, 8f)
                    pan += panChange
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val points = allPoints.ifEmpty { listOf(FloorPoint(0f, 0f), FloorPoint(1f, 1f)) }
            val minX = points.minOf { it.xMeters }
            val maxX = points.maxOf { it.xMeters }
            val minY = points.minOf { it.yMeters }
            val maxY = points.maxOf { it.yMeters }
            val rangeX = max(1f, maxX - minX)
            val rangeY = max(1f, maxY - minY)
            val fitScale = min((size.width - 48f) / rangeX, (size.height - 48f) / rangeY)
            val centreX = (minX + maxX) / 2f
            val centreY = (minY + maxY) / 2f
            fun project(point: FloorPoint) = Offset(
                x = size.width / 2f + (point.xMeters - centreX) * fitScale * zoom + pan.x,
                y = size.height / 2f - (point.yMeters - centreY) * fitScale * zoom + pan.y,
            )
            floorPlan.roomOutline?.let { outline ->
                if (outline.size >= 3) {
                    val path = Path().apply {
                        val first = project(outline.first())
                        moveTo(first.x, first.y)
                        outline.drop(1).forEach { point -> val p = project(point); lineTo(p.x, p.y) }
                        close()
                    }
                    drawPath(path, Color(0x553B82F6))
                }
            }
            if (trajectory.size >= 2) {
                val path = Path().apply {
                    val first = project(trajectory.first())
                    moveTo(first.x, first.y)
                    trajectory.drop(1).forEach { point -> val p = project(point); lineTo(p.x, p.y) }
                }
                drawPath(path, Color(0xFFF97316), style = Stroke(width = 3f))
            }
            floorPlan.walls.forEach { wall ->
                drawLine(Color(0xFF102A43), project(wall.start), project(wall.end), strokeWidth = 7f)
            }
            floorPlan.corners.forEach { drawCircle(Color(0xFF7C3AED), radius = 5f, center = project(it)) }
            anchors.forEach { drawCircle(Color(0xFF0FBB9B), radius = 9f, center = project(it)) }
        }
        Row(Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Text("Pinch to zoom · drag to pan", color = Color(0xFF334155))
        }
    }
}
