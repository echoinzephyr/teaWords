package com.teameow.teawords.ui.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.teameow.teawords.data.*
import com.teameow.teawords.ui.StatsDashboard
import com.teameow.teawords.ui.theme.TeaWordsTheme
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MaterialRefreshTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var helper: DatabaseHelper? = null
    private val databaseName = "material-refresh-${System.nanoTime()}.db"
    @After fun cleanup() { helper?.close(); compose.activity.deleteDatabase(databaseName) }

    private fun capture(name: String) {
        val dir = File(compose.activity.getExternalFilesDir(null), "material-refresh").apply { mkdirs() }
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun groupedOptionsRespectDisabledAndSelectedStates() {
        var calls = 0
        compose.setContent {
            TeaWordsTheme {
                TeaSegmentedGroup {
                    TeaOptionRow("已选择", selected = true, onClick = { calls++ })
                    TeaOptionRow("不可用", enabled = false, onClick = { calls++ })
                }
            }
        }
        compose.onNodeWithText("已选择").assertIsSelected().performClick()
        compose.onNodeWithText("不可用").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun largeTitleCollapsesAndLastItemClearsDock() {
        compose.setContent {
            TeaWordsTheme {
                CompositionLocalProvider(LocalReservedBottom provides 100.dp) {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        TeaListPage("页面标题", subtitle = "页面说明") {
                            items(30) { Text("项目 $it", Modifier.height(60.dp)) }
                        }
                    }
                }
            }
        }
        val before = compose.onNodeWithText("页面标题").getUnclippedBoundsInRoot().top
        compose.onNode(hasScrollToNodeAction()).performTouchInput { swipeUp() }
        assertTrue(compose.onNodeWithText("页面标题").getUnclippedBoundsInRoot().top < before)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("项目 29"))
        compose.onNode(hasScrollToNodeAction()).performTouchInput { swipeUp() }
        compose.onNodeWithText("项目 29").assertIsDisplayed()
        assertTrue("List bottom: ${compose.onNodeWithText("项目 29").getUnclippedBoundsInRoot().bottom}", compose.onNodeWithText("项目 29").getUnclippedBoundsInRoot().bottom <= 540.dp)
    }

    @Test fun fixedHeaderAllowsTouchScrollingToLastItem() {
        compose.setContent {
            TeaWordsTheme {
                Box(Modifier.width(320.dp).height(600.dp)) {
                    TeaListPage("固定标题", expandedTitle = false) {
                        items(20) { Text("固定列表 $it", Modifier.height(60.dp)) }
                    }
                }
            }
        }
        val list = compose.onNode(hasScrollToNodeAction())
        val before = compose.onNodeWithText("固定列表 0").getUnclippedBoundsInRoot().top
        list.performTouchInput { swipeUp() }
        assertTrue(compose.onAllNodesWithText("固定列表 0").fetchSemanticsNodes().isEmpty() ||
            compose.onNodeWithText("固定列表 0").getUnclippedBoundsInRoot().top < before)
        repeat(5) { list.performTouchInput { swipeUp() } }
        compose.onNodeWithText("固定列表 19").assertIsDisplayed()
        compose.onNodeWithText("固定标题").assertIsDisplayed()
    }

    @Test fun customSearchHeaderAllowsTouchScrolling() {
        compose.setContent {
            TeaWordsTheme {
                Box(Modifier.width(320.dp).height(600.dp)) {
                    TeaListPage("", header = { Text("搜索框", Modifier.height(56.dp)) }) {
                        items(20) { Text("查询结果 $it", Modifier.height(60.dp)) }
                    }
                }
            }
        }
        repeat(6) { compose.onNode(hasScrollToNodeAction()).performTouchInput { swipeUp() } }
        compose.onNodeWithText("查询结果 19").assertIsDisplayed()
        compose.onNodeWithText("搜索框").assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun statisticsInsideSheetScrollsToBottomWithTouch() {
        val database = DatabaseHelper(compose.activity, databaseName).also { helper = it; it.ensureSchema() }
        compose.setContent {
            TeaWordsTheme {
                ModalBottomSheet(onDismissRequest = {},
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                    Box(Modifier.fillMaxWidth().height(600.dp)) { StatsDashboard(database) }
                }
            }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("还没有学习记录，完成一轮学习后查看你的积累。").fetchSemanticsNodes().isNotEmpty()
        }
        val list = compose.onNode(hasScrollToNodeAction() and androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
        repeat(8) { list.performTouchInput { swipeUp() } }
        compose.onNodeWithText("词汇明细").assertIsDisplayed()
        compose.onNodeWithText("暂无词汇").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭统计").assertIsDisplayed()
        val folder = File(compose.activity.getExternalFilesDir(null), "material-refresh").apply { mkdirs() }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(folder, "statistics-scrolled-bottom.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun studyBookPickerSearchFiltersAndKeepsMultiSelection() {
        val database = DatabaseHelper(compose.activity, databaseName).also { helper = it; it.ensureSchema() }
        com.teameow.teawords.data.search.DictionaryGateway.open(compose.activity)
        val dictionary = com.teameow.teawords.data.search.DictionaryGateway.sourceOrNull()!!
        val books = dictionary.books()
        assertEquals(dictionary.learningWords(setOf("cet4", "cet6")).size, dictionary.countBookWords(setOf("cet4", "cet6")))
        assertEquals(0, dictionary.countBookWords(emptySet()))
        var selected by mutableStateOf(emptySet<String>())
        var applied = emptySet<String>()
        compose.setContent {
            TeaWordsTheme {
                StudyBookPicker(database, books, selected, { selected = it }, {}, { applied = selected })
            }
        }
        compose.onNode(hasText("大学") and hasClickAction()).performClick()
        val cet4 = books.first { it.id == "cet4" }
        compose.onNodeWithText(cet4.name).performScrollTo().performClick().assertIsOn()
        compose.onNode(hasText("全部") and hasClickAction()).performClick()
        val toefl = books.first { it.id == "toefl" }
        compose.onNode(hasSetTextAction()).performTextInput("托福")
        compose.onNodeWithText(cet4.name).assertDoesNotExist()
        compose.onNodeWithText(toefl.name).performClick().assertIsOn()
        compose.onNodeWithText("应用选择 · 2 本").assertIsDisplayed()
        val folder = File(compose.activity.getExternalFilesDir(null), "material-refresh").apply { mkdirs() }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
            File(folder, "study-book-picker.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("应用选择 · 2 本").performClick()
        compose.runOnIdle { assertEquals(setOf(cet4.id, toefl.id), applied) }
    }

    @Test fun studyOverviewShortcutsUseRealCountsAndCallbacks() {
        var favoriteCalls = 0
        var query by mutableStateOf("")
        var submitted = ""
        compose.setContent {
            TeaWordsTheme {
                StudyOverview("大学英语四级", 100, 20, 3, 7, 10, 2, false, null,
                    {}, {}, {}, {}, favorites = 5, onFavorites = { favoriteCalls++ },
                    query = query, onQuery = { query = it }, onSearch = { submitted = query })
            }
        }
        compose.onNodeWithTag("study-favorites-entry").performClick()
        compose.runOnIdle { assertEquals(1, favoriteCalls) }
        compose.onNodeWithTag("study-search").performTextInput("apple")
        compose.onNodeWithTag("study-search").performImeAction()
        compose.runOnIdle { assertEquals("apple", submitted) }
    }

    @Test fun learningFavoritesOpenWordAndReturnToCollection() {
        val database = DatabaseHelper(compose.activity, databaseName).also { helper = it; it.ensureSchema() }
        database.addVocabulary("apple", "", "苹果")
        compose.setContent {
            androidx.compose.animation.SharedTransitionLayout {
                CompositionLocalProvider(LocalTeaSearchScope provides this) {
                    TeaWordsTheme { LearningScreen(database) }
                }
            }
        }
        compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("study-favorites-entry") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("study-favorites-entry").performClick()
        compose.onNodeWithText("apple").performClick()
        compose.onNodeWithTag("lookup-search-input").assertIsDisplayed().assertTextContains("apple")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("生词本").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("study-learn-entry").assertIsDisplayed()
    }

    @Test fun profileFooterStaysVisibleWithLargeFontAndEmptyData() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                TeaWordsTheme(darkTheme = true) {
                    TeaKnowledgeProfileSheet(0, 0, 0, 0, 0, {}, {})
                }
            }
        }
        compose.onNodeWithText("查看统计").assertIsDisplayed()
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(compose.activity.getExternalFilesDir(null), "material-refresh").apply { mkdirs() }
        File(dir, "profile-sheet-large-empty.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun captureRealDeviceThemeAndPageGallery() {
        val database = DatabaseHelper(compose.activity, databaseName).also { helper = it; it.ensureSchema() }
        LearningRepository(database).importWords(listOf(ImportedWord("apple", "苹果")), "test", "test")
        var page by mutableStateOf("home")
        var appearance by mutableStateOf("light")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,
                if (appearance == "large") 1.5f else 1f)) {
                TeaWordsTheme(darkTheme = appearance == "dark" || appearance == "black",
                    pureBlack = appearance == "black", dynamicColor = appearance == "dynamic") {
                    Surface {
                        key(page, appearance) {
                            when (page) {
                                "home" -> HomeView("", {}, {}, emptyList(), false, {}, emptyList(),
                                    "茶词", "查词、翻译，慢慢记住。", {}, {}, {}, {})
                                "learning" -> LearningScreen(database)
                                "recall" -> RecordsView(emptyList(), 0, {}, {})
                                "lookup" -> LookupScreen("apple", onBack = {})
                                "statistics" -> StatsDashboard(database)
                                "about" -> TeaListPage("关于", onBack = {}) {
                                    item { AboutIdentity() }; item { AboutCredits() }
                                }
                                "translation" -> TranslationSettingsPage({})
                                "word" -> ImmersiveWordScreen(database, "apple", "苹果；苹果树", 1, 10,
                                    false, null, onPause = {}, onComplete = { _, _ -> })
                            }
                        }
                    }
                }
            }
        }
        for (theme in listOf("light", "dark", "black", "dynamic", "large")) {
            for (screen in listOf("home", "learning", "recall", "lookup", "statistics", "about", "translation", "word")) {
                compose.runOnIdle { appearance = theme; page = screen }
                compose.waitForIdle()
                if (screen == "learning") compose.waitUntil(15000) {
                    compose.onAllNodes(hasTestTag("study-learn-entry") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                }
                if (screen == "word") {
                    compose.waitUntil(15000) { compose.onAllNodes(hasText("认识") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                    capture("$theme-word-hidden")
                    compose.onNodeWithText("认识").performClick()
                    compose.onNodeWithText("苹果；苹果树").assertExists()
                    capture("$theme-word-revealed")
                } else capture("$theme-$screen")
            }
        }
    }
}
