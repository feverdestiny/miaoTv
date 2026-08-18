package com.feverdestiny.miaotv.defaults

/**
 * 首装默认端点与更新检查地址（与源码同仓维护；不再使用 CI 解密 proprietary bundle）。
 */
object AppBuiltinEndpoints {
    /** 公开 GitHub 代理前缀（国内可访问）；后面接完整 https://github.com/... 或 api.github.com URL。 */
    const val GITHUB_PROXY_PREFIX = "https://gh-proxy.com/"

    /**
     * 检查更新首选：Releases **列表** API（含 prerelease，不含草稿）。
     * `/releases/latest` 会排除 prerelease，仅有预发布时官方与 gh-proxy 都返回 404。
     */
    const val GIT_RELEASE_LIST_API =
        "https://api.github.com/repos/feverdestiny/miaoTv/releases?per_page=20"

    const val GIT_RELEASE_LIST_API_GH_PROXY =
        GITHUB_PROXY_PREFIX + GIT_RELEASE_LIST_API

    /** 旧 Latest API；仅作内置别名，获取层会改走 [GIT_RELEASE_LATEST_API_FALLBACKS]。 */
    const val GIT_RELEASE_LATEST_API =
        "https://api.github.com/repos/feverdestiny/miaoTv/releases/latest"

    const val GIT_RELEASE_LATEST_API_GH_PROXY =
        GITHUB_PROXY_PREFIX + GIT_RELEASE_LATEST_API

    /**
     * 应用内检查更新的拉取顺序：gh-proxy 列表 API → 官方列表 API。
     * 仅用于获取层回退，不改写用户自定义检查地址。
     */
    val GIT_RELEASE_LATEST_API_FALLBACKS: List<String> =
        listOf(GIT_RELEASE_LIST_API_GH_PROXY, GIT_RELEASE_LIST_API)

    /**
     * 视为内置检查地址的集合：新列表 API + 旧 `/releases/latest`（及其 gh-proxy）。
     * 任一命中都走 [GIT_RELEASE_LATEST_API_FALLBACKS]，不把旧 Latest 当唯一请求。
     */
    val GIT_RELEASE_BUILTIN_API_URLS: List<String> =
        GIT_RELEASE_LATEST_API_FALLBACKS +
            listOf(GIT_RELEASE_LATEST_API_GH_PROXY, GIT_RELEASE_LATEST_API)

    /** 默认订阅首选（国内可访问）；失败时按 [IPTV_DEFAULT_SUBSCRIPTION_FALLBACKS] 依次回退。 */
    const val IPTV_DEFAULT_SUBSCRIPTION_URL =
        "https://cdn.jsdelivr.net/gh/feverdestiny/miaoTv@main/sources/cn.m3u"

    const val IPTV_DEFAULT_SUBSCRIPTION_URL_GH_PROXY =
        "https://gh-proxy.com/https://raw.githubusercontent.com/feverdestiny/miaoTv/main/sources/cn.m3u"

    const val IPTV_DEFAULT_SUBSCRIPTION_URL_GITHUB_RAW =
        "https://raw.githubusercontent.com/feverdestiny/miaoTv/main/sources/cn.m3u"

    /**
     * 默认中国频道列表的拉取顺序：jsDelivr → gh-proxy → GitHub raw。
     * 仅用于获取层回退，不把某个镜像写成用户唯一订阅地址。
     */
    val IPTV_DEFAULT_SUBSCRIPTION_FALLBACKS: List<String> =
        listOf(
            IPTV_DEFAULT_SUBSCRIPTION_URL,
            IPTV_DEFAULT_SUBSCRIPTION_URL_GH_PROXY,
            IPTV_DEFAULT_SUBSCRIPTION_URL_GITHUB_RAW,
        )

    /** 仅央视列表首选；失败时按 [IPTV_CCTV_SUBSCRIPTION_FALLBACKS] 依次回退，不与 cn 链混用。 */
    const val IPTV_CCTV_SUBSCRIPTION_URL =
        "https://cdn.jsdelivr.net/gh/feverdestiny/miaoTv@main/sources/cctv.m3u"

    const val IPTV_CCTV_SUBSCRIPTION_URL_GH_PROXY =
        "https://gh-proxy.com/https://raw.githubusercontent.com/feverdestiny/miaoTv/main/sources/cctv.m3u"

    const val IPTV_CCTV_SUBSCRIPTION_URL_GITHUB_RAW =
        "https://raw.githubusercontent.com/feverdestiny/miaoTv/main/sources/cctv.m3u"

    val IPTV_CCTV_SUBSCRIPTION_FALLBACKS: List<String> =
        listOf(
            IPTV_CCTV_SUBSCRIPTION_URL,
            IPTV_CCTV_SUBSCRIPTION_URL_GH_PROXY,
            IPTV_CCTV_SUBSCRIPTION_URL_GITHUB_RAW,
        )

    /**
     * 内置播放列表回退链。当前订阅命中某条链中任一 URL 时，获取层走该整链。
     * cn 与 cctv 分列，互不混用。
     */
    val IPTV_BUILTIN_PLAYLIST_CHAINS: List<List<String>> =
        listOf(
            IPTV_DEFAULT_SUBSCRIPTION_FALLBACKS,
            IPTV_CCTV_SUBSCRIPTION_FALLBACKS,
        )

    /** GitHub raw / jsDelivr / gh-proxy 无需自定义 UA；空串即使用系统默认。 */
    const val IPTV_DEFAULT_REQUEST_HEADERS = ""

    const val EPG_XML_PRIMARY = "http://epg.51zmt.top:8000/e1.xml.gz"

    const val EPG_XML_SECONDARY = "https://epg.aptv.app/pp.xml.gz"

    val EPG_BUILTIN_ORDERED: List<String> =
        listOf(EPG_XML_PRIMARY, EPG_XML_SECONDARY)

    /** 空：出站不改写 User-Agent；若需与自建源校验 UA，可改为足够长度的密钥 Base64。 */
    const val REQUEST_SIGNING_KEY_B64: String = ""
}
