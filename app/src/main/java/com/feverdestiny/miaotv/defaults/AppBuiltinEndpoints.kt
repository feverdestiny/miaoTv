package com.feverdestiny.miaotv.defaults

/**
 * 首装默认端点与更新检查地址（与源码同仓维护；不再使用 CI 解密 proprietary bundle）。
 */
object AppBuiltinEndpoints {
    const val GIT_RELEASE_LATEST_API =
        "https://api.github.com/repos/feverdestiny/miaoTv/releases/latest"

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

    /** GitHub raw / jsDelivr / gh-proxy 无需自定义 UA；空串即使用系统默认。 */
    const val IPTV_DEFAULT_REQUEST_HEADERS = ""

    const val EPG_XML_PRIMARY = "http://epg.51zmt.top:8000/e1.xml.gz"

    const val EPG_XML_SECONDARY = "https://epg.aptv.app/pp.xml.gz"

    val EPG_BUILTIN_ORDERED: List<String> =
        listOf(EPG_XML_PRIMARY, EPG_XML_SECONDARY)

    /** 空：出站不改写 User-Agent；若需与自建源校验 UA，可改为足够长度的密钥 Base64。 */
    const val REQUEST_SIGNING_KEY_B64: String = ""
}
