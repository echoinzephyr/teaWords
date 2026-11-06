package com.teameow.teawords.ui.screens

import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import com.teameow.teawords.data.*
import com.teameow.teawords.ui.theme.TeaWordsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WordRoundInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @SdkSuppress(minSdkVersion = 30)
    @Test fun extraSpellingDismissalHidesKeyboardAndPullSearchKeepsExerciseProgress() {
        val name = "round-interaction-${System.nanoTime()}.db"
        val helper = DatabaseHelper(compose.activity, name)
        helper.ensureSchema()
        try {
            var helped = 0
            var submitted = 0
            compose.setContent { TeaWordsTheme {
                WordRoundExerciseScreen(helper, WordLearningTask("comprehensive", "全面的；综合的"), WordExerciseProgress(),
                    0, 5, listOf(WordLearningTask("book", "书"), WordLearningTask("go", "去")), false, null,
                    null, StudyWordDetails(phonetic = "kɒmprɪˈhensɪv"), {}, { _, _, _, _, _ -> submitted++; true }, { helped++; true }, {})
            } }
            compose.onNodeWithContentDescription("拼写练习").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("spelling-input").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("spelling-input").performTextInput("comp")
            compose.onNodeWithContentDescription("返回词卡").performClick()
            compose.waitUntil(5000) {
                compose.onAllNodesWithTag("spelling-input").fetchSemanticsNodes().isEmpty() &&
                    compose.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) != true
            }
            compose.onNodeWithContentDescription("本词已完成 0 / 3 项练习").assertExists()
            val before = helped
            compose.onNodeWithTag("word-round-content").performTouchInput { swipeDown() }
            compose.waitUntil(5000) { helped > before }
            assertEquals("Searching or opening spelling practice must not submit a round answer", 0, submitted)
        } finally { helper.close(); compose.activity.deleteDatabase(name) }
    }

    @Test fun longWordFitsNarrowWidthWithLargeSystemText() {
        val word = "comprehensiveness"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                TeaWordsTheme { Column(Modifier.width(260.dp)) { StudyAdaptiveWord(word) } }
            }
        }
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(word).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertFalse(results.single().hasVisualOverflow)
        assertTrue(results.single().lineCount <= 2)
    }

    @Test fun optionalSpellingSavesMemoryButLeavesAllThreeRoundStepsUntouched() {
        val name = "optional-spelling-ui-${System.nanoTime()}.db"
        val helper = DatabaseHelper(compose.activity, name)
        helper.ensureSchema()
        try {
            LearningRepository(helper).importWords(listOf(ImportedWord("apple", "苹果")), "test", "test")
            val sessions = WordLearningSessionRepository(helper)
            val round = sessions.start(listOf(WordLearningTask("apple", "苹果")), threeStep = true)
            compose.setContent { TeaWordsTheme {
                WordRoundExerciseScreen(helper, round.tasks[0], WordExerciseProgress(), 0, 5, round.tasks, false,
                    null, null, StudyWordDetails(), {}, { _, _, _, _, _ -> error("Optional spelling must not submit a round step") }, { true }, {},
                    onExtraSpelling = { token, correct, assisted, elapsed ->
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            SpellingPracticeRepository(helper).record(token, "apple", correct, assisted, elapsed, 10000,
                                com.teameow.teawords.algorithm.AbilityEstimator.initial, .9)
                        }
                        true
                    })
            } }
            compose.onNodeWithContentDescription("拼写练习").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("spelling-input").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("spelling-input").performTextInput("apple")
            compose.onNodeWithContentDescription("检查拼写").performClick()
            compose.waitUntil(5000) { LearningRepository(helper).totals().tests == 1 }
            compose.onNodeWithText("保存并继续").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("spelling-input").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("0 / 15").assertIsDisplayed()
            assertEquals(round, sessions.load())
            val id = SenseRepository(helper).ensurePrimarySense("apple").second
            assertEquals(1, SenseRepository(helper).memoryFor(id)!!.reps)
        } finally { helper.close(); compose.activity.deleteDatabase(name) }
    }
}
