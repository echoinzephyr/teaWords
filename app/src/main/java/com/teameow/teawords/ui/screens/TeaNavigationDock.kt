package com.teameow.teawords.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun TeaNavigationDockRow(
    selectedTab: AppTab,
    onSelected: (AppTab) -> Unit,
    onRecentHistory: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    onLearningProfile: (() -> Unit)? = null
) {
    val height = 62.dp + (10f * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)).dp
    val showAction = selectedTab == AppTab.TRANSLATE || (selectedTab == AppTab.REVIEW && onLearningProfile != null)
    Row(modifier.widthIn(max = 224.dp + 8.dp + height).fillMaxWidth().height(height),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        TeaNavigationDock(selectedTab, onSelected, Modifier.weight(1f, fill = false), enabled, containerColor)
        AnimatedVisibility(visible = showAction,
            enter = expandHorizontally(dockSpring(320), expandFrom = Alignment.End, clip = false) + fadeIn(tween(180)) + scaleIn(dockSpring(300, .12f), initialScale = .85f),
            exit = shrinkHorizontally(dockSpring(320), shrinkTowards = Alignment.End, clip = false) + fadeOut(tween(140)) + scaleOut(tween(140), targetScale = .9f)) {
            val sharpness by transition.animateFloat(transitionSpec = { tween(180) }, label = "dock-action-sharpness") {
                if (it == EnterExitState.Visible) 1f else 0f
            }
            Row(Modifier.blur((6f * (1f - sharpness)).dp, BlurredEdgeTreatment.Unbounded), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                val actionInteraction = remember { MutableInteractionSource() }
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                FilledIconButton(onClick = { if (selectedTab == AppTab.TRANSLATE) onRecentHistory() else onLearningProfile?.invoke() },
                    enabled = enabled && showAction, interactionSource = actionInteraction,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    modifier = Modifier.size(height).teaElasticPress(actionInteraction, enabled && showAction)
                        .shadow(8.dp, CircleShape, clip = false)) {
                    AnimatedContent(selectedTab == AppTab.REVIEW,
                        transitionSpec = { (fadeIn(tween(180)) + scaleIn(dockSpring(300, .12f), initialScale = .85f))
                            .togetherWith(fadeOut(tween(140)) + scaleOut(tween(140), targetScale = .9f)) }, label = "dock-action-change") { profile ->
                        Icon(if (profile) AppSymbols.Person else AppSymbols.Schedule,
                            contentDescription = if (profile) "词汇知识画像" else "最近查阅", modifier = Modifier.size(28.dp))
                    }
                }
                }
            }
        }
    }
}

@Composable
internal fun TeaNavigationDock(
    selectedTab: AppTab,
    onSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer
) {
    val tabs = AppTab.entries
    val density = LocalDensity.current
    val height = 62.dp + (10f * (density.fontScale - 1f).coerceAtLeast(0f)).dp
    val interactions = remember { tabs.map { MutableInteractionSource() } }
    val pressedIndex = interactions.map { it.collectIsPressedAsState().value }.indexOfFirst { it }
    val pressed = pressedIndex >= 0
    var draggedPosition by remember { mutableStateOf<Float?>(null) }
    val latestSelection by rememberUpdatedState(onSelected)
    val latestTab by rememberUpdatedState(selectedTab)
    val targetPosition = draggedPosition ?: if (enabled && pressed) pressedIndex.toFloat() else selectedTab.ordinal.toFloat()
    val motion = rememberDockMotion(targetPosition, dockSpring(380, .16f), followingPointer = draggedPosition != null)
    val position = motion.first
    val velocity = motion.second
    val haptic = LocalHapticFeedback.current
    var pullTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) { draggedPosition = null; pullTarget = 0f }
    }
    val animatedPull by animateFloatAsState(pullTarget, dockSpring(320, .12f), label = "dock-edge-pull")
    val pull = if (draggedPosition != null) pullTarget else animatedPull
    val lift by animateFloatAsState(
        targetValue = if (enabled && (pressed || draggedPosition != null)) 1f else 0f,
        animationSpec = if (pressed || draggedPosition != null) dockSpring(220, .08f) else dockSpring(320, .12f),
        label = "dock-lens-lift"
    )
    val pill = RoundedCornerShape(50)
    val icons = listOf(AppSymbols.Search, AppSymbols.Update, AppSymbols.School)
    BoxWithConstraints(
        modifier.widthIn(max = 224.dp).fillMaxWidth().height(height)
            .testTag("tea-navigation-dock")
            .graphicsLayer {
                val swell = 1f + lift * minOf(.25f, 8.dp.toPx() / size.width)
                scaleX = swell * (1f + abs(pull) / size.width * .5f)
                scaleY = swell
                translationX = pull
            }
            .shadow(8.dp, pill, clip = false)
            .background(containerColor, pill)
            .selectableGroup()
    ) {
        val slotWidth = (maxWidth - 8.dp) / tabs.size
        val slotPixels = with(density) { slotWidth.toPx() }
        val paddingPixels = with(density) { 4.dp.toPx() }
        fun pointerPosition(x: Float): Float {
            val raw = (x - paddingPixels) / slotPixels - .5f
            return when { raw < 0 -> dockRubberBand(raw, .35f)
                raw > tabs.lastIndex -> tabs.lastIndex + dockRubberBand(raw - tabs.lastIndex, .35f)
                else -> raw }
        }
        val drag = if (enabled) Modifier.pointerInput(slotPixels) {
            val tracker = VelocityTracker()
            var lastIndex = latestTab.ordinal
            detectHorizontalDragGestures(
                onDragStart = { tracker.resetTracking(); draggedPosition = pointerPosition(it.x); lastIndex = draggedPosition!!.roundToInt().coerceIn(0, tabs.lastIndex) },
                onDragCancel = { draggedPosition = null; pullTarget = 0f },
                onDragEnd = {
                    val index = draggedPosition?.let { at ->
                        dockReleaseIndex(at, tracker.calculateVelocity().x / slotPixels, tabs.lastIndex)
                    }
                    draggedPosition = null; pullTarget = 0f
                    if (index != null && tabs[index] != latestTab) latestSelection(tabs[index])
                },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, change.position)
                    draggedPosition = pointerPosition(change.position.x)
                    val nextIndex = draggedPosition!!.roundToInt().coerceIn(0, tabs.lastIndex)
                    if (nextIndex != lastIndex) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); lastIndex = nextIndex }
                    val overshoot = change.position.x - change.position.x.coerceIn(0f, size.width.toFloat())
                    pullTarget = dockRubberBand(overshoot, size.height * 7f / 32f)
                }
            )
        } else Modifier
        Box(Modifier.fillMaxSize().then(drag).padding(4.dp)) {
            Box(
                Modifier.offset { IntOffset((slotPixels * position).roundToInt(), 0) }
                    .width(slotWidth).fillMaxHeight()
                    .graphicsLayer {
                        val stretch = (abs(velocity) / 8f).coerceIn(0f, 1f) * .12f
                        val growth = 12.dp.toPx() * lift
                        scaleX = (1f + growth / size.width) * (1f + stretch)
                        scaleY = (1f + growth / size.height) * (1f - stretch / 2f)
                    }
                    .background(MaterialTheme.colorScheme.secondaryContainer, pill)
            )
            Row(Modifier.fillMaxSize()) {
                tabs.forEachIndexed { index, tab ->
                    val selected = tab == selectedTab
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(pill)
                            .selectable(selected = selected, enabled = enabled, role = Role.Tab,
                                interactionSource = interactions[index], indication = null,
                                onClick = { if (tab != latestTab) latestSelection(tab) })
                            .graphicsLayer {
                                val emphasis = (1f - abs(position - index)).coerceIn(0f, 1f)
                                scaleX = 1f + lift * .06f * emphasis
                                scaleY = scaleX
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides lerp(
                            MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSecondaryContainer,
                            (1f - abs(position - index)).coerceIn(0f, 1f))) {
                            Icon(icons[index], contentDescription = null)
                            Spacer(Modifier.height(2.dp))
                            Text(tab.title, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

            }
        }
    }
}
