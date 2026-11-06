package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import com.teameow.teawords.data.DatabaseHelper
import com.teameow.teawords.ui.theme.TeaWordsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UnifiedLearningPagesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun capture(name: String) {
        compose.waitForIdle()
        val folder = java.io.File(compose.activity.getExternalFilesDir(null), "unified-pages").apply { mkdirs() }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun personalLibrarySupportsLargeTextImportAndAnimatedSuccessClose() {
        var revision by mutableIntStateOf(0)
        var visible by mutableStateOf(true)
        var imported = ""
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                TeaWordsTheme(darkTheme = true) {
                    var text by remember { mutableStateOf("") }
                    if (visible) PersonalWordbookSheet(text, { text = it }, false, null, revision,
                        onDismiss = { visible = false }, onBooks = {}, onFile = {}, onImport = { imported = text; revision++ })
                }
            }
        }
        compose.onNodeWithText("导入词库").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("apple,苹果")
        compose.onNodeWithText("导入词库").assertIsDisplayed().assertIsEnabled()
        capture("personal-library")
        compose.onNodeWithText("导入词库").performClick()
        compose.waitForIdle()
        assertEquals("apple,苹果", imported)
        compose.onNodeWithText("个人词库").assertDoesNotExist()
    }
    @Test fun personalLibraryCanSwitchToBooksAndBlocksActionsWhileImporting() {
        var busy by mutableStateOf(true)
        var selected = false
        compose.setContent {
            TeaWordsTheme {
                PersonalWordbookSheet("", {}, busy, null, 0, {}, { selected = true }, {}, {})
            }
        }
        compose.onNodeWithContentDescription("关闭个人词库").assertIsNotEnabled()
        compose.onNodeWithText("从文件导入").assertIsNotEnabled()
        compose.runOnIdle { busy = false }
        compose.onNodeWithText("选择内置词书").performClick()
        compose.waitForIdle()
        assertTrue(selected)
    }
    @Test fun diagnosticEmptyStateAndEndActionRemainAccessible() {
        val name = "diagnostic-ui-${System.nanoTime()}.db"
        val helper = DatabaseHelper(compose.activity, name)
        var finished = false
        try {
            compose.setContent { TeaWordsTheme { DiagnosticScreen(helper, learningWords = emptyList(), onBack = {}, onFinished = { finished = true }) } }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("没有可用的诊断题目").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("词汇能力诊断").assertIsDisplayed()
            capture("diagnostic")
            compose.onNodeWithContentDescription("结束诊断").performClick()
            compose.onNodeWithText("去学习").performScrollTo().performClick()
            assertTrue(finished)
        } finally { helper.close(); compose.activity.deleteDatabase(name) }
    }
    @Test fun practiceHubUsesOneHeadingAndKeepsRoundSetupEntry() {
        val name = "practice-hub-ui-${System.nanoTime()}.db"
        val helper = DatabaseHelper(compose.activity, name)
        try {
            compose.setContent { TeaWordsTheme { LearningScreen(helper) } }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("专项练习").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("专项练习").performScrollTo().performClick()
            compose.onAllNodesWithText("专项练习").assertCountEquals(1)
            compose.onNodeWithText("打字记词").assertIsDisplayed()
            capture("practice-hub")
            compose.onNodeWithText("混合小练习").performClick()
            compose.onNodeWithText("准备小练习").assertIsDisplayed()
        } finally { helper.close(); compose.activity.deleteDatabase(name) }
    }
    @Test fun diagnosticQuestionStillPersistsAnswerAndShowsResult() {
        val name = "diagnostic-answer-ui-${System.nanoTime()}.db"
        val helper = DatabaseHelper(compose.activity, name)
        val store = AbilityStore(compose.activity)
        val original = store.load()
        try {
            com.teameow.teawords.data.LocalDictionary(helper).upsert("azure", "蔚蓝", source = "test")
            compose.setContent { TeaWordsTheme { DiagnosticScreen(helper, learningWords = listOf("azure"), onBack = {}) } }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("azure").fetchSemanticsNodes().isNotEmpty() }
            capture("diagnostic-question")
            compose.onNodeWithText("认识这个词").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("诊断结束").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1, com.teameow.teawords.data.SenseRepository(helper).evidenceByMode()[com.teameow.teawords.data.SenseRepository.MODE_DIAGNOSTIC])
        } finally { store.save(original); helper.close(); compose.activity.deleteDatabase(name) }
    }
    @Test fun statisticsCloseStaysInsideSmallScreenAndPinnedWhileScrolling() {
        val name = "stats-header-ui-${System.nanoTime()}.db"
        val helper = DatabaseHelper(compose.activity, name)
        var closed = false
        try {
            compose.setContent {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    TeaWordsTheme(darkTheme = true) {
                        Box(Modifier.width(320.dp).height(640.dp)) {
                            com.teameow.teawords.ui.StatsDashboard(helper, onBack = { closed = true })
                        }
                    }
                }
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("已验证词汇").fetchSemanticsNodes().isNotEmpty() }
            val close = compose.onNodeWithContentDescription("关闭统计")
            close.assertIsDisplayed()
            val before = close.getUnclippedBoundsInRoot()
            assertTrue(before.right <= 304.dp)
            compose.onNodeWithText("7 天").performScrollTo().performClick()
            compose.onNode(hasScrollToNodeAction() and SemanticsMatcher.keyIsDefined(
                androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)).performScrollToNode(hasText("词汇明细"))
            assertEquals(before, close.getUnclippedBoundsInRoot())
            close.performClick()
            assertTrue(closed)
            capture("statistics")
        } finally { helper.close(); compose.activity.deleteDatabase(name) }
    }
}
