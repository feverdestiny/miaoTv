package com.feverdestiny.miaotv.data.repositories.iptv

import java.io.File
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import com.feverdestiny.miaotv.AppGlobal
import com.feverdestiny.miaotv.data.entities.IptvGroupList
import com.feverdestiny.miaotv.data.repositories.FileCacheRepository
import com.feverdestiny.miaotv.data.repositories.iptv.parser.IptvParser
import com.feverdestiny.miaotv.defaults.IptvDefaultSubscription
import com.feverdestiny.miaotv.utils.AppOkHttp
import com.feverdestiny.miaotv.utils.Logger
import com.feverdestiny.miaotv.utils.IptvOutboundHeaderPolicy
import com.feverdestiny.miaotv.utils.normalizeIptvRequestHeadersInput
import com.feverdestiny.miaotv.utils.parseHttpHeaderLines
import com.feverdestiny.miaotv.utils.toOkHttpHeaders
import com.feverdestiny.miaotv.ui.utils.SP

/**
 * 直播源获取
 */
class IptvRepository : FileCacheRepository("iptv.txt") {
    private val log = Logger.create(javaClass.simpleName)
    private val m3uEpgAttrRegex = Regex("""\b(?:x-tvg-url|url-tvg)\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)

    /**
     * 获取远程直播源数据。
     * 内置 cn / cctv 订阅按各自的 jsDelivr → gh-proxy → GitHub raw 回退；
     * 自定义地址只请求用户填写的 URL。
     */
    private suspend fun fetchSource(sourceUrl: String, requestHeadersText: String) =
        withContext(Dispatchers.IO) {
        log.d("获取远程直播源: $sourceUrl")

        if (sourceUrl.trim().startsWith(SP.IPTV_LOCAL_SOURCE_URL)) {
            val text = SP.readIptvLocalUploadOrNull()
            if (text.isNullOrBlank()) {
                throw Exception("本地订阅文件不存在或为空，请在网页管理端重新上传")
            }
            return@withContext text
        }

        val urls = IptvDefaultSubscription.fetchUrlsFor(sourceUrl)
        val requireM3u = IptvDefaultSubscription.isBuiltin(sourceUrl)
        var lastError: Exception? = null
        for (url in urls) {
            try {
                return@withContext fetchRemotePlaylist(url, requestHeadersText, requireM3u)
            } catch (ex: Exception) {
                lastError = ex
                log.e("获取远程直播源失败: $url", ex)
            }
        }
        throw Exception("获取远程直播源失败，请检查网络连接", lastError)
    }

    private fun fetchRemotePlaylist(
        url: String,
        requestHeadersText: String,
        requireM3u: Boolean,
    ): String {
        log.d("拉取直播源: $url")
        val client = AppOkHttp.client()
        val norm = normalizeIptvRequestHeadersInput(requestHeadersText)
        val blended =
            IptvOutboundHeaderPolicy.applyToNormalizedHeadersText(norm, url)
        val headerMap = blended.parseHttpHeaderLines()
        val reqBuilder = Request.Builder().url(url)
        if (headerMap.isNotEmpty()) {
            reqBuilder.headers(headerMap.toOkHttpHeaders())
        }
        val request = reqBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("获取远程直播源失败: ${response.code}")
            }
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) {
                throw Exception("获取远程直播源失败: 内容为空")
            }
            if (requireM3u && !IptvDefaultSubscription.isValidDefaultPlaylist(text)) {
                throw Exception("获取远程直播源失败: 不是有效的 M3U")
            }
            return text
        }
    }

    /**
     * 获取直播源分组列表
     */
    suspend fun getIptvGroupList(
        sourceUrl: String,
        cacheTime: Long,
        requestHeadersText: String = "",
    ): IptvGroupList {
        if (sourceUrl.isBlank()) {
            return IptvGroupList()
        }
        try {
            val sourceData = getOrRefresh(cacheTime) {
                fetchSource(sourceUrl, requestHeadersText)
            }
            SP.iptvSourceEmbeddedEpgUrl = extractEmbeddedEpgUrlFromM3u(sourceData)

            // 大文件解析耗 CPU，避免在 viewModel 主线程上执行导致界面长期停在「加载中」
            return withContext(Dispatchers.Default) {
                val parser = IptvParser.instances.first { it.isSupport(sourceUrl, sourceData) }
                val normalizedData = resolveRelativeStreamUrls(sourceUrl, sourceData)
                var groupList = parser.parse(normalizedData)
                log.i("解析直播源完成：${groupList.size}个分组，${groupList.flatMap { it.iptvList }.size}个频道")

                groupList
            }
        } catch (ex: Exception) {
            log.e("获取直播源失败", ex)
            throw Exception(ex)
        }
    }

    /**
     * 仅读本地 [iptv.txt] 缓存并解析，不发起网络（供后台 EPG 任务按当前订阅频道过滤节目单）。
     * 无缓存或解析失败时返回空分组。
     */
    suspend fun loadCachedIptvGroupListOrEmpty(): IptvGroupList {
        val data = withContext(Dispatchers.IO) {
            val f = File(AppGlobal.cacheDir, "iptv.txt")
            if (!f.isFile) return@withContext ""
            f.readText()
        }
        if (data.isBlank()) return IptvGroupList()
        return withContext(Dispatchers.Default) {
            val url = SP.iptvSourceUrl.ifBlank { "https://local.invalid/playlist.m3u" }
            val parser = IptvParser.instances.firstOrNull { it.isSupport(url, data) }
                ?: return@withContext IptvGroupList()
            try {
                parser.parse(resolveRelativeStreamUrls(url, data))
            } catch (e: Exception) {
                log.e("解析本地 IPTV 缓存失败", e)
                IptvGroupList()
            }
        }
    }

    /**
     * 对 HTTP(S) 订阅中的相对频道地址做 URI.resolve 归一化，兼容 rtp2httpd 等网关输出的
     * `/rtp/...`、`./xx.ts` 形式地址。
     */
    private fun resolveRelativeStreamUrls(sourceUrl: String, sourceData: String): String {
        if (!sourceUrl.startsWith("http://", true) && !sourceUrl.startsWith("https://", true)) {
            return sourceData
        }
        val base = runCatching { URI(sourceUrl) }.getOrNull() ?: return sourceData
        val lines = sourceData.split("\r\n", "\n")
        var changed = false
        val rewritten = lines.map { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("#")) return@map raw
            val hasScheme = Regex("""^[a-zA-Z][a-zA-Z0-9+\-.]*://""").containsMatchIn(line)
            if (hasScheme) return@map raw
            val resolved = runCatching { base.resolve(line).toString() }.getOrElse { line }
            if (resolved != line) changed = true
            raw.replace(line, resolved)
        }
        return if (changed) rewritten.joinToString("\n") else sourceData
    }

    /**
     * 从 M3U 顶部 `#EXTM3U` 行提取节目单地址（`x-tvg-url` / `url-tvg`）。
     */
    private fun extractEmbeddedEpgUrlFromM3u(sourceData: String): String {
        val normalized = sourceData.trimStart('\uFEFF')
        val lines = normalized.split("\r\n", "\n")
        for (line in lines) {
            val t = line.trim()
            if (t.isBlank()) continue
            if (!t.startsWith("#")) break
            if (!t.startsWith("#EXTM3U", ignoreCase = true)) continue
            val match = m3uEpgAttrRegex.find(t) ?: continue
            val url = match.groupValues.getOrNull(2)?.trim().orEmpty()
            if (url.isNotBlank()) return url
        }
        return ""
    }
}