package com.example.compsci399testproject.viewmodel

import android.app.Application
import android.content.Context
import android.net.wifi.ScanResult
import android.os.Environment
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.example.compsci399testproject.utils.WifiScanner
import java.io.File

class WifiViewModel(application: Context) : AndroidViewModel(application as Application) {
    private val scanner = WifiScanner(application.applicationContext, this)

    private val _lastScanTime = mutableStateOf<Long?>(null)
    val lastScanTime: State<Long?> = _lastScanTime
    var scanResults = scanner.scanResults
    private var lastUploadedResults: Map<String, Int>? = null

    private var accessPoints = mutableListOf<String>()
    private var strengthArray = mutableListOf<Float>()

    init {
        val fileName = "macAddresses.csv"
        val inputStream = application.assets.open(fileName)
        val reader = inputStream.bufferedReader()
        val firstLine = reader.readLine()
        this.accessPoints = firstLine.split(",").toMutableList()
    }

    fun updateScanResults() {
        _lastScanTime.value = System.currentTimeMillis()
        this.scanResults = scanner.scanResults
        this.convertResultsToStrengthArray()
    }

    fun scan() {
        scanner.scanWifi()
    }

    override fun onCleared() {
        super.onCleared()
        scanner.cleanup()
    }

    fun getResults(): List<ScanResult> {
        return this.scanResults.value
    }


    fun hasScanChanged(newResults: List<ScanResult>): Boolean {
        val currentMap = newResults.associate { it.BSSID to it.level }

        val changed = currentMap != lastUploadedResults
        if (changed) {
            lastUploadedResults = currentMap
        }
        return changed
    }

    private fun convertResultsToStrengthArray(){
        strengthArray.clear()
        for (i in 0 until accessPoints.size) {
            strengthArray.add(100f)
        }
        for (result in scanResults.value) {
            val signalName = result.BSSID + "(${result.SSID})"
            val level = result.level.toFloat()
            val index = accessPoints.indexOf(signalName)
            if (index != -1) {
                strengthArray[index] = level
            }
        }
//        this.writeStrengthArrayToFile()
    }

    fun getStrengthArray(): List<Float> {
        return strengthArray
    }

    fun writeStrengthArrayToFile() {
        val fileName = "strengthArray.csv"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        val writer = file.bufferedWriter()
        writer.write(strengthArray.joinToString(","))
        writer.close()
    }
}