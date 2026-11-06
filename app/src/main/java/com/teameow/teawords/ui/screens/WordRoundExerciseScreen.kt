package com.teameow.teawords.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
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
    onExtraSpelling: suspend (String, Boolean, Boolean, Long) -> Boolean = { _, _, _, _ -> true },
    completedStages: Int = completedWords * 3 + progress.completed,
    roundPhase: Int = progress.completed.coerceAtMost(2), turnPosition: Int = completedWords,
    drawBackground: Boolean = true,
    phraseBank: List<Pair<String, String>> = emptyList(), onPhrasesPrepared: (List<Pair<String, String>>) -> Unit = {},
    phraseLookup: suspend (String) -> List<Pair<String, String>> = { OnlineDictionary.shared.lookup(it).entry?.detail?.phrases.orEmpty() }
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("learning", 0) }
    var useWallpaper by rememberSaveable { mutableStateOf(prefs.getBoolean("word_card_wallpaper_v2", false)) }
    var step by rememberSaveable(task.word) { mutableIntStateOf(if (roundPhase == 1) 1 else progress.completed.coerceAtMost(2)) }
    var feedback by rememberSaveable(task.word) { mutableStateOf(if (progress.completed == 3 || roundPhase == 1 && progress.completed == 2) true else null) }
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
    var practiceToken by rememberSaveable(task.word) { mutableStateOf("") }
    var practiceStarted by rememberSaveable(task.word) { mutableLongStateOf(0L) }
    var practiceHint by rememberSaveable(task.word) { mutableStateOf(false) }
    val scroll = rememberLazyListState()
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    var working by remember { mutableStateOf(false) }
    val navigationEnabled = !busy && !working
    val latestBusy by rememberUpdatedState(busy)
    val enabled = navigationEnabled && detailsLoaded && !usageLoading
    var reviewedPrompt by remember(task.word, step) { mutableStateOf<WordExercisePrompt?>(null) }
    val preparedPrompt = remember(task, step, bank, details.phrases, phraseBank) { WordExercisePlanner.build(task, step, bank, details.phrases, phraseBank) }
    val prompt = reviewedPrompt ?: preparedPrompt
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
    LaunchedEffect(details.phrases) { if (details.phrases.isNotEmpty()) onPhrasesPrepared(details.phrases) }
    DisposableEffect(audio) { onDispose { audio.close(); speech.manager.stop(); focus.clearFocus(force = true); keyboard?.hide() } }

    fun help(open: () -> Unit) {
        if (!navigationEnabled || !detailsLoaded) return
        working = true
        scope.launch { try { if (onHelp()) { usedHint = true; focus.clearFocus(); keyboard?.hide(); open() } } finally { working = false } }
    }
    fun submit(correct: Boolean, assisted: Boolean = false) {
        if (!enabled || feedback != null) return
        if (step == 1) reviewedPrompt = prompt
        working = true
        focus.clearFocus(force = true); keyboard?.hide()
        val elapsed = activeTime[0] + if (activeTime[1] == 0L) 0 else android.os.SystemClock.elapsedRealtime() - activeTime[1]
        scope.launch { try {
            if (onExercise(step, progress.attempts, correct, assisted, elapsed)) {
                // Successful early steps navigate to another turn. Keep this outgoing card unchanged.
                if (step > 0 || !correct || assisted) feedback = correct && !assisted
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
    fun openPractice() {
        practiceAnswer = ""; practiceResult = null; practiceHint = false
        practiceToken = java.util.UUID.randomUUID().toString()
        practiceStarted = android.os.SystemClock.elapsedRealtime()
        extraSpelling = true
    }
    fun submitPractice(revealed: Boolean = false) {
        if (!navigationEnabled || practiceResult != null) return
        val correct = !revealed && LexicalText.normalize(practiceAnswer) == LexicalText.normalize(task.word)
        // This word has just been shown in the learning round; record training, not spaced independent evidence.
        val assisted = revealed || practiceHint || introductionKnown != null || roundPhase < 2 || feedback != null
        val elapsed = (android.os.SystemClock.elapsedRealtime() - practiceStarted).coerceIn(0, 300_000)
        working = true
        scope.launch { try {
            if (onExtraSpelling(practiceToken, correct, assisted, elapsed)) practiceResult = StudySpellingResult(task.word, correct, revealed)
        } finally { working = false } }
    }
    fun continueExercise() {
        if (!enabled) return
        if (progress.completed == 3) onFinish()
        else if (step == 1 || feedback == false) {
            working = true
            scope.launch { try {
                if (onRetryLater()) {
                    withFrameNanos { }
                    // If this word was moved out, preserve its feedback throughout the exit animation.
                    if (!latestBusy) { selected = null; answer = ""; feedback = null; usedHint = false; reviewedPrompt = null }
                }
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
            progress = completedStages to totalWords * 3, closeLabel = "暂停学习", completedSteps = progress.completed,
            onReveal = { submit(false, true) }, drawBackground = drawBackground)
    } else {
        Box(Modifier.fillMaxSize()) {
            if (drawBackground) StudyAmbientBackground(activeWallpaper)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    FilledTonalIconButton(onClick = onPause, enabled = navigationEnabled, modifier = Modifier.size(48.dp)) { Icon(AppSymbols.ArrowBack, "暂停学习") }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.width(140.dp).padding(horizontal = 20.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$completedStages / ${totalWords * 3}", style = MaterialTheme.typography.titleMedium)
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
                            if (horizontal < -threshold && currentEnabled) help { openPractice() }
                        }) { change, delta -> horizontal += delta; change.consume() }
                    }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Surface(shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .94f)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(when { roundPhase == 0 -> "认读"; roundPhase == 2 -> "拼写巩固"; prompt.kind == WordExerciseKind.USAGE -> "搭配辨认"; else -> "词义辨认" },
                                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    WordExerciseDots(progress.completed)
                                }
                                if (step == 0 || step == 2 && feedback != null) StudyAdaptiveWord(task.word)
                                else if (prompt.kind == WordExerciseKind.USAGE) StudyAdaptiveWord(prompt.prompt)
                                else Text(prompt.prompt.lineSequence().firstOrNull().orEmpty(), style = MaterialTheme.typography.titleLarge)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (step == 0 || step == 2) TextButton(onClick = { audio.play(task.word, pronunciation.dialect ?: PronunciationDialect.US) }) {
                                        Icon(AppSymbols.VolumeUp, "播放发音"); Spacer(Modifier.width(6.dp))
                                        Text(details.phonetic.takeIf { it.isNotBlank() }?.let { "/${it.trim('/')}/" } ?: "播放发音")
                                    }
                                    else FilledTonalIconButton(onClick = { audio.play(if (prompt.kind == WordExerciseKind.USAGE) prompt.prompt else task.word, pronunciation.dialect ?: PronunciationDialect.US) }) { Icon(AppSymbols.VolumeUp, "播放发音") }
                                    Spacer(Modifier.width(8.dp))
                                    FilledTonalIconButton(onClick = { help { openPractice() } }, enabled = enabled) {
                                        Icon(Icons.Outlined.Keyboard, "拼写练习")
                                    }
                                }
                            }
                        }
                    }
                    if (step == 0 && introductionKnown != null && feedback == null) item {
                        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .96f)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(task.definition, style = MaterialTheme.typography.titleLarge)
                                if (details.phrases.isNotEmpty()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                                    details.phrases.take(2).forEach { (phrase, meaning) ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(phrase, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                            Text(meaning, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (usageLoading) item { Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("正在准备搭配练习…")
                    } }
                    else if (feedback == null || step == 1) items(prompt.choices.size) { index ->
                        val choice = prompt.choices[index]
                        val correctOption = feedback != null && choice == prompt.answer
                        val wrongSelection = feedback != null && selected == choice && !correctOption
                        val resultColor = if (correctOption) MaterialTheme.colorScheme.primary else if (wrongSelection) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                        Surface(onClick = { selected = choice; submit(choice == prompt.answer) }, enabled = enabled && feedback == null,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(if (correctOption || wrongSelection) 2.dp else 1.dp, resultColor),
                            color = when { correctOption -> MaterialTheme.colorScheme.primaryContainer; wrongSelection -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surfaceContainerLow }.copy(alpha = .96f),
                            modifier = Modifier.fillMaxWidth().testTag("word-choice-$index")) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(when { correctOption -> AppSymbols.Check; wrongSelection -> AppSymbols.Close; else -> Icons.Outlined.RadioButtonUnchecked },
                                    when { correctOption -> "正确答案"; wrongSelection -> "你的选择"; else -> null }, Modifier.size(24.dp),
                                    tint = if (correctOption || wrongSelection) resultColor else MaterialTheme.colorScheme.onSurfaceVariant)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (feedback != null) Text(prompt.choiceMatches[choice].orEmpty(), style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(choice, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
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
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (activeWallpaper != null) .78f else 1f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        if (feedback != null) Button(onClick = ::continueExercise, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(if (progress.completed == 3) "下一个单词" else if (step == 1) "下一题" else "继续练习")
                        } else if (step == 0 && introductionKnown == null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { introduce(true) }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("认识") }
                            FilledTonalButton(onClick = { introduce(false) }, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("不认识") }
                        } else if (step == 0) Button(onClick = { submit(true) }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("word-intro-continue")) {
                            Text(if (turnPosition + 1 < totalWords) "记住了，下一个" else "开始第一轮巩固")
                        }
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
        StudySpellingScreen(task.definition, practiceAnswer, { practiceAnswer = it }, error, details.phonetic, activeWallpaper,
            navigationEnabled, onClose = close, onHint = { practiceHint = true }, onSubmit = { submitPractice() },
            result = practiceResult, onContinue = close, onReveal = { submitPractice(true) })
    }
}

@Composable
internal fun WordExerciseDots(completed: Int) {
    Row(Modifier.semantics { contentDescription = "本词已完成 ${completed.coerceIn(0, 3)} / 3 项练习" }, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(3) { index -> Box(Modifier.size(8.dp).background(if (index < completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)) }
    }
}
