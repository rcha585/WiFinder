package com.example.compsci399testproject.viewmodel

import android.app.Application
import android.net.wifi.ScanResult
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.compsci399testproject.machinelearning.LocationPredictor
import com.example.compsci399testproject.utils.BssidVectorizer
import com.example.compsci399testproject.utils.PositionSmoother
import com.example.compsci399testproject.utils.ReliabilityStats
import com.example.compsci399testproject.utils.WifiScanner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

class WifiViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "WifiVM"

    // ---- context & scanner ----
    private val appContext = getApplication<Application>().applicationContext

    // 统计
    private val stats = ReliabilityStats()

    private val scanner = WifiScanner(appContext, this).also {
        it.attachReliabilityStats(stats)
    }

    // ---- runtime toggles ----
    var enableSmoothing by mutableStateOf(true)
    var enableFloorHysteresis by mutableStateOf(true)

    // ---- stabilizers ----
    private val smoother = PositionSmoother(alpha = 0.30f)

    // 内建简易楼层迟滞器
    private val floorWindow = ArrayDeque<Int>()
    private val floorWindowSize = 5
    private var lastStableFloor: Int? = null
    private val floorHysteresis = 1

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
        try {
            val base = getApplication<Application>()
                .getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IllegalStateException("External files dir unavailable")
            val file = File(base, "wifi_eval_${runName}.csv")
            csvWriter = file.bufferedWriter()
            csvWriter!!.apply {
                write("time,rawX,rawY,stX,stY,rawFloor,stFloor,attempts,successes,failures,avgLatencyMs,successRate,lastMaxRssi")
                newLine()
                flush()
            }
            enableCsvLogging = true
            Log.i(TAG, "CSV logging to: ${file.absolutePath}")
        } catch (t: Throwable) {
            enableCsvLogging = false
            csvWriter = null
            Log.e(TAG, "startCsvLogging failed", t)
        }
    }

    fun stopCsvLogging() {
        try { csvWriter?.run { flush(); close() } } catch (_: Throwable) {}
        csvWriter = null
        enableCsvLogging = false
    }

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

        try {
            csvWriter?.apply {
                write(listOf(
                    timeFmt.format(System.currentTimeMillis()),
                    rx, ry, sx, sy, rf, sf,
                    attempts, successes, failures,
                    avgLatency, successRate, lastMaxRssi
                ).joinToString(","))
                newLine()
                flush()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "appendCsvRow failed", t)
            stopCsvLogging()
        }
    }

    // ---- scan state ----
    private val _lastScanTime = mutableStateOf<Long?>(null)
    val lastScanTime: State<Long?> = _lastScanTime
    var scanResults = scanner.scanResults
    private var lastUploadedResults: Map<String, Int>? = null

    // 当前特征向量
    private var strengthArray = mutableListOf<Float>()

    // ===== 仿射变换（使用 map，避免与 Kotlin 的 apply 冲突）=====
    data class Affine(
        val a: Float, val b: Float, val c: Float, val d: Float, val tx: Float, val ty: Float
    ) {
        fun map(x: Float, y: Float): Pair<Float, Float> =
            (a * x + b * y + tx) to (c * x + d * y + ty)
    }

    // 默认=单位变换
    private var mapTransform: Affine = Affine(1f, 0f, 0f, 1f, 0f, 0f)

    // 是否应用仿射
    private val _applyMapTransform = mutableStateOf(true)
    var applyMapTransform: Boolean
        get() = _applyMapTransform.value
        set(v) { _applyMapTransform.value = v }

    // 你的原点像素（进入页面时蓝点默认位置）
    private val initialPixelX = 885f
    private val initialPixelY = 972f

    // 地图显示用坐标（像素）——默认就是原点像素
    private val _displayXY = mutableStateOf(initialPixelX to initialPixelY)
    val displayXY: State<Pair<Float, Float>> = _displayXY

    init {
        // 加载白名单
        BssidVectorizer.ensureLoaded(appContext)

        // 初始化一个全 -100 的向量，长度 = 词表大小
        val dim = BssidVectorizer.vocabSize(appContext)
        strengthArray = MutableList(dim) { -100f }
        Log.i(TAG, "whitelist size = $dim")

        // 应用两点配准（你的四个点）
        applyTwoPointCalibration()
    }

    // ====== public API ======
    fun scan() = scanner.scanWifi()
    fun getResults(): List<ScanResult> = this.scanResults.value

    fun hasScanChanged(newResults: List<ScanResult>): Boolean {
        val currentMap = newResults.associate { it.BSSID to it.level }
        val changed = currentMap != lastUploadedResults
        if (changed) lastUploadedResults = currentMap
        return changed
    }

    override fun onCleared() {
        super.onCleared()
        scanner.cleanup()
    }

    // ====== Scanner 回调触发 ======
    fun updateScanResults() {
        _lastScanTime.value = System.currentTimeMillis()
        this.scanResults = scanner.scanResults

        // 1) 向量化
        convertResultsToStrengthArray()
        val feats = strengthArray.toFloatArray()
        val dim = feats.size
        val filled = feats.count { it > -99.5f }           // 命中 AP 个数
        val coverage = if (dim > 0) filled.toFloat() / dim else 0f

        // ===== 调整门槛：命中 >= 3 或 覆盖率 >= 0.03 才跑预测 =====
        val minHits = 3
        val minCoverage = 0.03f
        val tooWeak = scanResults.value.isEmpty() || dim == 0 || (filled < minHits && coverage < minCoverage)
        if (tooWeak) {
            // 不更新坐标，只刷新统计/CSV；保留上一次位置，不会“卡在 0,0”
            refreshStatsText()
            appendCsvRow()
            return
        }

        // 2) 模型预测（NaN/Inf 保护）
        val rx = LocationPredictor.predictX(feats)
        val ry = LocationPredictor.predictY(feats)
        val rf = LocationPredictor.predictFloor(feats)
        if (!rx.isFinite() || !ry.isFinite()) {
            refreshStatsText()
            appendCsvRow()
            return
        }

        _rawXY.value = rx to ry
        _rawFloor.value = rf
        stats.onFloorPrediction(rf)

        // 3) 平滑
        val (sx, sy) = if (enableSmoothing) smoother.smooth(rx, ry) else (rx to ry)
        _stableXY.value = sx to sy

        // 4) 仿射到像素坐标
        val (dx, dy) = if (applyMapTransform) mapTransform.map(sx, sy) else (sx to sy)
        if (dx.isFinite() && dy.isFinite()) {
            _displayXY.value = dx to dy
        }

        // 5) 楼层迟滞
        val stFloor = if (enableFloorHysteresis) applyFloorHysteresis(rf) else rf
        _stableFloor.value = stFloor

        // 6) 文本 & CSV
        refreshStatsText()
        appendCsvRow()
    }



    // ====== internal helpers ======
    private fun convertResultsToStrengthArray() {
        val vec: FloatArray = BssidVectorizer.toFeatureVector(appContext, scanResults.value)
        if (strengthArray.size != vec.size) {
            strengthArray = MutableList(vec.size) { -100f }
        }
        for (i in vec.indices) strengthArray[i] = vec[i]
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
    }

    // 多数投票 + 迟滞
    private fun applyFloorHysteresis(rawFloor: Int): Int {
        floorWindow.addLast(rawFloor)
        if (floorWindow.size > floorWindowSize) floorWindow.removeFirst()
        val voted = floorWindow.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: rawFloor

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
        try {
            val base = getApplication<Application>()
                .getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IllegalStateException("External files dir unavailable")
            val file = File(base, "strengthArray.csv")
            file.bufferedWriter().use { w -> w.write(strengthArray.joinToString(",")) }
            Log.i(TAG, "strengthArray written to: ${file.absolutePath}")
        } catch (t: Throwable) {
            Log.e(TAG, "writeStrengthArrayToFile failed", t)
        }
    }

    /** 两点配准：src=模型(st)坐标，dst=底图像素 */
    fun setTransformFromTwoPairs(
        src1: Pair<Float, Float>, src2: Pair<Float, Float>,
        dst1: Pair<Float, Float>, dst2: Pair<Float, Float>
    ) {
        val (x1, y1) = src1; val (x2, y2) = src2
        val (X1, Y1) = dst1; val (X2, Y2) = dst2
        val dx = x2 - x1; val dy = y2 - y1
        val DX = X2 - X1; val DY = Y2 - Y1
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()

        if (len < 1e-3f || (DX == 0f && DY == 0f)) {
            mapTransform = Affine(1f, 0f, 0f, 1f, 0f, 0f)
            applyMapTransform = false
            Log.w(TAG, "Invalid calibration pairs, transform disabled")
            return
        }

        val s = hypot(DX.toDouble(), DY.toDouble()).toFloat() / len
        val cos = (dx * DX + dy * DY) / (len * len * s)
        val sin = (dx * DY - dy * DX) / (len * len * s)
        val a = s * cos
        val b = s * -sin
        val c = s * sin
        val d = s * cos
        val tx = X1 - (a * x1 + b * y1)
        val ty = Y1 - (c * x1 + d * y1)
        mapTransform = Affine(a, b, c, d, tx, ty)
    }

    /** 你的四个标定点 */
    fun applyTwoPointCalibration() {
        val srcA = -269f to -319f
        val srcB =  -90f to -111f

        val dstA =  616f to 1291f
        val dstB =  795f to 1083f

        setTransformFromTwoPairs(srcA, srcB, dstA, dstB)
        applyMapTransform = true
    }
}
