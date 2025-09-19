package com.example.compsci399testproject.utils

/**
 * Stabilize floor predictions to avoid rapid flipping using majority vote.
 * Prevents floor jumping by requiring consensus over a sliding window.
 */
class FloorStabilizer(private val window: Int = 5) {
    private val recent = ArrayDeque<Int>()
    private var currentStableFloor: Int? = null

    fun reset() {
        recent.clear()
        currentStableFloor = null
    }

    fun stabilize(predicted: Int): Int {
        // Add new prediction to sliding window
        recent.addLast(predicted)
        if (recent.size > window) recent.removeFirst()

        // If we don't have enough data yet, use the prediction directly
        if (recent.size < 3) {
            currentStableFloor = predicted
            return predicted
        }

        // Count votes for each floor in the window
        val votes = recent.groupingBy { it }.eachCount()
        val maxVotes = votes.values.maxOrNull() ?: 0
        val majority = votes.filter { it.value == maxVotes }.keys.firstOrNull()

        // Require strong consensus (>50%) to change floors
        val consensusThreshold = (recent.size / 2.0) + 1

        return if (majority != null && maxVotes >= consensusThreshold) {
            currentStableFloor = majority
            majority
        } else {
            // No strong consensus, keep current stable floor
            currentStableFloor ?: predicted
        }
    }

    // For debugging/stats
    fun getRecentHistory(): List<Int> = recent.toList()
    fun getCurrentStableFloor(): Int? = currentStableFloor
}
