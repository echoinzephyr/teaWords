package com.teameow.teawords.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.filters.SdkSuppress
import com.teameow.teawords.ui.theme.TeaWordsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TeaAppIconTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun assertPalette(colors: ColorScheme, name: String) {
        val bitmap = compose.onNodeWithContentDescription("茶词图标").assertIsDisplayed().captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        listOf(colors.primaryContainer, colors.primary, colors.tertiary, colors.onPrimary, colors.onTertiary).forEach { color ->
            assertTrue("Icon must use the active Material colour ${color.toArgb()}", pixels.any { it == color.toArgb() })
        }
        val folder = File(compose.activity.getExternalFilesDir(null), "icon-redesign").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun paletteChangesRecolorTheSameIconInLightAndDarkThemes() {
        val light = lightColorScheme(primaryContainer = Color(0xFFD9EFE3), onPrimaryContainer = Color(0xFF164D42), primary = Color(0xFF4F8067))
        val dark = darkColorScheme(primaryContainer = Color(0xFF482A38), onPrimaryContainer = Color(0xFFFADAE8), primary = Color(0xFFE4A5C0))
        var palette by mutableStateOf(light)
        var mounts = 0
        compose.setContent {
            MaterialTheme(colorScheme = palette) {
                DisposableEffect(Unit) { mounts++; onDispose {} }
                TeaAppIcon()
            }
        }
        assertPalette(light, "light")
        compose.runOnIdle { palette = dark }
        assertPalette(dark, "dark")
        assertEquals(1, mounts)
    }

    @SdkSuppress(minSdkVersion = 31)
    @Test fun monetUsesTheSystemWallpaperPalette() {
        val colors = dynamicLightColorScheme(compose.activity)
        compose.setContent { TeaWordsTheme(dynamicColor = true) { TeaAppIcon() } }
        assertPalette(colors, "monet")
    }

    @SdkSuppress(minSdkVersion = 33)
    @Test fun launcherRendersAdaptiveLegacyAndThemedIcons() {
        val activity = compose.activity
        val icon = activity.packageManager.getApplicationIcon(activity.packageName)
        assertTrue(icon is android.graphics.drawable.AdaptiveIconDrawable)
        icon as android.graphics.drawable.AdaptiveIconDrawable
        val monochrome = requireNotNull(icon.monochrome).mutate()
        monochrome.setTint(android.graphics.Color.rgb(4, 86, 116))
        val themed = android.graphics.drawable.AdaptiveIconDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.rgb(185, 228, 251)), monochrome
        )
        val folder = File(activity.getExternalFilesDir(null), "icon-redesign").apply { mkdirs() }
        mapOf("launcher" to icon, "launcher-themed" to themed,
            "launcher-legacy" to requireNotNull(activity.getDrawable(com.teameow.teawords.R.drawable.teawords_logo))).forEach { (name, drawable) ->
            val bitmap = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
            drawable.setBounds(0, 0, 512, 512)
            drawable.draw(android.graphics.Canvas(bitmap))
            assertTrue("Packaged launcher icon must render", bitmap.getPixel(256, 256) ushr 24 > 0)
            File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
