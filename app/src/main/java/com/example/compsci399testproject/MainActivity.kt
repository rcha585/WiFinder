package com.example.compsci399testproject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.compsci399testproject.viewmodel.WifiScannerViewModelFactory
import com.example.compsci399testproject.viewmodel.WifiViewModel
import com.example.compsci399testproject.viewmodel.MapViewModel
import com.example.compsci399testproject.viewmodel.MapViewModelFactory
import com.example.compsci399testproject.sensors.RotationSensorService
import com.example.compsci399testproject.utils.CoordTransform

class MainActivity : ComponentActivity() {
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private val NEARBY_PERMISSION_REQUEST_CODE = 1002

    private lateinit var wifiViewModel: WifiViewModel
    private lateinit var mapViewModel: MapViewModel
    private lateinit var rotationService: RotationSensorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CoordTransform.load(applicationContext)

        requestBasePermissions()

        val factory = WifiScannerViewModelFactory(application)
        wifiViewModel = ViewModelProvider(this, factory)[WifiViewModel::class.java]

        val mapFactory = MapViewModelFactory(wifiViewModel)
        mapViewModel = ViewModelProvider(this, mapFactory)[MapViewModel::class.java]

        rotationService = RotationSensorService(applicationContext)

        setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "HomeMenu") {
                composable("HomeMenu") { Menu(navController) }
                composable("WifiSignals") { WifiSignalList(wifiViewModel) }
                composable("ScanTool") { ScanTool(wifiViewModel) }
                composable("MainApp") { MapView(mapViewModel) }

                composable("LocationTool") { FindingLocationScreen() }
            }
        }
    }

    private fun requestBasePermissions() {
        val needs = mutableListOf<String>()
        fun addIfMissing(p: String) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                needs += p
            }
        }
        addIfMissing(Manifest.permission.ACCESS_FINE_LOCATION)
        addIfMissing(Manifest.permission.ACCESS_COARSE_LOCATION)
        addIfMissing(Manifest.permission.ACCESS_WIFI_STATE)
        addIfMissing(Manifest.permission.CHANGE_WIFI_STATE)
        if (needs.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needs.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), NEARBY_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
}

@Composable
fun Menu(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().background(color = colorResource(id = R.color.lighter_grey)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MenuButton(onClick = { navController.navigate("WifiSignals") }, text = "Wi-Fi Signals")
        MenuButton(onClick = { navController.navigate("ScanTool") }, text = "Scan Tool")
        MenuButton(onClick = { navController.navigate("MainApp") }, text = "Main App")

    }
}

@Composable
fun MenuButton(onClick: () -> Unit, text: String) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.darker_white))
    ) {
        Text(text, color = colorResource(R.color.light_blue))
    }
}

@Preview
@Composable
fun PreviewFun() {
    Button(
        onClick = {},
        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.darker_white))
    ) { Text("aaaa", color = colorResource(R.color.light_blue)) }
}
