package com.example.compsci399testproject

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log // Used for testing/bugfixes.
import android.widget.Toast
import android.net.wifi.ScanResult

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

import com.example.compsci399testproject.viewmodel.WifiViewModel

import kotlinx.coroutines.*

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

import org.json.JSONObject
import java.io.IOException

import android.Manifest
import android.os.Build

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import com.example.compsci399testproject.utils.Net
import com.example.compsci399testproject.utils.PositionSmoother
import com.example.compsci399testproject.utils.FloorStabilizer
import com.example.compsci399testproject.utils.ReliabilityStats
import com.example.compsci399testproject.utils.BssidVectorizer
import com.example.compsci399testproject.machinelearning.LocationPredictor


@Composable
fun ScanTool(wifiViewModel: WifiViewModel) {
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var floorNumber by remember { mutableStateOf("") }
    var phoneId by remember { mutableStateOf("") }

    var googleSheetLink by remember { mutableStateOf("https://script.google.com/macros/s/AKfycbx0OsDLTOoTGKY6BFvrgEdLOZud-8j4XtWUa5a6HW7fBYe3uNujxR-CNQ7XegUiMXsi1w/exec") }

    val lastScanTime by wifiViewModel.lastScanTime

    var timeSinceLastScan by remember { mutableStateOf("Never") }
    var bestSignal by remember { mutableStateOf("") }
    var timeSeconds by remember { mutableStateOf(0) }

    // Launcher that requests the right runtime permissions and reports the result.
    // NOTE: On Android 13+ we request NEARBY_WIFI_DEVICES; on older versions we
    // request ACCESS_FINE_LOCATION because Wi-Fi scans are gated by location.
    val appContext = LocalContext.current.applicationContext

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // results is a map<permission, granted?>
        val denied = results.filterValues { granted -> !granted }.keys
        if (denied.isNotEmpty()) {
            // Some permission(s) were denied; Wi-Fi scans may return empty results.
            Toast.makeText(
                appContext,
                "Permissions denied: ${denied.joinToString()}. Wi-Fi scan may fail.",
                Toast.LENGTH_LONG
            ).show()
            Log.w("ScanTool", "Denied permissions: $denied")
        } else {
            Log.d("ScanTool", "All requested permissions granted.")
        }}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            launcher.launch(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES))
        } else {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    // 1) 进页面必打
    LaunchedEffect(Unit) {
        android.util.Log.d("BssidVec", "hello ScanTool")
    }
    // 2) 打一次白名单大小
    LaunchedEffect(Unit) {
        val n = com.example.compsci399testproject.utils.BssidVectorizer.vocabSize(appContext)
        android.util.Log.d("BssidVec", "screen start, whitelist size = $n")
    }

    LaunchedEffect(lastScanTime) {
        while (true) {
            val now = System.currentTimeMillis()
            timeSinceLastScan = if (lastScanTime != null) {
                val seconds = (now - lastScanTime!!) / 1000.0
                timeSeconds = seconds.toInt()
                "Last scanned %.1f seconds ago".format(seconds)
            } else {
                "Last scanned: never"
            }
            delay(100)
        }
    }

    val introMessage = "Where are you?"

    //Error handling.
    val context = LocalContext.current
    val showToast: (String) -> Unit = { msg ->
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    // ——可选：在界面上显示预测结果——
    var predFloor by remember { mutableStateOf<Int?>(null) }
    var predX     by remember { mutableStateOf<Float?>(null) }
    var predY     by remember { mutableStateOf<Float?>(null) }

    // 稳定化三件套
    val smoother = remember { PositionSmoother(alpha = 0.3f) }
    val floorStabilizer = remember { FloorStabilizer(window = 5) }
    val reliabilityStats = remember { ReliabilityStats() }

    // 你已有的扫描结果
    val wifiSignals = wifiViewModel.getResults()

    // ①【新增】只负责打日志——无论本次扫描是否为空都会执行
    LaunchedEffect(wifiSignals) {
        // toFeatureVector 支持空列表，这里安全
        val feature = BssidVectorizer.toFeatureVector(context, wifiSignals)
        val vocabN  = BssidVectorizer.vocabSize(context)
        val sizeN   = feature.size
        val hits    = feature.count { it > -99.5f }
        val misses  = sizeN - hits
        val hitRate = if (sizeN > 0) "%.1f".format(100.0 * hits / sizeN) else "0.0"

        val head8   = feature.take(8).joinToString(prefix = "[", postfix = "]") { "%.1f".format(it) }

        android.util.Log.d(
            "BssidVec",
            "scan results=${wifiSignals.size}, vocab=$vocabN, featSize=$sizeN, " +
                    "hits=$hits($hitRate%), misses=$misses, head8=$head8"
        )
    }

    // 当扫描结果变化且非空时触发一次预测
    LaunchedEffect(wifiSignals) {
        if (wifiSignals.isNotEmpty()) {
            try {
                val feature = BssidVectorizer.toFeatureVector(context, wifiSignals)
                val startTime = System.currentTimeMillis()

                // 原始ML预测
                val fRaw = LocationPredictor.predictFloor(feature)
                val xRaw = LocationPredictor.predictX(feature)
                val yRaw = LocationPredictor.predictY(feature)

                // 使用稳定化组件
                val fStable = floorStabilizer.stabilize(fRaw)
                val (xSmooth, ySmooth) = smoother.smooth(xRaw, yRaw)

                // 更新UI状态
                predFloor = fStable
                predX = xSmooth
                predY = ySmooth

                // 统计跟踪
                val responseTime = System.currentTimeMillis() - startTime
                reliabilityStats.recordResponseTime(responseTime)
                reliabilityStats.onFloorPrediction(fStable)

                // 调试日志 - 显示改进前后对比
                android.util.Log.d("Predict", "Raw: F=$fRaw X=$xRaw Y=$yRaw | Stable: F=$fStable X=$xSmooth Y=$ySmooth | Time:${responseTime}ms")

            } catch (t: Throwable) {
                android.util.Log.w("Predict", "prediction failed: ${t.message}")
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(colorResource(id = R.color.lighter_grey))
        .padding(0.dp, 10.dp, 0.dp, 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = introMessage,
            color = colorResource(id = R.color.dark_blue),
            fontWeight = FontWeight(600),
            fontFamily = FontFamily.SansSerif,
            style = TextStyle(
                fontSize = 24.sp
            ),
            modifier = Modifier.padding(0.dp, 10.dp, 0.dp, 0.dp)
        )

        val strongestSignal = wifiSignals.maxByOrNull { it.level }
        bestSignal = if (strongestSignal != null) {
            """Best:
                SSID: ${strongestSignal.SSID}
                Signal Strength: ${strongestSignal.level} dbm
            """.trimIndent()
        } else {
            "No WiFi signals found."
        }


        Text(
            text = bestSignal,
            style = TextStyle(fontSize = 16.sp),
            color = colorResource(id = R.color.dark_blue),
            modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 10.dp)
        )


        Text(
            text = timeSinceLastScan,
            style = TextStyle(fontSize = 16.sp),
            color = colorResource(id = R.color.dark_blue),
            modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 10.dp)
        )

        Spacer(modifier = Modifier.height(0.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal=32.dp)
        ) {
            OutlinedTextField(
                value = longitude,
                onValueChange = { longitude = it },
                label = { Text("Longitude (X)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = latitude,
                onValueChange = { latitude = it },
                label = { Text("Latitude (Y)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = floorNumber,
                onValueChange = { floorNumber = it },
                label = { Text("Floor Number") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = phoneId,
                onValueChange = { phoneId = it },
                label = { Text("Phone ID") },
                modifier = Modifier.fillMaxWidth()
            )
        }


        Spacer(modifier = Modifier.height(25.dp))

        // Reliability Improvement Display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.darker_white))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "WiFinder Reliability Improvements",
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = colorResource(id = R.color.dark_blue)
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Current Prediction Display
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Floor: ${predFloor ?: "—"}", fontSize = 14.sp)
                        Text("X: ${predX?.let { "%.1f".format(it) } ?: "—"}", fontSize = 14.sp)
                        Text("Y: ${predY?.let { "%.1f".format(it) } ?: "—"}", fontSize = 14.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(reliabilityStats.snapshot(), fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Capture Button
        Button(
            onClick = {
                captureData(
                    context,
                    latitude,
                    longitude,
                    floorNumber,
                    phoneId,
                    showToast,
                    wifiViewModel,
                    googleSheetLink
                )
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.dark_blue),
                contentColor = colorResource(id = R.color.darker_white)
            ),
            modifier = Modifier
                .height(50.dp)
                .width(250.dp)
        ) {
            Text(text = "Capture",
                style = TextStyle(fontSize = 24.sp)
            )
        }

        Spacer(modifier = Modifier.height(60.dp))
    }


}



//////////////////////////////////////////////////////////////////
//                      YOUR CHANGES BELOW                      //
//////////////////////////////////////////////////////////////////
//
// captureData() is run when the "capture" button is pressed.
// WiFi scan results are collected and pushed to the Google Sheet.
//
// By default, the scan results are observed for 10 seconds. This will
// only work if WiFi throttling is turned off, otherwise you will
// miss captures due to the scan cooldown time.
fun captureData(
    context: Context,
    latitudeInput: String,
    longitudeInput: String,
    floorNumberInput: String,
    phoneIdInput: String,
    onError: (String) -> Unit,
    wifiViewModel: WifiViewModel,
    webAppUrl: String
) {
    val latitude = latitudeInput.toFloatOrNull() ?: run {
        onError("Invalid Latitude.")
        return
    }

    val longitude = longitudeInput.toFloatOrNull() ?: run {
        onError("Invalid Longitude.")
        return
    }

    val floor = floorNumberInput.trim().ifEmpty {
        onError("Please enter a floor.")
        return
    }

    val phoneId = phoneIdInput.trim().ifEmpty {
        onError("Please enter a phone ID.")
        return
    }

    wifiViewModel.scan()

    // Observe scan results until we get some (max 10 seconds)
    CoroutineScope(Dispatchers.Main).launch {
        val timeout = withTimeoutOrNull(20000) {   // 20s 更稳
            wifiViewModel.scanResults.collect { results ->
                if (results.isNotEmpty()) {
                    // 立刻上传，别再卡在“必须和上一次不同”
                    sendResultsToWebApp(
                        context = context,
                        latitude = latitude,
                        longitude = longitude,
                        floor = floor,
                        phoneId = phoneId,
                        results = results,
                        webAppUrl = webAppUrl,
                        onError = onError
                    )
                    cancel() // 成功后停止收集
                }
            }
        }

        if (timeout == null) {
            onError("WiFi scan timed out.")
        }
    }
}
//////////////////////////////////////////////////////////////////
//                      YOUR CHANGES ABOVE                      //
//////////////////////////////////////////////////////////////////


fun sendResultsToWebApp(
    context: Context,
    latitude: Float,
    longitude: Float,
    floor: String,
    phoneId: String,
    results: List<ScanResult>,
    webAppUrl: String,
    onError: (String) -> Unit
) {
    val signals = JSONObject()
    results.forEach {
        if (it.SSID in listOf("eduroam", "UoA-Guest-WiFi", "UoA-WiFi")) {
            val signalName = it.BSSID + "(${it.SSID})"
            signals.put(signalName, it.level)
        }
    }


    // convert system millis to time
    val currentTime = System.currentTimeMillis()
    val time = java.text.SimpleDateFormat("HH:mm:ss").format(currentTime)

    val payload = JSONObject().apply {
        put("latitude", latitude)
        put("longitude", longitude)
        put("floor", floor)
        put("phoneId", phoneId)
        put("timestamp", time)
        put("signals", signals)
    }

    Log.d("captureData", "Sending payload: $payload")

    val body = payload.toString().toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url(webAppUrl)
        .post(body)
        .build()

    Log.d("request", "Sending request: $request")

    Net.http.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Handler(Looper.getMainLooper()).post {
                onError("Upload failed: ${e.message}")
            }
        }

        override fun onResponse(call: Call, response: Response) {
            // 一定要关闭 response，避免连接泄漏
            response.use { resp ->
                Handler(Looper.getMainLooper()).post {
                    if (resp.isSuccessful) {
                        Toast.makeText(context, "Data uploaded!", Toast.LENGTH_SHORT).show()
                    } else {
                        onError("Upload failed: ${resp.code}")
                    }
                }
            }
        }
    })
}