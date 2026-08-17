package com.example.compsci399testproject.anchor.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.compsci399testproject.anchor.session.MarkerPattern
import com.google.ar.core.AugmentedImageDatabase as GoogleAugmentedImageDatabase
import com.google.ar.core.Session as GoogleSession
import com.huawei.hiar.ARAugmentedImageDatabase as HuaweiAugmentedImageDatabase
import com.huawei.hiar.ARSession as HuaweiSession

object MarkerBitmapFactory {
    private const val BITMAP_SIZE = 620

    fun create(sessionId: String, anchorId: String): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { isAntiAlias = false }
        canvas.drawColor(Color.WHITE)
        paint.color = Color.BLACK
        val cell = BITMAP_SIZE / MarkerPattern.GRID_SIZE
        val bits = MarkerPattern.bits(sessionId, anchorId)
        for (y in 0 until MarkerPattern.GRID_SIZE) {
            for (x in 0 until MarkerPattern.GRID_SIZE) {
                val border = x < 2 || y < 2 || x >= MarkerPattern.GRID_SIZE - 2 || y >= MarkerPattern.GRID_SIZE - 2
                if (border || bits[y * MarkerPattern.GRID_SIZE + x]) {
                    canvas.drawRect(
                        (x * cell).toFloat(),
                        (y * cell).toFloat(),
                        ((x + 1) * cell).toFloat(),
                        ((y + 1) * cell).toFloat(),
                        paint,
                    )
                }
            }
        }
        return bitmap
    }

    fun createGoogleDatabase(
        session: GoogleSession,
        sessionId: String,
        markerWidthsMetersByAnchor: Map<String, Float>,
    ): GoogleAugmentedImageDatabase {
        val database = GoogleAugmentedImageDatabase(session)
        for ((anchorId, markerWidthMeters) in markerWidthsMetersByAnchor.validMarkerWidths()) {
            val bitmap = create(sessionId, anchorId)
            database.addImage("Anchor $anchorId", bitmap, markerWidthMeters)
            bitmap.recycle()
        }
        return database
    }

    fun createHuaweiDatabase(
        session: HuaweiSession,
        sessionId: String,
        markerWidthsMetersByAnchor: Map<String, Float>,
    ): HuaweiAugmentedImageDatabase {
        val database = HuaweiAugmentedImageDatabase(session)
        for ((anchorId, markerWidthMeters) in markerWidthsMetersByAnchor.validMarkerWidths()) {
            val bitmap = create(sessionId, anchorId)
            database.addImage("Anchor $anchorId", bitmap, markerWidthMeters)
            bitmap.recycle()
        }
        return database
    }

    private fun Map<String, Float>.validMarkerWidths(): List<Pair<String, Float>> =
        MarkerPattern.anchorIds.mapNotNull { anchorId ->
            val width = this[anchorId] ?: return@mapNotNull null
            if (width > 0f) anchorId to width else null
        }
}
