package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.teameow.teawords.data.HistoryItem
import com.teameow.teawords.data.VocabularyItem
import com.teameow.teawords.ui.theme.TeaWordsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PageLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val history = (1..30).map { HistoryItem(it, "word$it", "释义 $it", 0L) }

    @Test fun hostedListScrollsBehindFloatingDockAndClearsItAtTheEnd() {
        compose.setContent {
            CompositionLocalProvider(LocalReservedBottom provides 100.dp) {
                TeaWordsTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        TeaListPage("浮动导航") { items(30) { androidx.compose.material3.Text("entry$it", Modifier.height(60.dp)) } }
                    }
                }
            }
        }
        val list = compose.onNode(hasScrollToNodeAction())
        assertEquals(640.dp, list.getUnclippedBoundsInRoot().bottom)
        list.performScrollToNode(hasText("entry29"))
        list.performTouchInput { swipeUp() }
        compose.onNodeWithText("entry29").assertIsDisplayed()
        assertTrue(compose.onNodeWithText("entry29").getUnclippedBoundsInRoot().bottom <= 540.dp)
    }

    @Test fun lastNotebookEntryStaysAboveDockAtLargeFont() {
        var clicked: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalReservedBottom provides 100.dp,
                LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                TeaWordsTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        RecordsView(history.map { VocabularyItem(it.id, it.word, null, it.translation, it.timestamp) }, 0, { clicked = it }, {})
                    }
                }
            }
        }
        compose.onNodeWithText("生词本 · 30").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("word30"))
        compose.onNodeWithText("word30").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("word30", clicked) }
        assertTrue("Notebook bottom: ${compose.onNodeWithText("word30").getUnclippedBoundsInRoot().bottom}", compose.onNodeWithText("word30").getUnclippedBoundsInRoot().bottom <= 540.dp)
    }

    @Test fun homeRecentWordsRemainAccessibleInSheet() {
        compose.setContent {
            TeaWordsTheme {
                Box(Modifier.width(320.dp).height(640.dp)) {
                    val recent = remember { mutableStateOf(false) }
                    HomeView("", {}, {}, emptyList(), false, {}, history,
                        "茶词", "专注翻译，见字如面", {}, {}, {}, {}, bottomClearance = 100.dp,
                        recentHistoryOpen = recent.value, onRecentHistoryDismiss = { recent.value = false })
                    TeaNavigationDockRow(AppTab.TRANSLATE, {}, { recent.value = true },
                        Modifier.align(Alignment.BottomCenter).padding(bottom = 21.dp))
                }
            }
        }
        compose.onNodeWithContentDescription("设置").assertIsDisplayed()
        compose.onNodeWithContentDescription("学习统计").assertIsDisplayed()
        val dock = compose.onNodeWithTag("tea-navigation-dock").getUnclippedBoundsInRoot()
        val recentButton = compose.onNodeWithContentDescription("最近查阅").getUnclippedBoundsInRoot()
        assertTrue(recentButton.left >= dock.right)
        assertEquals(dock.top, recentButton.top)
        compose.onNodeWithContentDescription("最近查阅").performClick()
        compose.onNodeWithTag("tea-sheet-header").performTouchInput { swipeUp(durationMillis = 100) }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("word30"))
        compose.onNodeWithText("word30").assertIsDisplayed()
    }

    @Test fun deletingNotebookEntryMovesRemainingRowsAndPreservesActions() {
        var opened: String? = null
        compose.setContent {
            val words = remember { mutableStateOf(history.take(3).map { VocabularyItem(it.id, it.word, null, it.translation, it.timestamp) }) }
            TeaWordsTheme {
                RecordsView(words.value, 0, { opened = it }, { removed -> words.value = words.value.filter { it.word != removed } })
            }
        }
        compose.onNodeWithText("生词本 · 3").performClick()
        val before = compose.onNodeWithText("word2").getUnclippedBoundsInRoot().top
        compose.onNodeWithContentDescription("移除 word1").performClick()
        compose.onNodeWithText("word1").assertDoesNotExist()
        assertTrue(compose.onNodeWithText("word2").getUnclippedBoundsInRoot().top < before)
        compose.onNodeWithText("word2").performClick()
        compose.runOnIdle { assertEquals("word2", opened) }
        compose.onNodeWithContentDescription("移除 word2").performClick()
        compose.onNodeWithContentDescription("移除 word3").performClick()
        compose.onNodeWithText("给新词留个位置").assertIsDisplayed()
    }

    @Test fun profileSheetKeepsStatisticsActionAfterExpanding() {
        var closed = 0
        var statistics = 0
        compose.setContent {
            TeaWordsTheme { TeaKnowledgeProfileSheet(1, 2, 3, 4, 10, { closed++ }, { statistics++ }) }
        }
        compose.onNodeWithText("词汇知识画像").assertIsDisplayed()
        compose.onNodeWithTag("tea-sheet-header").performTouchInput { swipeUp(durationMillis = 100) }
        compose.onNodeWithText("查看统计").assertIsDisplayed()
        compose.onNodeWithText("查看统计").performClick()
        compose.runOnIdle { assertEquals(1, closed); assertEquals(1, statistics) }
    }
}
