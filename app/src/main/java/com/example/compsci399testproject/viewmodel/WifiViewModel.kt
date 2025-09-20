package com.example.compsci399testproject.viewmodel

import android.app.Application
import android.net.wifi.ScanResult
import android.os.Environment
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.compsci399testproject.utils.PositionSmoother
import com.example.compsci399testproject.utils.ReliabilityStats
import com.example.compsci399testproject.utils.WifiScanner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

class WifiViewModel(application: Application) : AndroidViewModel(application) {

    // ---- context & scanner ----
    private val appContext = getApplication<Application>().applicationContext
    private val scanner = WifiScanner(appContext, this).also {
        val stats = ReliabilityStats()
        it.attachReliabilityStats(stats)
    }

    // ---- runtime toggles（Phase-4 对比时会用到；现在默认全开即可） ----
    var enableSmoothing by mutableStateOf(true)
    var enableFloorHysteresis by mutableStateOf(true)

    // ---- stabilizers ----
    private val smoother = PositionSmoother(alpha = 0.30f)

    // 内建简易楼层迟滞器：窗口多数投票 + 迟滞 1 层
    private val floorWindow = ArrayDeque<Int>()
    private val floorWindowSize = 5
    private var lastStableFloor: Int? = null
    private val floorHysteresis = 1  // 相差 <= 1 层时不跳

    // ---- stats for UI ----
    private val _statsText = mutableStateOf("n/a")
    val statsText: State<String> = _statsText

    private val _rawXY = mutableStateOf(0f to 0f)
    val rawXY: State<Pair<Float, Float>> = _rawXY

    private val _stableXY = mutableStateOf(0f to 0f)
    val stableXY: State<Pair<Float, Float>> = _stableXY

    private val _rawFloor = mutableStateOf(0)
    val rawFloor: State<Int> = _rawFloor

    private val _stableFloor = mutableStateOf(0)
    val stableFloor: State<Int> = _stableFloor

    // ---- CSV logging ----
    private var csvWriter: java.io.BufferedWriter? = null
    var enableCsvLogging by mutableStateOf(false)
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun startCsvLogging(runName: String = "run") {
        if (csvWriter != null) return
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloads, "wifi_eval_${runName}.csv")
        csvWriter = file.bufferedWriter()
        csvWriter!!.apply {
            write("time,rawX,rawY,stX,stY,rawFloor,stFloor,attempts,successes,failures,avgLatencyMs,successRate,lastMaxRssi")
            newLine()
            flush()
        }
        enableCsvLogging = true
    }

    fun stopCsvLogging() {
        csvWriter?.run {
            flush()
            close()
        }
        csvWriter = null
        enableCsvLogging = false
    }

    /** 每次 updateScanResults() 末尾调用，落一行 CSV */
    private fun appendCsvRow() {
        if (!enableCsvLogging) return
        val (rx, ry) = _rawXY.value
        val (sx, sy) = _stableXY.value
        val rf = _rawFloor.value
        val sf = _stableFloor.value

        val snap = scanner.getStatsSnapshot()
        val attempts    = (snap["scanAttempts"] as? Number)?.toInt() ?: 0
        val successes   = (snap["scanSuccesses"] as? Number)?.toInt() ?: 0
        val failures    = (snap["scanFailures"] as? Number)?.toInt() ?: 0
        val avgLatency  = (snap["avgResponseTimeMs"] as? Number)?.toDouble() ?: 0.0
        val successRate = (snap["scanSuccessRate"] as? Number)?.toDouble() ?: 0.0
        val lastMaxRssi = (snap["lastMaxRssi"] as? Number)?.toInt() ?: -999

        csvWriter?.apply {
            write(
                listOf(
                    timeFmt.format(System.currentTimeMillis()),
                    rx, ry, sx, sy, rf, sf,
                    attempts, successes, failures,
                    avgLatency, successRate, lastMaxRssi
                ).joinToString(",")
            )
            newLine()
            flush()
        }
    }

    // ---- scan state you already had ----
    private val _lastScanTime = mutableStateOf<Long?>(null)
    val lastScanTime: State<Long?> = _lastScanTime
    var scanResults = scanner.scanResults
    private var lastUploadedResults: Map<String, Int>? = null

    private var accessPoints = mutableListOf<String>()
    private var strengthArray = mutableListOf<Float>()

    init {
        // 载入 AP 列表
        val fileName = "macAddresses.csv"
        val inputStream = application.assets.open(fileName)
        val reader = inputStream.bufferedReader()
        val firstLine = reader.readLine()
        this.accessPoints = firstLine.split(",").toMutableList()
    }

    // ====== public API ======

    fun scan() {
        scanner.scanWifi()
    }

    fun getResults(): List<ScanResult> = this.scanResults.value

    fun hasScanChanged(newResults: List<ScanResult>): Boolean {
        val currentMap = newResults.associate { it.BSSID to it.level }
        val changed = currentMap != lastUploadedResults
        if (changed) lastUploadedResults = currentMap
        return changed
    }

    // ViewModel 生命周期
    override fun onCleared() {
        super.onCleared()
        scanner.cleanup()
    }

    // Scanner 回调触发
    fun updateScanResults() {
        _lastScanTime.value = System.currentTimeMillis()
        this.scanResults = scanner.scanResults

        // 1) 你的原始处理：向量化强度数组
        convertResultsToStrengthArray()

        // 1.5) 空结果保护
        val noValid = strengthArray.all { it == 100f }
        if (scanResults.value.isEmpty() || noValid) {
            refreshStatsText()
            appendCsvRow()
            return
        }

        // 2) 从 Wi-Fi 结果推导“原始坐标/楼层”（占位逻辑，之后替换成真实预测）
        val rawX = strengthArray.indexOfFirst { it != 100f }.coerceAtLeast(0).toFloat()
        val rawY = strengthArray.count { it != 100f }.toFloat()
        val rawFloorGuess = 0

        _rawXY.value = rawX to rawY
        _rawFloor.value = rawFloorGuess

        // 3) 稳定化（可开关）
        val (sx, sy) = if (enableSmoothing) smoother.smooth(rawX, rawY) else (rawX to rawY)
        _stableXY.value = sx to sy

        val stFloor = if (enableFloorHysteresis) applyFloorHysteresis(rawFloorGuess) else rawFloorGuess
        _stableFloor.value = stFloor

        // 4) 统计文本 + CSV
        refreshStatsText()
        appendCsvRow()
    }

    // ====== internal helpers ======

    private fun convertResultsToStrengthArray() {
        strengthArray.clear()
        repeat(accessPoints.size) { strengthArray.add(100f) }

        for (result in scanResults.value) {
            val signalName = result.BSSID + "(${result.SSID})"
            val level = result.level.toFloat()
            val index = accessPoints.indexOf(signalName)
            if (index != -1) strengthArray[index] = level
        }
        // 如需调试保存：writeStrengthArrayToFile()
    }

    private fun refreshStatsText() {
        val snap = scanner.getStatsSnapshot()
        _statsText.value =
            if (snap.isEmpty()) "n/a"
            else snap.entries.joinToString(" | ") { (k, v) ->
                val s = when (v) {
                    is Double -> String.format(Locale.US, "%.3f", v)
                    else -> v.toString()
                }
                "$k=$s"
            }
    } // <- ★ 这里必须闭合！

    // 多数投票 + 迟滞（相邻层差 <= 1 时不切换）
    private fun applyFloorHysteresis(rawFloor: Int): Int {
        // 窗口多数投票
        floorWindow.addLast(rawFloor)
        if (floorWindow.size > floorWindowSize) floorWindow.removeFirst()
        val voted = floorWindow
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key ?: rawFloor

        val current = lastStableFloor
        val stable = if (current == null) {
            voted
        } else {
            if (abs(voted - current) <= floorHysteresis) current else voted
        }
        lastStableFloor = stable
        return stable
    }


    fun getStrengthArray(): List<Float> = strengthArray

    fun writeStrengthArrayToFile() {
        val fileName = "strengthArray.csv"
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        file.bufferedWriter().use { w ->
            w.write(strengthArray.joinToString(","))
        }
    }
}
