package com.feverdestiny.miaotv.defaults

/**
 * 内置播放列表的获取策略：当前地址命中某条内置链（cn 或 cctv 及其镜像）时，
 * 按该链的 jsDelivr → gh-proxy → GitHub raw 回退，且 cn / cctv 互不混用。
 * 用户自定义地址只请求该地址本身。
 */
object IptvDefaultSubscription {
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

    private fun matchingChain(sourceUrl: String): List<String>? {
        val trimmed = sourceUrl.trim()
        if (trimmed.isEmpty()) return null
        return AppBuiltinEndpoints.IPTV_BUILTIN_PLAYLIST_CHAINS.firstOrNull { chain ->
            chain.any { it.equals(trimmed, ignoreCase = true) }
        }
    }
}
