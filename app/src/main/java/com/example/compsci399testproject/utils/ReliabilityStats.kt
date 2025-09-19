package com.example.compsci399testproject.utils

/**
 * Track comprehensive reliability indicators for WiFi scanning and positioning.
 * Monitors scan success rate, floor stability, response times, and error patterns.
 */
class ReliabilityStats {
    // Upload tracking
    var totalUploads = 0; private set
    var uploadFailures = 0; private set
    var lastMaxRssi: Int? = null; private set

    // Scan tracking
    var scanAttempts = 0; private set
    var scanSuccesses = 0; private set
    var scanFailures = 0; private set

    // Floor stability tracking
    var floorChanges = 0; private set
    var lastPredictedFloor: Int? = null; private set

    // Response time tracking
    private val responseTimes = mutableListOf<Long>()
    private val maxResponseTimeHistory = 20

    fun onUploadSuccess(maxRssi: Int?) {
        totalUploads += 1
        if (maxRssi != null) lastMaxRssi = maxRssi
    }

    fun onUploadFailure() {
        uploadFailures += 1
    }

    fun onScanAttempt() {
        scanAttempts += 1
    }

    fun onScanSuccess() {
        scanSuccesses += 1
    }

    fun onScanFailure() {
        scanFailures += 1
    }

    fun onFloorPrediction(predictedFloor: Int) {
        if (lastPredictedFloor != null && lastPredictedFloor != predictedFloor) {
            floorChanges += 1
        }
        lastPredictedFloor = predictedFloor
    }

    fun recordResponseTime(timeMs: Long) {
        responseTimes.add(timeMs)
        if (responseTimes.size > maxResponseTimeHistory) {
            responseTimes.removeFirst()
        }
    }

    fun getScanSuccessRate(): Double {
        return if (scanAttempts > 0) scanSuccesses.toDouble() / scanAttempts else 0.0
    }

    fun getUploadSuccessRate(): Double {
        val totalAttempts = totalUploads + uploadFailures
        return if (totalAttempts > 0) totalUploads.toDouble() / totalAttempts else 0.0
    }

    fun getAverageResponseTime(): Double {
        return if (responseTimes.isNotEmpty()) responseTimes.average() else 0.0
    }

    fun snapshot(): String {
        val scanRate = (getScanSuccessRate() * 100).toInt()
        val uploadRate = (getUploadSuccessRate() * 100).toInt()
        val avgResponse = getAverageResponseTime().toInt()

        return "Scan:$scanRate% Upload:$uploadRate% Resp:${avgResponse}ms FloorChanges:$floorChanges RSSI:${lastMaxRssi ?: "n/a"}"
    }

    fun detailedReport(): String {
        return """
            |=== WiFinder Reliability Report ===
            |Scanning:
            |  Attempts: $scanAttempts
            |  Successes: $scanSuccesses ($scanSuccessRate%)
            |  Failures: $scanFailures
            |
            |Uploads:
            |  Successes: $totalUploads
            |  Failures: $uploadFailures (${getUploadSuccessRate()}%)
            |
            |Floor Stability:
            |  Changes: $floorChanges
            |  Last Floor: ${lastPredictedFloor ?: "n/a"}
            |
            |Performance:
            |  Avg Response: ${getAverageResponseTime().toInt()}ms
            |  Last Max RSSI: ${lastMaxRssi ?: "n/a"}dBm
        """.trimMargin()
    }
}