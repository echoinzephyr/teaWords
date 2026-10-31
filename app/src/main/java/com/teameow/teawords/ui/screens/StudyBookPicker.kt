package com.teameow.teawords.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.teameow.teawords.data.DatabaseHelper
import com.teameow.teawords.data.LearningRepository
import com.teameow.teawords.data.search.DictionaryGateway
import com.teameow.teawords.data.search.WordSearchRepository.BookInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun bookCategory(book: BookInfo): String = when (book.category) {
    "school" -> "中学"
    "college" -> "大学"
    "postgraduate" -> "考研"
    "international" -> "留学"
    else -> "其他"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyBookPicker(helper: DatabaseHelper, books: List<BookInfo>, selected: Set<String>,
    onSelection: (Set<String>) -> Unit, onDismiss: () -> Unit, onApply: () -> Unit) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部") }
    var unionCount by remember { mutableStateOf<Int?>(null) }
    var learned by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(books) {
        try {
            val snapshot = withContext(Dispatchers.IO) {
                val dictionary = DictionaryGateway.sourceOrNull()
                val known = LearningRepository(helper).wordRows().filter { it.attempts > 0 }.map { it.word }.toSet()
                val counts = books.associate { book -> book.id to if (known.isEmpty()) 0 else
                    dictionary?.learningWords(setOf(book.id)).orEmpty().count { it.word in known } }
                dictionary?.countBookWords(books.map { it.id }.toSet()) to counts
            }
            unionCount = snapshot.first; learned = snapshot.second
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { error = "学习进度暂时无法读取，仍可选择词书。" }
    }
    val categories = listOf("全部") + listOf("大学", "留学", "中学", "考研", "其他").filter { c -> books.any { bookCategory(it) == c } }
    val ordered = books.sortedWith(compareBy({ listOf("cet4", "cet6", "ielts", "toefl", "gre", "zk", "gk", "ky").indexOf(it.id).let { n -> if (n < 0) Int.MAX_VALUE else n } }, { it.name }))
    val filtered = ordered.filter { book -> (category == "全部" || bookCategory(book) == category) &&
        (query.isBlank() || listOf(book.name, book.id, book.description).any { it.contains(query.trim(), ignoreCase = true) }) }
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp - 12).dp.coerceAtLeast(240.dp)).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(AppSymbols.ArrowBack, "取消选择词书") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("选择词书", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${books.size} 本" + (unionCount?.let { " · $it 个不重复词条" } ?: " · 正在统计词数"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(AppSymbols.Book5, null, Modifier.padding(12.dp))
            }
            TextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("搜索词书") }, leadingIcon = { Icon(AppSymbols.Search, null) },
                singleLine = true, shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
            LazyRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { item -> FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) }) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("study-book-list"),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                error?.let { item { TeaStatusMessage(it, error = true) } }
                if (filtered.isEmpty()) item { TeaEmptyState("没有匹配的词书", "换个关键词或分类试试。") }
                filtered.groupBy(::bookCategory).forEach { (group, entries) ->
                    item(key = "heading-$group") { TeaSectionLabel(group) }
                    item(key = "group-$group") {
                        TeaSegmentedGroup {
                            entries.forEach { book ->
                                val checked = book.id in selected
                                Surface(color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox,
                                        onValueChange = { onSelection(if (it) selected + book.id else selected - book.id) })
                                        .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        StudyBookCover(book.id.uppercase(), compact = true)
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(book.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            TeaCaption("${book.wordCount} 词" + (learned[book.id]?.let { " · 已学 $it 词" } ?: ""))
                                        }
                                        Checkbox(checked, onCheckedChange = null)
                                    }
                                }
                            }
                        }
                    }
                }
                item { TeaCaption("词书来自 ECDICT 考试标签，不代表官方完整大纲；切换或取消选择保留已有学习记录。") }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Button(onClick = { focus.clearFocus(); keyboard?.hide(); onApply() }, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp)) {
                    Text(if (selected.isEmpty()) "仅学习个人词库" else "应用选择 · ${selected.size} 本")
                }
            }
        }
    }
}
