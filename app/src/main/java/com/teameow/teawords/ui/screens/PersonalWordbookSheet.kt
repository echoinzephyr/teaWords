package com.teameow.teawords.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonalWordbookSheet(text: String, onText: (String) -> Unit, busy: Boolean, error: String?,
    importRevision: Int, onDismiss: () -> Unit, onBooks: () -> Unit, onFile: () -> Unit, onImport: () -> Unit) {
    TeaModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = !busy, backEnabled = !busy, dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        val close = LocalTeaSheetClose.current
        val initialRevision = remember { importRevision }
        LaunchedEffect(importRevision) { if (importRevision > initialRevision) close(null) }
        Column(Modifier.fillMaxWidth().height(teaModalPageHeight()).imePadding()) {
            TeaPanelHeader("个人词库", onClose = { close(null) }, enabled = !busy)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("personal-wordbook-content"),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { TeaCaption("添加自己的单词，重复导入会保留已有学习记录。") }
                item { TeaSectionLabel("导入方式") }
                item {
                    TeaSegmentedGroup {
                        TeaOptionRow("从文件导入", "支持 CSV、JSON、TXT", AppSymbols.Add, enabled = !busy, onClick = onFile)
                        TeaOptionRow("选择内置词书", "四级、六级、雅思、托福等", AppSymbols.Book5, enabled = !busy,
                            onClick = { close { onDismiss(); onBooks() } })
                    }
                }
                item { TeaSectionLabel("粘贴词条") }
                item {
                    OutlinedTextField(text, onText, Modifier.fillMaxWidth(), enabled = !busy,
                        label = { Text("单词与释义") }, placeholder = { Text("apple,苹果\nbook,书") },
                        minLines = 5, maxLines = 9, shape = MaterialTheme.shapes.large)
                }
                item { TeaCaption("文本每行填写“单词,释义”；JSON 使用 word 和 definition 字段。请使用你有权使用的资料。") }
                error?.let { item { TeaStatusMessage(it, error = true) } }
            }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Button(onClick = onImport, enabled = !busy && text.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (busy) "正在导入…" else "导入词库")
                }
            }
        }
    }
}
