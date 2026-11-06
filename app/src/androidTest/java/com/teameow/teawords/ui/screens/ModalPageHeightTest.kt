package com.teameow.teawords.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.teameow.teawords.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModalPageHeightTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun statisticsAndSettingsExpandToTheSameTopAndShareHeaderHeight() {
        compose.onNodeWithText("翻译").performClick()
        compose.onNodeWithContentDescription("设置").performClick()
        val settings = compose.onNodeWithContentDescription("关闭设置").getUnclippedBoundsInRoot()
        compose.onNodeWithContentDescription("关闭设置").performClick()
        compose.onNodeWithContentDescription("学习统计").performClick()
        val statistics = compose.onNodeWithContentDescription("关闭统计").getUnclippedBoundsInRoot()
        assertEquals(settings.top, statistics.top)
        assertEquals(settings.bottom - settings.top, statistics.bottom - statistics.top)
        val folder = java.io.File(compose.activity.getExternalFilesDir(null), "unified-pages").apply { mkdirs() }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(folder, "statistics-full-page.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithContentDescription("关闭统计").performClick()
    }
}
