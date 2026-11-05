package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PredictiveBackLayerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var commits = 0
    private var parentMounts = 0

    private fun mount(predictive: Boolean = true, topLevel: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalPredictiveBackEnabled provides predictive) {
            var visible by remember { mutableStateOf(true) }
            Box(Modifier.fillMaxSize()) {
                DisposableEffect(Unit) { parentMounts++; onDispose { } }
                Text("Source page")
                if (visible) {
                    PredictiveBackLayer(onBack = { commits++; visible = false }, topLevel = topLevel) { back ->
                        Box(Modifier.fillMaxSize().testTag("page")) {
                            Button(onClick = back, modifier = Modifier.testTag("back")) { Text("Back") }
                        }
                    }
                }
            }
            }
        }
        compose.waitForIdle()
    }

    @Test fun defaultBackDoesNotPreviewUntilEnabled() {
        mount(predictive = false)
        val before = compose.onNodeWithTag("back").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT)
            )
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(100f, 200f, 0.6f, BackEventCompat.EDGE_LEFT)
            )
        }
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithTag("back").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, commits)
    }

    @Test fun toolbarBackCommitsOnceAndKeepsSourceMounted() {
        mount()
        compose.onNodeWithTag("back").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("back").assertDoesNotExist()
        assertEquals(1, commits)
        assertEquals(1, parentMounts)
    }

    @Test fun cancelledGestureRestoresPageAndNextBackStillWorks() {
        mount()
        val before = compose.onNodeWithTag("back").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT)
            )
        }
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(100f, 200f, 0.6f, BackEventCompat.EDGE_LEFT)
            )
        }
        compose.waitForIdle()
        val previewed = compose.onNodeWithTag("back").fetchSemanticsNode().boundsInRoot
        assertEquals(before.width, previewed.width, 1f)
        assertEquals(before.left, previewed.left, 1f)
        assertTrue("Preview must slide the page downward", previewed.top > before.top)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(0, commits)
        assertEquals(before, compose.onNodeWithTag("back").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, commits)
        assertEquals(1, parentMounts)
    }

    @Test fun rightEdgeGestureCommitsAfterRelease() {
        mount()
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(400f, 200f, 0f, BackEventCompat.EDGE_RIGHT)
            )
        }
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(300f, 200f, 0.7f, BackEventCompat.EDGE_RIGHT)
            )
        }
        compose.runOnIdle {
            assertEquals(0, commits)
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.onNodeWithTag("back").assertDoesNotExist()
        assertEquals(1, commits)
    }

    private fun verifyExitDirection(edge: Int, topLevel: Boolean = false) {
        mount(topLevel = topLevel)
        val initial = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(if (edge == BackEventCompat.EDGE_RIGHT) 400f else 0f, 200f, 0f, edge))
        }
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(if (edge == BackEventCompat.EDGE_RIGHT) 300f else 100f, 200f, .6f, edge))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val previewed = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        assertEquals(initial.left, previewed.left, 1f)
        assertEquals(initial.width, previewed.width, 1f)
        assertTrue("Both edges must move the topmost page downward", previewed.top > initial.top)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        val exiting = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        assertEquals(initial.left, exiting.left, 1f)
        assertEquals(initial.width, exiting.width, 1f)
        assertTrue("Release must continue downward", exiting.top > previewed.top)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, commits)
    }

    @Test fun rightEdgeReleaseContinuesDown() = verifyExitDirection(BackEventCompat.EDGE_RIGHT)
    @Test fun leftEdgeReleaseContinuesDown() = verifyExitDirection(BackEventCompat.EDGE_LEFT)
    @Test fun topLevelRightEdgeReleaseContinuesDown() = verifyExitDirection(BackEventCompat.EDGE_RIGHT, topLevel = true)

    @Test fun rightEdgeCancellationRestoresPosition() {
        mount()
        val before = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(400f, 200f, 0f, BackEventCompat.EDGE_RIGHT))
        }
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(300f, 200f, .6f, BackEventCompat.EDGE_RIGHT))
        }
        compose.waitForIdle()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot)
        assertEquals(0, commits)
    }

    @Test fun backDuringEntranceIsNotLost() {
        compose.mainClock.autoAdvance = false
        mount()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("back").assertDoesNotExist()
        assertEquals(1, commits)
    }

    @Test fun backDuringCancelledGestureReboundIsNotLost() {
        mount()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT)
            )
        }
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(
                BackEventCompat(100f, 200f, 0.6f, BackEventCompat.EDGE_LEFT)
            )
        }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("back").assertDoesNotExist()
        assertEquals(1, commits)
    }

    @Test fun repeatedBackDuringExitCommitsOnlyOnce() {
        mount()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, commits)
        compose.onNodeWithTag("back").assertDoesNotExist()
    }

    @Test fun coveredControlsAreHiddenUntilPageReturns() {
        var covered by mutableStateOf(true)
        compose.setContent {
            CoveredPage(covered) {
                Button(onClick = {}, modifier = Modifier.testTag("source-action")) { Text("Source") }
            }
        }
        compose.onNodeWithTag("source-action").assertDoesNotExist()
        compose.runOnIdle { covered = false }
        compose.onNodeWithTag("source-action").assertExists()
    }
}
