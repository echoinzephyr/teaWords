package com.teameow.teawords.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerFallbackTest {
    private val releases = """[
        {"tag_name":"v1.2.1","assets":[{"name":"app.apk","size":1234,
         "browser_download_url":"https://github.com/echoinzephyr/teaWords/releases/download/v1.2.1/app.apk",
         "digest":"sha256:${"a".repeat(64)}"}]},
        {"tag_name":"v1.2.2-rc.1","prerelease":true},
        {"tag_name":"v99.0","draft":true}
    ]"""
    private fun manifest(entries: String = releases, repository: String = "echoinzephyr/teaWords", schema: Int = 1) =
        """{"schema":$schema,"repository":"$repository","releases":$entries}"""
    private fun response(request: Request, code: Int, body: String) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .body(body.toResponseBody()).build()

    @Test fun `403 falls back with size digest and stable channel intact`() = runBlocking {
        val hosts = mutableListOf<String>()
        val checker = UpdateChecker(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); hosts += request.url.host
            response(request, if (request.url.host == "api.github.com") 403 else 200, manifest())
        }.build())
        val stable = checker.latest().getOrThrow()!!
        assertEquals("v1.2.1", stable.tag)
        assertFalse(stable.prerelease); assertFalse(stable.fromTag)
        assertEquals(1234L, stable.apk!!.size); assertEquals("a".repeat(64), stable.apk!!.sha256)
        val beta = checker.latest(true).getOrThrow()!!
        assertEquals("v1.2.2-rc.1", beta.tag); assertTrue(beta.prerelease)
        assertEquals(listOf("api.github.com", "raw.githubusercontent.com", "raw.githubusercontent.com"), hosts)
    }

    @Test fun `rate limit respects reset and retry after across cache expiry`() = runBlocking {
        var clock = 1_000_000L; var apiCalls = 0; var feedCalls = 0
        val checker = UpdateChecker(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host == "api.github.com") {
                apiCalls++
                response(request, 429, """{"message":"API rate limit exceeded"}""").newBuilder()
                    .header("X-RateLimit-Remaining", "0").header("X-RateLimit-Reset", "1900")
                    .header("Retry-After", "1200").build()
            } else { feedCalls++; response(request, 200, manifest()) }
        }.build(), now = { clock })
        checker.latest().getOrThrow()
        clock += 301_000; checker.latest().getOrThrow()
        assertEquals(1, apiCalls); assertEquals(2, feedCalls)
        clock = 2_201_000; checker.latest().getOrThrow()
        assertEquals(2, apiCalls)
    }

    @Test fun `successful results are cached per channel and expire`() = runBlocking {
        var clock = 0L; var requests = 0
        val checker = UpdateChecker(OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            response(chain.request(), 200, if (chain.request().url.encodedPath.endsWith("latest"))
                """{"tag_name":"v1.2.1"}""" else releases)
        }.build(), now = { clock })
        assertEquals("v1.2.1", checker.latest().getOrThrow()!!.tag)
        assertEquals("v1.2.1", checker.latest().getOrThrow()!!.tag)
        assertEquals("v1.2.2-rc.1", checker.latest(true).getOrThrow()!!.tag)
        checker.latest(true).getOrThrow(); assertEquals(2, requests)
        clock = 300_000; checker.latest().getOrThrow(); assertEquals(3, requests)
    }

    @Test fun `concurrent checks share successful result`() = runBlocking {
        var requests = 0
        val checker = UpdateChecker(OkHttpClient.Builder().addInterceptor { chain ->
            requests++; response(chain.request(), 200, """{"tag_name":"v1.2.1"}""")
        }.build())
        (1..6).map { async { checker.latest().getOrThrow() } }.awaitAll()
        assertEquals(1, requests)
    }

    @Test fun `API network and server errors use fallback`() = runBlocking {
        for (code in listOf(0, 500, 502, 503, 504)) {
            val checker = UpdateChecker(OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                if (request.url.host == "api.github.com") {
                    if (code == 0) throw IOException("offline API")
                    response(request, code, "{}")
                } else response(request, 200, manifest())
            }.build())
            assertEquals("v1.2.1", checker.latest().getOrThrow()!!.tag)
        }
    }

    @Test fun `bad fallback never becomes no update or an external download`() = runBlocking {
        for (body in listOf("not json", manifest(schema = 2), manifest(repository = "other/repo"), manifest("[]"))) {
            val checker = UpdateChecker(OkHttpClient.Builder().addInterceptor { chain ->
                response(chain.request(), if (chain.request().url.host == "api.github.com") 403 else 200, body)
            }.build())
            val result = checker.latest()
            assertTrue(result.isFailure); assertTrue(result.exceptionOrNull()!!.message!!.contains("403"))
        }
        val checker = UpdateChecker(OkHttpClient.Builder().addInterceptor { chain ->
            response(chain.request(), if (chain.request().url.host == "api.github.com") 403 else 200,
                manifest(releases.replace("https://github.com/echoinzephyr/teaWords/releases/download/", "https://evil.example/")))
        }.build())
        assertNull(checker.latest().getOrThrow()!!.apk)
    }

    @Test fun `both sources failing stays failure and permits fallback retry`() = runBlocking {
        var apiCalls = 0; var feedCalls = 0
        val checker = UpdateChecker(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host == "api.github.com") { apiCalls++; response(request, 403, "{}") }
            else { feedCalls++; response(request, if (feedCalls == 1) 503 else 200, manifest()) }
        }.build())
        assertTrue(checker.latest().isFailure)
        assertEquals("v1.2.1", checker.latest().getOrThrow()!!.tag)
        assertEquals(1, apiCalls); assertEquals(2, feedCalls)
    }

    @Test fun `cancellation does not trigger fallback`() = runBlocking {
        var calls = 0
        val checker = UpdateChecker(OkHttpClient.Builder().addInterceptor {
            calls++; throw CancellationException("left page")
        }.build())
        try { checker.latest(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
        assertEquals(1, calls)
    }
}
