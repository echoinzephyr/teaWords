package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.teameow.teawords.data.DatabaseHelper
import com.teameow.teawords.data.PocketLearningRepository
import com.teameow.teawords.data.WordLearningSessionRepository
import com.teameow.teawords.ui.theme.TeaWordsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LearningPreparationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun pageRepositoriesDoNotOpenStorageOnTheUiThread() {
        val name = "deferred-page-storage-${System.nanoTime()}.db"
        val helper = DatabaseHelper(compose.activity, name)
        lateinit var words: WordLearningSessionRepository
        lateinit var practice: PocketLearningRepository
        lateinit var details: StudyWordDetailsRepository
        try {
            compose.runOnUiThread {
                words = WordLearningSessionRepository(helper)
                practice = PocketLearningRepository(helper)
                details = StudyWordDetailsRepository(helper)
            }
            assertFalse("Composing a page must not open SQLite", compose.activity.getDatabasePath(name).exists())
            runBlocking(Dispatchers.IO) {
                assertNull(words.load())
                assertNull(practice.load())
                assertEquals("", details.load("unlistedword").note)
            }
            assertTrue(compose.activity.getDatabasePath(name).exists())
        } finally { helper.close(); compose.activity.deleteDatabase(name) }
    }

    @Test fun preparedWordDetailsAreAvailableWhenTheCardAppears() {
        val name = "prepared-word-${System.nanoTime()}.db"
        val helper = DatabaseHelper(compose.activity, name)
        try {
            compose.setContent {
                TeaWordsTheme {
                    ImmersiveWordScreen(helper, "preparedword", "准备好的词义", 1, 10, false, null,
                        onPause = {}, initialDetails = StudyWordDetails(phonetic = "prepared-ipa")) { _, _ -> }
                }
            }
            compose.onNodeWithText("/prepared-ipa/").assertIsDisplayed()
            compose.onNodeWithContentDescription("拼写单词").assertIsEnabled()
            compose.onNodeWithText("认识").assertIsEnabled()
        } finally { helper.close(); compose.activity.deleteDatabase(name) }
    }

    @Test fun recallAndLearningUseTheSameTopLevelHeaderHeight() {
        val name = "shared-page-header-${System.nanoTime()}.db"
        val helper = DatabaseHelper(compose.activity, name)
        var review by mutableStateOf(true)
        try {
            compose.setContent {
                TeaWordsTheme {
                    if (review) RecordsView(emptyList(), 0, {}, {}) else LearningScreen(helper)
                }
            }
            val before = compose.onNodeWithTag("top-level-header-回望").getUnclippedBoundsInRoot()
            compose.runOnIdle { review = false }
            val after = compose.onNodeWithTag("top-level-header-学习").getUnclippedBoundsInRoot()
            assertEquals(before, after)
        } finally { helper.close(); compose.activity.deleteDatabase(name) }
    }
}
