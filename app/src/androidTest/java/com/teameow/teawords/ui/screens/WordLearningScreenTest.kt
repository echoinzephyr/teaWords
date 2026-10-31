package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.teameow.teawords.data.*
import com.teameow.teawords.ui.theme.TeaWordsTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WordLearningScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var helper: DatabaseHelper? = null
    private var name = ""

    @After fun clean() {
        helper?.close()
        if (name.isNotEmpty()) compose.activity.deleteDatabase(name)
    }

    @Test fun recallShowsDefinitionAndDistinguishesPracticeFromIndependentRecall() {
        var saved: Triple<Boolean, Long, Boolean>? = null
        compose.setContent {
            TeaWordsTheme {
                TeaListPage("回忆测试") { item { RecallCard(Knowledge("apple"), "苹果", 1, 1, false, "") { correct, elapsed, revealed ->
                    saved = Triple(correct, elapsed, revealed)
                } } }
            }
        }
        compose.onNodeWithText("记住了，试试回忆").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("apple")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("检查回答"))
        compose.onNodeWithText("检查回答").performClick()
        compose.onNodeWithText("苹果").assertIsDisplayed()
        compose.onNodeWithText("本次看过答案，作为练习记录，不计作独立回忆通过。").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("保存并继续"))
        compose.onNodeWithText("保存并继续").performClick()
        compose.runOnIdle {
            assertTrue(saved!!.first)
            assertTrue(saved!!.third)
        }
    }

    @Test fun pausedPlanResumesAfterLearningScreenIsRecreated() {
        name = "word-screen-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        database.ensureSchema()
        LearningRepository(database).importWords(listOf(ImportedWord("apple", "苹果")), "test", "test")
        val session = WordLearningSessionRepository(database).start(listOf(WordLearningTask("apple", "原轮次释义")))
        val generation = mutableIntStateOf(0)
        compose.setContent {
            TeaWordsTheme { key(generation.intValue) { LearningScreen(database) } }
        }
        fun capture(name: String) {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        fun open() {
            compose.waitUntil(15000) {
                compose.onAllNodes(hasTestTag("study-learn-entry") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("study-learn-entry"))
            capture("study-layout-home.png")
            compose.onNodeWithTag("study-learn-entry").performClick()
            compose.waitUntil(10000) {
                compose.onAllNodes(hasText("认识") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
        }
        open()
        capture("study-layout-focus.png")
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("暂停，稍后继续").performClick()
        compose.runOnIdle { generation.intValue++ }
        open()
        compose.onNodeWithText("认识").performClick()
        compose.onNodeWithText("原轮次释义").assertExists()
        assertEquals(0, WordLearningSessionRepository(database).load()!!.position)
        compose.onNodeWithText("下一个").performClick()
        compose.waitUntil(10000) { WordLearningSessionRepository(database).load()?.complete == true }
        assertEquals(session.id, WordLearningSessionRepository(database).load()!!.id)
        assertEquals(1, LearningRepository(database).totals().tests)
        compose.onNodeWithText("本轮完成").assertIsDisplayed()
        compose.onNodeWithText("辅助练习正确 1 个，不计入独立回忆正确。").assertExists()
    }
    @Test fun focusActionsStayVisibleWhenLongPromptScrollsAtLargeFont() {
        name = "immersive-long-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                TeaWordsTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        ImmersiveWordScreen(database, "consideration", "考虑；考量；体谅。".repeat(40),
                            1, 10, false, null, onPause = {}) { _, _ -> }
                    }
                }
            }
        }
        compose.waitUntil(10000) { compose.onAllNodes(hasText("认识") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("认识").assertIsDisplayed().performClick()
        val before = compose.onNodeWithTag("immersive-word-actions").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("immersive-word-content").performTouchInput { swipeUp() }
        val after = compose.onNodeWithTag("immersive-word-actions").getUnclippedBoundsInRoot()
        assertEquals(before, after)
        assertTrue(after.bottom <= 640.dp)
        compose.onNodeWithText("下一个").assertIsDisplayed()
        compose.onNodeWithText("记错了").assertIsDisplayed()
    }

    @Test fun screenshotLayoutRevealsMeaningAndKeepsNotesAndSpellingWorking() {
        name = "immersive-demo-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        database.ensureSchema()
        var completed: Boolean? = null
        com.teameow.teawords.data.search.DictionaryGateway.open(compose.activity)
        compose.setContent {
            TeaWordsTheme {
                ImmersiveWordScreen(database, "access", "n. 入口，通道；获得的机会，使用权；访问，存取", 1, 20,
                    false, null, onPause = {}) { correct, _ -> completed = correct }
            }
        }
        compose.waitUntil(10000) { compose.onAllNodes(hasText("认识") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        fun capture(name: String) {
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            File(compose.activity.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        capture("immersive-word-hidden.png")
        compose.onNodeWithText("认识").performClick()
        compose.onNodeWithText("下一个").assertIsDisplayed()
        assertNull(completed)
        capture("immersive-word-revealed.png")
        compose.onNodeWithContentDescription("加入生词本").performClick()
        compose.waitUntil(5000) { database.isInVocabulary("access") }
        compose.onNodeWithContentDescription("移出生词本").assertExists()
        compose.onNodeWithTag("immersive-word-content").performScrollToNode(hasText("添加笔记"))
        compose.onNodeWithText("添加笔记").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("记住：获得使用权")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5000) { StudyWordDetailsRepository(database).load("access").note == "记住：获得使用权" }
        compose.onNodeWithTag("immersive-word-content").performScrollToNode(hasText("A"))
        compose.onNodeWithText("A").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("access")
        compose.onNodeWithText("检查拼写").performClick()
        compose.onNodeWithText("下一个").performClick()
        compose.runOnIdle { assertEquals(true, completed) }
    }
    @Test fun checkedSpellingUsesRecallCallbackInsteadOfRecognition() {
        name = "word-recall-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        database.ensureSchema()
        var recognitionCalls = 0
        var recall: Boolean? = null
        compose.setContent {
            TeaWordsTheme {
                ImmersiveWordScreen(database, "access", "入口", 1, 10, false, null,
                    onPause = {}, onRecallComplete = { correct, _ -> recall = correct },
                    onComplete = { _, _ -> recognitionCalls++ })
            }
        }
        compose.waitUntil(10000) { compose.onAllNodes(hasText("认识") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("A").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("access")
        compose.onNodeWithText("检查拼写").performClick()
        compose.onNodeWithText("下一个").performClick()
        compose.runOnIdle { assertEquals(true, recall); assertEquals(0, recognitionCalls) }
    }

    @Test fun leavingSpellingWithoutCheckingRemainsRecognition() {
        name = "word-recognition-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        database.ensureSchema()
        var recognitionCalls = 0
        var recallCalls = 0
        compose.setContent {
            TeaWordsTheme {
                ImmersiveWordScreen(database, "access", "入口", 1, 10, false, null,
                    onPause = {}, onRecallComplete = { _, _ -> recallCalls++ },
                    onComplete = { _, _ -> recognitionCalls++ })
            }
        }
        compose.waitUntil(10000) { compose.onAllNodes(hasText("认识") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("认识").performClick()
        compose.onNodeWithText("A").performClick()
        compose.onNodeWithText("返回词卡").performClick()
        compose.onNodeWithText("下一个").performClick()
        compose.runOnIdle { assertEquals(1, recognitionCalls); assertEquals(0, recallCalls) }
    }
}
