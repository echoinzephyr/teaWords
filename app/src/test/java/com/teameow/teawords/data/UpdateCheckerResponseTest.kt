package com.teameow.teawords.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerResponseTest {
    @Test fun `release APK includes download size and checksum`() = runBlocking {
        val digest = "a".repeat(64)
        val info = checker(mapOf("/releases/latest" to (200 to """{
            "tag_name":"v1.2.0","assets":[
                {"name":"notes.txt","browser_download_url":"https://example.com/notes","size":100},
                {"name":"teaWords-1.2.0.apk","browser_download_url":"https://github.com/echoinzephyr/teaWords/releases/download/v1.2.0/teaWords-1.2.0.apk","size":1234,"digest":"sha256:$digest"}
            ]}"""))).latest().getOrThrow()!!
        assertEquals(1234L, info.apk!!.size)
        assertEquals(digest, info.apk!!.sha256)
    }
    @Test fun `external APK and nullable assets cannot create a download`() = runBlocking {
        for (assets in listOf("null", """[{"name":"update.apk","browser_download_url":"https://example.com/update.apk","size":1234}]""")) {
            val info = checker(mapOf("/releases/latest" to (200 to """{"tag_name":"v1.2.0","assets":$assets}"""))).latest().getOrThrow()!!
            assertNull(info.apk)
        }
        assertFalse(trustedUpdateUrl("http://github.com/echoinzephyr/teaWords/releases/download/v1.2.0/update.apk"))
        assertFalse(trustedUpdateUrl("https://github.com/another/project/releases/download/v1.2.0/update.apk"))
    }
    private fun checker(responses: Map<String, Pair<Int, String>>) = UpdateChecker(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val (code, body) = checkNotNull(responses[request.url.encodedPath.substringAfter("/teaWords")])
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(body.toResponseBody()).build()
        }.build()
    )

    @Test fun `nullable release fields do not break update checks`() = runBlocking {
        val info = checker(mapOf("/releases/latest" to (200 to
            """{"tag_name":"v1.2","name":null,"body":null,"html_url":null,"published_at":null}""")))
            .latest().getOrThrow()!!
        assertEquals("v1.2", info.tag)
        assertEquals("", info.notes)
        assertEquals(UpdateChecker.PAGE_URL, info.pageUrl)
        assertNull(info.publishedAt)
    }

    @Test fun `fallback selects highest numeric tag rather than first tag`() = runBlocking {
        val info = checker(mapOf(
            "/releases/latest" to (404 to "{}"), "/releases" to (200 to "[]"),
            "/tags" to (200 to """[{"name":"nightly"},{"name":"v1.9"},{"name":"v1.10"}]""")
        )).latest().getOrThrow()!!
        assertEquals("v1.10", info.tag)
        assertTrue(info.fromTag)
    }

    @Test fun `fallback skips drafts and prereleases and tolerates nullable notes`() = runBlocking {
        val info = checker(mapOf(
            "/releases/latest" to (404 to "{}"),
            "/releases" to (200 to """[
                {"tag_name":"v3.0","draft":true},
                {"tag_name":"v2.0-rc1","prerelease":true},
                {"tag_name":"nightly"},
                {"tag_name":"v1.9","body":null},
                {"tag_name":"v1.10","body":null}
            ]""")
        )).latest().getOrThrow()!!
        assertEquals("v1.10", info.tag)
    }

    @Test fun `empty repository and unreadable tags have no comparable release`() = runBlocking {
        for (tags in listOf("[]", """[{"name":"nightly"}]""")) {
            assertNull(checker(mapOf(
                "/releases/latest" to (404 to "{}"), "/releases" to (200 to "[]"),
                "/tags" to (200 to tags)
            )).latest().getOrThrow())
        }
    }

    @Test fun `http errors remain failures rather than no release`() = runBlocking {
        val result = checker(mapOf("/releases/latest" to (403 to "{}"))).latest()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("403"))
    }
    @Test fun `beta opt in includes RC even while latest stable already exists`() = runBlocking {
        val info = checker(mapOf("/releases" to (200 to """[
          {"tag_name":"v1.2.1"}, {"tag_name":"v1.2.2-rc.2","prerelease":true},
          {"tag_name":"v1.2.2-rc.10","prerelease":true}, {"tag_name":"v9.0","draft":true}
        ]"""))).latest(includePrereleases = true).getOrThrow()!!
        assertEquals("v1.2.2-rc.10", info.tag)
        assertTrue(info.prerelease)
    }
    @Test fun `stable fallback tags cannot leak RC when beta is disabled`() = runBlocking {
        val info = checker(mapOf("/releases/latest" to (404 to "{}"), "/releases" to (200 to "[]"),
            "/tags" to (200 to """[{"name":"v2.0-rc.1"},{"name":"v1.2.1"}]"""))).latest().getOrThrow()!!
        assertEquals("v1.2.1", info.tag)
    }
    @Test fun `beta falls back to stable when it is newer than every prerelease`() = runBlocking {
        val info = checker(mapOf("/releases" to (200 to """[{"tag_name":"v1.2.2-rc.1","prerelease":true},{"tag_name":"v1.2.2"}]""")))
            .latest(true).getOrThrow()!!
        assertEquals("v1.2.2", info.tag); assertFalse(info.prerelease)
    }
    @Test fun `private repository is a failed check rather than no update`() = runBlocking {
        val result = checker(mapOf("/releases" to (404 to "{}"))).latest(true)
        assertTrue(result.isFailure)
    }
}
