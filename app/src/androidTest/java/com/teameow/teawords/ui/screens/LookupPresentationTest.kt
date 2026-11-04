package com.teameow.teawords.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.teameow.teawords.data.OnlineDictionary
import com.teameow.teawords.ui.LookupViewModel
import org.junit.Rule
import org.junit.Test

class LookupPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun onlineAnswerReplacesDuplicateLocalHeroAndKeepsBookmark() {
        lateinit var model: LookupViewModel
        compose.runOnUiThread {
            model = LookupViewModel(compose.activity.applicationContext as Application, OnlineDictionary {
                """{"ec":{"word":[{"trs":[{"tr":[{"l":{"i":["相同；同样"]}}]}]}]}}"""
            })
        }
        compose.setContent { MaterialTheme { LookupScreen("same", onBack = {}, model = model) } }
        compose.waitUntil(10_000) { model.state.value.onlineEntry != null }
        compose.onAllNodesWithText("same", useUnmergedTree = true).assertCountEquals(2) // input + one heading
        compose.onAllNodesWithText("复制释义").assertCountEquals(1)
        compose.onNodeWithContentDescription("收藏单词").assertExists()
    }
}
