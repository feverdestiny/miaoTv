package com.feverdestiny.miaotv.defaults

import com.feverdestiny.miaotv.data.repositories.git.parser.GiteeGitReleaseParser
import com.feverdestiny.miaotv.data.repositories.git.parser.GithubGitReleaseParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitReleaseDefaultSourceTest {

    private val listApi =
        "https://api.github.com/repos/feverdestiny/miaoTv/releases?per_page=20"
    private val listApiProxy = "https://gh-proxy.com/$listApi"
    private val latestApi =
        "https://api.github.com/repos/feverdestiny/miaoTv/releases/latest"
    private val latestApiProxy = "https://gh-proxy.com/$latestApi"

    @Test
    fun defaultFirstTryIsGhProxyThenOfficialListApi() {
        assertEquals(listApi, AppBuiltinEndpoints.GIT_RELEASE_LIST_API)
        assertEquals(
            listOf(listApiProxy, listApi),
            GitReleaseDefaultSource.fetchUrlsFor(AppBuiltinEndpoints.GIT_RELEASE_LIST_API),
        )
    }

    @Test
    fun proxiedListApiAlsoGetsFullFallbackChain() {
        assertEquals(
            GitReleaseDefaultSource.fetchUrlsFor(AppBuiltinEndpoints.GIT_RELEASE_LIST_API),
            GitReleaseDefaultSource.fetchUrlsFor(
                AppBuiltinEndpoints.GIT_RELEASE_LIST_API_GH_PROXY,
            ),
        )
        assertTrue(
            GitReleaseDefaultSource.isBuiltinLatestApi(
                AppBuiltinEndpoints.GIT_RELEASE_LIST_API_GH_PROXY,
            ),
        )
    }

    @Test
    fun oldLatestApiStillExpandsToListApiChain() {
        assertEquals(latestApi, AppBuiltinEndpoints.GIT_RELEASE_LATEST_API)
        assertEquals(
            listOf(listApiProxy, listApi),
            GitReleaseDefaultSource.fetchUrlsFor(AppBuiltinEndpoints.GIT_RELEASE_LATEST_API),
        )
        assertEquals(
            GitReleaseDefaultSource.fetchUrlsFor(AppBuiltinEndpoints.GIT_RELEASE_LATEST_API),
            GitReleaseDefaultSource.fetchUrlsFor(
                AppBuiltinEndpoints.GIT_RELEASE_LATEST_API_GH_PROXY,
            ),
        )
        assertTrue(GitReleaseDefaultSource.isBuiltinLatestApi(latestApi))
        assertTrue(GitReleaseDefaultSource.isBuiltinLatestApi(latestApiProxy))
    }

    @Test
    fun customUpdateUrlIsUnchanged() {
        val custom = "https://example.com/releases/latest"
        assertFalse(GitReleaseDefaultSource.isBuiltinLatestApi(custom))
        assertEquals(listOf(custom), GitReleaseDefaultSource.fetchUrlsFor(custom))
    }

    @Test
    fun giteeUpdateUrlIsUnchanged() {
        val gitee = "https://gitee.com/some/repo/releases/latest"
        assertFalse(GitReleaseDefaultSource.isBuiltinLatestApi(gitee))
        assertEquals(listOf(gitee), GitReleaseDefaultSource.fetchUrlsFor(gitee))
    }

    @Test
    fun githubAssetDownloadTriesProxyThenOfficial() {
        val asset =
            "https://github.com/feverdestiny/miaoTv/releases/download/v1.0.0/miaotv-1.0.0-arm.apk"
        assertEquals(
            listOf(
                "https://gh-proxy.com/$asset",
                asset,
            ),
            GitReleaseDefaultSource.downloadUrlsFor(asset),
        )
    }

    @Test
    fun githubusercontentAssetDownloadTriesProxyThenOfficial() {
        val asset =
            "https://objects.githubusercontent.com/github-production-release-asset-2e65be/123/abc"
        assertEquals(
            listOf(
                "https://gh-proxy.com/$asset",
                asset,
            ),
            GitReleaseDefaultSource.downloadUrlsFor(asset),
        )
    }

    @Test
    fun alreadyProxiedGithubAssetStillGetsOfficialFallback() {
        val official =
            "https://github.com/feverdestiny/miaoTv/releases/download/v1.0.0/miaotv-1.0.0-arm.apk"
        val proxied = "https://gh-proxy.com/$official"
        assertEquals(
            listOf(proxied, official),
            GitReleaseDefaultSource.downloadUrlsFor(proxied),
        )
    }

    @Test
    fun customDownloadUrlIsUnchanged() {
        val custom = "https://example.com/app.apk"
        assertFalse(GitReleaseDefaultSource.isGithubHostedDownload(custom))
        assertEquals(listOf(custom), GitReleaseDefaultSource.downloadUrlsFor(custom))
    }

    @Test
    fun githubParserStillMatchesProxiedListAndLatestApi() {
        val proxiedList = AppBuiltinEndpoints.GIT_RELEASE_LIST_API_GH_PROXY
        val proxiedLatest = AppBuiltinEndpoints.GIT_RELEASE_LATEST_API_GH_PROXY
        assertTrue(proxiedList.contains("github.com"))
        assertTrue(GithubGitReleaseParser().isSupport(proxiedList))
        assertTrue(GithubGitReleaseParser().isSupport(AppBuiltinEndpoints.GIT_RELEASE_LIST_API))
        assertTrue(GithubGitReleaseParser().isSupport(proxiedLatest))
        assertTrue(GithubGitReleaseParser().isSupport(AppBuiltinEndpoints.GIT_RELEASE_LATEST_API))
        assertFalse(GiteeGitReleaseParser().isSupport(proxiedList))
    }
}
