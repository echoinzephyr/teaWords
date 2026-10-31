package com.teameow.teawords.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

internal val TeaEnterEasing = CubicBezierEasing(0f, 0f, 0f, 1f)
internal val TeaExitEasing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

@Composable
internal fun Modifier.teaElasticPress(source: MutableInteractionSource, enabled: Boolean = true): Modifier {
    val pressed by source.collectIsPressedAsState()
    var touching by remember { mutableStateOf(false) }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var pull by remember { mutableStateOf(Offset.Zero) }
    val active = enabled && (pressed || touching)
    val lift by animateFloatAsState(if (active) 1f else 0f,
        if (active) dockSpring(280, .2f) else dockSpring(500, .32f), label = "button-press")
    val offset by animateOffsetAsState(if (active) pull else Offset.Zero,
        if (active) dockSpring(120) else dockSpring(500, .32f), label = "button-pull")
    val maxGrowth = with(LocalDensity.current) { 16.dp.toPx() }
    return onSizeChanged { measured = it }.pointerInput(enabled, source) {
        if (enabled) awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            touching = true
            try {
                do {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    val delta = (change?.position ?: down.position) - down.position
                    val limit = size.width.coerceAtMost(size.height) * 7f / 32f
                    pull = Offset(dockRubberBand(delta.x, limit), dockRubberBand(delta.y, limit))
                } while (change?.pressed == true)
            } finally { touching = false; pull = Offset.Zero }
        }
    }.graphicsLayer {
        val width = measured.width.toFloat().coerceAtLeast(1f)
        val height = measured.height.toFloat().coerceAtLeast(1f)
        val swell = 1f + lift * minOf(.25f, maxGrowth / maxOf(width, height))
        scaleX = swell * (1f + kotlin.math.abs(offset.x) / width * .5f)
        scaleY = swell * (1f + kotlin.math.abs(offset.y) / height * .5f)
        translationX = offset.x
        translationY = offset.y
    }
}

@Composable
internal fun TeaNavigationIcon(icon: ImageVector, selected: Boolean, description: String?) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0f)
            pop.animateTo(1f, tween(350))
        } else pop.snapTo(1f)
    }
    Icon(icon, contentDescription = description, modifier = Modifier.graphicsLayer {
        val scale = 1f + sin(Math.PI * pop.value).toFloat() * 0.1f
        scaleX = scale
        scaleY = scale
    })
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TeaLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    LoadingIndicator(modifier = modifier.size(40.dp).semantics {
        contentDescription = "正在加载"
    }, color = color)
}
