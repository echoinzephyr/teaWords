package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.teameow.teawords.data.AppPreferences
import com.teameow.teawords.data.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PressedShapesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun pressedSegmentAndPureBlackChoiceLeaveRoundedCornersUnchanged() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsAppearanceOptions(AppThemeMode.SYSTEM, {}, false, {}, false, true, {},
                        AppPreferences.DEFAULT_THEME_SEED, {}, null, {})
                }
            }
        }
        listOf(hasText("浅色") and hasAnyAncestor(hasTestTag("theme-mode")), hasText("标准"),
            hasContentDescription("海蓝")).forEach { matcher ->
            val node = compose.onNode(matcher).performScrollTo()
            val before = node.captureToImage().asAndroidBitmap()
            node.performTouchInput { down(center) }
            compose.waitForIdle()
            val pressed = node.captureToImage().asAndroidBitmap()
            listOf(1 to 1, (before.width - 2) to 1, 1 to (before.height - 2),
                (before.width - 2) to (before.height - 2)).forEach { (x, y) ->
                assertEquals("Press feedback must stay within the rounded shape", before.getPixel(x, y), pressed.getPixel(x, y))
            }
            node.performTouchInput { up() }
        }
    }
}
