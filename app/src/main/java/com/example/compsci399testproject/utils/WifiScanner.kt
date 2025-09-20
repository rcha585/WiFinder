package com.example.compsci399testproject.utils

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.compsci399testproject.viewmodel.WifiViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WifiScanner(private val context: Context, wifiViewModel: WifiViewModel) {
    private val wifiManager: WifiManager = context.getSystemService(WifiManager::class.java)

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults

    private var legacyReceiver: BroadcastReceiver? = null
    private var modernCallback: WifiManager.ScanResultsCallback? = null

    // Retry mechanism
    private var currentRetryCount = 0
    private val maxRetries = 3
    private val baseRetryDelay = 2000L // 2 seconds
    private val handler = Handler(Looper.getMainLooper())

    // Reliability tracking - can be injected later
    private var reliabilityStats: ReliabilityStats? = null

    fun attachReliabilityStats(stats: ReliabilityStats) {
        reliabilityStats = stats
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            modernCallback = object : WifiManager.ScanResultsCallback() {
                override fun onScanResultsAvailable() {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val results = wifiManager.scanResults
                        _scanResults.value = results
                        wifiViewModel.updateScanResults()

                        currentRetryCount = 0
                        reliabilityStats?.onScanSuccess()
                    }
                }
            }
            wifiManager.registerScanResultsCallback(context.mainExecutor, modernCallback!!)
        } else {
            legacyReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                    Log.d("wifiScan", "BroadcastReceiver triggered. Success: $success")

                    if (success) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED &&
                            ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            Log.w("wifiScan", "Missing location permissions.")
                            reliabilityStats?.onScanFailure()
                            return
                        }

                        val results = wifiManager.scanResults
                        _scanResults.value = results
                        wifiViewModel.updateScanResults()

                        currentRetryCount = 0
                        reliabilityStats?.onScanSuccess()
                        Log.d("wifiScan", "WiFi scan successful, ${results.size} networks found")
                    } else {
                        handleScanFailure("WiFi scan failed or returned no results")
                    }
                }
            }
            val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(legacyReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(legacyReceiver, intentFilter)
            }
        }
    }

    fun scanWifi() {
        reliabilityStats?.onScanAttempt()

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION")
            val scanStarted = wifiManager.startScan()
            Log.d("wifiScan", "WiFi scan initiated. Success: $scanStarted")

            if (!scanStarted) {
                handleScanFailure("Failed to start WiFi scan - system may be throttling")
            }
        } else {
            Log.d("wifiScan", "Permission not granted to start scan.")
            reliabilityStats?.onScanFailure()
            return
        }
    }

    private fun handleScanFailure(reason: String) {
        reliabilityStats?.onScanFailure()
        Log.w("wifiScan", "Scan failed: $reason. Retry count: $currentRetryCount/$maxRetries")

        if (currentRetryCount < maxRetries) {
            currentRetryCount++
            val retryDelay = baseRetryDelay * currentRetryCount

            handler.postDelayed({
                Log.d("wifiScan", "Retrying scan (attempt $currentRetryCount)...")
                scanWifi()
            }, retryDelay)

            Toast.makeText(
                context,
                "WiFi scan failed, retrying... ($currentRetryCount/$maxRetries)",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            currentRetryCount = 0
            Toast.makeText(
                context,
                "WiFi scan failed after $maxRetries attempts",
                Toast.LENGTH_LONG
            ).show()
            Log.e("wifiScan", "WiFi scan failed after $maxRetries attempts: $reason")
        }
    }

    fun getStatsSnapshot(): Map<String, Any> =
        reliabilityStats?.snapshotMap() ?: emptyMap<String, Any>()

    fun cleanup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            modernCallback?.let { wifiManager.unregisterScanResultsCallback(it) }
        } else {
            legacyReceiver?.let { context.unregisterReceiver(it) }
        }
    }
}
