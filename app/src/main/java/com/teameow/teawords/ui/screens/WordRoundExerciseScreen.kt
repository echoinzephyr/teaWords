package com.teameow.teawords.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.teameow.teawords.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Three distinct exercises share one word's progress; only the caller commits a memory review. */
@Composable
internal fun WordRoundExerciseScreen(
    helper: DatabaseHelper, task: WordLearningTask, progress: WordExerciseProgress,
    completedWords: Int, totalWords: Int, bank: List<WordLearningTask>, busy: Boolean, error: String?,
    wallpaper: String?, initialDetails: StudyWordDetails?, onPause: () -> Unit,
    onExercise: suspend (Int, Int, Boolean, Boolean, Long) -> Boolean,
    onHelp: suspend () -> Boolean, onFinish: () -> Unit,
    onIntroduction: suspend (Boolean) -> Boolean = { true },
    onRetryLater: suspend () -> Boolean = { true },
    completedStages: Int = completedWords * 3 + progress.completed,
    roundPhase: Int = progress.completed.coerceAtMost(2), turnPosition: Int = completedWords,
    phraseLookup: suspend (String) -> List<Pair<String, String>> = { OnlineDictionary.shared.lookup(it).entry?.detail?.phrases.orEmpty() }
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("learning", 0) }
    var useWallpaper by rememberSaveable { mutableStateOf(prefs.getBoolean("word_card_wallpaper_v2", false)) }
    var step by rememberSaveable(task.word) { mutableIntStateOf(progress.completed.coerceAtMost(2)) }
    var feedback by rememberSaveable(task.word) { mutableStateOf(if (progress.completed == 3) true else null) }
    var selected by rememberSaveable(task.word, step) { mutableStateOf<String?>(null) }
    var answer by rememberSaveable(task.word, step) { mutableStateOf("") }
    var usedHint by rememberSaveable(task.word, step) { mutableStateOf(false) }
    var introductionKnown by rememberSaveable(task.word) { mutableStateOf(progress.introductionKnown) }
    var details by remember(task.word) { mutableStateOf(initialDetails ?: StudyWordDetails()) }
    var detailsLoaded by remember(task.word) { mutableStateOf(initialDetails != null) }
    var usageLoading by remember(task.word, step) { mutableStateOf(step == 1 && details.phrases.isEmpty()) }
    var menu by remember { mutableStateOf(false) }
    var lookup by rememberSaveable(task.word) { mutableStateOf(false) }
    var reader by rememberSaveable(task.word) { mutableStateOf(false) }
    var extraSpelling by rememberSaveable(task.word) { mutableStateOf(false) }
    var practiceAnswer by rememberSaveable(task.word) { mutableStateOf("") }
    var practiceResult by remember { mutableStateOf<StudySpellingResult?>(null) }
    val scroll = rememberLazyListState()
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    var working by remember { mutableStateOf(false) }
    val navigationEnabled = !busy && !working
    val enabled = navigationEnabled && detailsLoaded && !usageLoading
    val prompt = remember(task, step, bank, details.phrases) { WordExercisePlanner.build(task, step, bank, details.phrases) }
    val speech = rememberPronunciationServices()
    val audio = remember(speech.manager) { com.teameow.teawords.data.pronunciation.DictionaryAudioPlayer(context, speech.manager) }
    val pronunciation by speech.preference.collectAsState()
    val activeWallpaper = if (useWallpaper) wallpaper else null
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activeTime = remember(task.word, step, feedback, lookup, reader, extraSpelling) { longArrayOf(0, 0) }
    DisposableEffect(activeTime, lifecycle) {
        val timing = feedback == null && !lookup && !reader && !extraSpelling
        fun pause() {
            if (activeTime[1] != 0L) activeTime[0] += android.os.SystemClock.elapsedRealtime() - activeTime[1]
            activeTime[1] = 0L
        }
        if (timing && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) activeTime[1] = android.os.SystemClock.elapsedRealtime()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) { pause(); audio.stop(); speech.manager.stop() }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && timing) activeTime[1] = android.os.SystemClock.elapsedRealtime()
        }
        lifecycle.addObserver(observer)
        onDispose { pause(); lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(task.word) {
        focus.clearFocus(force = true); keyboard?.hide()
        if (initialDetails == null) details = withContext(Dispatchers.IO) { runCatching { StudyWordDetailsRepository(helper).load(task.word) }.getOrDefault(StudyWordDetails()) }
        detailsLoaded = true
    }
    LaunchedEffect(task.word, step, detailsLoaded, introductionKnown) {
        if ((step == 1 || step == 0 && introductionKnown != null) && detailsLoaded && details.phrases.isEmpty()) {
            usageLoading = true
            try {
                val phrases = kotlinx.coroutines.withTimeoutOrNull(1500) { phraseLookup(task.word) }.orEmpty()
                if (phrases.isNotEmpty()) details = details.copy(phrases = phrases)
                // Online phrases stay in this composition / the process LRU, never in the round JSON.
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { /* Offline practice uses reverse recognition from the local bank. */ }
            finally { usageLoading = false }
        } else if (detailsLoaded) usageLoading = false
    }
    DisposableEffect(audio) { onDispose { audio.close(); speech.manager.stop(); focus.clearFocus(force = true); keyboard?.hide() } }

    fun help(open: () -> Unit) {
        if (!navigationEnabled || !detailsLoaded) return
        working = true
        scope.launch { try { if (onHelp()) { usedHint = true; focus.clearFocus(); keyboard?.hide(); open() } } finally { working = false } }
    }
    fun submit(correct: Boolean, assisted: Boolean = false) {
        if (!enabled || feedback != null) return
        working = true
        focus.clearFocus(force = true); keyboard?.hide()
        val elapsed = activeTime[0] + if (activeTime[1] == 0L) 0 else android.os.SystemClock.elapsedRealtime() - activeTime[1]
        scope.launch { try {
            if (onExercise(step, progress.attempts, correct, assisted, elapsed)) {
                if (step == 0 && correct && !assisted) { step = 1; selected = null; feedback = null }
                else feedback = correct && !assisted
            }
        } finally { working = false } }
    }
    fun introduce(known: Boolean) {
        if (!enabled || introductionKnown != null) return
        working = true
        scope.launch { try {
            if (onIntroduction(known)) introductionKnown = known
        } finally { working = false } }
    }
    fun continueExercise() {
        if (!enabled) return
        if (progress.completed == 3) onFinish()
        else if (feedback == false) {
            working = true
            scope.launch { try {
                if (onRetryLater()) { selected = null; answer = ""; feedback = null; usedHint = false }
            } finally { working = false } }
        }
        else {
            step = progress.completed; selected = null; answer = ""; feedback = null; usedHint = false
            scope.launch { scroll.scrollToItem(0) }
        }
    }
    val currentEnabled by rememberUpdatedState(enabled)
    val openLookup by rememberUpdatedState<() -> Unit> { help { lookup = true } }
    val pullSearch = remember(scroll, threshold) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!currentEnabled || source != NestedScrollSource.UserInput || scroll.canScrollBackward || available.y <= 0) return Offset.Zero
                pull = (pull + available.y).coerceAtMost(threshold * 1.5f)
                return Offset(0f, available.y)
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                val hadPull = pull > 0
                if (pull >= threshold && currentEnabled) openLookup()
                pull = 0f
                return if (hadPull) Velocity(0f, available.y) else Velocity.Zero
            }
        }
    }

    if (step == 2 && feedback == null && !lookup && !reader && !extraSpelling) {
        StudySpellingScreen(task.definition, answer, { answer = it }, error, details.phonetic,
            activeWallpaper, enabled, onClose = onPause, onHint = { help {} },
            onSubmit = { submit(LexicalText.normalize(answer) == LexicalText.normalize(task.word), usedHint) },
            progress = completedWords to totalWords, closeLabel = "暂停学习", completedSteps = progress.completed,
            onReveal = { submit(false, true) })
    } else {
        Box(Modifier.fillMaxSize()) {
            StudyAmbientBackground(activeWallpaper)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    FilledTonalIconButton(onClick = onPause, enabled = navigationEnabled, modifier = Modifier.size(48.dp)) { Icon(AppSymbols.ArrowBack, "暂停学习") }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.width(140.dp).padding(horizontal = 20.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${completedWords + if (progress.completed == 3) 1 else 0} / $totalWords", style = MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(progress = { completedStages.toFloat() / (totalWords * 3).coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp), drawStopIndicator = {})
                        }
                    }
                    Box {
                        FilledTonalIconButton(onClick = { menu = true }, enabled = navigationEnabled, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreHoriz, "更多") }
                        TeaDropdownMenu(menu, { menu = false }, listOf(TeaMenuSection(listOf(
                            TeaMenuOption("查看词卡、收藏与笔记", AppSymbols.Info) { menu = false; help { reader = true } },
                            TeaMenuOption("查词", AppSymbols.Search) { menu = false; help { lookup = true } },
                            TeaMenuOption(if (useWallpaper) "使用应用主题" else "跟随首页壁纸", AppSymbols.Wallpaper) {
                                useWallpaper = !useWallpaper; prefs.edit().putBoolean("word_card_wallpaper_v2", useWallpaper).apply(); menu = false
                            },
                            TeaMenuOption("暂停，稍后继续", AppSymbols.ArrowBack) { menu = false; onPause() }
                        ))))
                    }
                }
                LazyColumn(state = scroll, modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(pullSearch)
                    .testTag("word-round-content").pointerInput(task.word, enabled) {
                        var horizontal = 0f
                        detectHorizontalDragGestures(onDragStart = { horizontal = 0f }, onDragEnd = {
                            if (horizontal < -threshold && currentEnabled) help { practiceAnswer = ""; practiceResult = null; extraSpelling = true }
                        }) { change, delta -> horizontal += delta; change.consume() }
                    }, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .92f)) {
                            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (step == 0 || feedback != null) StudyAdaptiveWord(task.word)
                                else Text(prompt.prompt, style = MaterialTheme.typography.headlineSmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { audio.play(task.word, pronunciation.dialect ?: PronunciationDialect.US) }) {
                                        Icon(AppSymbols.VolumeUp, "播放发音"); Spacer(Modifier.width(6.dp))
                                        Text(if (step == 1 && feedback == null) "播放发音" else details.phonetic.takeIf { it.isNotBlank() }?.let { "/${it.trim('/')}/" } ?: "播放发音")
                                    }
                                    IconButton(onClick = { help { practiceAnswer = ""; practiceResult = null; extraSpelling = true } }, enabled = enabled) {
                                        Icon(Icons.Outlined.Keyboard, "拼写练习")
                                    }
                                }
                                WordExerciseDots(progress.completed)
                                Text("${when (roundPhase) { 0 -> "认读"; 1 -> "第一轮巩固"; else -> "第二轮巩固" }} · ${turnPosition + 1} / $totalWords",
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(when (prompt.kind) {
                                    WordExerciseKind.INTRODUCTION -> if (introductionKnown == null) "先认读，再巩固" else "听一听，记住词义和用法"
                                    WordExerciseKind.USAGE -> "选择短语或搭配的含义"
                                    WordExerciseKind.REVERSE -> "根据释义选择单词"
                                    WordExerciseKind.SPELLING -> "根据释义拼写单词"
                                }, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (step == 0 && introductionKnown != null && feedback == null) item {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(task.definition, style = MaterialTheme.typography.headlineSmall)
                                details.phrases.take(2).forEach { (phrase, meaning) ->
                                    Text(phrase, style = MaterialTheme.typography.titleMedium)
                                    Text(meaning, style = MaterialTheme.typography.bodyLarge)
                                }
                                Text(if (introductionKnown == false) "先看释义、听发音，记住后再开始巩固。" else "核对一下词义，随后用两项练习巩固。",
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (usageLoading) item { Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("正在准备搭配练习…")
                    } }
                    else if (feedback == null) items(prompt.choices.size) { index ->
                        val choice = prompt.choices[index]
                        Surface(onClick = { selected = choice; submit(choice == prompt.answer) }, enabled = enabled,
                            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .96f),
                            modifier = Modifier.fillMaxWidth().testTag("word-choice-$index")) {
                            Text(choice, Modifier.padding(horizontal = 20.dp, vertical = 18.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    else item {
                        Surface(shape = MaterialTheme.shapes.large, color = if (feedback == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (feedback == true) "答对了" else "再记一次", style = MaterialTheme.typography.titleLarge)
                                selected?.takeIf { feedback == false && it != prompt.answer }?.let { Text("你选的是：$it", style = MaterialTheme.typography.bodyMedium) }
                                Text(prompt.explanation, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    if (pull > 0) item { TeaCaption(if (pull >= threshold) "松开查词" else "继续下拉查词") }
                    error?.let { item { TeaStatusMessage(it, error = true) } }
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                        if (feedback != null) Button(onClick = ::continueExercise, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(if (progress.completed == 3) "下一个单词" else if (feedback == true) "继续下一项" else "继续练习")
                        } else if (step == 0 && introductionKnown == null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { introduce(true) }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("认识") }
                            FilledTonalButton(onClick = { introduce(false) }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("不认识") }
                        } else if (step == 0) Button(onClick = { submit(true) }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("记住了，开始巩固") }
                        else TextButton(onClick = { submit(false, true) }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("想不起来，回看讲解") }
                    }
                }
            }
        }
    }
    if (lookup) PredictiveBackLayer(enterFromRight = false, onBack = { lookup = false }) { close -> LookupScreen(task.word, onBack = close) }
    if (reader) PredictiveBackLayer(enterFromRight = false, onBack = { reader = false }) { close ->
        ImmersiveWordScreen(helper, task.word, task.definition, completedWords + 1, totalWords, false, null,
            activeWallpaper, onPause = close, initialDetails = details, onComplete = { _, _ -> close() })
    }
    if (extraSpelling) PredictiveBackLayer(enterFromRight = true, onBack = { extraSpelling = false }) { close ->
        StudySpellingScreen(task.definition, practiceAnswer, { practiceAnswer = it }, null, details.phonetic, activeWallpaper,
            enabled, onClose = close, onHint = {}, onSubmit = {
                practiceResult = StudySpellingResult(task.word, LexicalText.normalize(practiceAnswer) == LexicalText.normalize(task.word), false)
            }, result = practiceResult, onContinue = close,
            onReveal = { practiceResult = StudySpellingResult(task.word, false, true) })
    }
}

@Composable
internal fun WordExerciseDots(completed: Int) {
    Row(Modifier.semantics { contentDescription = "本词已完成 ${completed.coerceIn(0, 3)} / 3 项练习" }, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(3) { index -> Box(Modifier.size(8.dp).background(if (index < completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)) }
    }
}
