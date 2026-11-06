package com.teameow.teawords.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.teameow.teawords.algorithm.*
import com.teameow.teawords.data.DatabaseHelper
import com.teameow.teawords.data.LearningRepository
import com.teameow.teawords.data.SenseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Adaptive vocabulary diagnostic.
 *
 * A short CAT session on the Rasch model: each question is chosen because it carries the most
 * information about the user's current ability estimate, so the test can stop as soon as the estimate
 * is precise instead of walking the word book in order.
 *
 * What it does not claim: the ability number is a model estimate from a fixed prior difficulty
 * table, not a calibrated IRT score; and it says nothing about any individual word. Questions are
 * answered as recognition ("do you know this word"), which is why the record is tagged with its own
 * practice mode.
 */
@Composable
fun DiagnosticScreen(
    dbHelper: DatabaseHelper,
    learningWords: Collection<String>? = null,
    wordTags: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
    onFinished: () -> Unit = {},
    onBusyChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val repo = remember(dbHelper) { SenseRepository(dbHelper) }
    val scope = rememberCoroutineScope()

    var candidates by remember { mutableStateOf(emptyList<SenseRepository.Candidate>()) }
    var state by remember { mutableStateOf(CatState()) }
    var current by remember { mutableStateOf<SenseRepository.Candidate?>(null) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(busy) { onBusyChange(busy) }
    DisposableEffect(Unit) { onDispose { onBusyChange(false) } }
    /** Rows the app itself can read back, so a persistence failure is visible instead of silent. */
    var storedEvidence by remember { mutableStateOf(0) }
    var startedAt by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }

    // Ability is carried across sessions: the diagnostic continues where the last one left off.
    val abilityStore = remember { AbilityStore(context) }
    var ability by remember { mutableStateOf(abilityStore.load()) }

    LaunchedEffect(Unit) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                // Derived meanings are built on demand: a database created by an earlier version has
                // its dictionary but no sense rows, and this is the screen that needs them. The call
                // is idempotent, so on later visits it returns almost immediately.
                if (learningWords != null) repo.diagnosticCandidates(learningWords, wordTags)
                else { repo.deriveMissingSenseUnits(); repo.diagnosticCandidates() }
            }
            candidates = loaded
            storedEvidence = withContext(Dispatchers.IO) { repo.replayableRecordCount() }
            val restored = CatState(ability = ability)
            state = restored
            current = CatSelector.selectNext(restored, loaded.map { it.toItem() })
                ?.let { item -> loaded.first { it.wordId == item.wordId } }
            startedAt = android.os.SystemClock.elapsedRealtime()
            if (candidates.isEmpty()) message = "本地词典还没有可用于诊断的词条。"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = "读取词条失败：${e.message}"
        } finally {
            loading = false
        }
    }

    fun answer(known: Boolean) {
        if (busy || state.isFinished) return
        val candidate = current ?: return
        val item = candidate.toItem()
        val before = state.ability
        busy = true
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val elapsed = (android.os.SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0)
                val next = CatSelector.answer(state, item, known, elapsed, candidates.map { it.toItem() })
                // Persist before advancing the UI: if writing fails the user must see it rather than
                // watch answers vanish on the next launch. The exception is logged with its cause.
                withContext(Dispatchers.IO) {
                    repo.recordDiagnostic(candidate, known, elapsed, now, before.theta, next.ability.theta, item.difficulty)
                }
                abilityStore.save(next.ability)
                storedEvidence = withContext(Dispatchers.IO) { repo.replayableRecordCount() }
                ability = next.ability
                state = next
                current = if (next.isFinished) null
                else CatSelector.selectNext(next, candidates.map { it.toItem() })
                    ?.let { picked -> candidates.first { it.wordId == picked.wordId } }
                startedAt = android.os.SystemClock.elapsedRealtime()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DiagnosticScreen", "failed to record diagnostic answer", e)
                message = "保存失败（本次答题未写入记录）：${e.message ?: e::class.simpleName}"
            } finally {
                busy = false
            }
        }
    }

    fun finishEarly() {
        state = CatSelector.stop(state)
        current = null
    }

    BackHandler(enabled = !LocalPageBackHandled.current) {
        if (busy) return@BackHandler
        if (!state.isFinished) abilityStore.save(state.ability)
        onBack()
    }

    TeaListPage(expandedTitle = false,
        loading = loading,
        title = "词汇能力诊断",
        subtitle = "凭第一反应判断，逐步了解词汇水平",
        onBack = onBack,
        backEnabled = !busy,
        actions = {
            if (!state.isFinished) {
                IconButton(onClick = ::finishEarly, enabled = !busy) {
                    Icon(Icons.Default.Close, contentDescription = "结束诊断")
                }
            }
        }
    ) {
        message?.let { item { TeaCaption(it) } }

        if (!state.isFinished) item {
            TeaCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("诊断进度", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TeaChip("已答 ${state.answeredCount} 题")
                }
                TeaCaption("按第一反应选择认识或不认识；可随时结束，已答内容会保存。")
            }
        }

        val candidate = current
        when {
            state.isFinished -> item {
                LearningCard("诊断结束", stopReasonText(state.stopReason)) {
                    if (state.answeredCount > 0) Text(CatSelector.approximateBand(state.ability.theta), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "本次答了 ${state.answeredCount} 题，自评认识 ${state.correctCount} 题。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TeaCaption("结果已保存。这是词汇水平估计，不代表具体单词已掌握；后续学习会继续修正。")
                    Button(
                        onClick = { onFinished() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("去学习") }
                }
            }
            candidate == null -> item {
                TeaCard { TeaEmptyState("没有可用的诊断题目", "先导入词库，或稍后再试。") }
            }
            else -> item(key = "diag-${candidate.senseId}") {
                LearningCard("第 ${state.answeredCount + 1} 题", "凭印象判断，不需要回忆释义") {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(candidate.word, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = { answer(true) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("认识这个词") }
                    OutlinedButton(
                        onClick = { answer(false) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text("不认识") }
                    TeaCaption("诊断只用于估计整体能力，不会把词标记为已掌握。")
                }
            }
        }

        if (!state.isFinished && state.answeredCount > 0) {
            item { TeaSectionLabel("已答题记录") }
            items(state.answered.reversed().take(8), key = { "ans-${it.item.wordId}" }) { entry ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(entry.item.word, style = MaterialTheme.typography.bodyLarge)
                        TeaCaption(
                            if (entry.correct) "认识" else "不认识"
                        )
                    }
                }
            }
        }
    }
}

private fun SenseRepository.Candidate.toItem() = DiagnosticItem(
    wordId = wordId,
    word = word,
    difficulty = DifficultyPrior
        .adjustForMode(DifficultyPrior.forWord(word, tags), PracticeMode.RECOGNITION)
        .value,
    existingEvidence = if (attempts > 0) 0.8 else 0.0
)

private fun stopReasonText(reason: CatStopReason?): String = when (reason) {
    CatStopReason.PRECISION_REACHED -> "已答到精度要求，估计足够稳定"
    CatStopReason.MAX_ITEMS_REACHED -> "已达到本次题量上限"
    CatStopReason.NO_INFORMATIVE_ITEM -> "剩余词条已不能提供更多信息"
    CatStopReason.EXHAUSTED -> "可用词条已全部答完"
    CatStopReason.USER_STOPPED -> "你提前结束了本次诊断"
    null -> "已完成"
}

/**
 * Persists the ability estimate between sessions.
 *
 * Kept deliberately small and separate from the algorithm: the algorithm package stays pure Kotlin
 * and does not know about Android storage. Only the estimate, its standard error and the answer count
 * are stored, and the information total so the next session continues with the same precision.
 */
class AbilityStore(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("teawords_diagnostic", android.content.Context.MODE_PRIVATE)

    fun load(): AbilityEstimate {
        if (!prefs.contains(KEY_THETA)) return AbilityEstimator.initial
        return AbilityEstimate(
            theta = prefs.getFloat(KEY_THETA, 0f).toDouble(),
            standardError = prefs.getFloat(KEY_SE, AbilityEstimator.PRIOR_SD.toFloat()).toDouble(),
            itemsAnswered = prefs.getInt(KEY_ITEMS, 0),
            totalInformation = prefs.getFloat(KEY_INFO, 0f).toDouble()
        )
    }
    fun save(estimate: AbilityEstimate) {
        prefs.edit()
            .putFloat(KEY_THETA, estimate.theta.toFloat())
            .putFloat(KEY_SE, estimate.standardError.toFloat())
            .putInt(KEY_ITEMS, estimate.itemsAnswered)
            .putFloat(KEY_INFO, estimate.totalInformation.toFloat())
            .apply()
    }

    private companion object {
        const val KEY_THETA = "ability_theta"
        const val KEY_SE = "ability_se"
        const val KEY_ITEMS = "ability_items"
        const val KEY_INFO = "ability_info"
    }
}
