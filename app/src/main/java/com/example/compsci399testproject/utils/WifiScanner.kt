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

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            modernCallback = object : WifiManager.ScanResultsCallback() {
                override fun onScanResultsAvailable() {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val results =  wifiManager.scanResults

                        _scanResults.value = results
                        wifiViewModel.updateScanResults()

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
                            return
                        }

                        val results = wifiManager.scanResults

                        _scanResults.value = results
                        wifiViewModel.updateScanResults()
                    } else {
                        Log.d("wifiScan", "WiFi scan failed or returned no results.")
                        Toast.makeText(context, "WiFi scan failed or empty", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    legacyReceiver,
                    intentFilter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(
                    legacyReceiver,
                    intentFilter
                )
            }
        }
    }

    fun scanWifi() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
            Log.d("wifiScan", "Scanning achieved.")
        } else {
            Log.d("wifiScan", "Permission not granted to start scan.")
            return
        }
    }

    fun cleanup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            modernCallback?.let {
                wifiManager.unregisterScanResultsCallback(it)
            }
        } else {
            legacyReceiver?.let {
                context.unregisterReceiver(it)
            }
        }
    }
}