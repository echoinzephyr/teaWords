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
            TeaWordsTheme { key(generation.intValue) { LearningScreen(database, exercisePhraseLookup = { emptyList() }) } }
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
                compose.onAllNodes(hasTestTag("word-round-content")).fetchSemanticsNodes().isNotEmpty()
            }
        }
        open()
        capture("study-layout-focus.png")
        compose.onNodeWithContentDescription("更多").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("暂停，稍后继续").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("暂停，稍后继续").performClick()
        compose.runOnIdle { generation.intValue++ }
        open()
        compose.onNodeWithText("原轮次释义").assertDoesNotExist()
        compose.onNodeWithText("0 / 3").assertIsDisplayed()
        compose.onNodeWithTag("word-choice-0").assertDoesNotExist()
        compose.onNodeWithText("不认识").performClick()
        compose.onNodeWithText("原轮次释义").assertIsDisplayed()
        capture("study-introduction.png")
        compose.onNodeWithContentDescription("本词已完成 0 / 3 项练习").assertExists()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("暂停，稍后继续").performClick()
        compose.runOnIdle { generation.intValue++ }
        open()
        compose.onNodeWithText("原轮次释义").assertIsDisplayed()
        compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("word-intro-continue") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("word-intro-continue").performClick()
        compose.onNodeWithContentDescription("本词已完成 1 / 3 项练习").assertExists()
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        assertEquals(0, WordLearningSessionRepository(database).load()!!.position)
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("暂停，稍后继续").performClick()
        compose.runOnIdle { generation.intValue++ }
        open()
        compose.onNodeWithContentDescription("本词已完成 1 / 3 项练习").assertExists()
        capture("study-consolidation.png")
        compose.onNodeWithText("apple").performClick()
        compose.onNodeWithText("下一题").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("spelling-input").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("2 / 3").assertIsDisplayed()
        compose.onNodeWithTag("spelling-input").performTextInput("apple")
        capture("study-spelling.png")
        compose.onNodeWithContentDescription("检查拼写").performClick()
        compose.onNodeWithContentDescription("本词已完成 3 / 3 项练习").assertExists()
        compose.onNodeWithText("3 / 3").assertIsDisplayed()
        compose.onNodeWithText("下一个单词").performClick()
        compose.waitUntil(10000) { WordLearningSessionRepository(database).load()?.complete == true }
        assertEquals(session.id, WordLearningSessionRepository(database).load()!!.id)
        assertEquals(1, LearningRepository(database).totals().tests)
        compose.onNodeWithText("本轮完成").assertIsDisplayed()
        compose.onNodeWithText("独立回忆正确 1 个").assertDoesNotExist()
        assertEquals(1, WordLearningSessionRepository(database).load()!!.practiced)
    }
    @Test fun newRoundStillStartsAfterPreviousDailyAllowanceWasUsed() {
        name = "round-cap-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        val repository = LearningRepository(database)
        repository.importWords(listOf(ImportedWord("roundfresh", "新一轮单词")), "test", "test")
        repository.save(Knowledge("roundfresh", SelfReport.UNKNOWN), "manual")
        repeat(25) { repository.recordEvent("previous$it", "test", true, 1000) }
        assertEquals(25, repository.newWordsSince(0))
        val preferences = AppPreferences(compose.activity)
        val previous = preferences.dailyNewCapOverride
        preferences.dailyNewCapOverride = 1
        try {
            compose.setContent { TeaWordsTheme { LearningScreen(database) } }
            compose.waitUntil(15_000) {
                compose.onAllNodes(hasTestTag("study-learn-entry") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("study-learn-entry").performScrollTo().performClick()
            compose.waitUntil(5_000) { WordLearningSessionRepository(database).load() != null }
            assertEquals(listOf("roundfresh"), WordLearningSessionRepository(database).load()!!.tasks.map { it.word })
        } finally { preferences.dailyNewCapOverride = previous }
    }

    @Test fun changingWordsKeepsWallpaperFixedThroughEveryTransitionFrame() {
        name = "word-background-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        database.ensureSchema()
        LearningRepository(database).importWords(listOf(ImportedWord("apple", "苹果"), ImportedWord("book", "书")), "test", "test")
        val sessions = WordLearningSessionRepository(database)
        sessions.start(listOf(WordLearningTask("apple", "苹果"), WordLearningTask("book", "书")), threeStep = true)
        val wallpaper = File(compose.activity.cacheDir, "transition-wallpaper.png")
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.rgb(35, 160, 80))
        wallpaper.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val preferences = compose.activity.getSharedPreferences("learning", 0)
        val previous = preferences.getBoolean("word_card_wallpaper_v2", false)
        preferences.edit().putBoolean("word_card_wallpaper_v2", true).commit()
        try {
            compose.setContent { TeaWordsTheme { LearningScreen(database, studyWallpaper = wallpaper.absolutePath, exercisePhraseLookup = { emptyList() }) } }
            compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("study-learn-entry") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("study-learn-entry").performScrollTo().performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasText("认识") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("认识").performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("word-intro-continue") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            val before = compose.onRoot().captureToImage().asAndroidBitmap()
            val x = 2; val y = before.height / 2
            val expected = before.getPixel(x, y)
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("word-intro-continue").performClick()
            compose.waitUntil(5000) { sessions.load()?.position == 1 }
            repeat(18) {
                compose.mainClock.advanceTimeByFrame()
                val frame = compose.onRoot().captureToImage().asAndroidBitmap()
                if (frame.getPixel(x, y) != expected) {
                    File(compose.activity.getExternalFilesDir(null), "transition-before.png").outputStream().use { before.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    File(compose.activity.getExternalFilesDir(null), "transition-frame.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                assertEquals("Wallpaper must not be faded with outgoing/incoming word cards", expected, frame.getPixel(x, y))
                compose.onNodeWithTag("word-choice-0").assertDoesNotExist()
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.onNodeWithText("book").assertIsDisplayed()
            compose.onNodeWithText("认识").assertIsDisplayed()
            assertEquals(0, sessions.load()!!.completedWords)
        } finally {
            compose.mainClock.autoAdvance = true
            preferences.edit().putBoolean("word_card_wallpaper_v2", previous).commit()
            wallpaper.delete()
        }
    }

    @Test fun choicesRevealAllMatchesAndWaitForConfirmationForBothVerdicts() {
        name = "choice-review-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        database.ensureSchema()
        val bank = listOf(WordLearningTask("stageapple", "苹果"), WordLearningTask("stagebook", "书"))
        LearningRepository(database).importWords(bank.map { ImportedWord(it.word, it.definition) }, "test", "test")
        val sessions = WordLearningSessionRepository(database)
        sessions.start(bank, threeStep = true)
        val generation = mutableIntStateOf(0)
        compose.setContent { TeaWordsTheme { key(generation.intValue) { LearningScreen(database, exercisePhraseLookup = { emptyList() }) } } }
        fun open() {
            compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("study-learn-entry") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("study-learn-entry").performScrollTo().performClick()
        }
        open()
        repeat(2) {
            compose.waitUntil(5000) { compose.onAllNodes(hasText("认识") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("认识").performClick()
            compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("word-intro-continue") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("word-intro-continue").performClick()
        }
        val question = WordExercisePlanner.build(bank[0], 1, bank)
        compose.waitUntil(5000) { compose.onAllNodes(hasTestTag("word-choice-0") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(question.choices.first { it != question.answer }).performClick()
        compose.onNodeWithText("下一题").assertIsDisplayed()
        compose.onNodeWithText("2 / 6").assertIsDisplayed()
        assertEquals(0, sessions.load()!!.position)
        question.choices.forEachIndexed { index, choice ->
            compose.onNodeWithTag("word-choice-$index").assertExists().assertIsNotEnabled()
            assertTrue(compose.onAllNodesWithText(question.choiceMatches.getValue(choice)).fetchSemanticsNodes().isNotEmpty())
        }
        compose.onNodeWithContentDescription("正确答案").assertExists()
        File(compose.activity.getExternalFilesDir(null), "study-choice-review.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("下一题").performClick()
        compose.waitUntil(5000) { compose.onAllNodes(hasText("stagebook") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("stagebook").performClick()
        compose.onNodeWithText("3 / 6").assertIsDisplayed()
        compose.onNodeWithText("下一题").assertIsDisplayed()
        assertEquals(1, sessions.load()!!.position)
        assertEquals(1, sessions.load()!!.phase)
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("暂停，稍后继续").performClick()
        compose.runOnIdle { generation.intValue++ }
        open()
        compose.onNodeWithText("下一题").assertIsDisplayed()
        compose.onNodeWithText("3 / 6").assertIsDisplayed()
        compose.onNodeWithText("下一题").performClick()
        compose.waitUntil(5000) { sessions.load()?.position == 0 }
        assertEquals(1, sessions.load()!!.phase)
    }

    @Test fun revealingAutomaticallyLoadsOnlineDetailsAndTopContinueResponds() {
        name = "automatic-details-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        var correct: Boolean? = null
        val calls = java.util.concurrent.atomic.AtomicInteger()
        compose.setContent {
            TeaWordsTheme {
                ImmersiveWordScreen(database, "autodetailword", "本轮释义", 1, 10, false, null, onPause = {},
                    onlineLookup = { calls.incrementAndGet(); StudyDictionaryEntry("在线补充", "test", "test", listOf("phrase" to "短语"), emptyList()) }
                ) { value, _ -> correct = value }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("认识，查看释义") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, calls.get())
        compose.onNodeWithContentDescription("认识，查看释义").performClick()
        compose.waitUntil(5_000) { calls.get() == 1 }
        compose.onNodeWithTag("immersive-word-content").performScrollToNode(hasText("在线补充"))
        compose.onNodeWithText("在线补充").assertIsDisplayed()
        compose.onNodeWithTag("immersive-word-content").performScrollToNode(hasContentDescription("继续下一个单词"))
        compose.onNodeWithContentDescription("继续下一个单词").performClick()
        assertEquals(true, correct)
    }
    @Test fun swipingLeftOpensFocusedSpellingWithoutTheLegacyInlineView() {
        name = "swipe-spelling-${System.nanoTime()}.db"
        val database = DatabaseHelper(compose.activity, name)
        helper = database
        compose.setContent {
            TeaWordsTheme {
                ImmersiveWordScreen(database, "access", "入口", 1, 10, false, null, onPause = {}) { _, _ -> }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasContentDescription("拼写单词") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("immersive-word-content").performTouchInput { swipeLeft(startX = width * .8f, endX = width * .2f) }
        compose.onNodeWithTag("spelling-input").assertIsDisplayed()
        compose.onNodeWithText("拼写自测").assertDoesNotExist()
        compose.onNodeWithText("英文单词").assertDoesNotExist()
        val screenshot = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir(null), "spelling-rc2.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithContentDescription("返回词卡").performClick()
        compose.onNodeWithText("access").assertIsDisplayed()
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
        compose.onNodeWithTag("immersive-word-content").performScrollToNode(hasContentDescription("拼写单词"))
        compose.onNodeWithContentDescription("拼写单词").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("access")
        compose.onNodeWithContentDescription("检查拼写").performClick()
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
        compose.onNodeWithContentDescription("拼写单词").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("access")
        compose.onNodeWithContentDescription("检查拼写").performClick()
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
        compose.onNodeWithContentDescription("拼写单词").performClick()
        compose.onNodeWithContentDescription("返回词卡").performClick()
        compose.onNodeWithText("下一个").performClick()
        compose.runOnIdle { assertEquals(1, recognitionCalls); assertEquals(0, recallCalls) }
    }
}
