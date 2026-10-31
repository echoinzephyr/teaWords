package com.teameow.teawords.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource

/** The learning entry card grows with its text instead of reserving a narrow title column. */
@Composable
internal fun ExpressiveLearningPlan(due: Int, fuzzy: Int, unseen: Int, busy: Boolean, onStart: () -> Unit) {
    val startInteraction = remember { MutableInteractionSource() }
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("开始学习", style = MaterialTheme.typography.headlineLarge)
            Text(
                "$due/$fuzzy/$unseen",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium)
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("到期 / 模糊 / 待排查", style = MaterialTheme.typography.labelMedium)
                    Text("进入后选择单词、短课或专项练习", style = MaterialTheme.typography.labelMedium)
                }
                FilledIconButton(
                    onClick = onStart,
                    enabled = !busy,
                    interactionSource = startInteraction,
                    modifier = Modifier.size(72.dp).teaElasticPress(startInteraction, !busy)
                ) {
                    Icon(AppSymbols.ArrowForward, "开始学习", Modifier.size(32.dp))
                }
            }
        }
    }
}

/** Round size and a single toggle select what the main start button does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LearningControls(limit: Int?, onLimitChange: (Int) -> Unit, quickMode: Boolean, onQuickModeChange: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(160.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("每轮目标", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp))
            Box {
                val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "目标菜单箭头")
                FilledTonalButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)) {
                    Text(limit?.let { "$it 个词" } ?: "请选择", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(AppSymbols.ExpandMore, null, Modifier.size(24.dp).rotate(angle))
                }
                TeaDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, sections = listOf(
                    TeaMenuSection(listOf(10, 15, 20).map { count ->
                        TeaMenuOption("$count 个词", AppSymbols.School, selected = count == limit) { onLimitChange(count); expanded = false }
                    })
                ))
            }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("快速模式", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = quickMode, onCheckedChange = onQuickModeChange,
                modifier = Modifier.semantics { contentDescription = "快速模式" })
        }
    }
}
