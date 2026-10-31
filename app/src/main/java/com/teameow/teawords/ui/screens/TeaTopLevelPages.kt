package com.teameow.teawords.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Tabs are peer destinations; only their child pages register app back handlers. */
@Composable
internal fun TeaTopLevelPages(
    selectedTab: AppTab, covered: Boolean,
    content: @Composable (AppTab) -> Unit
) {
    CoveredPage(covered = selectedTab != AppTab.TRANSLATE || covered) {
        content(AppTab.TRANSLATE)
    }
    if (selectedTab != AppTab.TRANSLATE) {
        AnimatedContent(targetState = selectedTab,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(180)) + slideInHorizontally(tween(240)) { direction * it / 20 }) togetherWith fadeOut(tween(90))
            }, label = "main-tab") { tab ->
            CoveredPage(covered = covered || tab != selectedTab) { content(tab) }
        }
    }
}
