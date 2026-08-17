package com.example.compsci399testproject.anchor.export

import android.content.Context
import android.os.Environment
import com.example.compsci399testproject.anchor.spatial.FloorPlanResult
import com.example.compsci399testproject.anchor.spatial.MappingSnapshot
import java.io.File

data class ExportedMapping(
    val directory: File,
    val mappingJson: File,
    val floorPlanSvg: File,
)

class ExportRepository(private val context: Context) {
    fun export(snapshot: MappingSnapshot, floorPlan: FloorPlanResult): ExportedMapping {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val safeSessionId = snapshot.metadata.sessionId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val directory = File(root, "WiFinderAnchor/$safeSessionId-${snapshot.metadata.startedAtEpochMillis}")
        check(directory.mkdirs() || directory.isDirectory) { "Could not create ${directory.absolutePath}" }
        val json = File(directory, "mapping.json")
        val svg = File(directory, "floorplan.svg")
        json.writeText(MappingJsonExporter.toJson(snapshot, floorPlan), Charsets.UTF_8)
        svg.writeText(SvgFloorPlanExporter.toSvg(snapshot, floorPlan), Charsets.UTF_8)
        return ExportedMapping(directory, json, svg)
    }
}
