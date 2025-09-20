package com.example.compsci399testproject

import android.Manifest
import android.content.Context
import android.net.wifi.ScanResult
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compsci399testproject.machinelearning.LocationPredictor
import com.example.compsci399testproject.utils.BssidVectorizer
import com.example.compsci399testproject.utils.FloorStabilizer
import com.example.compsci399testproject.utils.Net
import com.example.compsci399testproject.utils.PositionSmoother
import com.example.compsci399testproject.viewmodel.WifiViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.flow.collect
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

@Composable
fun ScanTool(wifiViewModel: WifiViewModel) {
    // ====== Inputs ======
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var floorNumber by remember { mutableStateOf("") }
    var phoneId by remember { mutableStateOf("") }

    var googleSheetLink by remember {
        mutableStateOf(
            "https://script.google.com/macros/s/AKfycbx0OsDLTOoTGKY6BFvrgEdLOZud-8j4XtWUa5a6HW7fBYe3uNujxR-CNQ7XegUiMXsi1w/exec"
        )
    }

    val appContext = LocalContext.current.applicationContext
    val context = LocalContext.current

    // ====== Permissions ======
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(
                appContext,
                "Permissions denied: ${denied.joinToString()}. Wi-Fi scan may fail.",
                Toast.LENGTH_LONG
            ).show()
            Log.w("ScanTool", "Denied permissions: $denied")
        } else {
            Log.d("ScanTool", "All requested permissions granted.")
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            launcher.launch(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES))
        } else {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    // ====== Time since last scan ======
    val lastScanTime by wifiViewModel.lastScanTime
    var timeSinceLastScan by remember { mutableStateOf("Last scanned: never") }
    LaunchedEffect(lastScanTime) {
        if (lastScanTime == null) {
            timeSinceLastScan = "Last scanned: never"
        }
        while (isActive) {
            val t = lastScanTime
            timeSinceLastScan = if (t != null) {
                val seconds = (System.currentTimeMillis() - t) / 1000.0
                "Last scanned %.1f seconds ago".format(seconds)
            } else {
                "Last scanned: never"
            }
            delay(1000) // 1s tick，避免高频重组
        }
    }

    // ——可选：在界面上显示预测结果——
    var predFloor by remember { mutableStateOf<Int?>(null) }
    var predX by remember { mutableStateOf<Float?>(null) }
    var predY by remember { mutableStateOf<Float?>(null) }

    // 稳定化三件套
    val smoother = remember { PositionSmoother(alpha = 0.3f) }
    val floorStabilizer = remember { FloorStabilizer(window = 5) }

    // 你已有的扫描结果（Compose 层只读）
    val wifiSignals = wifiViewModel.getResults()

    // ====== Logging（后台）======
    LaunchedEffect(wifiSignals) {
        if (wifiSignals.isEmpty()) return@LaunchedEffect
        withContext(Default) {
            val feature = BssidVectorizer.toFeatureVector(context, wifiSignals)
            val vocabN = BssidVectorizer.vocabSize(context)
            val sizeN = feature.size
            val hits = feature.count { it > -99.5f }
            val hitRate = if (sizeN > 0) 100.0 * hits / sizeN else 0.0
            val head8 = feature.take(8).joinToString(prefix = "[", postfix = "]") { "%.1f".format(it) }
            Log.d(
                "BssidVec",
                "scan=${wifiSignals.size}, vocab=$vocabN, featSize=$sizeN, hits=$hits " +
                        "(%.1f%%), head8=$head8".format(hitRate)
            )
        }
    }

    // ====== 预测节流 ======
    var lastPredictAt by remember { mutableStateOf(0L) }
    val minPredictIntervalMs = 500L

    // 当扫描结果变化且非空时触发一次预测（后台计算 + 主线程更新）
    LaunchedEffect(wifiSignals) {
        if (wifiSignals.isEmpty()) return@LaunchedEffect

        val now = SystemClock.elapsedRealtime()
        if (now - lastPredictAt < minPredictIntervalMs) return@LaunchedEffect
        lastPredictAt = now

        try {
            val startNs = System.nanoTime()

            // 重活放后台
            val (fRaw, xRaw, yRaw) = withContext(Default) {
                val feature = BssidVectorizer.toFeatureVector(context, wifiSignals)
                Triple(
                    LocationPredictor.predictFloor(feature),
                    LocationPredictor.predictX(feature),
                    LocationPredictor.predictY(feature)
                )
            }

            // 稳定化（主线程即可）
            val fStable = floorStabilizer.stabilize(fRaw)
            val (xSmooth, ySmooth) = smoother.smooth(xRaw, yRaw)

            predFloor = fStable
            predX = xSmooth
            predY = ySmooth

            val costMs = (System.nanoTime() - startNs) / 1_000_000
            Log.d(
                "Predict",
                "Raw F=$fRaw X=$xRaw Y=$yRaw | Stable F=$fStable X=$xSmooth Y=$ySmooth | ${costMs}ms"
            )
        } catch (t: Throwable) {
            Log.w("Predict", "prediction failed: ${t.message}")
        }
    }

    // ====== UI ======
    val introMessage = "Where are you?"

    val showToast: (String) -> Unit = { msg ->
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.lighter_grey))
            .padding(top = 10.dp)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = introMessage,
            color = colorResource(id = R.color.dark_blue),
            fontWeight = FontWeight(600),
            fontFamily = FontFamily.SansSerif,
            style = TextStyle(fontSize = 24.sp),
            modifier = Modifier.padding(top = 10.dp)
        )

        Spacer(Modifier.height(16.dp))

        // Best signal（轻量）
        val strongestSignal = wifiSignals.maxByOrNull { it.level }
        val bestSignal = strongestSignal?.let {
            """Best:
                SSID: ${it.SSID}
                Signal Strength: ${it.level} dbm
            """.trimIndent()
        } ?: "No WiFi signals found."

        Text(
            text = bestSignal,
            style = TextStyle(fontSize = 16.sp),
            color = colorResource(id = R.color.dark_blue),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Text(
            text = timeSinceLastScan,
            style = TextStyle(fontSize = 16.sp),
            color = colorResource(id = R.color.dark_blue),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
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

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Floor: ${predFloor ?: "—"}", fontSize = 14.sp)
                        Text("X: ${predX?.let { "%.1f".format(it) } ?: "—"}", fontSize = 14.sp)
                        Text("Y: ${predY?.let { "%.1f".format(it) } ?: "—"}", fontSize = 14.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(wifiViewModel.statsText.value, fontSize = 12.sp)
                    }
                }
            }
        }

        DebugPanel(wifiViewModel)

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
            enabled = wifiSignals.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = colorResource(id = R.color.dark_blue),
                contentColor = colorResource(id = R.color.darker_white)
            ),
            modifier = Modifier
                .height(50.dp)
                .width(250.dp)
        ) {
            Text(text = "Capture", style = TextStyle(fontSize = 24.sp))
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

    // 触发一次扫描
    wifiViewModel.scan()

    // 观察结果（最多 20s），拿到第一批就上传
    CoroutineScope(Main).launch {
        val ok = withTimeoutOrNull(20_000) {
            wifiViewModel.scanResults.collect { results ->
                if (results.isNotEmpty()) {
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
                    this.cancel() // 成功后停止收集
                }
            }
        }
        if (ok == null) onError("WiFi scan timed out.")
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
    results.forEach { ap ->
        val signalName = "${ap.BSSID}(${ap.SSID})"
        signals.put(signalName, ap.level)
    }

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

    Net.http.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Handler(Looper.getMainLooper()).post {
                onError("Upload failed: ${e.message}")
            }
        }

        override fun onResponse(call: Call, response: Response) {
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

@Composable
fun DebugPanel(vm: WifiViewModel) {
    var lastClick by remember { mutableStateOf(0L) }
    val minIntervalMs = 5000L

    Column(Modifier.padding(12.dp)) {
        Button(onClick = {
            val now = System.currentTimeMillis()
            if (now - lastClick < minIntervalMs) {
                // 前端防抖，避免手动连点导致间隔 <5s
                return@Button
            }
            lastClick = now
            vm.scan()  // -> WifiScanner.scanWifi()，内部也有网关与超时兜底
        }) { Text("Scan Now") }

        Spacer(Modifier.height(8.dp))

        Button(onClick = { vm.enableSmoothing = !vm.enableSmoothing }) {
            Text(if (vm.enableSmoothing) "Smoothing: ON" else "Smoothing: OFF")
        }
        Spacer(Modifier.height(8.dp))

        Button(onClick = { vm.enableFloorHysteresis = !vm.enableFloorHysteresis }) {
            Text(if (vm.enableFloorHysteresis) "Hysteresis: ON" else "Hysteresis: OFF")
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.startCsvLogging("baseline") }) { Text("Start CSV (Baseline)") }
            Button(onClick = { vm.stopCsvLogging() }) { Text("Stop CSV") }
        }
        Spacer(Modifier.height(8.dp))

        Text("Stats: ${vm.statsText.value}")
        Text("Raw XY: ${vm.rawXY.value}")
        Text("Stable XY: ${vm.stableXY.value}")
        Text("Floor Raw/Stable: ${vm.rawFloor.value}/${vm.stableFloor.value}")
    }
}
