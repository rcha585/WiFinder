package com.example.compsci399testproject.utils

import android.content.Context
import android.net.wifi.ScanResult
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min

object BssidVectorizer {

    private var vocab: List<String>? = null
    private var index: Map<String, Int>? = null

    fun ensureLoaded(context: Context) {
        if (vocab != null) return
        val am = context.assets
        am.open("macAddresses.csv").use { ins ->
            val text = BufferedReader(InputStreamReader(ins)).readText()
            val list = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            vocab = list
            index = list.withIndex().associate { it.value to it.index }
        }
    }

    /** 把一次扫描结果转为与词表顺序一致的 RSSI 向量（缺失填 -100） */
    fun toFeatureVector(context: Context, results: List<ScanResult>): FloatArray {
        ensureLoaded(context)
        val vcb = vocab ?: emptyList()
        val idx = index ?: emptyMap()
        val vec = FloatArray(vcb.size) { -100f }
        for (sr in results) {
            val key = "${sr.BSSID}(${sr.SSID})" // 与 csv 中的格式一致
            val j = idx[key] ?: continue
            val clipped = max(-100, min(-20, sr.level))
            vec[j] = max(vec[j], clipped.toFloat()) // 同一 AP 多次取最强
        }
        return vec
    }
}
