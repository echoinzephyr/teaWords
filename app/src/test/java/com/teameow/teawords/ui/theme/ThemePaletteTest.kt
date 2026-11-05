package com.teameow.teawords.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
    }

    @Test fun generatedSchemesKeepReadableTextInLightAndDark() {
        for (seed in listOf(0xFF286EA0, 0xFF9C4668, 0xFF286A5B, 0xFF64732E, 0xFF91632A, 0xFF69616E, 0xFF000000, 0xFFFFFFFF)) {
            for (dark in listOf(false, true)) {
                val colors = seededColorScheme(seed.toInt(), dark)
                listOf(colors.primary to colors.onPrimary, colors.primaryContainer to colors.onPrimaryContainer,
                    colors.secondaryContainer to colors.onSecondaryContainer, colors.background to colors.onBackground,
                    colors.surfaceContainerLow to colors.onSurface, colors.error to colors.onError).forEach { (a, b) ->
                    assertTrue("Unreadable seed=$seed dark=$dark", contrast(a, b) >= 4.5f)
                }
            }
        }
    }
}
