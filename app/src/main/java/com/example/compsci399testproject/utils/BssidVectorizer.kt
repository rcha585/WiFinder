package com.example.compsci399testproject.utils

import android.content.Context
import android.util.Log
import android.net.wifi.ScanResult
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min

object BssidVectorizer {

    private const val TAG = "BssidVectorizer"
    private const val FILE_NAME = "bssid_whitelist_order.txt"

    @Volatile private var vocab: List<String>? = null
    @Volatile private var index: Map<String, Int>? = null

    /** 尝试加载 assets/bssid_whitelist_order.txt；失败则使用空表并记录日志，不让应用崩溃。 */
    @Synchronized
    fun ensureLoaded(context: Context) {
        if (vocab != null && index != null) return
        try {
            context.assets.open(FILE_NAME).use { ins ->
                BufferedReader(InputStreamReader(ins, StandardCharsets.UTF_8)).use { br ->
                    val list = br.readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .map { it.lowercase() }
                    vocab = list
                    index = list.withIndex().associate { it.value to it.index }
                    Log.i(TAG, "Loaded whitelist: ${list.size} entries from assets/$FILE_NAME")
                }
            }
        } catch (e: Exception) {
            // 兜底：空词表，避免崩溃
            Log.e(TAG, "Failed to load assets/$FILE_NAME, fallback to empty list", e)
            vocab = emptyList()
            index = emptyMap()
        }
    }

    fun vocabSize(context: Context): Int {
        ensureLoaded(context)
        return vocab?.size ?: 0
    }

    fun vocab(context: Context): List<String> {
        ensureLoaded(context)
        return vocab ?: emptyList()
    }

    /** 一次扫描 -> 与词表一致的向量；缺失填 -100。 */
    fun toFeatureVector(context: Context, results: List<ScanResult>): FloatArray {
        ensureLoaded(context)
        val vcb = vocab ?: emptyList()
        val idx = index ?: emptyMap()
        val vec = FloatArray(vcb.size) { -100f }

        for (sr in results) {
            val bssid = sr.BSSID?.trim()?.lowercase() ?: continue
            val ssid  = sr.SSID?.trim()?.lowercase() ?: ""
            val key1 = "$bssid($ssid)"
            val key2 = bssid

            val j = idx[key1] ?: idx[key2] ?: continue
            val clipped = max(-100, min(-20, sr.level))
            vec[j] = max(vec[j], clipped.toFloat())
        }
        return vec
    }
}
