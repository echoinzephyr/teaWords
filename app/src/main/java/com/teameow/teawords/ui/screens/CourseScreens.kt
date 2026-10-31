package com.teameow.teawords.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.teameow.teawords.data.*
import com.teameow.teawords.data.pronunciation.SpeechPhase
import com.teameow.teawords.data.search.DictionaryGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CourseListScreen(helper: DatabaseHelper, initialTrack: String = "ky",
                              onTrackChange: (String) -> Unit = {}, onBack: () -> Unit,
                              onOpen: (CourseLesson) -> Unit,
                              onReviewWeak: (List<String>) -> Unit) {
    val repository = remember(helper) { CourseRepository(helper) }
    val progress = remember { repository.allProgress() }
    val weakWords = remember { repository.weakWords() }
    val comparison = remember { repository.recallComparison() }
    var track by rememberSaveable { mutableStateOf(initialTrack.takeIf { it in CourseDirectory.tracks } ?: "ky") }
    var query by rememberSaveable { mutableStateOf("") }
    var openChapter by rememberSaveable { mutableIntStateOf(-1) }
    val lessons = remember(track) { CourseDirectory.lessons(track) }
    val chapters = remember(track) { CourseDirectory.chapters(track) }
    val matches = remember(track, query) { CourseDirectory.search(track, query) }
    val finished = lessons.count { progress[it.id]?.complete == true }
    val next = lessons.firstOrNull { progress[it.id]?.let { state -> state.step > 0 && !state.complete } == true }
        ?: lessons.firstOrNull { progress[it.id]?.complete != true }

    TeaListPage("英语短课", subtitle = "按考试方向和主题单元找课", onBack = onBack) {
        item {
            TeaCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Text(CourseDirectory.trackName(track), style = MaterialTheme.typography.headlineSmall)
                Text("已完成 $finished / ${lessons.size} 课 · 每课约 8 步，可随时续学")
                LinearProgressIndicator(progress = { if (lessons.isEmpty()) 0f else finished.toFloat() / lessons.size },
                    modifier = Modifier.fillMaxWidth())
                if (next != null) {
                    val state = progress[next.id] ?: CourseRepository.Progress()
                    Text(if (state.step > 0) "接着学：${next.title}" else "下一课：${next.title}",
                        style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { onOpen(next) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.step > 0) "继续第 ${next.id.substringAfter('-').toInt()} 课" else "开始第 ${next.id.substringAfter('-').toInt()} 课")
                    }
                } else Text("这个方向的课程已学完，可以按单元回看。")
            }
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                CourseDirectory.tracks.forEachIndexed { index, id ->
                    SegmentedButton(selected = track == id, onClick = {
                        track = id
                        onTrackChange(id)
                        query = ""
                        openChapter = -1
                    }, shape = SegmentedButtonDefaults.itemShape(index, CourseDirectory.tracks.size)) {
                        Text(CourseDirectory.trackName(id))
                    }
                }
            }
        }
        item {
            TextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                leadingIcon = { Icon(AppSymbols.Search, null) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                label = { Text("找课程") }, placeholder = { Text("输入课名、重点词或课号") },
                singleLine = true)
        }
        if (query.isNotBlank()) {
            item { TeaSectionLabel("搜索结果 · ${matches.size} 课") }
            if (matches.isEmpty()) item { TeaCaption("这个方向没有匹配的课程。换个词，或切换考试方向。") }
            matches.forEach { lesson -> item(key = lesson.id) {
                CourseLessonRow(lesson, progress[lesson.id] ?: CourseRepository.Progress(), onOpen)
            } }
        } else {
            item { TeaSectionLabel("按主题单元浏览 · 点击展开") }
            chapters.forEachIndexed { index, chapter -> item(key = "$track-$index") {
                val done = chapter.lessons.count { progress[it.id]?.complete == true }
                TeaCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(chapter.title, style = MaterialTheme.typography.titleLarge)
                            TeaCaption("${chapter.description} · $done/${chapter.lessons.size} 课完成")
                        }
                        TextButton(onClick = { openChapter = if (openChapter == index) -1 else index }) {
                            Text(if (openChapter == index) "收起" else "查看课程")
                        }
                    }
                    if (openChapter == index) TeaSegmentedGroup {
                        chapter.lessons.forEach { lesson ->
                            CourseLessonRow(lesson, progress[lesson.id] ?: CourseRepository.Progress(), onOpen)
                        }
                    }
                }
            } }
        }
        if (weakWords.isNotEmpty() || comparison.beforeTotal > 0 && comparison.afterTotal > 0) item {
            TeaCard {
                Text("学习记录", style = MaterialTheme.typography.titleMedium)
                if (weakWords.isNotEmpty()) Button(onClick = { onReviewWeak(weakWords) },
                    modifier = Modifier.fillMaxWidth()) { Text("复习课程薄弱词 · ${weakWords.size} 个") }
                if (comparison.beforeTotal > 0 && comparison.afterTotal > 0) TeaCaption(
                    "主动回忆正确率：开课前 7 天 ${comparison.beforePercent}%（${comparison.beforeTotal} 题）" +
                        " · 开课后 ${comparison.afterPercent}%（${comparison.afterTotal} 题）。仅供观察。")
            }
        }
    }
}

@Composable
private fun CourseLessonRow(lesson: CourseLesson, progress: CourseRepository.Progress,
                            onOpen: (CourseLesson) -> Unit) {
    val action = if (progress.complete) "回看" else if (progress.step > 0) "继续" else "开始"
    TeaOptionRow(
        title = "${lesson.id.substringAfter('-').toInt()}. ${lesson.title}",
        summary = "${lesson.focus} · " + when {
            progress.complete -> "已完成"
            progress.step > 0 -> "进行中 ${progress.step}/${CourseRepository.STEPS}"
            else -> "未开始"
        },
        trailing = { TextButton(onClick = { onOpen(lesson) }) { Text(action) } },
        onClick = { onOpen(lesson) }
    )
}

private fun lessonStepName(step: Int) = when (step) {
    1 -> "词义选择"
    2 -> "听音辨词"
    3 -> "语境填空"
    4 -> "句子排序"
    5 -> "阅读理解"
    6 -> "简短写作"
    7 -> "听说练习"
    else -> "课程练习"
}

private fun courseDefinitionPreview(raw: String): String {
    val firstLine = raw.replace("\\n", "\n")
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    return if (firstLine.length > 72) firstLine.take(72).trimEnd() + "…" else firstLine
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourseLessonScreen(helper: DatabaseHelper, lesson: CourseLesson,
                                onBack: () -> Unit, onReviewWeak: (List<String>) -> Unit) {
    val context = LocalContext.current
    val repository = remember(helper) { CourseRepository(helper) }
    val scope = rememberCoroutineScope()
    var progress by remember(lesson.id) { mutableStateOf(repository.progress(lesson.id)) }
    val wrongSteps = remember(lesson.id, progress.step) {
        if (progress.complete) repository.wrongSteps(lesson.id) else emptyList()
    }
    var retryIndex by rememberSaveable(lesson.id) { mutableIntStateOf(-1) }
    val retryStep = wrongSteps.getOrNull(retryIndex)
    val displayedStep = retryStep ?: progress.step
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var typed by rememberSaveable(lesson.id, displayedStep, retryIndex) { mutableStateOf("") }
    var selectedOrder by rememberSaveable(lesson.id, displayedStep, retryIndex) { mutableStateOf(emptyList<Int>()) }
    var showReference by rememberSaveable(lesson.id, displayedStep, retryIndex) { mutableStateOf(false) }
    var hasVoiceRecording by remember(lesson.id, retryIndex) { mutableStateOf(false) }
    val start = remember(lesson.id, displayedStep, retryIndex) { SystemClock.elapsedRealtime() }
    var definitions by remember(lesson.id) { mutableStateOf(emptyMap<String, String>()) }
    var showAllWords by rememberSaveable(lesson.id) { mutableStateOf(false) }
    LaunchedEffect(lesson.id) {
        definitions = withContext(Dispatchers.IO) {
            DictionaryGateway.sourceOrNull()?.learningWords(setOf(lesson.bookId))
                ?.filter { it.word in lesson.words }
                ?.associate { it.word to it.zh.ifBlank { it.en } }
                .orEmpty()
        }
    }
    val speech = rememberPronunciationServices()
    val preference by speech.preference.collectAsState()
    val playback by speech.manager.state.collectAsState()
    var heard by remember(lesson.id, displayedStep, retryIndex) { mutableStateOf(false) }
    LaunchedEffect(playback.playbackCompleted, playback.word, displayedStep, retryIndex) {
        if (displayedStep == 2 && playback.word == lesson.focus && playback.playbackCompleted) heard = true
    }
    val meaningOptions = remember(lesson.id) {
        (listOf(lesson.meaning) + CourseCatalog.all.map { it.meaning }
            .filter { it != lesson.meaning }.take(3)).shuffled()
    }
    val wordOptions = remember(lesson.id) {
        (listOf(lesson.focus) + CourseCatalog.all.map { it.focus }
            .filter { it != lesson.focus }.take(3)).shuffled()
    }
    val orderTokens = remember(lesson.id) { lesson.orderedSentence.split(' ').shuffled() }
    val readingOptions = remember(lesson.id) {
        val sameTrack = CourseCatalog.all.filter { it.bookId == lesson.bookId }
        val index = sameTrack.indexOfFirst { it.id == lesson.id }
        val alternatives = if (lesson.readingDistractors.isNotEmpty()) lesson.readingDistractors
            else listOf(sameTrack[(index + 1) % sameTrack.size].readingAnswer,
                sameTrack[(index + 2) % sameTrack.size].readingAnswer)
        (listOf(lesson.readingAnswer) + alternatives).distinct().shuffled()
    }

    fun answer(correct: Boolean, explanation: String) {
        if (busy || feedback != null) return
        if (retryStep != null) {
            feedback = (if (correct) "这次答对了。" else "再留意这个知识点。") +
                "\n" + explanation + "\n本次重练不增加掌握次数，也不改变到期时间。"
            return
        }
        if (progress.complete) return
        busy = true
        scope.launch {
            try {
                val next = withContext(Dispatchers.IO) {
                    repository.answer(lesson, progress.step, correct, SystemClock.elapsedRealtime() - start)
                }
                progress = next
                feedback = (if (correct) "答对了。" else "再留意这个知识点。") + "\n" + explanation
            } catch (e: Exception) { error = "保存失败：${e.message}" }
            finally { busy = false }
        }
    }

    val noAudio = preference.dialect == null || playback.phase == SpeechPhase.ERROR
    TeaExercisePage(title = lesson.title,
        done = if (retryStep != null) retryIndex else progress.step,
        total = if (retryStep != null) wrongSteps.size else CourseRepository.STEPS,
        busy = busy, onBack = { if (retryStep != null) { retryIndex = -1; feedback = null } else onBack() },
        actions = {
            when {
                feedback != null -> TeaExerciseAction(if (retryStep != null) {
                    if (retryIndex + 1 < wrongSteps.size) "重练下一题" else "返回本课总结"
                } else if (progress.complete) "看学习结果" else "继续", !busy) {
                    feedback = null
                    if (retryStep != null) retryIndex = if (retryIndex + 1 < wrongSteps.size) retryIndex + 1 else -1
                }
                progress.complete && retryStep == null -> {
                    if (wrongSteps.isNotEmpty()) TeaExerciseAction("重练 ${wrongSteps.size} 道错题", !busy, secondary = true) { retryIndex = 0 }
                    TeaExerciseAction("复习本课重点词", !busy) { onReviewWeak(progress.mistakes.toList().ifEmpty { listOf(lesson.focus) }) }
                    TeaExerciseAction("返回课程", !busy, secondary = true, onClick = onBack)
                }
                else -> when (displayedStep) {
                    0 -> TeaExerciseAction("开始练习", !busy) { answer(true, "下一步开始练习 ${lesson.focus}。") }
                    1 -> meaningOptions.forEach { option ->
                        TeaExerciseOption(option, !busy) { answer(option == lesson.meaning, "${lesson.focus}：${lesson.meaning}") }
                    }
                    2 -> wordOptions.forEach { option ->
                        TeaExerciseOption(option, !busy && (heard || noAudio)) { answer(option == lesson.focus, "正确单词：${lesson.focus}") }
                    }
                    3 -> wordOptions.forEach { option ->
                        TeaExerciseOption(option, !busy) { answer(option == lesson.focus, lesson.example) }
                    }
                    4 -> {
                        TeaExerciseAction("检查句子", selectedOrder.size == orderTokens.size && !busy) {
                            answer(selectedOrder.joinToString(" ") { orderTokens[it] } == lesson.orderedSentence, lesson.orderedSentence)
                        }
                        TeaExerciseAction("重排", !busy, secondary = true) { selectedOrder = emptyList() }
                    }
                    5 -> readingOptions.forEach { option ->
                        TeaExerciseOption(option, !busy) { answer(option == lesson.readingAnswer, lesson.readingAnswer) }
                    }
                    6 -> if (!showReference) {
                        TeaExerciseAction("查看参考表达", typed.isNotBlank() && !busy) { showReference = true }
                    } else {
                        TeaExerciseAction("基本表达清楚", !busy) { answer(true, lesson.modelWriting) }
                        TeaExerciseAction("需要再练", !busy, secondary = true) { answer(false, lesson.modelWriting) }
                    }
                    7 -> {
                        TeaExerciseAction("朗读完成", hasVoiceRecording && !busy) { answer(true, lesson.orderedSentence) }
                        TeaExerciseAction("需要再练", !busy, secondary = true) { answer(false, lesson.orderedSentence) }
                    }
                }
            }
        }) {
        error?.let { item { TeaCaption(it) } }
        if (feedback != null) item {
            TeaExerciseContent {
                Text(feedback!!, style = MaterialTheme.typography.titleMedium)
            }
        } else if (progress.complete && retryStep == null) item {
            TeaExerciseContent {
                Text("本课完成", style = MaterialTheme.typography.headlineMedium)
                Text("已完成 ${CourseRepository.STEPS} 步；薄弱词：${progress.mistakes.joinToString("、").ifBlank { "暂无" }}")
                if (wrongSteps.isNotEmpty()) {
                    TeaCaption("本课薄弱题型：${wrongSteps.joinToString("、") { lessonStepName(it) }}")
                }
            }
        } else when (displayedStep) {
            0 -> item {
                TeaExerciseContent {
                    Text("认识本课词汇", style = MaterialTheme.typography.headlineSmall)
                    Text(lesson.focus, style = MaterialTheme.typography.headlineMedium)
                    Text(lesson.meaning, style = MaterialTheme.typography.titleLarge)
                    Text(lesson.example)
                    TeaCaption("常见搭配 · ${lesson.collocation}")
                    if (lesson.wordPart.isNotBlank()) TeaCaption("词形提示：${lesson.wordPart}")
                    TeaCaption("内容来源 · ${lesson.source}")
                }
                OutlinedButton(onClick = { showAllWords = !showAllWords }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAllWords) "收起本课词汇" else "查看本课 ${lesson.words.size} 个词")
                }
                if (showAllWords) {
                    TeaExerciseContent {
                        TeaCaption("其他词义来自内置 ECDICT 词典；词书标签不是官方大纲。")
                        lesson.words.forEach { word ->
                            Text(word, style = MaterialTheme.typography.titleMedium)
                            definitions[word]?.let { definition ->
                                TeaCaption(courseDefinitionPreview(definition))
                            }
                        }
                    }
                }
            }
            1 -> item {
                TeaExerciseContent {
                    Text("选择词义", style = MaterialTheme.typography.headlineSmall)
                    Text(lesson.focus, style = MaterialTheme.typography.displaySmall)
                }
            }
            2 -> item {
                TeaExerciseContent {
                    Text("听音辨词", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { preference.dialect?.let { speech.manager.play(lesson.focus, it) } },
                        enabled = preference.dialect != null) { Text("播放单词发音") }
                    val noAudio = preference.dialect == null || playback.phase == SpeechPhase.ERROR
                    if (noAudio) TeaCaption("设备语音不可用：${lesson.meaning}。可按词义继续完成本题。")
                    else if (!heard) TeaCaption("请先听完发音，再选择单词。")
                }
            }
            3 -> item {
                TeaExerciseContent {
                    Text("语境填空", style = MaterialTheme.typography.headlineSmall)
                    Text(lesson.example.replace(Regex("\\b${Regex.escape(lesson.focus)}\\b", RegexOption.IGNORE_CASE), "____"),
                        style = MaterialTheme.typography.titleLarge)
                }
            }
            4 -> item {
                TeaExerciseContent {
                    Text("排列句子", style = MaterialTheme.typography.headlineSmall)
                    Text(selectedOrder.joinToString(" ") { orderTokens[it] }.ifBlank { "依次点击下方单词" },
                        style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        orderTokens.forEachIndexed { index, token ->
                            if (index !in selectedOrder) FilledTonalButton(onClick = { selectedOrder = selectedOrder + index }, enabled = !busy) { Text(token) }
                        }
                    }
                }
            }
            5 -> item {
                TeaExerciseContent {
                    Text("阅读理解", style = MaterialTheme.typography.headlineSmall)
                    Text(lesson.passage, style = MaterialTheme.typography.bodyLarge)
                    TeaCaption(lesson.source)
                    Text(lesson.readingQuestion, style = MaterialTheme.typography.titleMedium)
                }
            }
            6 -> item {
                TeaExerciseContent {
                    Text("简短写作", style = MaterialTheme.typography.headlineSmall)
                    Text(lesson.writingPrompt)
                    OutlinedTextField(value = typed, onValueChange = { typed = it.take(600) },
                        label = { Text("用英语写一两句") }, modifier = Modifier.fillMaxWidth(),
                        minLines = 3, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    if (showReference) {
                        Text("参考表达：${lesson.modelWriting}")
                        TeaCaption("请按意思是否清楚、表达是否基本正确自评；参考答案不是唯一写法。")
                    }
                }
            }
            7 -> item {
                TeaExerciseContent {
                    Text("听说练习", style = MaterialTheme.typography.headlineSmall)
                    Text("听示范，再朗读这句话并回听自己的录音。")
                    Text(lesson.orderedSentence, style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { preference.dialect?.let { speech.manager.play(lesson.orderedSentence, it) } },
                        enabled = preference.dialect != null) { Text("播放 TTS 示范") }
                    CourseVoiceRecorder(lesson.id, onHasRecordingChange = { hasVoiceRecording = it })
                    TeaCaption("这一步由你自评，不进行自动发音评分。")
                }
            }
        }
    }
}
