package com.teameow.teawords.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** null closes normally; a supplied action runs after hiding instead of the default dismissal. */
internal val LocalTeaSheetClose = staticCompositionLocalOf<(((() -> Unit)?) -> Unit)> { error("No sheet close controller") }

/** Sheet back previews move vertically at full width, independently of Material's scale preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeaModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    backEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val textDensity = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Material keys its dialog window by LocalDensity. Custom text scaling must only
    // remeasure the content, rather than dispose/recreate the visible window.
    val windowDensity = remember(textDensity.density, configuration.fontScale) {
        Density(textDensity.density, configuration.fontScale)
    }
    val preview = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismissRequest)
    var settling by remember { mutableStateOf<Job?>(null) }
    var exiting by remember { mutableStateOf(false) }
    var generation by remember { mutableIntStateOf(0) }
    val predictive = LocalPredictiveBackEnabled.current
    val close: ((() -> Unit)?) -> Unit = { afterHidden ->
        if (!exiting) {
            exiting = true
            settling?.cancel()
            settling = scope.launch {
                // Keep the preview displacement until the native vertical hide finishes.
                // Resetting it first would jump upward when the gesture is committed.
                sheetState.hide()
                if (!sheetState.isVisible) (afterHidden ?: dismiss)()
                else { preview.animateTo(0f, spring()); exiting = false }
            }
        }
    }
    CompositionLocalProvider(LocalDensity provides windowDensity) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.graphicsLayer { translationY = size.height * .35f * preview.value }
            .pointerInput(exiting) {
                if (exiting) awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
        sheetState = sheetState,
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = shape,
        containerColor = containerColor,
        dragHandle = dragHandle,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        BackHandler(enabled = backEnabled && !predictive) { close(null) }
        PredictiveBackHandler(enabled = backEnabled && predictive) { events ->
            if (exiting) events.collect { }
            else {
                val activeGeneration = ++generation
                settling?.cancel()
                try {
                    events.collect { event -> preview.snapTo(event.progress.coerceIn(0f, 1f)) }
                    if (generation == activeGeneration) close(null)
                } catch (cancelled: CancellationException) {
                    if (generation == activeGeneration && !exiting) {
                        settling = scope.launch { preview.animateTo(0f, spring(dampingRatio = 1f, stiffness = 500f)) }
                    }
                    throw cancelled
                }
            }
        }
        CompositionLocalProvider(LocalTeaSheetClose provides close, LocalDensity provides textDensity) { content() }
    }
    }
}
