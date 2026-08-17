package com.example.compsci399testproject.anchor.spatial

/** Extension points only; MVP 1 deliberately ships no correction, ranging, or loop-closure implementation. */
interface SpatialConstraintSource {
    val sourceName: String
    fun constraints(snapshot: MappingSnapshot): List<SpatialConstraint>
}

data class SpatialConstraint(
    val fromTimestampEpochMillis: Long,
    val toTimestampEpochMillis: Long,
    val expectedDistanceMeters: Float?,
    val confidence: Float?,
    val source: String,
)

interface PoseGraphCorrector {
    fun correct(snapshot: MappingSnapshot, constraints: List<SpatialConstraint>): MappingSnapshot
}
