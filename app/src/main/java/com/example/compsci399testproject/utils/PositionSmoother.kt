package com.example.compsci399testproject.utils

/**
 * Smooth X/Y to reduce jitter. Currently a pass-through.
 * Later: apply EMA or median over a short window.
 */
class PositionSmoother(
    private val alpha: Float = 0.3f  // smoothing factor for EMA
) {
    private var lastX: Float? = null
    private var lastY: Float? = null

    fun reset() { lastX = null; lastY = null }

    fun smooth(x: Float, y: Float): Pair<Float, Float> {
        val sx = if (lastX == null) x else (alpha * x + (1 - alpha) * lastX!!)
        val sy = if (lastY == null) y else (alpha * y + (1 - alpha) * lastY!!)
        lastX = sx; lastY = sy
        return sx to sy
    }
}
