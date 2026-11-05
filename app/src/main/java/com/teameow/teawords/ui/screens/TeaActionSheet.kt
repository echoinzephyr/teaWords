package com.teameow.teawords.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeaActionSheet(
    title: String,
    onDismiss: () -> Unit,
    compact: Boolean = false,
    footer: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val available = LocalConfiguration.current.screenHeightDp
    val height = if (compact) (available * .65f).dp.coerceAtMost(420.dp) else (available * .9f).dp
    TeaModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = compact),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        val closeSheet = LocalTeaSheetClose.current
        Column(Modifier.fillMaxWidth().height(height).testTag("tea-action-sheet")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 56.dp)
                .testTag("tea-sheet-header"), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                actions()
                IconButton(onClick = { closeSheet(null) }) { Icon(AppSymbols.Close, "关闭$title") }
            }
            content()
            footer?.invoke()
        }
    }
}

@Composable
internal fun TeaKnowledgeProfileSheet(
    verified: Int, learning: Int, fuzzy: Int, unknown: Int, total: Int,
    onDismiss: () -> Unit, onShowStats: () -> Unit
) {
    TeaActionSheet("词汇知识画像", onDismiss, compact = true, footer = {
        val closeSheet = LocalTeaSheetClose.current
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Button(onClick = { closeSheet { onDismiss(); onShowStats() } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 48.dp)) {
                Text("查看统计")
                Spacer(Modifier.width(8.dp))
                Icon(AppSymbols.ArrowForward, null, Modifier.size(18.dp))
            }
        }
    }) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileCount(verified, "已验证", Modifier.weight(1f), true)
                    ProfileCount(learning, "学习中", Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileCount(fuzzy, "模糊", Modifier.weight(1f))
                    ProfileCount(unknown, "陌生", Modifier.weight(1f))
                }
            }
            item { TeaCaption(if (verified + learning + fuzzy + unknown == 0)
                "还没有学习记录，开始学习后可在这里查看变化。"
                else "已验证来自测试记录；模糊和陌生来自你的自评。") }
        }
    }
}

@Composable
private fun ProfileCount(count: Int, label: String, modifier: Modifier, highlighted: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(20.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$count", style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
