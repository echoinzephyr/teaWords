package com.teameow.teawords.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The same progress capsule, open question area and reachable actions as the word plan. */
@Composable
internal fun TeaExercisePage(
    title: String, done: Int, total: Int, busy: Boolean, onBack: () -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    content: LazyListScope.() -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .statusBarsPadding().navigationBarsPadding().imePadding()) {
      val actionHeight = maxHeight * .5f
      Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            FilledTonalIconButton(onClick = onBack, enabled = !busy, modifier = Modifier.size(48.dp)) {
                Icon(AppSymbols.ArrowBack, "返回")
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = CircleShape) {
                Column(Modifier.width(140.dp).padding(horizontal = 28.dp, vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${done.coerceAtMost(total)}/$total", fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { done.toFloat() / total.coerceAtLeast(1) },
                        color = MaterialTheme.colorScheme.onSurface,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .2f),
                        modifier = Modifier.padding(top = 3.dp).fillMaxWidth().height(2.dp), drawStopIndicator = {})
                }
            }
            Box {
                FilledTonalIconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.MoreHoriz, "更多")
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("暂停，稍后继续") },
                        onClick = { menu = false; onBack() }, enabled = !busy)
                }
            }
        }
        Text(title, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("tea-exercise-content"),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically), content = content)
        Column(Modifier.fillMaxWidth().heightIn(max = actionHeight)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("tea-exercise-actions"), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
      }
    }
}

@Composable
internal fun TeaExerciseContent(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
internal fun TeaExerciseAction(label: String, enabled: Boolean = true, secondary: Boolean = false,
    onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = CircleShape, colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
            contentColor = if (secondary) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary)) {
        Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun TeaExerciseOption(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(22.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
