package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.teameow.teawords.data.AppPreferences
import com.teameow.teawords.data.UpdateChecker
import com.teameow.teawords.ui.theme.TeaWordsTheme
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class UpdateFallbackScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun rateLimitedApiStillShowsStableAndBetaAndRecheckUsesCache() {
        val apiCalls = AtomicInteger()
        val rawCalls = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host == "api.github.com") {
                apiCalls.incrementAndGet()
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(403)
                    .message("rate limited").header("X-RateLimit-Remaining", "0")
                    .body("""{"message":"API rate limit exceeded"}""".toResponseBody()).build()
            } else {
                rawCalls.incrementAndGet()
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200)
                    .message("test").body("""{"schema":1,"repository":"echoinzephyr/teaWords","releases":[
                      {"tag_name":"v1.2.1"}, {"tag_name":"v1.2.2-rc.1","prerelease":true}
                    ]}""".toResponseBody()).build()
            }
        }.build()
        val prefs = AppPreferences(compose.activity)
        val original = prefs.receiveBetaUpdates
        prefs.receiveBetaUpdates = false
        val checker = UpdateChecker(client)
        try {
            compose.setContent {
                TeaWordsTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        UpdateCheckCard("1.0.0", 1, checker)
                    }
                }
            }
            compose.onNodeWithText("检查更新", useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("v1.2.1", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("重新检查").performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(1, apiCalls.get()); assertEquals(1, rawCalls.get())
            compose.onNodeWithTag("beta-updates-switch").performScrollTo().performClick()
            compose.onNodeWithText("检查更新", useUnmergedTree = true).performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("v1.2.2-rc.1", substring = true).fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, apiCalls.get()); assertEquals(2, rawCalls.get())
        } finally { prefs.receiveBetaUpdates = original }
    }
}
