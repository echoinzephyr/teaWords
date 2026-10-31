package com.teameow.teawords.ui.screens

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.teameow.teawords.ui.theme.TeaWordsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TeaTopLevelPagesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var systemBacks = 0
    private val childOpen = mutableStateOf(false)

    private fun mount(tab: AppTab) {
        compose.setContent {
            DisposableEffect(Unit) {
                val fallback = object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() { systemBacks++ }
                }
                compose.activity.onBackPressedDispatcher.addCallback(fallback)
                onDispose { fallback.remove() }
            }
            TeaWordsTheme {
                CompositionLocalProvider(LocalPredictiveBackEnabled provides true) {
                    Box(Modifier.fillMaxSize()) {
                        TeaTopLevelPages(tab, covered = false) { page ->
                            Box(Modifier.fillMaxSize().testTag("root-${page.name}")) { Text(page.title) }
                            if (page == tab && childOpen.value) {
                                PredictiveBackLayer(onBack = { childOpen.value = false }) {
                                    Box(Modifier.fillMaxSize().testTag("child")) { Text("Child") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkPeerRoot(tab: AppTab) {
        mount(tab)
        val before = compose.onNodeWithTag("root-${tab.name}").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(400f, 200f, 0f, BackEventCompat.EDGE_RIGHT))
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 200f, .6f, BackEventCompat.EDGE_RIGHT))
        }
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithTag("root-${tab.name}").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("root-TRANSLATE").assertDoesNotExist()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(1, systemBacks) }
        compose.onNodeWithTag("root-${tab.name}").assertExists()
    }

    @Test fun learningRootDoesNotReturnToTranslation() = checkPeerRoot(AppTab.REVIEW)
    @Test fun recordsRootDoesNotReturnToTranslation() = checkPeerRoot(AppTab.HISTORY)

    private fun checkChildBack(tab: AppTab) {
        childOpen.value = true
        mount(tab)
        compose.onNodeWithTag("child").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("child").assertDoesNotExist()
        compose.onNodeWithTag("root-${tab.name}").assertExists()
        compose.runOnIdle { assertEquals(0, systemBacks) }
    }

    @Test fun learningChildReturnsToLearningRoot() = checkChildBack(AppTab.REVIEW)
    @Test fun recordsChildReturnsToRecordsRoot() = checkChildBack(AppTab.HISTORY)
}
