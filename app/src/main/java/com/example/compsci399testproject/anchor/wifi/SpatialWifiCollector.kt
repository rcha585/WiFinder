package com.example.compsci399testproject.anchor.wifi

import com.example.compsci399testproject.anchor.spatial.PoseSample
import com.example.compsci399testproject.anchor.spatial.SpatialWifiSample
import com.example.compsci399testproject.viewmodel.WifiViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Reuses the v1 WifiScanner via WifiViewModel but stores raw RF data in metric ARCore space. */
class SpatialWifiCollector(private val wifiViewModel: WifiViewModel) {
    private var scanJob: Job? = null
    private var resultsJob: Job? = null

    fun start(
        scope: CoroutineScope,
        poseProvider: () -> PoseSample?,
        onSamples: (List<SpatialWifiSample>) -> Unit,
    ) {
        stop()
        resultsJob = scope.launch {
            wifiViewModel.scanResults.drop(1).collect { results ->
                val pose = poseProvider() ?: return@collect
                val timestamp = System.currentTimeMillis()
                val samples = results.mapNotNull { result ->
                    val bssid = result.BSSID?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    SpatialWifiSample(
                        timestampEpochMillis = timestamp,
                        pose = pose.position,
                        bssid = bssid,
                        rssiDbm = result.level,
                        frequencyMhz = result.frequency,
                    )
                }
                if (samples.isNotEmpty()) onSamples(samples)
            }
        }
        scanJob = scope.launch {
            while (true) {
                wifiViewModel.scan()
                delay(9_000L)
            }
        }
    }

    fun stop() {
        scanJob?.cancel()
        resultsJob?.cancel()
        scanJob = null
        resultsJob = null
    }
}
