package com.teameow.teawords.ui.screens

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.teameow.teawords.MainActivity
import java.io.File
import org.junit.Rule
import org.junit.Test

class SearchTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeSearchMovesIntoResultAndReturnsWithDraft() {
        compose.onNodeWithContentDescription("打开下拉搜索").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("apple")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNodeWithTag("lookup-search-input").assertIsDisplayed().assertTextContains("apple")
        compose.onNodeWithText("查词与翻译").assertDoesNotExist()
        val folder = File(compose.activity.getExternalFilesDir(null), "material-refresh").apply { mkdirs() }
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(folder, "search-result-production.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("lookup-search-input").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertTextContains("apple")
        compose.onNodeWithContentDescription("退出搜索").performClick()
        compose.onNodeWithContentDescription("打开下拉搜索").assertIsDisplayed()
    }
}
