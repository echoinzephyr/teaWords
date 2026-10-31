package com.teameow.teawords.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teameow.teawords.data.VocabularyItem
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

@Composable
fun RecordsView(
    vocabularyList: List<VocabularyItem>, reviewCount: Int,
    onItemClick: (String) -> Unit, onDeleteVocab: (String) -> Unit,
    onSettings: () -> Unit = {}, onStats: () -> Unit = {}, onStartReview: () -> Unit = {},
    loading: Boolean = false, error: String? = null
) {
    val layout = teaLayoutSize()
    var showNotebook by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        CoveredPage(covered = showNotebook) {
            TeaListPage(title = "回望", subtitle = "查过的生词，回来巩固", loading = loading, actions = {
                IconButton(onClick = onSettings) { Icon(AppSymbols.Settings, "设置") }
                IconButton(onClick = onStats) { Icon(AppSymbols.BarChart, "学习统计") }
            }) {
                error?.let { item { TeaStatusMessage(it, error = true) } }
                item {
                    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                        Box(Modifier.fillMaxWidth().heightIn(min = if (layout.compact) 160.dp else 220.dp).padding(if (layout.compact) 20.dp else 24.dp)) {
                            Column(Modifier.align(Alignment.TopStart).padding(bottom = if (layout.compact) 64.dp else 108.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("开始复习", style = MaterialTheme.typography.headlineMedium)
                                Text("$reviewCount 个查过的生词", style = MaterialTheme.typography.titleMedium)
                                Text("只复习单词，暂不包含句子", style = MaterialTheme.typography.bodyMedium)
                            }
                            FilledTonalIconButton(onClick = onStartReview, enabled = !loading && reviewCount > 0,
                                modifier = Modifier.align(Alignment.BottomEnd).size(if (layout.compact) 56.dp else 80.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                                Icon(AppSymbols.ArrowForward, "开始生词复习", Modifier.size(28.dp))
                            }
                        }
                    }
                }
                if (!loading && reviewCount == 0) item {
                    TeaCaption("暂时没有待巩固的生词。查词后可以回来复习。")
                }
                item {
                    TeaSegmentedGroup {
                        TeaOptionRow(title = "生词本 · ${vocabularyList.size}", summary = "查看和整理收藏的单词",
                            icon = AppSymbols.BookmarkFlag, onClick = { showNotebook = true })
                    }
                }
            }
        }
        if (showNotebook) PredictiveBackLayer(onBack = { showNotebook = false }) { back ->
            NotebookPage(vocabularyList, onItemClick, onDeleteVocab, back)
        }
    }
}

@Composable
internal fun NotebookPage(words: List<VocabularyItem>, onOpen: (String) -> Unit, onDelete: (String) -> Unit, onBack: () -> Unit) {
    TeaListPage(title = "生词本", onBack = onBack) {
        if (words.isEmpty()) item { TeaEmptyState("给新词留个位置", "收藏的单词会出现在这里。") }
        items(words, key = { it.word }) { word ->
            Surface(onClick = { onOpen(word.word) }, shape = MaterialTheme.shapes.large,
                modifier = Modifier.animateItem(fadeInSpec = tween(180),
                    placementSpec = spring(dampingRatio = 1f, stiffness = 500f), fadeOutSpec = tween(140)),
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(word.word, style = MaterialTheme.typography.titleMedium)
                        Text(word.definition, style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = { onDelete(word.word) }) { Icon(AppSymbols.Close, "移除 ${word.word}") }
                }
            }
        }
    }
}
