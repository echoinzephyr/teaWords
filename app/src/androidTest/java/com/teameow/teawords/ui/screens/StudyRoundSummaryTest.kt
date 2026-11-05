package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.teameow.teawords.data.WordLearningSession
import com.teameow.teawords.data.WordLearningTask
import com.teameow.teawords.ui.theme.TeaWordsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StudyRoundSummaryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun recognitionCompletionIsNotReportedAsZeroAccuracyAndCanContinue() {
        var next = 0
        compose.setContent {
            TeaWordsTheme {
                StudyRoundSummary(WordLearningSession(tasks = listOf(WordLearningTask("access", "入口")),
                    position = 1, practiced = 1), 1, 0, {}, { next++ })
            }
        }
        compose.onNodeWithText("本轮完成").assertIsDisplayed()
        compose.onNodeWithText("0%").assertDoesNotExist()
        compose.onNodeWithText("独立回忆率").assertDoesNotExist()
        compose.onNodeWithText("回顾本轮单词").performClick()
        compose.onNodeWithText("入口").assertIsDisplayed()
        compose.onNodeWithText("继续下一轮").assertIsDisplayed().performClick()
        assertEquals(1, next)
    }
}
