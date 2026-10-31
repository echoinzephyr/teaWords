package com.teameow.teawords.ui.screens

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

internal val LocalTeaSearchScope = staticCompositionLocalOf<SharedTransitionScope?> { null }
internal val LocalTeaSearchResultVisible = staticCompositionLocalOf { false }

@Composable
internal fun Modifier.teaSharedSearch(visible: Boolean): Modifier {
    val scope = LocalTeaSearchScope.current ?: return this
    return with(scope) {
        sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState("tea-search-input"),
            visible = visible,
            boundsTransform = { _, _ -> tween(360, easing = TeaEnterEasing) })
    }
}
