package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.teameow.teawords.ui.theme.TeaWordsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TeaNavigationDockTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var selections = 0
    private var chosen = AppTab.TRANSLATE

    private fun mount(enabled: Boolean = true, largeFont: Boolean = false) {
        compose.setContent {
            var selected by remember { mutableStateOf(AppTab.TRANSLATE) }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density,
                if (largeFont) 2f else 1f)) {
                TeaWordsTheme {
                    Box(Modifier.width(200.dp)) {
                        TeaNavigationDock(selected, { selections++; chosen = it; selected = it }, enabled = enabled)
                    }
                }
            }
        }
    }

    @Test fun tappingTabUpdatesSelectionAndSemantics() {
        mount()
        compose.onNodeWithText("翻译").assertIsSelected()
        compose.onNodeWithText("回望").performClick().assertIsSelected()
        compose.onNodeWithText("翻译").assertIsNotSelected()
        compose.runOnIdle { assertEquals(1, selections); assertEquals(AppTab.HISTORY, chosen) }
    }

    @Test fun tappingSelectedTabDoesNotRestartPage() {
        mount()
        compose.onNodeWithText(AppTab.TRANSLATE.title).performClick()
        compose.runOnIdle { assertEquals(0, selections) }
        compose.onNodeWithText(AppTab.REVIEW.title).performClick()
        compose.onNodeWithText(AppTab.REVIEW.title).performClick()
        compose.runOnIdle { assertEquals(1, selections) }
    }

    @Test fun disablingDuringDragRestoresSelectionWithoutDispatching() {
        val enabled = mutableStateOf(true)
        compose.setContent {
            TeaWordsTheme {
                TeaNavigationDock(AppTab.TRANSLATE, { selections++ }, enabled = enabled.value)
            }
        }
        compose.onNodeWithTag("tea-navigation-dock").performTouchInput {
            down(Offset(width * .16f, centerY))
            moveTo(Offset(width * .84f, centerY), 400)
        }
        compose.runOnIdle { enabled.value = false }
        compose.onNodeWithTag("tea-navigation-dock").performTouchInput { up() }
        compose.onNodeWithText(AppTab.TRANSLATE.title).assertIsSelected()
        compose.runOnIdle { assertEquals(0, selections) }
        compose.runOnIdle { enabled.value = true }
        compose.onNodeWithText(AppTab.HISTORY.title).performClick().assertIsNotSelected()
        compose.runOnIdle { assertEquals(1, selections) }
    }

    @Test fun trailingActionFollowsSelectedPage() {
        var historyOpens = 0
        var profileOpens = 0
        compose.setContent {
            var selected by remember { mutableStateOf(AppTab.TRANSLATE) }
            TeaWordsTheme {
                TeaNavigationDockRow(selected, { selected = it }, { historyOpens++ },
                    onLearningProfile = { profileOpens++ })
            }
        }
        compose.onNodeWithContentDescription("最近查阅").performClick()
        compose.onNodeWithText("学习").performClick()
        compose.onNodeWithContentDescription("最近查阅").assertDoesNotExist()
        val dock = compose.onNodeWithTag("tea-navigation-dock").getUnclippedBoundsInRoot()
        val profile = compose.onNodeWithContentDescription("词汇知识画像").getUnclippedBoundsInRoot()
        org.junit.Assert.assertTrue(profile.left >= dock.right)
        assertEquals(dock.top, profile.top)
        compose.onNodeWithContentDescription("词汇知识画像").performClick()
        compose.runOnIdle { assertEquals(1, historyOpens); assertEquals(1, profileOpens) }
        compose.onNodeWithText("回望").performClick()
        compose.onNodeWithContentDescription("词汇知识画像").assertDoesNotExist()
        compose.onNodeWithContentDescription("最近查阅").assertDoesNotExist()
    }

    @Test fun draggingSelectsOnlyOnRelease() {
        mount()
        compose.onNodeWithTag("tea-navigation-dock").performTouchInput {
            down(Offset(width * 0.16f, centerY))
            moveTo(Offset(width * 0.84f, centerY), 400)
        }
        compose.runOnIdle { assertEquals(0, selections) }
        compose.onNodeWithTag("tea-navigation-dock").performTouchInput { up() }
        compose.onNodeWithText("学习").assertIsSelected()
        compose.runOnIdle { assertEquals(1, selections); assertEquals(AppTab.REVIEW, chosen) }
    }

    @Test fun cancelledDragRestoresSelection() {
        mount()
        compose.onNodeWithTag("tea-navigation-dock").performTouchInput {
            down(Offset(width * 0.16f, centerY))
            moveTo(Offset(width * 0.84f, centerY), 400)
            cancel()
        }
        compose.onNodeWithText("翻译").assertIsSelected()
        compose.runOnIdle { assertEquals(0, selections) }
    }

    @Test fun disabledDockRejectsDragging() {
        mount(enabled = false)
        compose.onNodeWithText("回望").assertIsNotEnabled()
        compose.onNodeWithTag("tea-navigation-dock").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(0, selections) }
    }

    @Test fun labelsFitAtLargeFontOnNarrowScreen() {
        mount(largeFont = true)
        AppTab.entries.forEach { compose.onNodeWithText(it.title).assertIsDisplayed() }
        compose.onNodeWithText("学习").performClick().assertIsSelected()
    }
}
