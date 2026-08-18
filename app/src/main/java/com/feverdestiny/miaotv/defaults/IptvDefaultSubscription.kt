package com.feverdestiny.miaotv.defaults

/**
 * 默认中国频道订阅的获取策略：仅当当前地址是内置默认（或其镜像）时，
 * 按 jsDelivr → gh-proxy → GitHub raw 回退。用户自定义地址只请求该地址本身。
 */
object IptvDefaultSubscription {
    fun isBuiltin(sourceUrl: String): Boolean {
        val trimmed = sourceUrl.trim()
        if (trimmed.isEmpty()) return false
        return AppBuiltinEndpoints.IPTV_DEFAULT_SUBSCRIPTION_FALLBACKS.any {
            it.equals(trimmed, ignoreCase = true)
        }
    }

    /**
     * 实际拉取顺序。内置默认始终从 jsDelivr 起试，避免把已失效的首个 host
     * 当作唯一地址反复请求。
     */
    fun fetchUrlsFor(sourceUrl: String): List<String> {
        val trimmed = sourceUrl.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (isBuiltin(trimmed)) {
            AppBuiltinEndpoints.IPTV_DEFAULT_SUBSCRIPTION_FALLBACKS
        } else {
            listOf(trimmed)
        }
    }

    fun isValidDefaultPlaylist(body: String): Boolean {
        if (body.isBlank()) return false
        val first = body.trimStart('\uFEFF').lineSequence()
            .firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return first.startsWith("#EXTM3U", ignoreCase = true) ||
            first.startsWith("#EXTINF", ignoreCase = true)
    }
}
