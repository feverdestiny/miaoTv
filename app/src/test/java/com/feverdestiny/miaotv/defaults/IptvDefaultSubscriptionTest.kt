package com.feverdestiny.miaotv.defaults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvDefaultSubscriptionTest {

    @Test
    fun defaultFirstTryIsJsDelivrThenGhProxyThenGithubRaw() {
        assertEquals(
            "https://cdn.jsdelivr.net/gh/feverdestiny/miaoTv@main/sources/cn.m3u",
            AppBuiltinEndpoints.IPTV_DEFAULT_SUBSCRIPTION_URL,
        )
        assertEquals(
            listOf(
                "https://cdn.jsdelivr.net/gh/feverdestiny/miaoTv@main/sources/cn.m3u",
                "https://gh-proxy.com/https://raw.githubusercontent.com/feverdestiny/miaoTv/main/sources/cn.m3u",
                "https://raw.githubusercontent.com/feverdestiny/miaoTv/main/sources/cn.m3u",
            ),
            IptvDefaultSubscription.fetchUrlsFor(AppBuiltinEndpoints.IPTV_DEFAULT_SUBSCRIPTION_URL),
        )
    }

    @Test
    fun oldGithubRawDefaultAlsoGetsFullFallbackChain() {
        assertEquals(
            IptvDefaultSubscription.fetchUrlsFor(AppBuiltinEndpoints.IPTV_DEFAULT_SUBSCRIPTION_URL),
            IptvDefaultSubscription.fetchUrlsFor(
                AppBuiltinEndpoints.IPTV_DEFAULT_SUBSCRIPTION_URL_GITHUB_RAW,
            ),
        )
        assertTrue(
            IptvDefaultSubscription.isBuiltin(
                AppBuiltinEndpoints.IPTV_DEFAULT_SUBSCRIPTION_URL_GH_PROXY,
            ),
        )
    }

    @Test
    fun cctvFirstTryIsJsDelivrThenGhProxyThenGithubRaw() {
        assertEquals(
            "https://cdn.jsdelivr.net/gh/feverdestiny/miaoTv@main/sources/cctv.m3u",
            AppBuiltinEndpoints.IPTV_CCTV_SUBSCRIPTION_URL,
        )
        assertEquals(
            listOf(
                "https://cdn.jsdelivr.net/gh/feverdestiny/miaoTv@main/sources/cctv.m3u",
                "https://gh-proxy.com/https://raw.githubusercontent.com/feverdestiny/miaoTv/main/sources/cctv.m3u",
                "https://raw.githubusercontent.com/feverdestiny/miaoTv/main/sources/cctv.m3u",
            ),
            IptvDefaultSubscription.fetchUrlsFor(AppBuiltinEndpoints.IPTV_CCTV_SUBSCRIPTION_URL),
        )
    }

    @Test
    fun cctvGithubRawAlsoGetsFullCctvFallbackChain() {
        assertEquals(
            IptvDefaultSubscription.fetchUrlsFor(AppBuiltinEndpoints.IPTV_CCTV_SUBSCRIPTION_URL),
            IptvDefaultSubscription.fetchUrlsFor(
                AppBuiltinEndpoints.IPTV_CCTV_SUBSCRIPTION_URL_GITHUB_RAW,
            ),
        )
        assertEquals(
            IptvDefaultSubscription.fetchUrlsFor(AppBuiltinEndpoints.IPTV_CCTV_SUBSCRIPTION_URL),
            IptvDefaultSubscription.fetchUrlsFor(
                AppBuiltinEndpoints.IPTV_CCTV_SUBSCRIPTION_URL_GH_PROXY,
            ),
        )
        assertTrue(
            IptvDefaultSubscription.isBuiltin(
                AppBuiltinEndpoints.IPTV_CCTV_SUBSCRIPTION_URL_GITHUB_RAW,
            ),
        )
    }

    @Test
    fun cnAndCctvChainsAreNotMixed() {
        val cn = IptvDefaultSubscription.fetchUrlsFor(
            AppBuiltinEndpoints.IPTV_DEFAULT_SUBSCRIPTION_URL,
        )
        val cctv = IptvDefaultSubscription.fetchUrlsFor(
            AppBuiltinEndpoints.IPTV_CCTV_SUBSCRIPTION_URL,
        )
        assertTrue(cn.all { it.endsWith("cn.m3u") })
        assertTrue(cctv.all { it.endsWith("cctv.m3u") })
        assertTrue(cn.none { it in cctv })
    }

    @Test
    fun customSubscriptionIsUnchanged() {
        val custom = "https://example.com/my.m3u"
        assertFalse(IptvDefaultSubscription.isBuiltin(custom))
        assertEquals(listOf(custom), IptvDefaultSubscription.fetchUrlsFor(custom))
    }

    @Test
    fun defaultPlaylistRejectsEmptyAndNonM3u() {
        assertFalse(IptvDefaultSubscription.isValidDefaultPlaylist(""))
        assertFalse(IptvDefaultSubscription.isValidDefaultPlaylist("   "))
        assertFalse(IptvDefaultSubscription.isValidDefaultPlaylist("<html>404</html>"))
        assertTrue(IptvDefaultSubscription.isValidDefaultPlaylist("#EXTM3U\n#EXTINF:-1,CCTV1\nhttp://a"))
        assertTrue(IptvDefaultSubscription.isValidDefaultPlaylist("\uFEFF#EXTINF:-1,CCTV1\nhttp://a"))
    }
}
