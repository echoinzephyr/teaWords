package com.teameow.teawords.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teameow.teawords.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File

@Composable
internal fun CourseListScreen(helper: DatabaseHelper, onBack: () -> Unit, onOpen: (CourseLesson) -> Unit) {
    val repository = remember(helper) { CourseRepository(helper) }
    val progress = remember { repository.allProgress() }
    var query by rememberSaveable { mutableStateOf("") }
    val finished = progress.values.count { it.complete }
    val next = CourseCatalog.all.firstOrNull { CourseReviewTiming.due(progress.getValue(it.id), System.currentTimeMillis()) }
        ?: CourseCatalog.all.firstOrNull { progress[it.id]?.let { p -> p.step > 0 && !p.complete } == true }
        ?: CourseCatalog.all.firstOrNull { progress[it.id]?.complete != true }
    TeaListPage("入门英语", subtitle = "3 个单元 · 12 课", onBack = onBack) {
        item {
            TeaCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Text("今天学会一件小事", style = MaterialTheme.typography.titleLarge)
                Text("听懂情境，练习表达，再换个场景试一试。")
                TeaCaption("已完成 $finished / ${CourseCatalog.all.size} 课")
                LinearProgressIndicator(progress = { finished.toFloat() / CourseCatalog.all.size }, modifier = Modifier.fillMaxWidth())
                next?.let { Button(onClick = { onOpen(it) }, modifier = Modifier.fillMaxWidth()) {
                    Text(when {
                        CourseReviewTiming.due(progress.getValue(it.id), System.currentTimeMillis()) -> "复习第 ${it.number} 课"
                        progress[it.id]?.step == 0 -> "开始第 ${it.number} 课"
                        else -> "继续第 ${it.number} 课"
                    })
                } }
            }
        }
        item { OutlinedTextField(query, { query = it }, label = { Text("找课程") },
            placeholder = { Text("课名、目标、词句或课号") }, leadingIcon = { Icon(AppSymbols.Search, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (query.isNotBlank()) {
            val matches = CourseDirectory.search(query)
            item { TeaSectionLabel("搜索结果 · ${matches.size} 课") }
            if (matches.isEmpty()) item { TeaCaption("暂时没有匹配的课程。") }
            itemsIndexed(matches, key = { _, it -> it.id }) { _, lesson -> CourseLessonRow(lesson, progress[lesson.id], onOpen) }
        } else CourseDirectory.chapters().forEach { chapter ->
            item { TeaSectionLabel(chapter.title); TeaCaption(chapter.description) }
            itemsIndexed(chapter.lessons, key = { _, it -> it.id }) { _, lesson -> CourseLessonRow(lesson, progress[lesson.id], onOpen) }
        }
        item { TeaCaption("课文与原声：VOA Learning English · Let’s Learn English Level 1。中文讲解和练习由茶词编排。") }
    }
}

@Composable
private fun CourseLessonRow(lesson: CourseLesson, progress: CourseRepository.Progress?, onOpen: (CourseLesson) -> Unit) {
    TeaCard(Modifier.clickable { onOpen(lesson) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("%02d".format(lesson.number), style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                TeaCaption(lesson.teaching.mission)
                TeaCaption(when {
                    progress?.complete == true -> "本轮已过关 · 可复习"
                    progress?.attemptedAll == true -> "已练完 · ${progress.mistakes.size} 题待巩固"
                    else -> "${lesson.minutes} 分钟 · ${progress?.answers?.size ?: 0}/${lesson.questions.size} 题已练"
                })
            }
            Icon(AppSymbols.ChevronRight, contentDescription = "打开第 ${lesson.number} 课")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CourseReferenceScreen(helper: DatabaseHelper, lesson: CourseLesson, onBack: () -> Unit,
                                   initialSegmentId: String? = null) {
    val context = LocalContext.current
    val repository = remember(helper) { CourseRepository(helper) }
    val media = remember { CourseMedia(context) }
    val scope = rememberCoroutineScope()
    var progress by remember(lesson.id) { mutableStateOf(repository.progress(lesson.id)) }
    var checkpoint by remember(lesson.id) { mutableStateOf(progress.checkpoint) }
    var section by rememberSaveable(lesson.id) { mutableIntStateOf(checkpoint.section.coerceIn(0, 1)) }
    var showChinese by rememberSaveable(lesson.id) { mutableStateOf(true) }
    var selectedSentence by rememberSaveable(lesson.id) { mutableIntStateOf(checkpoint.sentence.coerceIn(0, lesson.segments.lastIndex)) }
    var follow by rememberSaveable(lesson.id) { mutableStateOf(checkpoint.follow) }
    var file by remember(lesson.id) { mutableStateOf<File?>(null) }
    var loading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var mediaError by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var savingError by remember { mutableStateOf<String?>(null) }
    fun save(position: Int? = null, sentence: Int? = null) {
        checkpoint = checkpoint.copy(section = section, sentence = sentence ?: selectedSentence,
            positionMs = position ?: checkpoint.positionMs,
            started = true, follow = follow)
        try { repository.save(lesson.id, checkpoint); savingError = null }
        catch (_: Exception) { savingError = "进度保存失败，请保留本页并重试" }
    }
    val player = rememberCourseAudioPlayer(lesson) { value ->
        checkpoint = checkpoint.copy(speed = value.speed)
        save(value.position, value.sentence)
    }
    val list = rememberLazyListState(checkpoint.listIndex.coerceAtLeast(0), checkpoint.listOffset.coerceAtLeast(0))
    fun download() {
        downloadJob?.cancel()
        downloadJob = scope.launch {
            loading = true; mediaError = null
            try {
                file = media.download(lesson.audio) { done, total -> downloadProgress = done.toFloat() / total }
                file?.let { player.prepare(it, checkpoint.positionMs) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mediaError = "原声下载未完成，请检查网络后重试；已下载片段会保留。" }
            finally { loading = false }
        }
    }
    LaunchedEffect(lesson.id) {
        file = withContext(Dispatchers.IO) { media.cached(lesson.audio) }
        player.speed = checkpoint.speed
        file?.let { player.prepare(it, checkpoint.positionMs) }
        save()
        initialSegmentId?.let { id ->
            lesson.segments.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { index ->
                section = 0; selectedSentence = index; list.scrollToItem(index + 3)
            }
        }
    }
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
            .distinctUntilChanged().collect { (index, offset) ->
                checkpoint = checkpoint.copy(listIndex = index, listOffset = offset)
            }
    }
    LaunchedEffect(player.playing) {
        while (player.playing) { delay(1000); save(player.position, player.sentence) }
    }
    LaunchedEffect(player.sentence, player.playing, section, follow) {
        if (player.playing && section == 0 && follow) {
            selectedSentence = player.sentence
            // Keep a reader's manual scroll when paused. Only active playback follows the transcript.
            list.animateScrollToItem(player.sentence + 3)
        }
    }
    fun openSection(index: Int) {
        player.pause(); section = index; save(player.position)
        scope.launch { list.scrollToItem(0) }
    }
    fun locate(segmentId: String) {
        val index = lesson.segments.indexOfFirst { it.id == segmentId }
        player.pause(); section = 0; selectedSentence = index; save(player.position)
        scope.launch { list.animateScrollToItem(index + 3) }
    }
    TeaListPage(lesson.title, expandedTitle = false, listState = list,
        header = {
            Column {
                TopAppBar(title = { Column {
                    Text("第 ${lesson.number} 课 · 学习资料", style = MaterialTheme.typography.titleMedium)
                    Text(lesson.subtitle, style = MaterialTheme.typography.bodySmall)
                } }, navigationIcon = { IconButton(onClick = { player.pause(); save(player.position); onBack() }) {
                    Icon(AppSymbols.ArrowBack, "返回")
                } })
                PrimaryTabRow(selectedTabIndex = section) {
                    listOf("课文", "词句笔记").forEachIndexed { index, title ->
                        Tab(selected = section == index, onClick = { openSection(index) }, text = { Text(title) })
                    }
                }
            }
        }) {
        savingError?.let { item { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { save() }) { Text("重试保存") } } }
        when (section) {
            0 -> {
                item {
                    Text(lesson.title, style = MaterialTheme.typography.headlineSmall)
                    lesson.objectives.forEach { TeaCaption("• $it") }
                }
                item {
                    TeaCard {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (file == null) Button(onClick = { if (loading) downloadJob?.cancel() else download() }) {
                                Text(if (loading) "暂停下载" else "下载本课原声 · ${lesson.audio.bytes / 1024} KB")
                            } else Button(enabled = player.ready, onClick = {
                                if (player.playing) player.pause() else player.play()
                            }) { Text(if (player.playing) "暂停" else "整课播放") }
                            if (file != null) listOf(0.75f, 1f, 1.25f, 1.5f).forEach { speed ->
                                FilterChip(selected = player.speed == speed, onClick = {
                                    player.changeSpeed(speed); checkpoint = checkpoint.copy(speed = speed); save()
                                }, label = { Text("${speed}×") })
                            }
                        }
                        if (loading) LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                        if (file != null && player.ready) {
                            Slider(value = player.position.toFloat(), onValueChange = {
                                player.pause(); player.seek(it.toInt()); save(it.toInt())
                            }, valueRange = 0f..lesson.audio.durationMs.toFloat())
                            TeaCaption("${formatCourseTime(player.position)} / ${formatCourseTime(lesson.audio.durationMs)} · 已下载，可离线播放")
                        }
                        (mediaError ?: player.error)?.let { Text(it, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { if (file != null) player.prepare(file!!, player.position) else download() }) { Text("重试原声") } }
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(showChinese, { showChinese = !showChinese }, label = { Text("中文译文") })
                        FilterChip(follow, { follow = !follow; save() }, label = { Text("跟随原声") })
                        FilterChip(player.loop, { player.loop = !player.loop }, label = { Text("单句循环") })
                    }
                    TeaCaption("点句子听原声；选中的句子可以跟读录音。")
                }
                itemsIndexed(lesson.segments, key = { _, s -> s.id }) { index, segment ->
                    TeaCard(Modifier.testTag("course-segment-${segment.id}").clickable {
                        selectedSentence = index; player.play(index); save(player.position, index)
                    }, containerColor = if (index == selectedSentence) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow) {
                        TeaCaption("${index + 1} · ${segment.speaker}")
                        Text(segment.english, style = MaterialTheme.typography.bodyLarge)
                        if (showChinese) Text(segment.chinese, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (index == selectedSentence && !player.playing) {
                            OutlinedButton(enabled = player.ready, onClick = { player.play(index) }) { Text("听这一句") }
                            CourseVoiceRecorder("${lesson.id}-${segment.id}", onBeforeRecord = { player.pause() })
                        }
                    }
                }
                item { TeaCaption("跟读录音仅保存在本机，可回听或删除。") }
                item { TeaCaption("${lesson.source.credit}\n${lesson.source.url}") }
            }
            1 -> {
                item { TeaSectionLabel("本课重点词句") }
                lesson.keyPhrases.forEach { phrase -> item {
                    TeaCard(Modifier.clickable { locate(phrase.segmentId) }) {
                        Text(phrase.english, style = MaterialTheme.typography.titleMedium)
                        Text(phrase.chinese); TeaCaption("查看原文 →")
                    }
                } }
                item { TeaSectionLabel("表达与语法") }
                lesson.explanations.forEach { point -> item {
                    TeaCard {
                        Text(point.title, style = MaterialTheme.typography.titleMedium)
                        Text(point.text); Text(point.example, color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { locate(point.segmentId) }) { Text("查看原文") }
                    }
                } }
            }
        }
    }
}

private fun formatCourseTime(ms: Int) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
