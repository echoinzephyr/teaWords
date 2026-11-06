package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.teameow.teawords.data.AppPreferences
import com.teameow.teawords.data.AppThemeMode
import com.teameow.teawords.ui.theme.TeaWordsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class ThemeSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var renderedScale = 0f
    private var systemScale = 0f

    @Test fun changingFontScaleKeepsTheSameSheetWindowAndContentMounted() {
        val scale = mutableStateOf<Float?>(null)
        var mounts = 0
        var disposals = 0
        var window: android.view.View? = null
        compose.setContent {
            TeaWordsTheme(customFontScale = scale.value) {
                TeaModalBottomSheet(onDismissRequest = {}) {
                    val view = LocalView.current
                    val actual = LocalDensity.current.fontScale
                    SideEffect { window = view; renderedScale = actual }
                    DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                    Text("保持当前主题页")
                }
            }
        }
        compose.waitForIdle()
        val original = window
        for (value in listOf(.85f, null, 1.3f, null, .75f, null)) {
            compose.runOnIdle { scale.value = value }
            compose.waitForIdle()
            compose.runOnIdle {
                assertSame("Changing text size must not rebuild the dialog window", original, window)
                assertEquals(1, mounts); assertEquals(0, disposals)
                value?.let { assertEquals(it, renderedScale, .001f) }
            }
        }
    }
    private fun mount(prefs: AppPreferences) {
        compose.setContent {
            var seed by remember { mutableIntStateOf(prefs.themeSeedColor) }
            var scale by remember { mutableStateOf(prefs.customFontScale) }
            val original = LocalDensity.current.fontScale
            SideEffect { systemScale = original }
            TeaWordsTheme(seedColor = seed, customFontScale = scale) {
                val actual = LocalDensity.current.fontScale
                SideEffect { renderedScale = actual }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsAppearanceOptions(AppThemeMode.LIGHT, {}, false, {}, false, false, {}, seed,
                        { prefs.themeSeedColor = it; seed = it }, scale,
                        { prefs.customFontScale = it; scale = prefs.customFontScale })
                }
            }
        }
    }

    @Test fun presetAndCustomColorPersistAndInvalidHexCannotApply() {
        val prefs = AppPreferences(compose.activity)
        val previous = prefs.themeSeedColor
        try {
            mount(prefs)
            compose.onNodeWithContentDescription("海蓝").performScrollTo().performClick().assertIsSelected()
            assertEquals(0xFF286EA0.toInt(), AppPreferences(compose.activity).themeSeedColor)
            compose.onNodeWithContentDescription("自定义主题颜色").performClick()
            compose.onNodeWithTag("theme-hex-input").performTextReplacement("BAD")
            compose.onNodeWithText("应用颜色").assertIsNotEnabled()
            compose.onNodeWithTag("theme-hex-input").performTextReplacement("2A8C72")
            compose.onNodeWithText("应用颜色").assertIsEnabled().performClick()
            assertEquals(0xFF2A8C72.toInt(), AppPreferences(compose.activity).themeSeedColor)
        } finally { prefs.themeSeedColor = previous }
    }

    @Test fun customFontScaleChangesActualTextDensityAndCanFollowSystemAgain() {
        val prefs = AppPreferences(compose.activity)
        val previous = prefs.customFontScale
        prefs.customFontScale = null
        try {
            mount(prefs)
            val custom = hasText("自定义") and hasAnyAncestor(hasTestTag("font-scale-mode"))
            compose.onNode(custom).performScrollTo().performClick()
            compose.onNodeWithTag("font-scale-slider").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(.85f) }
            compose.runOnIdle { assertEquals(.85f, renderedScale, .001f) }
            assertEquals(.85f, AppPreferences(compose.activity).customFontScale!!, .001f)
            val follow = hasText("跟随系统") and hasAnyAncestor(hasTestTag("font-scale-mode"))
            compose.onNode(follow).performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(systemScale, renderedScale, .001f) }
            assertNull(AppPreferences(compose.activity).customFontScale)
        } finally { prefs.customFontScale = previous }
    }
    @Test fun draggingFontSliderOnlyCommitsGlobalScaleOnRelease() {
        val prefs = AppPreferences(compose.activity)
        val previous = prefs.customFontScale
        prefs.customFontScale = 1f
        try {
            mount(prefs)
            val slider = compose.onNodeWithTag("font-scale-slider").performScrollTo()
            slider.performTouchInput {
                down(center)
                moveTo(androidx.compose.ui.geometry.Offset(width * .85f, centerY), delayMillis = 64)
            }
            compose.runOnIdle { assertEquals(1f, renderedScale, .001f) }
            assertEquals(1f, prefs.customFontScale!!, .001f)
            slider.performTouchInput { up() }
            compose.runOnIdle { assertTrue(renderedScale > 1f) }
            assertTrue(prefs.customFontScale!! > 1f)
        } finally { prefs.customFontScale = previous }
    }
}
