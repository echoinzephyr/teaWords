package com.teameow.teawords.data

import androidx.test.filters.LargeTest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Requires network; run explicitly to verify the deployed public feed on a physical device. */
@LargeTest
class PublicUpdateFeedTest {
    @Test fun publicFeedIsReadableWhenRestApiIsRateLimited() = runBlocking {
        val apiCalls = AtomicInteger()
        val rawCalls = AtomicInteger()
        val checker = UpdateChecker(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host == "api.github.com") {
                apiCalls.incrementAndGet()
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(403).message("rate limited").header("X-RateLimit-Remaining", "0")
                    .body("""{"message":"API rate limit exceeded"}""".toResponseBody()).build()
            } else { rawCalls.incrementAndGet(); chain.proceed(request) }
        }.build())
        val stable = checker.latest().getOrThrow()!!
        val beta = checker.latest(true).getOrThrow()!!
        assertTrue(isComparableVersion(stable.tag)); assertFalse(stable.prerelease)
        assertTrue(beta.tag == stable.tag || isNewerVersion(beta.tag, stable.tag))
        for (info in listOf(stable, beta)) {
            assertTrue(trustedUpdateUrl(checkNotNull(info.apk).url))
            assertTrue(info.apk!!.size > 0)
            assertTrue(info.apk!!.sha256!!.matches(Regex("[a-fA-F0-9]{64}")))
        }
        assertEquals(1, apiCalls.get()); assertEquals(2, rawCalls.get())
    }
}
