package com.example.compsci399testproject.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.compsci399testproject.viewmodel.WifiViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * WifiScanner（免节流/更健壮）
 * - 一次只允许一轮扫描（inFlight）
 * - 最小触发间隔
 * - 单轮超时兜底 + 指数退避
 * - API 33+ 使用 WifiManager.ScanResultsCallback；更低版本用动态广播
 * - 所有跨线程 UI/状态变更均切换到 Main 线程
 */
class WifiScanner(
    private val context: Context,
    private val wifiViewModel: WifiViewModel
) {
    private val tag = "WifiScanner"

    private val appHandler = Handler(Looper.getMainLooper())

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(WifiManager::class.java)

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults

    // >=33 用 callback；<33 用广播
    private var modernCallback: WifiManager.ScanResultsCallback? = null
    private var legacyReceiver: BroadcastReceiver? = null

    // 扫描状态
    private var inFlight = false
    private var lastScanStart = 0L

    private val minScanIntervalMs = 8_000L     // 触发间隔下限
    private val scanTimeoutMs = 8_000L         // 单轮超时

    // 指数退避
    private var currentRetry = 0
    private val maxRetries = 3
    private val baseRetryDelayMs = 2_000L

    // 可靠性统计（按需注入）
    private var reliabilityStats: ReliabilityStats? = null
    fun attachReliabilityStats(stats: ReliabilityStats) { reliabilityStats = stats }

    // 独立 runnable，避免互相干扰
    private val timeoutRunnable = Runnable {
        if (inFlight) onScanFailed("scan timeout ${scanTimeoutMs}ms (no callback)")
    }
    private val retryRunnable = Runnable { scanWifi() }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            modernCallback = object : WifiManager.ScanResultsCallback() {
                override fun onScanResultsAvailable() {
                    onResultsArrived(updated = true)
                }
            }
            try {
                wifiManager.registerScanResultsCallback(context.mainExecutor, modernCallback!!)
            } catch (e: Exception) {
                Log.w(tag, "registerScanResultsCallback failed", e)
            }
        } else {
            legacyReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val updated = intent?.getBooleanExtra(
                        WifiManager.EXTRA_RESULTS_UPDATED, false
                    ) ?: false
                    onResultsArrived(updated)
                }
            }
            try {
                val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(legacyReceiver, filter)
            } catch (e: Exception) {
                Log.w(tag, "registerReceiver failed", e)
            }
        }
    }

    /** 外部唯一入口：请求启动“一次扫描”。 */
    @SuppressLint("MissingPermission")
    fun scanWifi() {
        // 前置条件不满足直接返回
        if (!checkPreconditions()) return

        val now = System.currentTimeMillis()
        if (inFlight) {
            Log.d(tag, "scanWifi() ignored: in-flight")
            return
        }
        if (now - lastScanStart < minScanIntervalMs) {
            Log.d(tag, "scanWifi() ignored: min interval not met")
            return
        }

        // 起飞
        inFlight = true
        lastScanStart = now
        currentRetry = 0
        reliabilityStats?.onScanAttempt()

        val ok = try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
        } catch (se: SecurityException) {
            Log.w(tag, "startScan() SecurityException", se)
            false
        } catch (e: Exception) {
            Log.w(tag, "startScan() failed", e)
            false
        }
        Log.d(tag, "startScan() -> $ok")

        if (!ok) {
            val elapsed = System.currentTimeMillis() - lastScanStart
            // 一次缓存回退（多数机型会留有上一次的结果）
            val cached = try { wifiManager.scanResults ?: emptyList() } catch (_: Exception) { emptyList() }
            if (cached.isNotEmpty()) {
                val maxRssi = cached.maxOfOrNull { it.level }
                reliabilityStats?.onScanSuccess(maxRssi, elapsed)
                inFlight = false
                appHandler.post {
                    _scanResults.value = cached
                    try { wifiViewModel.updateScanResults() } catch (_: Exception) {}
                    Log.d(tag, "scan OK (cache fallback): ${cached.size} APs, maxRSSI=${maxRssi ?: "n/a"} elapsed=${elapsed}ms")
                }
            } else {
                onScanFailed("startScan=false (throttled) & cacheEmpty")
            }
            return
        }

        postTimeoutOnce()
    }

    // ===== 回调/广播统一落地 =====
    @SuppressLint("MissingPermission")
    private fun onResultsArrived(updated: Boolean) {
        if (!inFlight) {
            Log.d(tag, "late result ignored (not in-flight)")
            return
        }
        cancelTimeout()

        if (!hasLocationPermission()) {
            onScanFailed("missing ACCESS_FINE_LOCATION at results time")
            return
        }

        val now = System.currentTimeMillis()
        val elapsed = now - lastScanStart

        val results: List<ScanResult> = try {
            wifiManager.scanResults ?: emptyList()
        } catch (se: SecurityException) {
            onScanFailed("SecurityException when reading scanResults")
            return
        } catch (e: Exception) {
            onScanFailed("Unexpected when reading scanResults: ${e.javaClass.simpleName}")
            return
        }

        if (results.isEmpty()) {
            onScanFailed("empty scanResults (updated=$updated)")
            return
        }

        val maxRssi = results.maxOfOrNull { it.level }   // dBm，可能为负

        // 成功：更新统计（把 maxRssi 和 elapsed 写入），并落地到 VM
        reliabilityStats?.onScanSuccess(maxRssi, elapsed)
        inFlight = false

        appHandler.post {
            _scanResults.value = results
            try {
                wifiViewModel.updateScanResults()
            } catch (e: Exception) {
                Log.w(tag, "wifiViewModel.updateScanResults() threw", e)
            }
            Log.d(tag, "scan OK: ${results.size} APs, maxRSSI=${maxRssi ?: "n/a"} elapsed=${elapsed}ms")
        }
    }

    // ===== 失败路径（含指数退避）=====
    private fun onScanFailed(reason: String) {
        val elapsed = System.currentTimeMillis() - lastScanStart
        reliabilityStats?.onScanFailure(elapsed)
        Log.w(tag, "scan FAILED: $reason ; retry=$currentRetry/$maxRetries ; elapsed=${elapsed}ms")

        inFlight = false
        cancelTimeout()

        if (currentRetry < maxRetries) {
            currentRetry++
            val delay = baseRetryDelayMs * currentRetry
            appHandler.postDelayed(retryRunnable, delay)
            showToastMain("Wi-Fi scan failed，retrying… ($currentRetry/$maxRetries)")
        } else {
            currentRetry = 0
            showToastMain("Wi-Fi scan failed $maxRetries times in a row")
        }
    }

    // ===== 前置条件/权限/系统开关 =====
    private fun checkPreconditions(): Boolean {
        val wifiOn = isWifiEnabledSafe()
        val locationOn = isLocationEnabledCompat()
        val hasPerms = hasLocationPermission() && hasNearbyWifiPermissionIfNeeded()

        if (!wifiOn || !locationOn || !hasPerms) {
            Log.w(tag, "preconditions: wifiOn=$wifiOn, locationOn=$locationOn, perms=$hasPerms")
            showToastMain("Please open Wi-Fi, and authorised for position and location")
            return false
        }
        return true
    }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun hasNearbyWifiPermissionIfNeeded(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true

    private fun isWifiEnabledSafe(): Boolean = try {
        wifiManager.wifiState == WifiManager.WIFI_STATE_ENABLED
    } catch (_: SecurityException) {
        false
    }

    @Suppress("DEPRECATION")
    private fun isLocationEnabledCompat(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // 28+
            lm.isLocationEnabled
        } else {
            try {
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.LOCATION_MODE
                ) != Settings.Secure.LOCATION_MODE_OFF
            } catch (_: Settings.SettingNotFoundException) {
                false
            }
        }
    }

    // ===== 超时兜底 =====
    private fun postTimeoutOnce() {
        appHandler.removeCallbacks(timeoutRunnable)
        appHandler.postDelayed(timeoutRunnable, scanTimeoutMs)
    }

    private fun cancelTimeout() {
        appHandler.removeCallbacks(timeoutRunnable)
    }

    // ===== 工具 =====
    private fun showToastMain(text: String) {
        appHandler.post {
            try { Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show() }
            catch (_: Exception) {}
        }
    }

    // ===== 外部接口 =====
    fun getStatsSnapshot(): Map<String, Any> =
        reliabilityStats?.snapshotMap() ?: emptyMap()

    fun cleanup() {
        cancelTimeout()
        appHandler.removeCallbacks(retryRunnable)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            modernCallback?.let {
                try { wifiManager.unregisterScanResultsCallback(it) } catch (_: Exception) {}
            }
        } else {
            legacyReceiver?.let {
                try { context.unregisterReceiver(it) } catch (_: Exception) {}
            }
        }
        modernCallback = null
        legacyReceiver = null
    }
}
