package com.feverdestiny.miaotv.defaults

import java.net.URI

/**
 * 内置播放列表的获取策略：当前地址命中某条内置链（cn 或 cctv 及其镜像）时，
 * 按该链的 jsDelivr → gh-proxy → GitHub raw 回退，且 cn / cctv 互不混用。
 * 用户自定义地址只请求该地址本身。
 */
object IptvDefaultSubscription {
    /** 整链失败后给用户的说明（Error 页；可进设置或扫码填源）。 */
    const val FETCH_FAILURE_MESSAGE =
        "拉源失败。请检查模拟器或网络能否打开 jsDelivr；可进设置或扫码填写直播源。"

    fun isBuiltin(sourceUrl: String): Boolean = matchingChain(sourceUrl) != null

    /**
     * 实际拉取顺序。内置列表始终从该链的 jsDelivr 起试，避免把已失效的首个 host
     * 当作唯一地址反复请求。
     */
    fun fetchUrlsFor(sourceUrl: String): List<String> {
        val trimmed = sourceUrl.trim()
        if (trimmed.isEmpty()) return emptyList()
        return matchingChain(trimmed) ?: listOf(trimmed)
    }

    fun isValidDefaultPlaylist(body: String): Boolean {
        if (body.isBlank()) return false
        val first = body.trimStart('\uFEFF').lineSequence()
            .firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return first.startsWith("#EXTM3U", ignoreCase = true) ||
            first.startsWith("#EXTINF", ignoreCase = true)
    }

    /** 给 Loading 用的短 host 名：jsDelivr / gh-proxy / GitHub，或其它 host。 */
    fun hostLabel(url: String): String {
        val host = runCatching { URI(url.trim()).host.orEmpty() }.getOrDefault("").lowercase()
        return when {
            host.contains("jsdelivr") -> "jsDelivr"
            host.contains("gh-proxy") || host.contains("ghproxy") -> "gh-proxy"
            host.contains("githubusercontent") ||
                host == "github.com" ||
                host.endsWith(".github.com") -> "GitHub"
            host.isNotEmpty() -> host
            else -> "远程源"
        }
    }

    fun fetchProgressMessage(url: String, index: Int, total: Int): String {
        val label = hostLabel(url)
        return if (total > 1) {
            "正在从 $label 拉取直播源（${index + 1}/$total）…"
        } else {
            "正在从 $label 拉取直播源…"
        }
    }

    private fun matchingChain(sourceUrl: String): List<String>? {
        val trimmed = sourceUrl.trim()
        if (trimmed.isEmpty()) return null
        return AppBuiltinEndpoints.IPTV_BUILTIN_PLAYLIST_CHAINS.firstOrNull { chain ->
            chain.any { it.equals(trimmed, ignoreCase = true) }
        }
    }
}
