package com.feverdestiny.miaotv.data.repositories.git.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.feverdestiny.miaotv.data.entities.GitRelease
import com.feverdestiny.miaotv.utils.compareVersion

class GithubGitReleaseParser : GitReleaseParser {
    override fun isSupport(url: String): Boolean {
        return url.contains("github.com")
    }

    override suspend fun parse(data: String): GitRelease {
        val json = selectPublishedRelease(data)

        val assets = json.getValue("assets").jsonArray
        if (assets.isEmpty()) {
            throw Exception("Release 未包含任何附件，请确认已上传 APK")
        }
        val url = assets.pickMiaotvDefaultApkBrowserUrl()
            ?: throw Exception(
                "Release 中没有与本机渠道/CPU 匹配的 APK。" +
                    "鸿蒙变体仅升鸿蒙附件；原味 ARM 仅 arm 包；原味 x86 仅 x86_64 包，互不混用。"
            )
        // 解析结果保留 GitHub 直链；下载层再按 gh-proxy → 官方直链回退
        return GitRelease(
            version = json.getValue("tag_name").jsonPrimitive.content.removePrefix("v").trim(),
            downloadUrl = url,
            description = json.getValue("body").jsonPrimitive.content
        )
    }

    companion object {
        /**
         * 从 `/releases/latest` 单对象或 `/releases` 数组中选出可用发行版：
         * 跳过草稿，包含 prerelease；按 `tag_name` semver 取最新，必要时再比 `published_at`。
         * GitHub 的 404 JSON（无 `tag_name`）不视为有效发行版。
         */
        fun selectPublishedRelease(data: String): JsonObject {
            val element = Json.parseToJsonElement(data)
            val objects: List<JsonObject> = when (element) {
                is JsonArray -> element.map { it.jsonObject }
                is JsonObject -> listOf(element)
                else -> throw Exception("发行版数据格式无效")
            }
            val published = objects.filter { obj ->
                tagName(obj).isNotEmpty() && obj["draft"]?.jsonPrimitive?.booleanOrNull != true
            }
            if (published.isEmpty()) {
                throw Exception("没有可用的发行版")
            }
            return published.maxWith { a, b -> compareReleaseFreshness(a, b) }
        }

        private fun tagName(obj: JsonObject): String {
            return obj["tag_name"]?.jsonPrimitive?.content?.trim().orEmpty()
        }

        private fun publishedAt(obj: JsonObject): String {
            return obj["published_at"]?.jsonPrimitive?.content.orEmpty()
        }

        private fun compareReleaseFreshness(a: JsonObject, b: JsonObject): Int {
            val tagA = tagName(a).removePrefix("v")
            val tagB = tagName(b).removePrefix("v")
            val byTag = runCatching { tagA.compareVersion(tagB) }.getOrNull()
            if (byTag != null && byTag != 0) return byTag
            val byDate = publishedAt(a).compareTo(publishedAt(b))
            if (byDate != 0) return byDate
            return tagA.compareTo(tagB)
        }
    }
}
