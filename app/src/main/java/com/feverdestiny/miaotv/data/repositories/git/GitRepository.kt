package com.feverdestiny.miaotv.data.repositories.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import com.feverdestiny.miaotv.data.entities.GitRelease
import com.feverdestiny.miaotv.data.repositories.git.parser.GitReleaseParser
import com.feverdestiny.miaotv.defaults.GitReleaseDefaultSource
import com.feverdestiny.miaotv.utils.AppOkHttp
import com.feverdestiny.miaotv.utils.Loggable

class GitRepository : Loggable() {

    /**
     * 拉取最新发行版。内置 GitHub Latest API 按 gh-proxy → 官方 API 回退；
     * 自定义地址只请求传入的 URL。
     */
    suspend fun latestRelease(url: String) = withContext(Dispatchers.IO) {
        log.d("获取最新发行版: $url")
        val urls = GitReleaseDefaultSource.fetchUrlsFor(url)
        var lastError: Exception? = null
        for (tryUrl in urls) {
            try {
                return@withContext fetchLatestRelease(tryUrl)
            } catch (ex: Exception) {
                lastError = ex
                log.e("获取最新发行版失败: $tryUrl", ex)
            }
        }
        throw Exception("获取最新发行版失败，请检查网络连接", lastError)
    }

    private suspend fun fetchLatestRelease(url: String): GitRelease {
        log.d("拉取发行版: $url")
        val client = AppOkHttp.client()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("获取最新发行版失败: ${response.code}")
            }
            val parser = GitReleaseParser.instances.first { it.isSupport(url) }
            return parser.parse(response.body!!.string())
        }
    }
}
