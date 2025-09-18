package com.example.compsci399testproject.utils

/**
 * Stabilize floor predictions to avoid rapid flipping.
 * Now: returns the latest floor directly.
 * Later: use majority vote / hysteresis (need a short history).
 */
class FloorStabilizer(private val window: Int = 5) {
    private val recent = ArrayDeque<Int>()

    fun reset() { recent.clear() }

    fun stabilize(predicted: Int): Int {
        // Pass-through for now; keep history for future use.
        recent.addLast(predicted)
        if (recent.size > window) recent.removeFirst()
        return predicted
    }
}
