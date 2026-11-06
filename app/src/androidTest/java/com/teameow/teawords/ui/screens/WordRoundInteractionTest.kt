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
}
