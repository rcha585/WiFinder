package com.example.compsci399testproject.utils

/**
 * Track simple reliability indicators for debugging & dashboards.
 * Now: count uploads / last RSSI max etc. Extend later.
 */
class ReliabilityStats {
    var totalUploads = 0; private set
    var lastMaxRssi: Int? = null; private set

    fun onUploadSuccess(maxRssi: Int?) {
        totalUploads += 1
        if (maxRssi != null) lastMaxRssi = maxRssi
    }

    fun snapshot(): String =
        "uploads=$totalUploads, lastMaxRssi=${lastMaxRssi ?: "n/a"}"
}