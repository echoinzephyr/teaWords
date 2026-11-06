package com.teameow.teawords.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teameow.teawords.data.*
import com.teameow.teawords.ui.LookupViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookupScreen(initialText: String, external: Boolean = false, onBack: () -> Unit, requestId: Long = 0L, onInputChange: (String) -> Unit = {}, model: LookupViewModel = viewModel(key = if (external) "lookup-external" else "lookup-internal")) {
    DisposableEffect(model) { onDispose { model.cancel() } }
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    var historyVisible by rememberSaveable { mutableStateOf(false) }
    var importVisible by remember { mutableStateOf(false) }
    // Save the consumed request, not a boolean: SaveableStateHolder restores values even
    // when rememberSaveable inputs changed while this page was absent.
    val requestKey = "$external:$requestId:$initialText"
    var consumedRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var scopeExpanded by remember { mutableStateOf(false) }
    var modeExpanded by remember { mutableStateOf(false) }
    var learningExpanded by remember { mutableStateOf(false) }
    var dictionaryExpanded by remember { mutableStateOf(false) }
    var moreExpanded by remember { mutableStateOf(false) }
    var infoVisible by remember { mutableStateOf(false) }
    var detailsVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) { onDispose { focusManager.clearFocus(force = true); keyboard?.hide() } }
    fun submit() {
        focusManager.clearFocus(force = true); keyboard?.hide()
        if (state.dictionaryMode) model.search() else if (state.available) model.translate()
    }
    val pronunciation = rememberPronunciationServices()
    DisposableEffect(model) { onDispose { pronunciation.manager.stop(); model.cancel() } }
    LaunchedEffect(state.input) { if (consumedRequest == requestKey) onInputChange(state.input) }
    LaunchedEffect(requestKey) {
        if (consumedRequest != requestKey || model.state.value.input.isEmpty()) { model.open(initialText, external); consumedRequest = requestKey }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file -> if (file != null) model.importEcdict(file) }
    val searchActions: @Composable RowScope.() -> Unit = {
        Box {
            IconButton(onClick = { moreExpanded = true }) { Icon(Icons.Default.MoreVert, "词典选项") }
            val sections = buildList {
                add(TeaMenuSection(listOf(TeaMenuOption(if (historyVisible) "关闭历史" else "查询历史", AppSymbols.History) {
                    moreExpanded = false; historyVisible = !historyVisible
                })))
                add(TeaMenuSection(buildList {
                    add(TeaMenuOption("输入方式", AppSymbols.Translate,
                        supportingText = if (state.automaticMode) "自动识别" else if (state.dictionaryMode) "手动查词" else "手动翻译", opensMenu = true) {
                        moreExpanded = false; modeExpanded = true
                    })
                    if (state.dictionaryMode && state.books.isNotEmpty()) {
                        add(TeaMenuOption("查询范围", AppSymbols.Book5,
                            supportingText = state.books.firstOrNull { it.id == state.scope }?.name ?: "全部词典", opensMenu = true) {
                            moreExpanded = false; scopeExpanded = true
                        })
                    }
                }))
                if (state.selected != null) add(TeaMenuSection(listOf(
                    TeaMenuOption("学习标记", AppSymbols.School, opensMenu = true) { moreExpanded = false; learningExpanded = true },
                    TeaMenuOption("词条资料", AppSymbols.Info) { moreExpanded = false; detailsVisible = true }
                )))
                add(TeaMenuSection(listOf(
                    TeaMenuOption("词典管理", AppSymbols.Settings, opensMenu = true) { moreExpanded = false; dictionaryExpanded = true }
                )))
            }
            TeaDropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }, sections = sections)
            TeaDropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }, sections = listOf(
                TeaMenuSection(listOf(
                    TeaMenuOption("自动识别输入", AppSymbols.Insights, selected = state.automaticMode) { modeExpanded = false; model.automaticMode() },
                    TeaMenuOption("手动查词", AppSymbols.Search, selected = !state.automaticMode && state.dictionaryMode) { modeExpanded = false; model.mode(true) },
                    TeaMenuOption("手动翻译", AppSymbols.Translate, selected = !state.automaticMode && !state.dictionaryMode) { modeExpanded = false; model.mode(false) }
                ), "输入方式")
            ))
            state.selected?.let { selected ->
                TeaDropdownMenu(expanded = learningExpanded, onDismissRequest = { learningExpanded = false }, sections = listOf(
                    TeaMenuSection(listOf(
                        TeaMenuOption("加入学习队列", AppSymbols.Add, enabled = !state.busy) { learningExpanded = false; model.mark(selected, SelfReport.UNKNOWN) },
                        TeaMenuOption("有些模糊", AppSymbols.Help, enabled = !state.busy) { learningExpanded = false; model.mark(selected, SelfReport.FUZZY) },
                        TeaMenuOption("自评认识", AppSymbols.Check, enabled = !state.busy) { learningExpanded = false; model.mark(selected, SelfReport.KNOWN) }
                    ), "学习标记")
                ))
            }
            TeaDropdownMenu(expanded = dictionaryExpanded, onDismissRequest = { dictionaryExpanded = false }, sections = listOf(
                TeaMenuSection(listOf(
                    TeaMenuOption("词典说明", AppSymbols.Info, supportingText = "${state.count} 词条") { dictionaryExpanded = false; infoVisible = true },
                    TeaMenuOption("导入 ECDICT", AppSymbols.ArrowDownward, enabled = !state.busy) { dictionaryExpanded = false; importVisible = true }
                ), "词典管理")
            ))
            TeaDropdownMenu(expanded = scopeExpanded, onDismissRequest = { scopeExpanded = false }, sections = listOf(
                TeaMenuSection(buildList {
                    add(TeaMenuOption("全部词典", AppSymbols.Book5, selected = state.scope == null) { scopeExpanded = false; model.scope(null) })
                    state.books.forEach { book ->
                        add(TeaMenuOption(book.name, AppSymbols.Book5, selected = state.scope == book.id, supportingText = "${book.wordCount} 词条") {
                            scopeExpanded = false; model.scope(book.id)
                        })
                    }
                }, "查询范围")
            ))
        }
    }
    TeaListPage(title = "", expandedTitle = false, header = {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = state.input,
                onValueChange = { model.input(it); onInputChange(it) },
                modifier = Modifier.weight(1f).teaSharedSearch(visible = LocalTeaSearchResultVisible.current || LocalTeaSearchScope.current == null)
                    .testTag("lookup-search-input"),
                placeholder = { Text("输入单词或句子") },
                leadingIcon = { IconButton(onClick = onBack) { Icon(AppSymbols.ArrowBack, "返回") } },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                minLines = 1, maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = if (state.dictionaryMode) ImeAction.Search else ImeAction.Default),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                trailingIcon = {
                    Row {
                        if (state.input.isNotBlank()) IconButton(onClick = { model.input(""); onInputChange("") }) {
                            Icon(AppSymbols.Close, "清空输入")
                        }
                        IconButton(enabled = !state.busy && state.input.isNotBlank() && (state.dictionaryMode || state.available),
                            onClick = ::submit) { Icon(AppSymbols.Search, if (state.dictionaryMode) "查询" else "翻译") }
                    }
                }
            )
            searchActions()
        }
    }) {
        if (state.external) item { TeaCaption("来自其他应用 · 本次输入不保存查询历史") }
        if (state.busy) item {
            TeaCard {
                TeaProgressBar()
                Text(state.status, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = model::cancel) { Text("取消等待") }
                if (state.status.contains("模型")) {
                    TeaCaption("取消等待后，系统已开始的模型下载可能继续；完成后可删除模型。")
                }
            }
        }
        if (state.onlineBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()); TeaCaption("正在联网查词…") }
        state.notice?.let { item { TeaStatusMessage(it) } }
        state.error?.let { item { TeaCard {
            TeaStatusMessage(it, error = true)
            TextButton(onClick = model::search) { Text("重试") }
        } } }
        if (state.dictionaryMode) {
            if (!state.dictionaryReady && state.books.isEmpty()) item {
                TeaCard { TeaCaption("本地词库正在准备；可联网查词，也可下载词书后离线使用。") }
            }
            // Spelling suggestions are a different claim from a result: the query itself was not found.
            if (!state.busy && state.entries.isEmpty() && state.suggestions.isNotEmpty()) item {
                TeaCard {
                    Text("未找到该词，是否想查：", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.suggestions.forEach { candidate ->
                            TextButton(onClick = { model.open(candidate.word) }) { Text(candidate.word) }
                        }
                    }
                    TeaCaption("按编辑距离排序的拼写建议，不是词典释义。")
                }
            }
            if (!state.busy && !state.onlineBusy && state.error == null && state.input.isNotBlank() && state.entries.isEmpty() && state.suggestions.isEmpty() && state.onlineEntry == null && state.onlineSuggestions.isEmpty()) item {
                TeaCard {
                    TeaEmptyState("未找到匹配词条", "可尝试原形、较短关键词，或下载更多词书。", icon = AppSymbols.Search)
                    TextButton(onClick = { model.mode(false); if (model.state.value.available) model.translate() }) { Text("改用句子翻译") }
                }
            }
            if (state.onlineSuggestions.isNotEmpty()) item {
                TeaCard {
                    Text("在线搜索建议", style = MaterialTheme.typography.titleMedium)
                    state.onlineSuggestions.forEach { candidate ->
                        TextButton(onClick = { onInputChange(candidate.word); model.open(candidate.word) }) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(candidate.word)
                                if (candidate.explanation.isNotBlank()) TeaCaption(candidate.explanation)
                            }
                        }
                    }
                    TeaCaption("有道在线建议 · 点击查询详细释义")
                }
            }
            state.onlineEntry?.let { online -> item {
                TeaCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(online.word, style = MaterialTheme.typography.headlineMedium)
                            TeaCaption("有道在线词典" + if (state.scope != null) " · 不受本地词书范围限制" else "")
                        }
                        IconButton(enabled = !state.busy, onClick = { state.selected?.let { model.star(it, !state.starred) } ?: model.starOnline(online) }) {
                            Icon(if (state.starred) AppSymbols.FavoriteFilled else AppSymbols.Favorite, "收藏单词")
                        }
                        CompactPronunciationButton(online.word)
                    }
                    if (online.detail.uk.isNotBlank()) TeaCaption("英 /${online.detail.uk}/")
                    if (online.detail.us.isNotBlank()) TeaCaption("美 /${online.detail.us}/")
                    Text(online.detail.definition, style = MaterialTheme.typography.bodyLarge)
                    if (online.detail.phrases.isNotEmpty()) {
                        TeaSectionLabel("常用短语")
                        online.detail.phrases.forEach { (phrase, meaning) -> Text("$phrase\n$meaning") }
                    }
                    if (online.detail.forms.isNotEmpty()) {
                        TeaSectionLabel("词形变化")
                        online.detail.forms.forEach { (name, value) -> TeaCaption("$name · $value") }
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(online.detail.definition)) }) { Text("复制释义") }
                }
            } }
            val selected = state.selected
            if (selected != null && state.onlineEntry == null) item {
                WordHeroCard(
                    entry = selected,
                    starred = state.starred,
                    busy = state.busy,
                    onToggleStar = { model.star(selected, !state.starred) },
                    onCopy = { clipboard.setText(AnnotatedString("${selected.word}\n${selected.zh}\n${selected.en}")) }
                )
            }
            // Cards with neither Chinese nor English text render as a title-only blank block, which
            // reads as an unrendered white bar. Only entries that actually carry a meaning are listed.
            val others = state.entries.filter {
                it.id != selected?.id && (it.zh.isNotBlank() || it.en.isNotBlank())
            }
            if (others.isNotEmpty()) {
                item { TeaSectionLabel("其他匹配 · ${others.size}", Modifier.fillMaxWidth()) }
                items(others, key = { "entry-${it.id}" }) { entry ->
                    Surface(
                        onClick = { model.select(entry) },
                        enabled = !state.busy,
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                entry.word,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (entry.zh.isNotBlank()) TeaCaption(entry.zh.take(90))
                            // Saying how a word was found is what lets a user trust the ordering: an exact
                            // hit, an inflected form and a loose meaning match are different claims.
                            entry.match?.let { TeaCaption(it.describe()) }
                        }
                    }
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.source == "en") "英语 → 中文" else "中文 → 英语", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(enabled = !state.busy, onClick = model::reverse) { Text("交换方向") }
                }
                TeaCaption(when (state.translationEngine) {
                    TranslationEngine.MYMEMORY -> "在线翻译 · 原文会发送给 MyMemory，无需下载模型"
                    TranslationEngine.DEEPSEEK -> "DeepSeek 翻译 · 原文会发送给 DeepSeek，按账户用量计费"
                })
                if (!state.available) TeaCaption("请在「设置 → 句子与长文翻译」中准备离线模型或配置翻译服务。")
                if (state.input.length > TranslationText.MAX_INPUT_CHARS) TeaCaption("原文 ${state.input.length} 字符；单次上限 20,000，请分次提交。")
            }
            if (state.translation.isNotBlank()) item {
                LearningCard(if (state.translationComplete) "译文" else "已翻译部分", state.translationAttribution) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(state.translation, style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(onClick = { clipboard.setText(AnnotatedString(state.translation)) }) { Text("复制译文") }
                    if (!state.translationComplete) {
                        TeaCaption("本次翻译尚未完成，当前只显示已完成的部分。")
                        if (!state.busy) TextButton(onClick = model::translate) { Text("继续翻译") }
                    }

                }
            }
            item {
                LearningCard("分析生词", "从原文中找出可能不认识的词，手动决定是否学习") {
                    Button(
                        enabled = !state.busy && state.input.isNotBlank(),
                        onClick = model::analyze,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("分析英文原文中的生词") }
                    TeaCaption("按词典词形数据还原，过滤已验证和自评认识的词；只有手动选择才加入学习。")
                }
            }
            items(state.candidates, key = { "candidate-${it.first.id}" }) { (entry, knowledge) ->
                LearningCard(entry.word, knowledgeLabel(knowledge)) {
                    if (entry.zh.isNotBlank()) Text(entry.zh, style = MaterialTheme.typography.bodyMedium)
                    Button(
                        enabled = !state.busy,
                        onClick = { model.mark(entry, SelfReport.UNKNOWN) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("加入学习") }
                }
            }
        }
        if (historyVisible) {
            item {
                LearningCard("查询历史", "与学习记录分开保存") {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("保存应用内查询历史", style = MaterialTheme.typography.bodyMedium)
                        Switch(state.historyEnabled, onCheckedChange = model::history)
                    }
                    TextButton(enabled = !state.busy, onClick = { model.deleteHistory() }) { Text("清空查询历史（保留学习记录）") }
                }
            }
            items(state.history, key = { "history-${it.id}" }) { record ->
                val date = remember(record.time) { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(record.time)) }
                LearningCard(record.text.take(100), "${record.kind} · ${record.direction} · $date") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { model.open(record.text); historyVisible = false }) { Text("再次查询") }
                        TextButton(onClick = { model.deleteHistory(record.id) }) { Text("删除") }
                    }
                }
            }
        }
    }
    if (detailsVisible && state.selected != null) {
        val entry = state.selected!!
        AlertDialog(onDismissRequest = { detailsVisible = false }, title = { Text("词条资料") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.pos.isNotBlank()) Text("词性 · ${entry.pos}")
                if (entry.forms.isNotBlank()) Text("词形 · ${entry.forms}")
                if (entry.tags.isNotBlank()) Text("词书标签 · ${entry.tags}")
                Text("来源 · ${entry.source}")
                Text(knowledgeLabel(state.knowledge))
            }
        }, confirmButton = { TextButton(onClick = { detailsVisible = false }) { Text("关闭") } })
    }
    if (infoVisible) AlertDialog(onDismissRequest = { infoVisible = false }, title = { Text("离线词典 · ${state.count} 词条") }, text = {
        Text("支持精确查询、前缀、词形还原、拼写建议和中文反查。\n\n词书由词典数据自带的考试标签生成，不代表官方最新完整大纲。")
    }, confirmButton = { TextButton(onClick = { infoVisible = false }) { Text("知道了") } })
    if (importVisible) AlertDialog(onDismissRequest = { importVisible = false }, title = { Text("导入本地 ECDICT CSV") }, text = {
        Text("请选择 UTF-8 编码、包含 word / definition / translation 列的词典。支持流式导入，取消或解析失败会回滚。本操作只扩充词典，不把所有单词加入学习。\n\nECDICT 上游仓库标注 MIT，但混合词典数据的具体授权需要按来源核验。请使用你有权使用的文件。")
    }, confirmButton = { Button(onClick = { importVisible = false; picker.launch(arrayOf("text/*", "application/octet-stream", "application/vnd.ms-excel")) }) { Text("选择 CSV 文件") } }, dismissButton = { TextButton(onClick = { importVisible = false }) { Text("取消") } })
}

private fun knowledgeLabel(state: Knowledge?): String = when {
    state == null -> "尚无知识记录"
    state.verified -> "已通过连续回忆验证 · 仅限学习过的释义"
    state.report == SelfReport.KNOWN -> "自评认识 · 尚需间隔验证"
    state.attempts > 0 -> "学习中 · 已测试 ${state.attempts} 次"
    state.report == SelfReport.FUZZY -> "模糊认识 · 优先巩固"
    state.report == SelfReport.UNKNOWN -> "待学习 · 自评陌生"
    else -> "待确认 · 查询不会更改知识状态"
}

/**
 * 查词页的词头卡：整块 `secondaryContainer` 卡片 + 大字号词头 + 考试标签 + 快捷动作。
 * 词头字号按长度分级，宁可小一号也不要被截断——查词页把单词截掉是不可接受的。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordHeroCard(
    entry: LocalEntry,
    starred: Boolean,
    busy: Boolean,
    onToggleStar: () -> Unit,
    onCopy: () -> Unit
) {
    val headline = when {
        entry.word.length <= 10 -> MaterialTheme.typography.displayMedium
        entry.word.length <= 14 -> MaterialTheme.typography.displaySmall
        else -> MaterialTheme.typography.headlineMedium
    }
    val labels = remember(entry.word, entry.tags, entry.books) {
        (if (entry.books.isNotEmpty()) entry.books
        else entry.tags.split(' ', ',', ';', '/').map { it.trim() }.filter { it.isNotEmpty() })
            .distinct().take(4)
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 272.dp).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(entry.word, style = headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.phonetic.isNotBlank()) Text(entry.phonetic, style = MaterialTheme.typography.bodyMedium)
                    if (labels.isNotEmpty()) FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        labels.forEach { label -> WordLabelChip(label) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedIconButton(onClick = onToggleStar, enabled = !busy, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (starred) AppSymbols.FavoriteFilled else AppSymbols.Favorite,
                            if (starred) "移出生词本" else "加入生词本",
                            Modifier.size(20.dp)
                        )
                    }
                    CompactPronunciationButton(entry.word)
                }
            }
            PronunciationFeedback(entry.word, rememberPronunciationServices().manager)
            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.16f))
            if (entry.zh.isNotBlank()) Text(entry.zh.replace("\\n", "\n"), style = MaterialTheme.typography.bodyLarge)
            if (entry.en.isNotBlank()) Text(entry.en.replace("\\n", "\n"), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCopy) {
                    Icon(AppSymbols.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("复制释义")
                }
            }
        }
    }
}

/** 词书 / 考试标签。卡片本身已是 container 色，标签改用 surface 才能在卡面上读得出来。 */
@Composable
private fun WordLabelChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}
