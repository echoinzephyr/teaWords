package com.teameow.teawords.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teameow.teawords.data.*
import com.teameow.teawords.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Self-reported known words are left alone for this long before the first spot check. */
private const val SAMPLE_AFTER_MS = 7 * 86_400_000L
private const val DAY_MS = 86_400_000L

/**
 * Personal vocabulary knowledge profile.
 *
 * Layout follows the reference mock, with one rule applied everywhere: **nothing is drawn that the
 * app cannot measure**. The hero number is words verified by real test answers, never a model guess;
 * the band chart uses the word books that actually exist in the dictionary (CET-4 / CET-6 / outside
 * the syllabus) instead of inventing CEFR levels; and the dimension panel states plainly that
 * per-mode knowledge is not recorded yet rather than showing invented recognition/recall numbers.
 */
@Composable
fun StatsDashboard(dbHelper: DatabaseHelper, modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    val repo = remember(dbHelper) { LearningRepository(dbHelper) }
    var rows by remember { mutableStateOf(emptyList<LearningRepository.WordRow>()) }
    var totals by remember { mutableStateOf(LearningRepository.Totals(0, 0, 0, 0)) }
    var today by remember { mutableStateOf(LearningRepository.Totals(0, 0, 0, 0)) }
    var trend by remember { mutableStateOf(emptyList<LearningRepository.Daily>()) }
    var windowDays by remember { mutableIntStateOf(30) }
    var streak by remember { mutableIntStateOf(0) }
    var dictionarySize by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("待确认") }
    var legacyTests by remember { mutableIntStateOf(0) }
    var skippedExpanded by remember { mutableStateOf(false) }
    var reverting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        try {
            val midnight = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val loaded = withContext(Dispatchers.IO) {
                val days = repo.activeDays()
                val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                var streakCount = 0
                var cursor = midnight
                while (days.contains(format.format(Date(cursor)))) {
                    streakCount++
                    cursor -= DAY_MS
                }
                ProfileSnapshot(
                    rows = repo.wordRows(),
                    totals = repo.totals(),
                    today = repo.totals(midnight),
                    streak = streakCount,
                    dictionarySize = repo.dictionarySize(),
                    legacyTests = dbHelper.getReviewStats()?.totalProblems ?: 0
                )
            }
            rows = loaded.rows
            totals = loaded.totals
            today = loaded.today
            streak = loaded.streak
            dictionarySize = loaded.dictionarySize
            legacyTests = loaded.legacyTests
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            error = "读取统计失败：${e.message}"
        } finally {
            loading = false
        }
    }

    var trendLoading by remember { mutableStateOf(true) }
    LaunchedEffect(windowDays) {
        trendLoading = true
        try {
            trend = withContext(Dispatchers.IO) {
                val midnight = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                repo.daily(midnight - (windowDays - 1) * DAY_MS)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error = "读取趋势失败：${e.message}" }
        finally { trendLoading = false }
    }

    val verified = remember(rows) { rows.count { it.verified } }
    val selfKnown = remember(rows) { rows.count { it.selfReportedKnown } }
    val learning = remember(rows) { rows.count { it.attempts > 0 && !it.verified } }
    val fuzzy = remember(rows) { rows.count { it.report == "FUZZY" && it.attempts == 0 } }
    val unknown = remember(rows) { rows.count { it.report == "UNKNOWN" && it.attempts == 0 } }
    val pending = (rows.size - verified - selfKnown - learning - fuzzy - unknown).coerceAtLeast(0)

    fun category(row: LearningRepository.WordRow): String = when {
        row.verified -> "已验证"
        row.selfReportedKnown -> "自评认识"
        row.attempts > 0 -> "学习中"
        row.report == "FUZZY" -> "模糊认识"
        row.report == "UNKNOWN" -> "完全陌生"
        else -> "待确认"
    }
    val categories = listOf("已验证", "学习中", "自评认识", "模糊认识", "完全陌生", "待确认")
    val accents = mapOf(
        "已验证" to MaterialTheme.colorScheme.primary,
        "学习中" to MaterialTheme.colorScheme.secondary,
        "自评认识" to MaterialTheme.colorScheme.tertiary,
        "模糊认识" to MaterialTheme.colorScheme.tertiary,
        "完全陌生" to MaterialTheme.colorScheme.error,
        "待确认" to MaterialTheme.colorScheme.outline
    )

    val filtered = remember(rows, filter) { rows.filter { category(it) == filter } }
    val categoryCounts = remember(rows) { rows.groupingBy { category(it) }.eachCount() }
    val skipped = remember(rows) { rows.filter { it.selfReportedKnown } }
    val bands = remember(rows) { listOf(
        "CET-4" to rows.count { it.tags.contains("cet4") && it.verified },
        "CET-6" to rows.count { it.tags.contains("cet6") && it.verified },
        "大纲外" to rows.count { it.tags.isBlank() && it.verified }) }
    var showExplanation by remember { mutableStateOf(false) }
    TeaListPage(
        embedded = true, expandedTitle = false,
        title = "词汇知识画像",
        subtitle = "学习积累与回忆表现",
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = { TeaPanelHeader("词汇知识画像", onClose = onBack, closeLabel = "关闭统计") }
    ) {
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { item { TeaStatusMessage(it, error = true) } }

        item {
            TeaCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("已验证词汇", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("$verified", style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    TextButton(onClick = { showExplanation = !showExplanation }) { Text(if (showExplanation) "收起说明" else "统计说明") }
                }
                if (showExplanation) TeaCaption("连续 3 次主动回忆正确才算验证，仅代表练习过的释义。自评与答题记录分开保存；同一词跨词书共用进度。")
                if (!loading && rows.isEmpty()) Text("还没有学习记录，完成一轮学习后查看你的积累。",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        item {
            TeaCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                TeaMetricRow("$learning" to "学习中", "$selfKnown" to "自评认识", "$pending" to "待确认")
                TeaCaption("本地词库 $dictionarySize 词 · 已记录 ${rows.size} 词")
            }
        }
        if (rows.isNotEmpty()) {
            item {
                ProfileCard("词汇状态", "测试记录与自评分别统计") {
                    val states = listOf("已验证" to verified, "学习中" to learning, "自评认识" to selfKnown,
                        "模糊认识" to fuzzy, "完全陌生" to unknown, "待确认" to pending)
                    states.filter { it.second > 0 }.forEach { (label, count) ->
                        TeaProgressRow(label, count, rows.size, accent = accents.getValue(label))
                    }
                }
            }
        }
        if (verified > 0) {
            item {
                ProfileCard("词书覆盖", "已通过测试验证的词汇") {
                    BarChart(bands, "按本地词典的词书标签统计。")
                }
            }
        }

        // Learning trend with a switchable window.
        item {
            ProfileCard("学习趋势", "$windowDays 天内的真实答题") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90).forEach { days ->
                        FilterChip(
                            selected = windowDays == days,
                            onClick = { windowDays = days },
                            label = { Text("$days 天") }
                        )
                    }
                }
                if (trendLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (trend.isEmpty() && !trendLoading) {
                    TeaCaption("这 $windowDays 天没有答题记录。完成一次学习后这里会出现真实数据。")
                } else {
                    TrendChart(trend, windowDays)
                    val tests = trend.sumOf { it.tests }
                    val correct = trend.sumOf { it.correct }
                    val activeDays = trend.count { it.tests > 0 }
                    TeaCaption(
                        "$windowDays 天共 $tests 次回忆 · 正确 ${if (tests == 0) 0 else correct * 100 / tests}% · " +
                            "活跃 $activeDays 天 · 累计时长 ${formatDuration(trend.sumOf { it.seconds })}"
                    )
                }
            }
        }

        item {
            ProfileCard("学习积累", "来自实际作答记录") {
                TeaMetricRow("$streak" to "连续天数", "${totals.tests}" to "累计回忆", formatDuration(totals.seconds) to "专注时长")
            }
        }

        item {
            ProfileCard("今日", "前台单题耗时；每题最多计 5 分钟") {
                TeaMetricRow(
                    "${today.tests}" to "回忆次数",
                    "${today.seconds / 60}:${(today.seconds % 60).toString().padStart(2, '0')}" to "专注时长",
                    if (today.tests == 0) "—" to "正确率" else "${today.correct * 100 / today.tests}%" to "正确率"
                )
                if (today.tests == 0) TeaCaption("今天还没有答题记录。")
            }
        }

        if (selfKnown > 0 || legacyTests > 0) item {
            ProfileCard("减少无效重复", "自评认识、且尚无测试记录的词不再占用新词队列") {
                val hiddenNow = skipped.count { System.currentTimeMillis() - it.updated < SAMPLE_AFTER_MS }
                Text("$hiddenNow 个词按你的判断暂离队列，系统只做少量间隔抽查。", style = MaterialTheme.typography.bodyMedium)
                if (skipped.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { skippedExpanded = !skippedExpanded },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(if (skippedExpanded) "收起这些词" else "查看这 ${skipped.size} 个词") }
                }
                Text("模型预测：未启用", fontWeight = FontWeight.SemiBold)
                if (legacyTests > 0) {
                    TeaCaption("旧版练习保留 $legacyTests 道答题记录；未换算成新的知识画像，避免混淆证据来源。")
                }
            }
        }

        if (skippedExpanded) {
            item { TeaSectionLabel("自评认识 · 暂离新词队列") }
            items(skipped, key = { "skipped-${it.word}" }) { k ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(k.word, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            TeaCaption(
                                if (System.currentTimeMillis() - k.updated < SAMPLE_AFTER_MS)
                                    "自评认识 · 暂不抽查，可撤销"
                                else "自评认识 · 已到抽查时间"
                            )
                        }
                        TextButton(
                            enabled = !reverting,
                            onClick = {
                                reverting = true
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) { repo.revertSelfReport(k.word) }
                                        rows = rows.map { if (it.word == k.word) it.copy(report = "FUZZY") else it }
                                    } finally {
                                        reverting = false
                                    }
                                }
                            }
                        ) { Text("撤销认识") }
                    }
                }
            }
        }

        item {
            TeaSectionLabel("词汇明细")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { label ->
                    FilterChip(
                        selected = filter == label,
                        onClick = { filter = label },
                        label = { Text("$label · ${categoryCounts[label] ?: 0}") }
                    )
                }
            }
        }

        if (filtered.isEmpty() && !loading) {
            item { TeaCard { TeaEmptyState("暂无词汇", "完成排雷或学习后，相应词汇会出现在这里。") } }
        }
        items(filtered, key = { "profile-${it.word}" }) { k ->
            WordEvidenceRow(k, accents[category(k)] ?: MaterialTheme.colorScheme.primary)
        }
    }
}

private data class ProfileSnapshot(
    val rows: List<LearningRepository.WordRow>,
    val totals: LearningRepository.Totals,
    val today: LearningRepository.Totals,
    val streak: Int,
    val dictionarySize: Int,
    val legacyTests: Int
)

@Composable
private fun WordEvidenceRow(k: LearningRepository.WordRow, accent: Color) {
    val dueText = remember(k.due) {
        if (k.due > 0) "下次复习 ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(k.due))}" else null
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(k.word, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                TeaCaption(
                    when {
                        k.verified -> "测试证据 · 连续答对 ${k.streak} 次"
                        k.attempts > 0 -> "测试证据 · 答过 ${k.attempts} 次，连续答对 ${k.streak} 次"
                        k.selfReportedKnown -> "自评认识 · 等待抽样验证"
                        k.report == "FUZZY" -> "自评模糊 · 尚无测试记录"
                        k.report == "UNKNOWN" -> "自评陌生 · 尚未学习"
                        else -> "尚无任何记录"
                    }
                )
                dueText?.let { TeaCaption(it) }
                if (k.tags.isNotBlank()) TeaCaption("词书 · ${k.tags}")
            }
            TeaChip(
                when {
                    k.verified -> "已验证"
                    k.attempts > 0 -> "学习中"
                    k.selfReportedKnown -> "自评认识"
                    k.report == "FUZZY" -> "模糊"
                    k.report == "UNKNOWN" -> "陌生"
                    else -> "待确认"
                },
                accent = accent
            )
        }
    }
}

/** Horizontal bars, one per band, scaled to the largest value. */
@Composable
private fun BarChart(entries: List<Pair<String, Int>>, caption: String) {
    val peak = (entries.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(56.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (value > 0) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(value.toFloat() / peak)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "$value",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(48.dp)
                )
            }
        }
        TeaCaption(caption)
    }
}

/** Daily bar chart over the selected window, with the peak day called out. */
@Composable
private fun TrendChart(days: List<LearningRepository.Daily>, windowDays: Int) {
    val window = remember(days, windowDays) {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val byDay = days.associateBy { it.day }
        (windowDays - 1 downTo 0).map { offset ->
            val day = format.format(Date(midnight - offset * DAY_MS))
            byDay[day] ?: LearningRepository.Daily(day, 0, 0, 0)
        }
    }
    val peak = window.maxByOrNull { it.tests }
    val peakValue = (peak?.tests ?: 0).coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        peak?.takeIf { it.tests > 0 }?.let { best ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("单日最多 ${best.tests} 次回忆", style = MaterialTheme.typography.labelLarge)
                    Text(best.day, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(if (windowDays > 40) 1.dp else 3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            window.forEach { day ->
                val fraction = day.tests.toFloat() / peakValue
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (day.tests == 0) 0.03f else fraction.coerceAtLeast(0.06f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(
                                if (day.tests == 0) MaterialTheme.colorScheme.surfaceContainerHighest
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                }
            }
        }
        if (windowDays <= 40) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(window.first().day.takeLast(5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(window.last().day.takeLast(5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h${minutes}m" else "${minutes}m"
}

@Composable
private fun ProfileCard(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    TeaCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        TeaCaption(subtitle)
        content()
    }
}
