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


@Composable
fun ScanTool(wifiViewModel: WifiViewModel) {
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var floorNumber by remember { mutableStateOf("") }
    var phoneId by remember { mutableStateOf("") }

    val lastScanTime by wifiViewModel.lastScanTime

    var timeSinceLastScan by remember { mutableStateOf("Never") }
    var bestSignal by remember { mutableStateOf("") }
    var timeSeconds by remember { mutableStateOf(0) }

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


        val wifiSignals = wifiViewModel.getResults()
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
                    "https://script.google.com/macros/s/AKfycbzsISU5WpqTe8rH3aYgHk3eEhRKhTZlJvRnFfvyFtMVk1dZMel-hCdfJRVCtco8_JSa/exec"
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
        val timeout = withTimeoutOrNull(10000) {
            wifiViewModel.scanResults.collect { results ->
                if (results.isNotEmpty()) {
                    if (wifiViewModel.hasScanChanged(results)) {
                        // On a successful scan, push the scan results to the Google Sheet
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
                        cancel() // Stop collecting
                    } else {
                        Toast.makeText(context, "No new results.", Toast.LENGTH_SHORT).show()
                    }
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

    OkHttpClient().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Handler(Looper.getMainLooper()).post {
                onError("Upload failed: ${e.message}")
            }
        }

        override fun onResponse(call: Call, response: Response) {
            Handler(Looper.getMainLooper()).post {
                if (response.isSuccessful) {
                    Toast.makeText(context, "Data uploaded!", Toast.LENGTH_SHORT).show()
                } else {
                    onError("Upload failed: ${response.code}")
                }
            }
        }
    })
}