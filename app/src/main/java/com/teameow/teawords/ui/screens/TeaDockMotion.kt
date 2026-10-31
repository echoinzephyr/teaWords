package com.teameow.teawords.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.roundToInt

/** Convert FlClash's duration/bounce springs to Compose's unit-mass stiffness and damping. */
internal fun <T> dockSpring(durationMillis: Int, bounce: Float = 0f): SpringSpec<T> {
    val seconds = durationMillis / 1000f
    return spring(dampingRatio = if (bounce > 0) 1f - bounce else 1f / (1f + bounce),
        stiffness = (4 * Math.PI * Math.PI / (seconds * seconds)).toFloat())
}

internal fun dockRubberBand(distance: Float, limit: Float): Float {
    if (limit <= 0) return 0f
    return limit * (1f - 1f / (abs(distance) * .55f / limit + 1f)) * sign(distance)
}

@Composable
internal fun rememberDockMotion(target: Float, spec: SpringSpec<Float>, followingPointer: Boolean = false): Pair<Float, Float> {
    val motion = remember { Animatable(target) }
    var carriedVelocity by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(target, spec, followingPointer) {
        if (followingPointer) {
            motion.snapTo(target)
            carriedVelocity = 0f
        } else {
            motion.animateTo(target, spec, initialVelocity = carriedVelocity) {
                carriedVelocity = velocity
            }
            carriedVelocity = 0f
        }
    }
    // Draw directly at the pointer; only the release should settle with a spring.
    return (if (followingPointer) target else motion.value) to carriedVelocity
}

/** A quick release can bias the nearest slot without skipping across the dock. */
internal fun dockReleaseIndex(position: Float, velocityInSlots: Float, lastIndex: Int): Int {
    val projection = (velocityInSlots * .1f).coerceIn(-.45f, .45f)
    return (position + projection).roundToInt().coerceIn(0, lastIndex)
}
