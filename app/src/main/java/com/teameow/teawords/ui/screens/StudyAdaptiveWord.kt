package com.teameow.teawords.ui.screens

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/** Fit using actual glyph measurements, with no layout-feedback state or recomposition loop. */
@Composable
internal fun StudyAdaptiveWord(word: String) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val base = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        val size = remember(word, width, density.fontScale, base) {
            var size = 34f
            while (size > 18 && measurer.measure(AnnotatedString(word), base.copy(fontSize = size.sp),
                    softWrap = false, maxLines = 1).size.width > width) size -= 1
            size.sp
        }
        Text(word, Modifier.fillMaxWidth(), style = base.copy(fontSize = size, lineHeight = size * 1.2f), textAlign = TextAlign.Center)
    }
}
