package com.teameow.teawords.ui.screens

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.teameow.teawords.ui.theme.TeaWordsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class TeaModalBottomSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var dispatcher: OnBackPressedDispatcher
    private var dismissed = 0

    private fun mount(predictive: Boolean = true) {
        compose.setContent {
            TeaWordsTheme {
                CompositionLocalProvider(LocalPredictiveBackEnabled provides predictive) {
                    var visible by remember { mutableStateOf(true) }
                    Text("学习首页", Modifier.testTag("source"))
                    if (visible) TeaModalBottomSheet(
                        onDismissRequest = { dismissed++; visible = false },
                        modifier = Modifier.testTag("sheet"),
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ) {
                        dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
                        Column(Modifier.fillMaxWidth().height(480.dp).testTag("sheet-content")) { Text("选择词书") }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun preview(edge: Int) {
        compose.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 200f, 0f, edge)) }
        compose.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 200f, .6f, edge)) }
        compose.waitForIdle()
    }

    private fun verifyPreviewAndCancel(edge: Int) {
        mount()
        val before = compose.onNodeWithTag("sheet-content").fetchSemanticsNode().boundsInRoot
        val source = compose.onNodeWithTag("source").fetchSemanticsNode().boundsInRoot
        preview(edge)
        val moving = compose.onNodeWithTag("sheet-content").fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, moving.left, 1f)
        assertEquals(before.width, moving.width, 1f)
        assertTrue("The whole top edge must move down", moving.top > before.top + 20f)
        assertEquals(source, compose.onNodeWithTag("source").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithTag("sheet-content").fetchSemanticsNode().boundsInRoot)
        assertEquals(0, dismissed)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithTag("sheet").assertDoesNotExist()
        assertEquals(1, dismissed)
    }

    @Test fun leftEdgeMovesDownWithoutScalingAndCancellationRestores() = verifyPreviewAndCancel(BackEventCompat.EDGE_LEFT)
    @Test fun rightEdgeMovesDownWithoutScalingAndCancellationRestores() = verifyPreviewAndCancel(BackEventCompat.EDGE_RIGHT)

    @Test fun commitContinuesDownAndDismissesOnce() {
        mount()
        preview(BackEventCompat.EDGE_RIGHT)
        val before = compose.onNodeWithTag("sheet-content").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(80)
        val moving = compose.onNodeWithTag("sheet-content").fetchSemanticsNode().boundsInRoot
        assertTrue(moving.top > before.top)
        assertEquals(before.width, moving.width, 1f)
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, dismissed)
        compose.onNodeWithTag("sheet").assertDoesNotExist()
    }

    @Test fun verticalDragStillDismisses() {
        mount(predictive = false)
        compose.onNodeWithTag("sheet").performTouchInput { swipeDown(startY = 30f, endY = height * .9f, durationMillis = 350) }
        compose.waitForIdle()
        compose.onNodeWithTag("sheet").assertDoesNotExist()
        assertEquals(1, dismissed)
    }
}
