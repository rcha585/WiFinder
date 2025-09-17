package com.example.compsci399testproject.utils

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Net {
    // 统一的 OkHttp 客户端（复用连接池 + 设置超时）
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)   // 建连超时
        .readTimeout(10, TimeUnit.SECONDS)     // 读超时
        .callTimeout(12, TimeUnit.SECONDS)     // 整体调用超时
        .build()
}
