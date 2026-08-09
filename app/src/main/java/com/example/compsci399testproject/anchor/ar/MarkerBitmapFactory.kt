package com.example.compsci399testproject.anchor.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.example.compsci399testproject.anchor.session.MarkerPattern
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Session

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

    fun createDatabase(session: Session, sessionId: String, markerWidthMeters: Float): AugmentedImageDatabase {
        val database = AugmentedImageDatabase(session)
        for (anchorId in MarkerPattern.anchorIds) {
            val bitmap = create(sessionId, anchorId)
            database.addImage("Anchor $anchorId", bitmap, markerWidthMeters)
            bitmap.recycle()
        }
        return database
    }
}
