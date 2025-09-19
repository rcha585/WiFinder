package com.example.compsci399testproject.utils

import android.content.Context
import android.net.wifi.ScanResult
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min
import java.nio.charset.StandardCharsets

object BssidVectorizer {

    private var vocab: List<String>? = null
    private var index: Map<String, Int>? = null

    /** 读取 assets/bssid_whitelist_order.txt ，一行一个，支持 "bssid" 或 "bssid(ssid)" 两种写法 */
    fun ensureLoaded(context: Context) {
        if (vocab != null) return
        val am = context.assets
        am.open("bssid_whitelist_order.txt").use { ins ->
            val br = BufferedReader(InputStreamReader(ins, StandardCharsets.UTF_8))
            val list = br.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map { it.lowercase() }            // 统一小写
            vocab = list
            index = list.withIndex().associate { it.value to it.index }
        }
    }

    /** 词表大小（用于日志） */
    fun vocabSize(context: Context): Int {
        ensureLoaded(context)
        return vocab?.size ?: 0
    }

    /** 只读词表（便于调试/缺失打印） */
    fun vocab(context: Context): List<String> {
        ensureLoaded(context)
        return vocab ?: emptyList()
    }

    /** 把一次扫描转为与词表顺序一致的 RSSI 向量（缺失填 -100） */
    fun toFeatureVector(context: Context, results: List<ScanResult>): FloatArray {
        ensureLoaded(context)
        val vcb = vocab ?: emptyList()
        val idx = index ?: emptyMap()
        val vec = FloatArray(vcb.size) { -100f }

        for (sr in results) {
            // 统一小写
            val bssid = sr.BSSID?.trim()?.lowercase() ?: continue
            val ssid  = sr.SSID?.trim()?.lowercase()  ?: ""
            // 两种键都尝试：1) bssid(ssid)   2) bssid
            val key1 = "$bssid($ssid)"
            val key2 = bssid

            val j = when {
                idx.containsKey(key1) -> idx[key1]
                idx.containsKey(key2) -> idx[key2]
                else -> null
            } ?: continue

            val clipped = max(-100, min(-20, sr.level))
            vec[j] = max(vec[j], clipped.toFloat()) // 同一 AP 多次取最强
        }
        return vec
    }
}
