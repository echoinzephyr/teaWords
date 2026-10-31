package com.teameow.teawords.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Adapt individual layout decisions; dp/sp and the user's font scale stay intact. */
internal data class TeaLayoutSize(val width: Int, val height: Int) {
    val compact get() = width < 400 || height < 600
    val horizontalPadding get() = if (compact) 16.dp else 24.dp
    val sectionGap get() = if (compact) 12.dp else 16.dp
    val titleSize get() = if (compact) 24.sp else 30.sp
    val wordSize get() = when { width < 360 || height < 500 -> 28.sp; compact -> 32.sp; else -> 40.sp }
    val actionHeight get() = if (compact) 48.dp else 56.dp
}

@Composable
internal fun teaLayoutSize(): TeaLayoutSize {
    val config = LocalConfiguration.current
    return TeaLayoutSize(config.screenWidthDp, config.screenHeightDp)
}
