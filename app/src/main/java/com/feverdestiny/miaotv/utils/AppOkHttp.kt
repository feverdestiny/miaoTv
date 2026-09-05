package com.feverdestiny.miaotv.utils

import com.feverdestiny.miaotv.data.utils.Constants
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** 应用内统一 OkHttp：附带调试 HTTP 日志拦截器（仅 [com.feverdestiny.miaotv.ui.utils.SP.debugAppLog] 开启时写历史）。 */
object AppOkHttp {
    fun newBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .addInterceptor(DebugHttpLogInterceptor())

    fun client(): OkHttpClient = newBuilder().build()

    /**
     * 播放列表 / EPG 等元数据：短超时，避免单个不可达 host 把首启 Loading 卡住数分钟。
     * 媒体探测与播放仍用 [client] 的 180s 默认。
     */
    fun metadataNewBuilder(): OkHttpClient.Builder =
        newBuilder()
            .connectTimeout(Constants.IPTV_FETCH_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constants.IPTV_FETCH_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Constants.IPTV_FETCH_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(Constants.IPTV_FETCH_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    fun metadataClient(): OkHttpClient = metadataNewBuilder().build()
}
