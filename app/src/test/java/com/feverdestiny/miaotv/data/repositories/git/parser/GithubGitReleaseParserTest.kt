package com.feverdestiny.miaotv.data.repositories.git.parser

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GithubGitReleaseParserTest {

    @Test
    fun listJsonSelectsPrerelease() {
        val json = """
            [
              {
                "tag_name": "v0.1.0",
                "prerelease": true,
                "draft": false,
                "published_at": "2026-08-01T00:00:00Z",
                "body": "first",
                "assets": []
              }
            ]
        """.trimIndent()
        val selected = GithubGitReleaseParser.selectPublishedRelease(json)
        assertEquals("v0.1.0", selected.getValue("tag_name").jsonPrimitive.content)
    }

    @Test
    fun draftsAreSkippedInFavorOfOlderPrerelease() {
        val json = """
            [
              {
                "tag_name": "v0.2.0",
                "prerelease": false,
                "draft": true,
                "published_at": "2026-08-10T00:00:00Z",
                "body": "draft",
                "assets": []
              },
              {
                "tag_name": "v0.1.0",
                "prerelease": true,
                "draft": false,
                "published_at": "2026-08-01T00:00:00Z",
                "body": "published prerelease",
                "assets": []
              }
            ]
        """.trimIndent()
        val selected = GithubGitReleaseParser.selectPublishedRelease(json)
        assertEquals("v0.1.0", selected.getValue("tag_name").jsonPrimitive.content)
    }

    @Test
    fun newestSemverWinsIncludingPrereleases() {
        val json = """
            [
              {
                "tag_name": "v0.1.0",
                "prerelease": true,
                "draft": false,
                "published_at": "2026-08-10T00:00:00Z",
                "body": "older",
                "assets": []
              },
              {
                "tag_name": "v0.2.0",
                "prerelease": true,
                "draft": false,
                "published_at": "2026-08-01T00:00:00Z",
                "body": "newer tag",
                "assets": []
              }
            ]
        """.trimIndent()
        val selected = GithubGitReleaseParser.selectPublishedRelease(json)
        assertEquals("v0.2.0", selected.getValue("tag_name").jsonPrimitive.content)
    }

    @Test
    fun latestNotFoundObjectIsNotAValidRelease() {
        val json = """
            {
              "message": "Not Found",
              "documentation_url": "https://docs.github.com/rest/releases/releases#get-the-latest-release",
              "status": "404"
            }
        """.trimIndent()
        assertThrows(Exception::class.java) {
            GithubGitReleaseParser.selectPublishedRelease(json)
        }
    }

    @Test
    fun singleObjectLatestPayloadStillWorks() {
        val json = """
            {
              "tag_name": "v1.2.3",
              "prerelease": false,
              "draft": false,
              "published_at": "2026-08-01T00:00:00Z",
              "body": "stable",
              "assets": []
            }
        """.trimIndent()
        val selected = GithubGitReleaseParser.selectPublishedRelease(json)
        assertEquals("v1.2.3", selected.getValue("tag_name").jsonPrimitive.content)
    }

    @Test
    fun emptyListHasNoRelease() {
        assertThrows(Exception::class.java) {
            GithubGitReleaseParser.selectPublishedRelease("[]")
        }
    }
}
