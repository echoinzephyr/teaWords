package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.teameow.teawords.data.AppPreferences
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BetaUpdateSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun betaPreferencePersistsWhenUpdatePageReopens() {
        val preferences = AppPreferences(compose.activity)
        val original = preferences.receiveBetaUpdates
        preferences.receiveBetaUpdates = false
        var visible by mutableStateOf(true)
        try {
            compose.setContent { MaterialTheme { if (visible) UpdateCheckCard("1.2.2-rc.1", 5) } }
            compose.onNodeWithTag("beta-updates-switch").assertIsOff().performClick()
            compose.runOnIdle { assertTrue(AppPreferences(compose.activity).receiveBetaUpdates); visible = false }
            compose.waitForIdle()
            compose.runOnIdle { visible = true }
            compose.onNodeWithTag("beta-updates-switch").assertIsOn().performClick().assertIsOff()
            compose.runOnIdle { assertFalse(preferences.receiveBetaUpdates) }
        } finally { preferences.receiveBetaUpdates = original }
    }
}
