package com.teameow.teawords.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.teameow.teawords.data.*
import com.teameow.teawords.data.pronunciation.SpeechPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PocketLearningScreen(
    helper: DatabaseHelper, round: PocketRound, retention: Double,
    onRoundChange: (PocketRound) -> Unit, onBack: () -> Unit, onBusyChange: (Boolean) -> Unit
) {
    val repo = remember(helper) { PocketLearningRepository(helper) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Keep feedback on screen after committing an answer; leaving now resumes the next task.
    var feedback by rememberSaveable(round.id) { mutableStateOf<String?>(null) }
    var selectedPair by rememberSaveable(round.id) { mutableStateOf<Int?>(null) }
    val currentIndex = round.position
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var spelling by rememberSaveable(round.id, currentIndex) { mutableStateOf("") }
    var typingErrors by rememberSaveable(round.id, currentIndex) { mutableIntStateOf(0) }
    val task = round.tasks.getOrNull(currentIndex)
    val speech = rememberPronunciationServices()
    val preference by speech.preference.collectAsState()
    val playback by speech.manager.state.collectAsState()
    var heard by remember(currentIndex) { mutableStateOf(false) }
    var audioRequested by remember(currentIndex) { mutableStateOf(false) }
    LaunchedEffect(playback.playbackCompleted, playback.word, audioRequested) {
        if (audioRequested && playback.word == task?.word && playback.playbackCompleted) heard = true
    }
    DisposableEffect(task?.word, currentIndex) { onDispose { speech.manager.stop() } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activeTime = remember(round.id, currentIndex) { longArrayOf(0L, 0L) }
    DisposableEffect(activeTime, lifecycle, feedback) {
        fun pause() {
            if (activeTime[1] != 0L) activeTime[0] += SystemClock.elapsedRealtime() - activeTime[1]
            activeTime[1] = 0
        }
        if (feedback == null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) activeTime[1] = SystemClock.elapsedRealtime()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) pause()
            if (event == Lifecycle.Event.ON_RESUME && feedback == null) activeTime[1] = SystemClock.elapsedRealtime()
        }
        lifecycle.addObserver(observer)
        onDispose { pause(); lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(saving) { onBusyChange(saving) }
    DisposableEffect(Unit) { onDispose {
        focus.clearFocus(force = true)
        keyboard?.hide()
        onBusyChange(false)
    } }

    fun submit(index: Int, correct: Boolean, skip: Boolean = false, pairedTo: Int? = null) {
        if (saving || feedback != null) return
        saving = true
        onBusyChange(true)
        error = null
        val answered = round.tasks[index]
        focus.clearFocus()
        keyboard?.hide()
        val elapsed = activeTime[0] + if (activeTime[1] == 0L) 0L else SystemClock.elapsedRealtime() - activeTime[1]
        scope.launch {
            try {
                val next = withContext(Dispatchers.IO) {
                    repo.answer(round.id, index, correct, skip, elapsed, AbilityStore(context).load(), retention,
                        pairedTo, if (answered.kind == PocketKind.TYPING) typingErrors else 0)
                }
                feedback = when {
                    answered.kind == PocketKind.MATCH -> {
                        val indices = next.tasks.indices.filter {
                            next.tasks[it].kind == PocketKind.MATCH && next.tasks[it].group == answered.group
                        }
                        if (indices.all { it in next.done }) buildString {
                            val wrong = indices.filter { it in next.missed }
                            append("这组连完了 · ${indices.size - wrong.size} / ${indices.size} 组正确")
                            if (wrong.isEmpty()) append("\n全部连对了！")
                            else wrong.forEach { i ->
                                val item = next.tasks[i]
                                append("\n\n${item.word}")
                                next.pairings[i]?.let { append("\n你连的是：${next.tasks[it].definition}") }
                                append("\n正确对应：${item.definition}")
                            }
                        } else null
                    }
                    answered.kind == PocketKind.STUDY -> null
                    skip -> "先看一眼：${answered.word} · ${answered.definition}"
                    correct -> "答对了！${answered.word} · ${answered.definition}"
                    else -> "记住这一组：${answered.word} · ${answered.definition}"
                }
                selectedPair = null
                activeTime[0] = 0
                activeTime[1] = if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) SystemClock.elapsedRealtime() else 0
                onRoundChange(next)
            } catch (e: Exception) {
                error = "保存失败，请重试：${e.message}"
            } finally {
                saving = false
                onBusyChange(false)
            }
        }
    }

    fun changeListening() {
        if (saving) return
        saving = true
        onBusyChange(true)
        speech.manager.stop()
        scope.launch {
            try {
                val next = withContext(Dispatchers.IO) {
                    // Prompts already snapshot definitions; this also works after changing books.
                    val bank = round.tasks.associate { it.word to VocabularyItem(word = it.word,
                        definition = it.definition, phonetic = null, timestamp = 0) }
                    repo.replaceListening(round.id, currentIndex, bank)
                }
                onRoundChange(next)
            } catch (e: Exception) { error = "切换失败，请重试：${e.message}" }
            finally { saving = false; onBusyChange(false) }
        }
    }

    TeaExercisePage(title = if (task?.kind == PocketKind.TYPING) "打字记词" else "专项练习",
        done = round.done.size, total = round.tasks.size, busy = saving, onBack = onBack,
        actions = {
            when {
                feedback != null -> TeaExerciseAction(if (round.complete) "看看这一轮" else "继续", !saving) { feedback = null }
                round.complete -> TeaExerciseAction("完成", !saving, onClick = onBack)
                task?.kind == PocketKind.MATCH -> TeaExerciseAction("这组还不熟，看看答案", !saving, secondary = true) {
                    submit(selectedPair ?: currentIndex, false, true)
                }
                task != null -> {
                    when (task.kind) {
                        PocketKind.STUDY -> TeaExerciseAction("认识了，来试试", !saving) { submit(currentIndex, false) }
                        PocketKind.SPELLING -> TeaExerciseAction("检查拼写", !saving && spelling.length == task.answer.length) {
                            submit(currentIndex, spelling.equals(task.answer, true))
                        }
                        PocketKind.TYPING -> TeaExerciseAction("完成默写", !saving && spelling.equals(task.word, true)) {
                            submit(currentIndex, true)
                        }
                        else -> task.options.forEach { option ->
                            TeaExerciseOption(option, !saving && (task.kind != PocketKind.LISTEN || heard)) {
                                submit(currentIndex, option == task.answer)
                            }
                        }
                    }
                    if (task.kind != PocketKind.STUDY) {
                        TextButton(onClick = { submit(currentIndex, false, true) }, enabled = !saving,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (task.kind == PocketKind.TYPING) "看答案，稍后复习" else "还不熟 / 跳过")
                        }
                    }
                }
            }
        }) {
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        when {
            feedback != null -> item { TeaExerciseContent {
                Text(feedback!!, style = MaterialTheme.typography.titleLarge)
            } }
            round.complete -> item { TeaExerciseContent {
                    Text("这一小轮完成了", style = MaterialTheme.typography.headlineMedium)
                    Text("练过 ${round.wordCount} 个词 · ${round.correct} / ${round.exerciseCount} 次答对")
                    if (round.tasks.any { it.kind == PocketKind.TYPING }) {
                        val stats = remember(round.id, round.done.size) { repo.typingStats(round.id) }
                        TeaCaption("整词默写 ${stats.words} 个 · 准确率 ${stats.accuracy}% · ${stats.wordsPerMinute} 词/分钟")
                    }
                    round.missed.map { round.tasks[it].word }.distinct().forEach { word ->
                        TeaCaption("再留意：$word · ${round.tasks.first { it.word == word }.definition}")
                    }
                    TeaCaption("进度已保存，今天到这里也很好。下次会优先安排需要复习的词。")
            } }
            task?.kind == PocketKind.MATCH -> item {
                val indices = round.tasks.indices.filter {
                    round.tasks[it].kind == PocketKind.MATCH && round.tasks[it].group == task.group
                }
                val rightOrder = remember(round.id, task.group) { indices.shuffled() }
                val usedRight = round.pairings.values.toSet() + round.done.filter { it in indices && it !in round.pairings }
                TeaExerciseContent {
                    Text("把单词和词义连起来", style = MaterialTheme.typography.headlineSmall)
                    TeaCaption("先点左边的词，再点右边的词义")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            indices.forEach { index ->
                                val paired = index in round.done
                                OutlinedButton(onClick = { selectedPair = index }, enabled = !saving && !paired,
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor =
                                        if (selectedPair == index) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                    Text(if (paired) "${indices.indexOf(index) + 1} · ${round.tasks[index].word}" else round.tasks[index].word)
                                }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rightOrder.forEach { index ->
                                OutlinedButton(onClick = { selectedPair?.let { submit(it, it == index, pairedTo = index) } },
                                    enabled = !saving && selectedPair != null && index !in usedRight,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                                    val left = round.pairings.entries.firstOrNull { it.value == index }?.key
                                    Text((left?.let { "${indices.indexOf(it) + 1} · " } ?: "") + round.tasks[index].definition)
                                }
                            }
                        }
                    }
                }
            }
            task != null -> item(key = "pocket-$currentIndex") {
                TeaExerciseContent {
                    TeaCaption(task.kind.label)
                    when (task.kind) {
                        PocketKind.STUDY -> {
                            Text(task.word, style = MaterialTheme.typography.displaySmall)
                            Text(task.definition, style = MaterialTheme.typography.titleLarge)
                            DefaultPronunciationButton(task.word)
                        }
                        PocketKind.LISTEN -> {
                            Text("听到的是哪个词？", style = MaterialTheme.typography.headlineSmall)
                            FilledTonalButton(onClick = { preference.dialect?.let {
                                audioRequested = true
                                speech.manager.play(task.word, it)
                            } },
                                enabled = preference.dialect != null && playback.phase != SpeechPhase.INITIALIZING) {
                                Text(if (heard) "再听一次" else "播放发音")
                            }
                            if (playback.phase == SpeechPhase.ERROR || preference.dialect?.let { it !in playback.voices } == true
                                && playback.phase != SpeechPhase.INITIALIZING) {
                                TeaCaption("当前口音的语音不可用，可改做词义题")
                                PronunciationFeedback(task.word, speech.manager)
                            }
                            TextButton(onClick = ::changeListening, enabled = !saving) { Text("不方便听音，改做词义题") }
                        }
                        PocketKind.CLOZE -> Text(task.prompt, style = MaterialTheme.typography.headlineSmall)
                        PocketKind.SPELLING -> {
                            Text(task.prompt.toList().joinToString(" "), style = MaterialTheme.typography.headlineMedium)
                            Text(task.definition, style = MaterialTheme.typography.titleMedium)
                            TeaCaption("按从左到右的顺序，补上 ${task.answer.length} 个字母")
                            OutlinedTextField(value = spelling, onValueChange = { value ->
                                spelling = value.filter { it in 'a'..'z' || it in 'A'..'Z' }.take(task.answer.length)
                            }, label = { Text("缺少的字母") }, singleLine = true,
                                enabled = !saving, modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (spelling.length == task.answer.length) submit(currentIndex, spelling.equals(task.answer, true))
                                }))
                        }
                        PocketKind.TYPING -> {
                            Text(task.definition, style = MaterialTheme.typography.titleLarge)
                            TeaCaption("根据词义输入完整英文词。输错后修改，再继续输入。")
                            val firstError = spelling.indices.firstOrNull {
                                it >= task.word.length || !spelling[it].equals(task.word[it], true)
                            }
                            val prefix = firstError ?: spelling.length
                            Text("已正确输入 $prefix / ${task.word.length} 个字符",
                                color = if (firstError == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            OutlinedTextField(value = spelling, onValueChange = { value ->
                                val next = value.take(task.word.length)
                                if (next.length > spelling.length && next.indices.any {
                                    it >= task.word.length || !next[it].equals(task.word[it], true)
                                } && spelling.indices.none { it >= task.word.length || !spelling[it].equals(task.word[it], true) }) {
                                    typingErrors++
                                }
                                spelling = next
                                if (next.equals(task.word, true)) {
                                    focus.clearFocus()
                                    keyboard?.hide()
                                }
                            }, label = { Text("输入单词") }, singleLine = true, isError = firstError != null,
                                enabled = !saving, modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (spelling.equals(task.word, true)) submit(currentIndex, true)
                                }))
                            if (typingErrors > 0) TeaCaption("本词已纠错 $typingErrors 次；纠错后完成仍会安排复习。")
                        }
                        else -> Text(task.word, style = MaterialTheme.typography.displaySmall)
                    }
                }
            }
        }
    }
}
