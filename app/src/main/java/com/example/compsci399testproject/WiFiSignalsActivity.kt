package com.example.compsci399testproject

import android.net.wifi.ScanResult
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.compsci399testproject.ui.theme.COMPSCI399TestProjectTheme
import com.example.compsci399testproject.viewmodel.WifiViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class WifiInfo(
    val ssid: String,
    val bssid: String,
    val level: Int
)

@Composable
fun WifiSignalList(wifiViewModel: WifiViewModel)
{
    val context = LocalContext.current
    val showToast: (String) -> Unit = { msg ->
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    var updatedResults by remember { mutableStateOf<Map<String, WifiInfo>>(emptyMap()) }

    val lastScanTime by wifiViewModel.lastScanTime


    var timeSinceLastScan by remember { mutableStateOf("Never") }
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
    LaunchedEffect(Unit) {
        while (true) {
            wifiViewModel.scan()

            val timeout = withTimeoutOrNull(10000) {
                wifiViewModel.scanResults.firstOrNull { results ->
                    if (results.isNotEmpty()) {
                        val resultMap = results.associate { it.BSSID to WifiInfo(it.SSID, it.BSSID, it.level) }
                        val sortedResults = resultMap.values.sortedByDescending { it.level }

                        if (wifiViewModel.hasScanChanged(results)) {
                            updatedResults = sortedResults.associateBy { it.bssid }
                        }
                        true
                    } else false
                }
            }

            if (timeout == null) {
                showToast("WiFi scan timed out.")
            }

            delay(30000) // wait before next scan
        }
    }




    Column(modifier = Modifier.fillMaxWidth().background(color = colorResource(id = R.color.lighter_grey)),
        horizontalAlignment = Alignment.CenterHorizontally
    )
    {
        Text(text = "Wi-Fi Scans",
            color = colorResource(id = R.color.dark_grey),
            fontWeight = FontWeight(600),
            style = TextStyle(
                fontSize = 38.sp
            ),
            modifier = Modifier.padding(0.dp, 60.dp, 0.dp, 6.dp),
            textAlign = TextAlign.Right
        )
        Text(text = timeSinceLastScan,
            color = colorResource(id = R.color.dark_grey),
            fontWeight = FontWeight(400),
            style = TextStyle(
                fontSize = 20.sp
            ),
            modifier = Modifier.padding(0.dp, 6.dp, 0.dp, 36.dp),
            textAlign = TextAlign.Right
        )


        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            items(updatedResults.values.toList()) { info ->
                WifiSignalBox(
                    SSID = info.ssid,
                    MAC = "MAC: ${info.bssid}",
                    signalStrength = "${info.level}dBm"
                )
            }
        }
    }
}

@Composable
fun WifiSignalBox(SSID: String, MAC: String, signalStrength: String) {
    Column(modifier = Modifier.fillMaxWidth()
        .padding(24.dp, 0.dp, 24.dp, 10.dp)
        .background(color = colorResource(id = R.color.light_grey), shape = RoundedCornerShape(14.dp))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column() {
                WifiSignalBoxText(SSID, TextAlign.Left, Modifier.padding(16.dp, 10.dp, 0.dp, 6.dp))
                WifiSignalBoxText(MAC, TextAlign.Left, Modifier.padding(16.dp, 2.dp, 0.dp, 10.dp))
            }
            WifiSignalBoxText(signalStrength, TextAlign.Right, Modifier.padding(0.dp, 6.dp, 16.dp, 6.dp).fillMaxWidth().fillMaxHeight())
        }
    }
}

@Composable
fun WifiSignalBoxText(s: String, ta: TextAlign, modifier: Modifier) {
    Text(text = s,
        color = colorResource(id = R.color.dark_grey),
        fontWeight = FontWeight(600),
        fontFamily = FontFamily.SansSerif,
        style = TextStyle(
            fontSize = 16.sp
        ),
        modifier = modifier,
        textAlign = ta
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewFunc() {
    WifiSignalListPreview()
}

@Composable
fun WifiSignalListPreview() {
    val mockResults = listOf(
        WifiInfo(ssid = "UoA-WIFI", bssid = "00:11:22:33:44:55", level = -45),
        WifiInfo(ssid = "Guest-WiFi", bssid = "AA:BB:CC:DD:EE:FF", level = -65)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        items(mockResults) { info ->
            WifiSignalBox(
                SSID = info.ssid,
                MAC = "MAC: ${info.bssid}",
                signalStrength = "${info.level}dBm"
            )
        }
    }
}
