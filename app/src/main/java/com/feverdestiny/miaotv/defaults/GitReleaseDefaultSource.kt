package com.feverdestiny.miaotv.defaults

import java.net.URI

/**
 * 应用内更新的获取 / 下载策略：
 * - 检查：仅当地址是内置 GitHub Releases 列表 API、旧 `/releases/latest`（或其 gh-proxy）时，
 *   按 gh-proxy 列表 API → 官方列表 API 回退（含 prerelease，解析层再跳过草稿）。
 * - 下载：GitHub / githubusercontent 附件先试 gh-proxy，再试直链。
 * 其它自定义地址只请求该地址本身。
 */
object GitReleaseDefaultSource {
    fun isBuiltinLatestApi(sourceUrl: String): Boolean {
        val trimmed = sourceUrl.trim()
        if (trimmed.isEmpty()) return false
        return AppBuiltinEndpoints.GIT_RELEASE_BUILTIN_API_URLS.any {
            it.equals(trimmed, ignoreCase = true)
        }
    }

    /**
     * 检查更新的实际拉取顺序。内置 Latest / 列表 API 始终从 gh-proxy 列表接口起试，
     * 避免把已失效的 `/releases/latest` 当作唯一地址反复请求。
     */
    fun fetchUrlsFor(sourceUrl: String): List<String> {
        val trimmed = sourceUrl.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (isBuiltinLatestApi(trimmed)) {
            AppBuiltinEndpoints.GIT_RELEASE_LATEST_API_FALLBACKS
        } else {
            listOf(trimmed)
        }
    }

    /**
     * APK 下载顺序。GitHub 托管附件：gh-proxy → 官方直链；其它地址不变。
     */
    fun downloadUrlsFor(assetUrl: String): List<String> {
        val trimmed = assetUrl.trim()
        if (trimmed.isEmpty()) return emptyList()
        val official = unwrapGithubProxy(trimmed)
        if (!isGithubHostedDownload(official)) return listOf(trimmed)
        val proxied = AppBuiltinEndpoints.GITHUB_PROXY_PREFIX + official
        return if (proxied.equals(official, ignoreCase = true)) {
            listOf(official)
        } else {
            listOf(proxied, official)
        }
    }

    fun isGithubHostedDownload(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return host == "github.com" ||
            host == "www.github.com" ||
            host == "githubusercontent.com" ||
            host.endsWith(".githubusercontent.com")
    }

    private fun unwrapGithubProxy(url: String): String {
        val prefix = AppBuiltinEndpoints.GITHUB_PROXY_PREFIX
        return if (url.startsWith(prefix, ignoreCase = true)) {
            url.substring(prefix.length)
        } else {
            url
        }
    }

    private fun hostOf(url: String): String? {
        return runCatching { URI(url).host }.getOrNull()?.lowercase()
    }
}
