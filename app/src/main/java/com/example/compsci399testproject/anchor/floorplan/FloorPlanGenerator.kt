package com.example.compsci399testproject.anchor.floorplan

import com.example.compsci399testproject.anchor.spatial.CoordinateTransforms
import com.example.compsci399testproject.anchor.spatial.FloorPlanResult
import com.example.compsci399testproject.anchor.spatial.FloorPoint
import com.example.compsci399testproject.anchor.spatial.MappingSnapshot
import com.example.compsci399testproject.anchor.spatial.WallSegment
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class FloorPlanGenerator(
    private val inlierThresholdMeters: Float = 0.12f,
    private val mergeAngleDegrees: Float = 10f,
    private val mergeDistanceMeters: Float = 0.2f,
) {
    fun generate(snapshot: MappingSnapshot): FloorPlanResult {
        val notes = mutableListOf<String>()
        val points = snapshot.depthObservations
            .asSequence()
            .filter { it.wallCandidate }
            .map { CoordinateTransforms.worldToFloor(it.worldCoordinate) }
            .filter { it.xMeters.isFinite() && it.yMeters.isFinite() }
            .distinctBy { (it.xMeters / 0.05f).roundToInt() to (it.yMeters / 0.05f).roundToInt() }
            .take(2_000)
            .toList()

        val pointWalls = fitDominantSegments(points)
        if (points.isEmpty()) notes += "No depth/plane wall points were available."
        if (pointWalls.isEmpty() && points.isNotEmpty()) {
            notes += "Depth points were collected, but no line had enough geometric support."
        }

        val merged = mergeWalls(snapshot.planeWallCandidates + pointWalls)
            .filter { distance(it.start, it.end) >= 0.25f }
        val corners = findCorners(merged)
        val outline = buildClosedOutline(merged)
        if (outline == null) notes += "Wall graph is not reliably closed; exporting detected segments only."
        return FloorPlanResult(
            walls = merged,
            corners = corners,
            roomOutline = outline,
            reconstructionNotes = notes,
        )
    }

    fun fitDominantSegments(input: List<FloorPoint>, minimumInliers: Int = 6): List<WallSegment> {
        val remaining = input.toMutableList()
        val result = mutableListOf<WallSegment>()
        while (remaining.size >= minimumInliers && result.size < 16) {
            var bestInliers: List<FloorPoint> = emptyList()
            val stride = max(1, remaining.size / 80)
            for (firstIndex in remaining.indices step stride) {
                for (secondIndex in firstIndex + stride until remaining.size step stride) {
                    val first = remaining[firstIndex]
                    val second = remaining[secondIndex]
                    if (distance(first, second) < 0.35f) continue
                    val inliers = remaining.filter { pointLineDistance(it, first, second) <= inlierThresholdMeters }
                    if (inliers.size > bestInliers.size) bestInliers = inliers
                }
            }
            if (bestInliers.size < minimumInliers) break
            val line = fitLine(bestInliers) ?: break
            result += WallSegment(
                start = line.start,
                end = line.end,
                source = "depth-ransac",
                supportCount = line.supportCount,
                rmsErrorMeters = line.rmsErrorMeters,
            )
            remaining.removeAll(bestInliers.toSet())
        }
        return result
    }

    fun mergeWalls(input: List<WallSegment>): List<WallSegment> {
        val walls = input.toMutableList()
        var changed = true
        val angleThreshold = Math.toRadians(mergeAngleDegrees.toDouble()).toFloat()
        while (changed) {
            changed = false
            outer@ for (firstIndex in walls.indices) {
                for (secondIndex in firstIndex + 1 until walls.size) {
                    val first = walls[firstIndex]
                    val second = walls[secondIndex]
                    if (angleDifference(angleOf(first.start, first.end), angleOf(second.start, second.end)) > angleThreshold) continue
                    val perpendicularDistance = minOf(
                        pointLineDistance(second.start, first.start, first.end),
                        pointLineDistance(second.end, first.start, first.end),
                        pointLineDistance(first.start, second.start, second.end),
                        pointLineDistance(first.end, second.start, second.end),
                    )
                    if (perpendicularDistance > mergeDistanceMeters) continue
                    val endpointGap = minOf(
                        distance(first.start, second.start), distance(first.start, second.end),
                        distance(first.end, second.start), distance(first.end, second.end),
                    )
                    val overlaps = projectionsOverlap(first, second, 0.45f)
                    if (!overlaps && endpointGap > 0.45f) continue
                    val merged = fitLine(listOf(first.start, first.end, second.start, second.end)) ?: continue
                    walls[firstIndex] = WallSegment(
                        start = merged.start,
                        end = merged.end,
                        source = listOf(first.source, second.source).distinct().joinToString("+"),
                        supportCount = first.supportCount + second.supportCount,
                        rmsErrorMeters = listOfNotNull(first.rmsErrorMeters, second.rmsErrorMeters).averageOrNull(),
                    )
                    walls.removeAt(secondIndex)
                    changed = true
                    break@outer
                }
            }
        }
        return walls
    }

    fun findCorners(walls: List<WallSegment>): List<FloorPoint> {
        val corners = mutableListOf<FloorPoint>()
        for (firstIndex in walls.indices) {
            for (secondIndex in firstIndex + 1 until walls.size) {
                val first = walls[firstIndex]
                val second = walls[secondIndex]
                val angle = angleDifference(angleOf(first.start, first.end), angleOf(second.start, second.end))
                if (angle < Math.toRadians(20.0).toFloat()) continue
                val intersection = lineIntersection(first.start, first.end, second.start, second.end) ?: continue
                if (!liesNearSegment(intersection, first.start, first.end, 0.35f)) continue
                if (!liesNearSegment(intersection, second.start, second.end, 0.35f)) continue
                if (corners.none { distance(it, intersection) < 0.18f }) corners += intersection
            }
        }
        return corners
    }

    fun buildClosedOutline(walls: List<WallSegment>, joinToleranceMeters: Float = 0.35f): List<FloorPoint>? {
        if (walls.size < 3) return null
        val nodes = mutableListOf<FloorPoint>()
        val edges = mutableListOf<Pair<Int, Int>>()
        fun nodeFor(point: FloorPoint): Int {
            val existing = nodes.indexOfFirst { distance(it, point) <= joinToleranceMeters }
            if (existing >= 0) {
                val old = nodes[existing]
                nodes[existing] = FloorPoint((old.xMeters + point.xMeters) / 2f, (old.yMeters + point.yMeters) / 2f)
                return existing
            }
            nodes += point
            return nodes.lastIndex
        }
        for (wall in walls) {
            val start = nodeFor(wall.start)
            val end = nodeFor(wall.end)
            if (start != end) edges += start to end
        }
        val adjacency = nodes.indices.associateWith { mutableListOf<Int>() }
        for ((start, end) in edges) {
            adjacency.getValue(start) += end
            adjacency.getValue(end) += start
        }
        val active = adjacency.filterValues { it.isNotEmpty() }
        if (active.size < 3 || active.values.any { it.size != 2 }) return null
        val start = active.keys.first()
        val order = mutableListOf<Int>()
        var previous = -1
        var current = start
        do {
            order += current
            val next = adjacency.getValue(current).firstOrNull { it != previous } ?: return null
            previous = current
            current = next
            if (order.size > active.size + 1) return null
        } while (current != start)
        if (order.size != active.size) return null
        return order.map { nodes[it] } + nodes[start]
    }

    private fun projectionsOverlap(first: WallSegment, second: WallSegment, padding: Float): Boolean {
        val angle = angleOf(first.start, first.end)
        val dx = cos(angle)
        val dy = sin(angle)
        fun projection(point: FloorPoint) = point.xMeters * dx + point.yMeters * dy
        val firstRange = listOf(projection(first.start), projection(first.end)).sorted()
        val secondRange = listOf(projection(second.start), projection(second.end)).sorted()
        return max(firstRange[0], secondRange[0]) <= min(firstRange[1], secondRange[1]) + padding
    }

    private fun List<Float>.averageOrNull(): Float? = if (isEmpty()) null else average().toFloat()
}
