package com.example.compsci399testproject

import android.content.pm.PackageManager
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
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.compsci399testproject.viewmodel.WifiScannerViewModelFactory
import com.example.compsci399testproject.viewmodel.WifiViewModel
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.VectorProperty
import com.example.compsci399testproject.sensors.RotationSensorService
import com.example.compsci399testproject.utils.initialiseGraph
import com.example.compsci399testproject.viewmodel.MapViewModel
import com.example.compsci399testproject.viewmodel.MapViewModelFactory

class MainActivity : ComponentActivity() {
    private var LOCATION_PERMISSION_REQUEST_CODE = 1

    private lateinit var wifiViewModel: WifiViewModel

    private lateinit var mapViewModel: MapViewModel

    private lateinit var rotationService: RotationSensorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_WIFI_STATE
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CHANGE_WIFI_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.ACCESS_WIFI_STATE,
                    android.Manifest.permission.CHANGE_WIFI_STATE
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            1001
        )

        val factory = WifiScannerViewModelFactory(applicationContext)
        wifiViewModel = ViewModelProvider(this, factory)[WifiViewModel::class.java]

        val mapFactory = MapViewModelFactory(wifiViewModel)
        mapViewModel = ViewModelProvider(this, mapFactory)[MapViewModel::class.java]

        rotationService = RotationSensorService(applicationContext)

        setContent {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "HomeMenu", builder = {
                composable("HomeMenu") {
                    Menu(navController)
                }

                composable("WifiSignals") {
                    WifiSignalList(wifiViewModel)
                }

                composable("ScanTool") {
                    ScanTool(wifiViewModel)
                }

                // This page just shows an absolute compass position implementation
                // but it needs a lit of work. Also has a rudimentary step detector
                // Feel free to look at it and use if you want, but it may take more
                // work to get it to be useful.
//                composable("SensorTool") {
//                    SensorTool()
//                }

                composable("MainApp") {
                    MapView(mapViewModel)
                }
            })
        }
    }
}

@Composable
fun Menu(navController: NavController)
{
    Column(modifier = Modifier.fillMaxSize().background(color = colorResource(id = R.color.lighter_grey)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        MenuButton(onClick = {navController.navigate("WifiSignals")}, text = "Wi-Fi Signals")
        MenuButton(onClick = {navController.navigate("ScanTool")}, text = "Scan Tool")
//        MenuButton(onClick = {navController.navigate("SensorTool")}, text="Sensor Tool")
        MenuButton(onClick = {navController.navigate("MainApp")}, text = "Main App")
    }
}

@Composable
fun MenuButton(onClick: () -> Unit, text: String) {
    Button(onClick = { onClick()},
        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.darker_white))) {
        Text(text, color = colorResource(R.color.light_blue))
    }
}

@Preview
@Composable
fun PreviewFun() {
    Button(onClick = {},
        colors = ButtonDefaults.buttonColors(containerColor = colorResource(R.color.darker_white))
    ) {
        Text("aaaa", color = colorResource(R.color.light_blue))
    }
}