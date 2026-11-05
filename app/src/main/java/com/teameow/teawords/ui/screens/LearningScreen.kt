package com.teameow.teawords.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teameow.teawords.algorithm.*
import com.teameow.teawords.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val DAY_MS = 86_400_000L

/**
 * Learning entry point.
 *
 * Three things live here and nothing else: screening unfamiliar words out of the way,
 * one plan that merges every word actually worth doing today, and a knowledge snapshot that
 * never presents a self-report as a test result.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningScreen(
    dbHelper: DatabaseHelper,
    selectedBookIds: Set<String> = emptySet(),
    onSelectedBooksChange: (Set<String>) -> Unit = {},
    onShowStats: () -> Unit = {},
    onModeChange: (String) -> Unit = {},
    profileOpen: Boolean = false,
    onProfileDismiss: () -> Unit = {},
    studyWallpaper: String? = null
) {
    val context = LocalContext.current
    val repo = remember(dbHelper) { LearningRepository(dbHelper) }
    val scope = rememberCoroutineScope()
    val pocketRepo = remember(dbHelper) { PocketLearningRepository(dbHelper) }
    val sessionRepo = remember(dbHelper) { WordLearningSessionRepository(dbHelper) }
    var wordSession by remember { mutableStateOf<WordLearningSession?>(null) }
    var pocketRound by remember { mutableStateOf<PocketRound?>(null) }
    val prefs = remember { context.getSharedPreferences("learning", 0) }
    var pocketWordLimit by rememberSaveable { mutableIntStateOf(
        prefs.getInt("pocket_word_limit", PocketPlanner.DEFAULT_WORD_LIMIT).coerceIn(1, PocketPlanner.MAX_WORD_LIMIT)
    ) }

    var bookRevision by remember { mutableIntStateOf(0) }
    var books by remember { mutableStateOf(emptyList<com.teameow.teawords.data.search.WordSearchRepository.BookInfo>()) }
    var tags by remember { mutableStateOf(emptyMap<String, String>()) }
    var favorites by remember { mutableStateOf(emptyList<VocabularyItem>()) }
    var studyQuery by rememberSaveable { mutableStateOf("") }
    var studySearchReturn by rememberSaveable { mutableStateOf("home") }
    var studySearchRequest by remember { mutableLongStateOf(0L) }
    var showBooks by remember { mutableStateOf(false) }
    var draftBooks by remember { mutableStateOf(selectedBookIds) }
    val appPrefs = remember { AppPreferences(context) }
    val strategy = LearningStrategy.valueOf(appPrefs.studyStrategy.id)
    val newCap = appPrefs.dailyNewCapOverride.takeIf { it > 0 } ?: strategy.dailyNewCap
    val retention = appPrefs.targetRetentionOverride.takeIf { it > 0 } ?: strategy.targetRetention
    var homeClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // A new round receives a fresh allowance; completed rounds do not exhaust today's budget.
    val newBudget = newCap.coerceAtLeast(0)
    var states by remember { mutableStateOf(emptyList<Knowledge>()) }
    var words by remember { mutableStateOf(emptyMap<String, VocabularyItem>()) }
    var mode by rememberSaveable { mutableStateOf("home") }
    var wordReturnMode by rememberSaveable { mutableStateOf("home") }
    var homeReview by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var queue by remember { mutableStateOf(emptyList<Knowledge>()) }
    var position by remember { mutableIntStateOf(0) }
    var sessionCorrect by remember { mutableIntStateOf(0) }
    var selectedLimit by rememberSaveable { mutableStateOf(prefs.getInt("expressive_round_limit", 0).takeIf { it in listOf(10, 15, 20) }) }
    val limit = selectedLimit ?: 10
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "expressive_round_limit") selectedLimit = prefs.getInt(key, 10).takeIf { it in listOf(10, 15, 20) }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var lastScreened by remember { mutableStateOf(emptyList<Knowledge>()) }
    var screeningTotal by remember { mutableIntStateOf(0) }
    var showImport by remember { mutableStateOf(false) }
    var diagnosticBusy by remember { mutableStateOf(false) }
    var importText by rememberSaveable { mutableStateOf("") }

    suspend fun reload() {
        val result = withContext(Dispatchers.IO) {
            check(com.teameow.teawords.data.search.DictionaryGateway.open(context)) { "离线词典未就绪，请稍后重试" }
            val dictionary = com.teameow.teawords.data.search.DictionaryGateway.sourceOrNull()!!
            dictionary.books() to LearningCatalog(dbHelper, dictionary).load(selectedBookIds)
        }
        pocketRound = withContext(Dispatchers.IO) { pocketRepo.load() }
        wordSession = withContext(Dispatchers.IO) { sessionRepo.load() }
        favorites = withContext(Dispatchers.IO) { dbHelper.getVocabulary() }
        books = result.first
        states = result.second.states
        words = result.second.words
        tags = result.second.tags
    }

    LaunchedEffect(selectedBookIds, bookRevision) {
        busy = true
        try { reload() } catch (e: Exception) { message = "读取失败：${e.message}" }
        finally { busy = false }
        if (mode != "learningHub") mode = "home"
    }

    fun importWords(source: String = "用户导入", license: String = "用户提供，许可由用户确认", read: () -> String) {
        val returnToHub = mode == "learningHub"
        busy = true
        scope.launch {
            try {
                val count = withContext(Dispatchers.IO) { repo.importWords(WordImport.parse(read()), source, license) }
                reload()
                screeningTotal = count
                lastScreened = emptyList()
                mode = if (returnToHub) "learningHub" else "home"
                message = "新增 $count 个词，重复词保留原有学习记录"
                showImport = false
            } catch (e: Exception) {
                message = "导入失败：${e.message}"
            } finally {
                busy = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importWords {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val buffer = CharArray(2_000_001)
                var size = 0
                while (size < buffer.size) {
                    val n = reader.read(buffer, size, buffer.size - size)
                    if (n < 0) break
                    size += n
                }
                require(size <= 2_000_000) { "文件超过 2 MB 文本限制" }
                String(buffer, 0, size)
            } ?: error("无法读取文件")
        }
    }

    // The host keeps the floating dock visible on the learning pages; reporting the current mode
    // lets it withhold the dock during a running round; saved recall progress survives leaving.
    LaunchedEffect(mode) { onModeChange(mode) }
    DisposableEffect(Unit) { onDispose { onModeChange("home") } }

    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(mode, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { homeClock = System.currentTimeMillis(); delay(15_000) }
        }
    }
    val now = homeClock
    // Recomputed whenever the knowledge states or the round size change; never cached against a
    // frozen clock, otherwise a freshly screened word would not enter today's plan.
    val sampleDay = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))
    val scheduledPlan = remember(states, limit, newBudget, mode, sampleDay, now) {
        LearningEngine.queue(states, now, limit, newBudget, prefs.getString("sample_day", null) != sampleDay)
    }
    val unseen = states.filter { it.report == SelfReport.UNSEEN && it.attempts == 0 }
    val dueCount = states.count { it.attempts > 0 && it.due <= now }
    val fuzzyCount = states.count { it.report == SelfReport.FUZZY && it.attempts == 0 }
    val verifiedCount = states.count { it.verified }
    val learningCount = states.count { it.attempts > 0 && !it.verified }
    val unknownCount = states.count { it.report == SelfReport.UNKNOWN }

    // Ability estimate and word book tags drive an ability-scoped screening queue, so a 4500-word
    // book does not have to be judged by hand. See ScreeningPriority for the banding rules.
    var ability by remember { mutableStateOf(AbilityStore(context).load()) }
    LaunchedEffect(mode) { if (mode == "home") ability = AbilityStore(context).load() }
    val tagged = remember(tags) { tags.map { LearningRepository.TaggedWord(it.key, it.value) } }
    val screeningPlan: ScreeningPriority.Plan = remember(unseen, tagged, ability, strategy) {
        val difficultyByWord = tagged.associate { it.word to DifficultyPrior.forWord(it.word, it.tags).value }
        ScreeningPriority.plan(
            ability,
            unseen.map { ScreenCandidate(it.word, difficultyByWord[it.word] ?: 1.0) }, strategy
        )
    }
    // Only the uncertain band is worth asking about; the rest is skipped or learned directly.
    val screeningQueue = remember(screeningPlan, unseen) {
        val byWord = unseen.associateBy { it.word }
        screeningPlan.needsScreening.mapNotNull { byWord[it.first.word] }
    }

    val plan = remember(scheduledPlan, screeningPlan, screeningQueue, states, newBudget, limit) {
        val remainingNew = (newBudget - scheduledPlan.count { it.attempts == 0 }).coerceAtLeast(0)
        val included = scheduledPlan.map { it.word }.toSet()
        val byWord = states.associateBy { it.word }
        val fresh = (screeningPlan.learnDirectly.map { it.first.word } + screeningQueue.map { it.word })
            .distinct().filterNot { it in included }.take(remainingNew).mapNotNull { byWord[it] }
        (scheduledPlan + fresh).take(limit)
    }

    // --- Spaced repetition state -----------------------------------------------------------------
    // Sense ids and stored memory state are needed to grade an answer with FSRS instead of a fixed
    // table. They are loaded once per round rather than per answer.
    val coordinator = remember { ReviewCoordinator() }
    val senses = remember(dbHelper) { SenseRepository(dbHelper) }
    var memoryByWord by remember { mutableStateOf(emptyMap<String, MemoryState>()) }
    // Resolved on first use and cached, so the round only pays for the words it actually reaches.
    val senseCache = remember { mutableMapOf<String, Pair<Long, Long>>() }
    fun senseOf(word: String): Pair<Long, Long> =
        senseCache.getOrPut(word) { senses.ensurePrimarySense(word) }
    val difficultyByWord = remember(states, tagged) {
        tagged.associate { it.word to DifficultyPrior.forWord(it.word, it.tags).value }
    }

    /** At most one self-reported word per day is pulled into the plan as a spot check. */
    val today = remember(now) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(calendar.timeInMillis))
    }
    val dailySample = remember(plan, today) {
        if (prefs.getString("sample_day", null) == today) null
        else plan.firstOrNull { it.report == SelfReport.KNOWN && it.attempts == 0 && now - it.updated >= 7 * DAY_MS }
    }
    fun markScreened(batch: List<Knowledge>, report: SelfReport) {
        if (batch.isEmpty()) return
        busy = true
        scope.launch {
            try {
                val stamp = System.currentTimeMillis()
                val updated = batch.map { it.copy(report = report, updated = stamp) }
                withContext(Dispatchers.IO) { updated.forEach { repo.save(it, "screen") } }
                val byWord = updated.associateBy { it.word }
                states = states.map { byWord[it.word] ?: it }
                lastScreened = batch
            } catch (e: Exception) {
                message = "保存失败：${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun undoLast() {
        if (lastScreened.isEmpty()) return
        val restore = lastScreened
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { restore.forEach { repo.save(it, "undo") } }
                val byWord = restore.associateBy { it.word }
                states = states.map { byWord[it.word] ?: it }
                lastScreened = emptyList()
            } catch (e: Exception) {
                message = "撤销失败：${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun startDueReview() {
        if (busy) return
        if (pocketRound?.complete == false) { homeReview = true; mode = "pocket"; return }
        val stamp = System.currentTimeMillis()
        val round = PocketPlanner.build(states.filter { it.attempts > 0 && it.due <= stamp },
            words, 0, stamp, emptyList(), wordLimit = limit)
        if (round == null) { message = "暂无到期复习"; return }
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { pocketRepo.start(round) }
                pocketRound = round
                message = null
                homeReview = true
                mode = "pocket"
            } catch (e: Exception) { message = "复习启动失败：${e.message}" }
            finally { busy = false }
        }
    }

    fun startWordPlan() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val active = withContext(Dispatchers.IO) { sessionRepo.load() }
                val session = if (active != null && !active.complete) active else {
                    val tasks = plan.mapNotNull { state ->
                        words[state.word]?.definition?.takeIf { it.isNotBlank() }?.let {
                            WordLearningTask(state.word, it)
                        }
                    }
                    if (tasks.isEmpty()) {
                        message = "本轮暂无任务：可选择其他词书、调整每轮新词上限，或等待复习到期。"
                        return@launch
                    }
                    withContext(Dispatchers.IO) { sessionRepo.start(tasks) }
                }
                val byWord = withContext(Dispatchers.IO) {
                    repo.states(session.tasks.map { it.word }).associateBy { it.word }
                }
                val roundQueue = session.tasks.map { byWord.getValue(it.word) }
                val roundMemory = withContext(Dispatchers.IO) {
                    roundQueue.drop(session.position).mapNotNull { item ->
                        val sense = senseOf(item.word)
                        senses.memoryFor(sense.second)?.let { record -> item.word to MemoryState(
                            difficulty = record.difficulty, stability = record.stability,
                            lastReview = record.lastReview, reps = record.reps, lapses = record.lapses)
                        }
                    }.toMap()
                }
                wordSession = session
                queue = roundQueue
                memoryByWord = roundMemory
                ability = AbilityStore(context).load()
                position = session.position
                sessionCorrect = session.correct
                message = null
                wordReturnMode = if (mode == "home") "home" else "learningHub"
                mode = "session"
            } catch (e: Exception) { message = "准备学习失败，请重试：${e.message}" }
            finally { busy = false }
        }
    }

    fun saveWordAnswer(state: Knowledge, correct: Boolean, elapsed: Long, revealed: Boolean, recognition: Boolean = false) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val known = withContext(Dispatchers.IO) { repo.states(listOf(state.word)).first { it.word == state.word } }
                // FSRS + BKT + ability, graded together; the verdict is what gets stored.
                val verdict = if (recognition) coordinator.gradeRecognition(
                    BeliefMapper.from(known.report.name, known.attempts, known.streak, known.updated, now),
                    memoryByWord[state.word] ?: MemoryState.unseen, ability, correct, now, retention
                ) else coordinator.grade(
                    belief = BeliefMapper.from(
                        report = known.report.name,
                        attempts = known.attempts,
                        streak = known.streak,
                        updatedAt = known.updated,
                        now = now
                    ),
                    memory = memoryByWord[state.word] ?: MemoryState.unseen,
                    ability = ability,
                    difficulty = difficultyByWord[state.word] ?: 0.0,
                    correct = correct,
                    revealed = revealed,
                    elapsedMillis = elapsed,
                    now = now,
                    targetRetention = retention
                )
                val saved = withContext(Dispatchers.IO) {
                    sessionRepo.answer(checkNotNull(wordSession).id, position, correct, revealed, elapsed) {
                        val sense = senseOf(it)
                        repo.saveReview(known, verdict, sense.first, sense.second,
                            correct, revealed, elapsed, now, ability.theta, mode = if (recognition) "RECOGNITION" else "RECALL")
                    }
                }
                val updated = saved.second
                wordSession = saved.first
                position = saved.first.position
                sessionCorrect = saved.first.correct
                AbilityStore(context).save(verdict.ability)
                homeClock = now
                ability = verdict.ability
                memoryByWord = memoryByWord + (state.word to verdict.memory)
                states = states.map { if (it.word == updated.word) updated else it }
                if (dailySample?.word == state.word) prefs.edit().putString("sample_day", today).apply()
            } catch (e: Exception) {
                message = "保存失败，请重试：${e.message}"
            } finally {
                busy = false
            }
        }
    }

    val page: @Composable (String, () -> Unit) -> Unit = { pageMode, back ->
    if (pageMode == "studySearch") {
        CompositionLocalProvider(LocalTeaSearchScope provides null) {
            LookupScreen(studyQuery, onBack = back, requestId = studySearchRequest,
                model = androidx.lifecycle.viewmodel.compose.viewModel(key = "study-search"))
        }
    } else if (pageMode == "favorites") {
        NotebookPage(favorites, onOpen = { word -> studyQuery = word; studySearchReturn = "favorites"; studySearchRequest++; mode = "studySearch" },
            onDelete = { word -> scope.launch {
                try { favorites = withContext(Dispatchers.IO) { dbHelper.removeVocabulary(word); dbHelper.getVocabulary() } }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { message = "收藏更新失败，请重试" }
            } }, onBack = back)
    } else if (pageMode == "learningHub") {
        TeaListPage("专项练习", onBack = back) {
            item {
                val paused = pocketRound?.takeUnless { it.complete }
                TeaCard {
                    Text("专项练习", style = MaterialTheme.typography.headlineSmall)
                    Text("混合小练习与整词打字，按自己的节奏巩固。")
                    paused?.let { TeaCaption("上次练到 ${it.done.size}/${it.tasks.size} 步") }
                    Button(onClick = {
                        if (paused != null) mode = "pocket"
                        else { message = null; mode = "pocketSetup" }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (paused != null) "继续上次练习" else "混合小练习")
                    }
                    OutlinedButton(onClick = {
                        if (paused != null) mode = "pocket"
                        else {
                            val round = PocketPlanner.typingRound(states, words, newBudget, System.currentTimeMillis())
                            if (round == null) message = "目前没有可练的到期词或新词"
                            else {
                                busy = true
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { pocketRepo.start(round) }
                                        pocketRound = round
                                        message = null
                                        mode = "pocket"
                                    } catch (e: Exception) { message = "开始失败：${e.message}" }
                                    finally { busy = false }
                                }
                            }
                        }
                    }, enabled = !busy && (paused == null || paused.tasks.all { it.kind == PocketKind.TYPING }),
                        modifier = Modifier.fillMaxWidth()) {
                        Text(when {
                            paused == null -> "打字记词"
                            paused.tasks.all { it.kind == PocketKind.TYPING } -> "继续打字记词"
                            else -> "完成上次练习后再打字"
                        })
                    }
                }
            }
            message?.let { note -> item { TeaCaption(note) } }
        }
    } else if (pageMode == "pocketSetup") {
        PocketRoundSetupScreen(initialWordLimit = pocketWordLimit, busy = busy,
            error = message, onBack = back, onStart = { count ->
                val round = PocketPlanner.build(states, words, newBudget, System.currentTimeMillis(),
                    screeningPlan.learnDirectly.map { it.first.word } + screeningQueue.map { it.word }, wordLimit = count)
                if (round == null) message = if (states.isEmpty()) "先选择词书或添加词库，就能开始小练习"
                    else "今天的新词额度已用完，暂时也没有到期的词，稍后再来吧"
                else {
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { pocketRepo.start(round) }
                            pocketWordLimit = count
                            prefs.edit().putInt("pocket_word_limit", count).apply()
                            pocketRound = round
                            message = null
                            mode = "pocket"
                        } catch (e: Exception) { message = "开始失败：${e.message}" }
                        finally { busy = false }
                    }
                }
            })
    } else if (pageMode == "pocket" && pocketRound != null) {
        PocketLearningScreen(dbHelper, pocketRound!!, retention,
            onRoundChange = { pocketRound = it }, onBack = back,
            onBusyChange = { diagnosticBusy = it })
    } else if (pageMode == "session" && queue.getOrNull(position) == null) {
        StudyRoundSummary(wordSession, position, sessionCorrect,
            onBack = back, onContinue = if (plan.isNotEmpty()) ({ startWordPlan() }) else null)
    } else if (pageMode == "session" && queue.getOrNull(position) != null) {
        val current = queue[position]
        val card = Triple(current, wordSession?.tasks?.getOrNull(position)?.definition.orEmpty(), position)
        AnimatedContent(card, transitionSpec = {
            (fadeIn(tween(280)) + slideInHorizontally(tween(280, easing = TeaEnterEasing)) { it / 8 })
                .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { -it / 8 })
                .using(SizeTransform(clip = false))
        }, label = "study-word-transition") { shown ->
            key(wordSession?.id, shown.first.word) {
                ImmersiveWordScreen(dbHelper, shown.first.word, shown.second,
                    shown.third + 1, queue.size, busy || shown.third != position, message, studyWallpaper, onPause = back,
                    onRecallComplete = { correct, elapsed -> saveWordAnswer(shown.first, correct, elapsed, false, recognition = false) },
                    onComplete = { correct, elapsed -> saveWordAnswer(shown.first, correct, elapsed, true, recognition = true) })
            }
        }
    } else if (pageMode == "diagnose") {
        DiagnosticScreen(
            dbHelper = dbHelper, learningWords = words.keys, wordTags = tags,
            onBack = back, onFinished = back, onBusyChange = { diagnosticBusy = it }
        )
    } else {
    TeaListPage(
        loading = busy && pageMode != "session",
        expandedTitle = false,
        header = if (pageMode == "home") ({
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("学习", Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { mode = "diagnose" }, enabled = !busy) { Icon(AppSymbols.Insights, "词汇能力诊断") }
                IconButton(onClick = { showImport = true }, enabled = !busy) { Icon(AppSymbols.Add, "导入词库") }
            }
        }) else null,
        title = when (pageMode) {
            "screen" -> "快速排雷"
            "session" -> "学习与复习"
            "diagnose" -> "词汇能力诊断"
            else -> "学习"
        },
        subtitle = when (pageMode) {
            "screen" -> "凭印象判断，自评不计作测试掌握"
            "session" -> "主动回忆，答对一次不代表掌握"
            "diagnose" -> "自适应选题，快速定位你的水平"
            else -> "单词与专项练习"
        },
        onBack = if (pageMode != "home") back else null,
        backEnabled = !busy,
        actions = {
            if (pageMode == "home") {
                IconButton(onClick = { mode = "diagnose" }, enabled = !busy) {
                    Icon(AppSymbols.Insights, "词汇能力诊断")
                }
            }
            IconButton(onClick = { showImport = true }, enabled = !busy) { Icon(AppSymbols.Add, "导入词库") }
            if (pageMode != "home") {
                IconButton(onClick = onShowStats, enabled = !busy) { Icon(AppSymbols.BarChart, "学习统计") }
            }
        }
    ) {
        message?.let { note -> item { TeaCaption(note) } }
        when {
            pageMode == "screen" -> screeningMode(
                current = screeningQueue.firstOrNull(),
                upcoming = screeningQueue.take(10),
                screened = (screeningTotal - screeningQueue.size).coerceAtLeast(0),
                total = screeningTotal,
                busy = busy,
                lastScreened = lastScreened,
                onRate = ::markScreened,
                onUndo = ::undoLast,
                onLearn = ::startWordPlan,
                onFinish = back
            )

            pageMode == "session" -> sessionMode(
                queue = queue,
                position = position,
                correct = sessionCorrect,
                definitions = wordSession?.tasks.orEmpty().associate { it.word to it.definition },
                session = wordSession,
                busy = busy,
                memory = memoryByWord,
                coordinator = coordinator,
                onAnswered = { state, correct, elapsed, revealed -> saveWordAnswer(state, correct, elapsed, revealed) },
                onFinish = back
            )

            pageMode == "home" -> item {
                StudyOverview(
                    bookName = books.filter { it.id in selectedBookIds }.joinToString("、") { it.name }.ifBlank { "个人词库" },
                    total = states.size, studied = states.count { it.attempts > 0 }, verified = verifiedCount,
                    due = dueCount, newBudget = newBudget, fuzzy = fuzzyCount, busy = busy,
                    paused = wordSession?.takeUnless { it.complete }?.let { WordStudyResume(it.position, it.tasks.size) },
                    onBooks = { draftBooks = selectedBookIds; showBooks = true },
                    favorites = favorites.size, onFavorites = { mode = "favorites" },
                    query = studyQuery, onQuery = { studyQuery = it },
                    onSearch = { studySearchReturn = "home"; studySearchRequest++; mode = "studySearch" },
                    onStart = {
                        if (wordSession?.complete == false) startWordPlan()
                        else if (states.isEmpty()) { draftBooks = selectedBookIds; showBooks = true }
                        else startWordPlan()
                    }, onReview = ::startDueReview, onMore = { mode = "learningHub" }
                )
            }
            else -> Unit
        }
    }

    }
    }
    val homeBottom = LocalReservedBottom.current
    Box(Modifier.fillMaxSize()) {
        CoveredPage(covered = mode != "home") { page("home") {} }
        if (mode != "home") key(mode) {
            PredictiveBackLayer(onBack = {
                val leavingPocket = mode == "pocket"
                mode = when (mode) {
                    "studySearch" -> studySearchReturn
                    "session" -> wordReturnMode
                    "pocket" -> if (homeReview) "home" else "learningHub"
                    "pocketSetup", "screen" -> "learningHub"
                    else -> "home"
                }
                if (leavingPocket) homeReview = false
                if (leavingPocket) scope.launch {
                    busy = true
                    try { reload() } catch (e: Exception) { message = "读取失败：${e.message}" }
                    finally { busy = false }
                }
            }, enabled = !busy && !diagnosticBusy) { back ->
                CompositionLocalProvider(LocalReservedBottom provides if (mode == "screen") homeBottom else null) {
                    page(mode, back)
                }
            }
        }
    }

    if (profileOpen) TeaKnowledgeProfileSheet(verifiedCount, learningCount, fuzzyCount, unknownCount,
        states.size, onProfileDismiss, onShowStats)

    if (showBooks) StudyBookPicker(dbHelper, books, draftBooks,
        onSelection = { draftBooks = it }, onDismiss = { showBooks = false },
        onApply = { onSelectedBooksChange(draftBooks); bookRevision++; showBooks = false })
    if (showImport) AlertDialog(
        onDismissRequest = { if (!busy) showImport = false },
        title = { Text("添加个人词库") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("使用你有权使用的词库。支持 CSV / JSON / TXT；每行“单词,释义”，JSON 使用 word 和 definition 字段。相同词条不会覆盖已有学习记录。")
                TextButton(enabled = !busy, onClick = { showImport = false; draftBooks = selectedBookIds; showBooks = true }) { Text("选择内置词书（含雅思、托福）") }
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("粘贴词条") },
                    minLines = 3,
                    maxLines = 5
                )
                TextButton(enabled = !busy, onClick = {
                    picker.launch(arrayOf("text/*", "application/json", "application/octet-stream"))
                }) { Text("选择文件") }
            }
        },
        confirmButton = {
            Button(
                enabled = importText.isNotBlank() && !busy,
                onClick = { importWords { importText } }
            ) { Text("导入词库") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { showImport = false }) { Text("取消") } }
    )
}

// --- Screening: one word, one tap, undo always available -------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.screeningMode(
    current: Knowledge?,
    upcoming: List<Knowledge>,
    screened: Int,
    total: Int,
    busy: Boolean,
    lastScreened: List<Knowledge>,
    onRate: (List<Knowledge>, SelfReport) -> Unit,
    onUndo: () -> Unit,
    onLearn: () -> Unit,
    onFinish: () -> Unit
) {
    if (current == null) {
        item {
            TeaCard {
                TeaEmptyState(
                    "排雷完成",
                    if (total == 0) "先导入词库，再从已知的词开始。" else "没有需要手动判断的词了，可以直接开始今日学习。"
                )
                Button(onClick = onLearn, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("开始单词学习") }
                TextButton(onClick = onFinish, enabled = !busy) { Text("返回学习方式") }
            }
        }
        return
    }
    item {
        TeaCard(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            TeaScreenProgress(
                done = screened + 1,
                total = total.coerceAtLeast(upcoming.size),
                modifier = Modifier.fillMaxWidth()
            )
            // Fixed height on purpose. Animating the word inside a size-changing container would
            // re-measure this list item on every tap and shove the whole page up and down, which
            // reads as flicker in the app bar and counters. Scaling a fixed box keeps the page still.
            Box(
                Modifier.fillMaxWidth().height(132.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = current.word,
                    transitionSpec = {
                        (fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.96f)) togetherWith
                            fadeOut(tween(90))
                    },
                    label = "screening-word"
                ) { word ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(word, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, maxLines = 1)
                        Text("先凭印象判断，无需逐个背诵释义", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            DefaultPronunciationButton(current.word)
            Button(
                onClick = { onRate(listOf(current), SelfReport.KNOWN) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text("完全认识 · 跳过") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { onRate(listOf(current), SelfReport.FUZZY) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("有些模糊") }
                OutlinedButton(
                    onClick = { onRate(listOf(current), SelfReport.UNKNOWN) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) { Text("完全陌生") }
            }
            // Screening a long wordbook one tap at a time is the expensive part, so a run of
            // words the user already knows leaves the queue in a single action (undoable).
            if (upcoming.size > 3) {
                OutlinedButton(
                    onClick = { onRate(upcoming, SelfReport.KNOWN) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text("接下来的 ${upcoming.size} 个也认识") }
            }
            // Labels stay single-line and the undo button is capped, so a long word can never widen
            // it enough to wrap this row and shift everything below.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onUndo,
                    enabled = !busy && lastScreened.isNotEmpty(),
                    modifier = Modifier.widthIn(max = 220.dp)
                ) {
                    Text(
                        when {
                            lastScreened.isEmpty() -> "撤销上一项"
                            lastScreened.size == 1 -> "撤销：${lastScreened.first().word}"
                            else -> "撤销这 ${lastScreened.size} 个"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onFinish, enabled = !busy) {
                    Text("先到这里", maxLines = 1)
                }
            }
            FilledTonalButton(onClick = onLearn, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("开始单词学习")
            }
            TeaCaption("认识或模糊的词会暂离新词队列，模糊词优先复习。自评不是测试证据。")
        }
    }
}

// --- Recall session --------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.sessionMode(
    queue: List<Knowledge>,
    position: Int,
    correct: Int,
    definitions: Map<String, String>,
    session: WordLearningSession?,
    busy: Boolean,
    memory: Map<String, MemoryState>,
    coordinator: ReviewCoordinator,
    onAnswered: (Knowledge, Boolean, Long, Boolean) -> Unit,
    onFinish: () -> Unit
) {
    val current = queue.getOrNull(position)
    if (current == null) return
    item {
        LinearProgressIndicator(progress = { position.toFloat() / queue.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
        TeaCaption("已完成 $position / ${queue.size} 个词 · 暂停将从当前词继续")
        TextButton(onClick = onFinish, enabled = !busy) { Text("暂停，稍后继续") }
    }
    item(key = "recall-${session?.id}-${current.word}") {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        RecallCard(
            state = current,
            definition = definitions[current.word].orEmpty(),
            index = position + 1,
            total = queue.size,
            busy = busy,
            // The scheduler explains why this word is here: real retrievability, not a fixed table.
            scheduleNote = memory[current.word]?.let { coordinator.reviewReason(it, System.currentTimeMillis()) }.orEmpty(),
            onComplete = { isCorrect, elapsed, revealed -> onAnswered(current, isCorrect, elapsed, revealed) }
        )
    }
}

@Composable
internal fun RecallCard(
    state: Knowledge,
    definition: String,
    index: Int,
    total: Int,
    busy: Boolean,
    scheduleNote: String,
    previewFirst: Boolean = state.attempts == 0 && state.report != SelfReport.KNOWN,
    focusMode: Boolean = false,
    onPause: () -> Unit = {},
    error: String? = null,
    onComplete: (Boolean, Long, Boolean) -> Unit
) {
    var answer by rememberSaveable(state.word) { mutableStateOf("") }
    var result by rememberSaveable(state.word) { mutableStateOf<Boolean?>(null) }
    var teaching by rememberSaveable(state.word) { mutableStateOf(previewFirst) }
    /** Whether the user gave up and looked at the answer; it changes how the answer is graded. */
    var revealed by rememberSaveable(state.word) { mutableStateOf(false) }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    val activeTime = remember(state.word) { longArrayOf(0, 0) }
    DisposableEffect(state.word, lifecycle) {
        fun pause() {
            if (activeTime[1] != 0L) activeTime[0] += android.os.SystemClock.elapsedRealtime() - activeTime[1]
            activeTime[1] = 0
        }
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            activeTime[1] = android.os.SystemClock.elapsedRealtime()
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) activeTime[1] = android.os.SystemClock.elapsedRealtime()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) pause()
        }
        lifecycle.addObserver(observer)
        onDispose { pause(); lifecycle.removeObserver(observer) }
    }
    fun timeSpent() = activeTime[0] + if (activeTime[1] == 0L) 0 else android.os.SystemClock.elapsedRealtime() - activeTime[1]
    var elapsed by rememberSaveable(state.word) { mutableLongStateOf(0) }

    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    fun checkAnswer(giveUp: Boolean = false) {
        if (busy || result != null || (!giveUp && answer.isBlank())) return
        result = !giveUp && answer.trim().equals(state.word, ignoreCase = true)
        revealed = giveUp || previewFirst
        elapsed = timeSpent()
        focus.clearFocus()
        keyboard?.hide()
    }
    val phase = when {
        teaching -> "认识新词"
        result == null -> "主动回忆"
        else -> "回忆反馈"
    }
    val content: @Composable ColumnScope.() -> Unit = {
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Text(phase, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        when {
            teaching -> {
                Text(state.word, style = if (focusMode) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium)
                DefaultPronunciationButton(state.word)
                if (definition.isNotBlank()) Text(definition, style = MaterialTheme.typography.titleLarge)
                TeaCaption("先建立一次词义联系，再试着主动回忆。")
            }
            result == null -> {
                Text(definition.ifBlank { state.word }, style = MaterialTheme.typography.headlineSmall)
                TeaCaption("根据所示释义，输入对应英文词。仅验证本条释义的主动回忆。")
                OutlinedTextField(
                    value = answer, onValueChange = { answer = it }, enabled = !busy,
                    singleLine = true, label = { Text("英文单词") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        autoCorrectEnabled = false, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { checkAnswer() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            else -> {
                Text(if (result == true) "回忆正确" else "再建立一次联系",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (result == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Text(state.word, style = if (focusMode) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineMedium)
                DefaultPronunciationButton(state.word)
                if (definition.isNotBlank()) Text(definition, style = MaterialTheme.typography.titleLarge)
                TeaCaption(when {
                    revealed -> "本次看过答案，作为练习记录，不计作独立回忆通过。"
                    result == true -> "答对一次不代表完全掌握，之后会按记忆模型安排复习。"
                    else -> "稍后再复习这个词，本轮先继续。"
                })
            }
        }
        val note = scheduleNote.ifBlank { LearningEngine.reason(state, System.currentTimeMillis()).orEmpty() }
        if (note.isNotBlank()) TeaCaption(note)
        TeaCaption("已测试 ${state.attempts} 次 · 连续答对 ${state.streak} 次")
    }
    val actions: @Composable ColumnScope.() -> Unit = {
        Button(onClick = {
            when {
                teaching -> teaching = false
                result == null -> checkAnswer()
                else -> onComplete(result == true, elapsed, revealed)
            }
        }, enabled = !busy && (teaching || result != null || answer.isNotBlank()),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(when {
                teaching -> "记住了，试试回忆"
                result == null -> "检查回答"
                else -> "保存并继续"
            })
        }
        if (!teaching && result == null) TextButton(enabled = !busy, onClick = { checkAnswer(true) },
            modifier = Modifier.fillMaxWidth()) { Text("想不起来，查看答案") }
        if (focusMode) TextButton(onClick = onPause, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text("暂停，稍后继续") }
    }
    if (focusMode) StudyFocusPage(index, total, phase, busy, onPause, content, actions)
    else LearningCard("$index / $total", "") { content(); actions() }
}
