package com.example.compsci399testproject.anchor.floorplan

import com.example.compsci399testproject.anchor.spatial.FloorPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class FittedLine(
    val start: FloorPoint,
    val end: FloorPoint,
    val supportCount: Int,
    val rmsErrorMeters: Float,
)

internal fun distance(first: FloorPoint, second: FloorPoint): Float =
    hypot(second.xMeters - first.xMeters, second.yMeters - first.yMeters)

internal fun angleOf(start: FloorPoint, end: FloorPoint): Float =
    atan2(end.yMeters - start.yMeters, end.xMeters - start.xMeters)

internal fun angleDifference(first: Float, second: Float): Float {
    var value = abs(first - second) % Math.PI.toFloat()
    if (value > Math.PI.toFloat() / 2f) value = Math.PI.toFloat() - value
    return value
}

fun fitLine(points: List<FloorPoint>): FittedLine? {
    if (points.size < 2) return null
    val meanX = points.map { it.xMeters }.average().toFloat()
    val meanY = points.map { it.yMeters }.average().toFloat()
    var xx = 0f
    var xy = 0f
    var yy = 0f
    for (point in points) {
        val dx = point.xMeters - meanX
        val dy = point.yMeters - meanY
        xx += dx * dx
        xy += dx * dy
        yy += dy * dy
    }
    val angle = 0.5f * atan2(2f * xy, xx - yy)
    val directionX = cos(angle)
    val directionY = sin(angle)
    var minProjection = Float.POSITIVE_INFINITY
    var maxProjection = Float.NEGATIVE_INFINITY
    var squaredError = 0f
    for (point in points) {
        val dx = point.xMeters - meanX
        val dy = point.yMeters - meanY
        val projection = dx * directionX + dy * directionY
        minProjection = min(minProjection, projection)
        maxProjection = max(maxProjection, projection)
        val perpendicular = -dx * directionY + dy * directionX
        squaredError += perpendicular * perpendicular
    }
    if (maxProjection - minProjection < 0.08f) return null
    return FittedLine(
        start = FloorPoint(meanX + minProjection * directionX, meanY + minProjection * directionY),
        end = FloorPoint(meanX + maxProjection * directionX, meanY + maxProjection * directionY),
        supportCount = points.size,
        rmsErrorMeters = sqrt(squaredError / points.size),
    )
}

internal fun pointLineDistance(point: FloorPoint, start: FloorPoint, end: FloorPoint): Float {
    val dx = end.xMeters - start.xMeters
    val dy = end.yMeters - start.yMeters
    val length = hypot(dx, dy)
    if (length < 1e-5f) return distance(point, start)
    return abs(dy * point.xMeters - dx * point.yMeters + end.xMeters * start.yMeters - end.yMeters * start.xMeters) / length
}

internal fun lineIntersection(
    firstStart: FloorPoint,
    firstEnd: FloorPoint,
    secondStart: FloorPoint,
    secondEnd: FloorPoint,
): FloorPoint? {
    val x1 = firstStart.xMeters
    val y1 = firstStart.yMeters
    val x2 = firstEnd.xMeters
    val y2 = firstEnd.yMeters
    val x3 = secondStart.xMeters
    val y3 = secondStart.yMeters
    val x4 = secondEnd.xMeters
    val y4 = secondEnd.yMeters
    val denominator = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
    if (abs(denominator) < 1e-5f) return null
    val determinant1 = x1 * y2 - y1 * x2
    val determinant2 = x3 * y4 - y3 * x4
    return FloorPoint(
        xMeters = (determinant1 * (x3 - x4) - (x1 - x2) * determinant2) / denominator,
        yMeters = (determinant1 * (y3 - y4) - (y1 - y2) * determinant2) / denominator,
    )
}

internal fun liesNearSegment(point: FloorPoint, start: FloorPoint, end: FloorPoint, extension: Float): Boolean {
    val segmentLength = distance(start, end)
    return distance(start, point) <= segmentLength + extension &&
        distance(end, point) <= segmentLength + extension
}
