package com.example.compsci399testproject.utils

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt

/**
 * 统一管理原点/缩放/朝向，并持久化。
 * - originX, originY: 以“楼层内坐标单位”表示（和你采集/训练一致）
 * - pxPerUnit: 如果后续你要把坐标映射到地图像素，可用这个做缩放（现在先用 1f）
 * - invertY: 屏幕坐标Y向下 → 一般为 true
 */
object CoordTransform {
    private const val PREF = "coord_prefs"
    private const val K_OX = "origin_x"
    private const val K_OY = "origin_y"
    private const val K_S = "px_per_unit"
    private const val K_INVY = "invert_y"

    var originX: Float = 0f
        private set
    var originY: Float = 0f
        private set
    var pxPerUnit: Float = 1f
        private set
    var invertY: Boolean = true
        private set

    fun load(context: Context) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        originX = sp.getFloat(K_OX, 0f)
        originY = sp.getFloat(K_OY, 0f)
        pxPerUnit = sp.getFloat(K_S, 1f)
        invertY = sp.getBoolean(K_INVY, true)
    }

    fun save(context: Context, ox: Float = originX, oy: Float = originY,
             scale: Float = pxPerUnit, invY: Boolean = invertY) {
        val sp = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp.edit()
            .putFloat(K_OX, ox)
            .putFloat(K_OY, oy)
            .putFloat(K_S, scale)
            .putBoolean(K_INVY, invY)
            .apply()
        originX = ox; originY = oy; pxPerUnit = scale; invertY = invY
    }

    /** 楼层内坐标 → 以原点为 (0,0) 的坐标（可再乘以像素缩放去画图） */
    fun toLocal(x: Float, y: Float): Pair<Float, Float> {
        val lx = (x - originX)
        val ly = if (invertY) (originY - y) else (y - originY)
        return Pair(lx, ly)
    }

    /** 如果地图用像素坐标，可用这个直接得到像素 */
    fun toPixels(x: Float, y: Float): Pair<Int, Int> {
        val (lx, ly) = toLocal(x, y)
        return Pair((lx * pxPerUnit).roundToInt(), (ly * pxPerUnit).roundToInt())
    }
}
