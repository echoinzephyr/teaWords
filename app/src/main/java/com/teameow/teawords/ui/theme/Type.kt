package com.teameow.teawords.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

// A denser type scale for phone content. sp still follows the user's system font setting.
private val baseline = Typography()
val Typography = baseline.copy(
    displayMedium = baseline.displayMedium.copy(fontSize = 40.sp, lineHeight = 48.sp),
    headlineLarge = baseline.headlineLarge.copy(fontSize = 26.sp, lineHeight = 32.sp),
    headlineMedium = baseline.headlineMedium.copy(fontSize = 22.sp, lineHeight = 28.sp),
    headlineSmall = baseline.headlineSmall.copy(fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = baseline.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = baseline.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp)
)
