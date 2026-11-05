package com.teameow.teawords.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.teameow.teawords.data.*
import com.teameow.teawords.data.search.DictionaryGateway
import com.teameow.teawords.data.search.SearchFilter
import com.teameow.teawords.data.search.WordFormKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

private val StudyInk: Color @Composable get() = MaterialTheme.colorScheme.onSurface
private val StudyGlass: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
private val StudyAccent: Color @Composable get() = MaterialTheme.colorScheme.primary

internal data class StudyWordDetails(
    val phonetic: String = "", val phrases: List<Pair<String, String>> = emptyList(),
    val forms: List<Pair<String, String>> = emptyList(), val saved: Boolean = false, val note: String = ""
)

internal class StudyWordDetailsRepository(private val helper: DatabaseHelper) {
    init { helper.writableDatabase.execSQL("CREATE TABLE IF NOT EXISTS study_word_notes (word TEXT PRIMARY KEY, note TEXT NOT NULL)") }
    fun load(word: String): StudyWordDetails {
        val source = DictionaryGateway.sourceOrNull()
        val head = source?.exists(LexicalText.normalize(word))
        val local = LocalDictionary(helper).exact(word)
        val prefix = LexicalText.normalize(word) + " "
        val phrases = source?.prefix(prefix, prefix + '\uFFFF', SearchFilter(limit = 8)).orEmpty()
            .map { it.word to it.zh }.filter { it.second.isNotBlank() }
        val forms = (head?.forms ?: local?.forms).orEmpty().split(Regex("[\\s/]+")).mapNotNull {
            val pair = it.split(':', limit = 2)
            if (pair.size == 2 && pair[1].isNotBlank()) WordFormKeys.label(pair[0]) to pair[1] else null
        }
        val note = helper.readableDatabase.rawQuery("SELECT note FROM study_word_notes WHERE word=?", arrayOf(word)).use {
            if (it.moveToFirst()) it.getString(0) else ""
        }
        return StudyWordDetails(head?.phonetic?.takeIf { it.isNotBlank() } ?: local?.phonetic.orEmpty(),
            phrases, forms, helper.isInVocabulary(word), note)
    }
    fun saveNote(word: String, note: String) {
        helper.writableDatabase.insertWithOnConflict("study_word_notes", null, android.content.ContentValues().apply {
            put("word", word); put("note", note)
        }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) }
    }
}

/** Recognition is practice; the caller keeps it separate from independently typed recall evidence. */
@Composable
internal fun ImmersiveWordScreen(
    helper: DatabaseHelper, word: String, definition: String, index: Int, total: Int,
    busy: Boolean, error: String?, wallpaper: String? = null, onPause: () -> Unit,
    onRecallComplete: ((Boolean, Long) -> Unit)? = null,
    onlineLookup: (suspend (String) -> StudyDictionaryEntry)? = null,
    onComplete: (Boolean, Long) -> Unit
) {
    val layout = teaLayoutSize()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(helper) { StudyWordDetailsRepository(helper) }
    var details by remember(word) { mutableStateOf(StudyWordDetails()) }
    var detailsBusy by remember(word) { mutableStateOf(true) }
    var localError by remember(word) { mutableStateOf<String?>(null) }
    var revealed by rememberSaveable(word) { mutableStateOf(false) }
    var recognized by rememberSaveable(word) { mutableStateOf(false) }
    var typing by rememberSaveable(word) { mutableStateOf(false) }
    var answer by rememberSaveable(word) { mutableStateOf("") }
    var typingMissed by rememberSaveable(word) { mutableStateOf(false) }
    var spellingCompleted by rememberSaveable(word) { mutableStateOf(false) }
    var typingFeedback by rememberSaveable(word) { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable(word) { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf(false) }
    var noteDraft by rememberSaveable(word) { mutableStateOf("") }
    val prefs = remember { context.getSharedPreferences("learning", 0) }
    var useWallpaper by remember { mutableStateOf(prefs.getBoolean("word_card_wallpaper_v2", false)) }
    val speech = rememberPronunciationServices()
    val pronunciation by speech.preference.collectAsState()
    val playback by speech.manager.state.collectAsState()
    val dictionary = remember(helper) { StudyDictionary(helper) }
    var onlineEntry by remember(word) { mutableStateOf<StudyDictionaryEntry?>(null) }
    var onlineBusy by remember(word) { mutableStateOf(false) }
    var onlineAttempted by remember(word) { mutableStateOf(false) }
    var onlineJob by remember { mutableStateOf<Job?>(null) }
    var dictionaryAudio by remember { mutableStateOf(prefs.getBoolean("dictionary_audio", true)) }
    val audio = remember(speech.manager) { com.teameow.teawords.data.pronunciation.DictionaryAudioPlayer(context, speech.manager) }
    val recordingPlaying by audio.playing.collectAsState()
    DisposableEffect(audio) { onDispose { audio.close() } }
    fun lookupOnline() {
        if (onlineBusy) return
        onlineBusy = true; onlineAttempted = true; localError = null
        onlineJob = scope.launch {
            try {
                onlineEntry = withContext(Dispatchers.IO) { onlineLookup?.invoke(word) ?: dictionary.lookup(word) }
                revealed = true
                if (onlineEntry?.phrases?.isNotEmpty() == true) selectedTab = 0
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { localError = "在线词典暂时无法连接，可继续使用本地词义。" }
            finally { onlineBusy = false }
        }
    }
    DisposableEffect(word) { onDispose { onlineJob?.cancel() } }
    LaunchedEffect(word, revealed) { if (revealed && !onlineAttempted) lookupOnline() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val activeTime = remember(word) { longArrayOf(0, 0) }
    DisposableEffect(word, lifecycle) {
        fun pause() {
            if (activeTime[1] != 0L) activeTime[0] += android.os.SystemClock.elapsedRealtime() - activeTime[1]
            activeTime[1] = 0
        }
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) activeTime[1] = android.os.SystemClock.elapsedRealtime()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) { pause(); audio.stop(); speech.manager.stop() }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) activeTime[1] = android.os.SystemClock.elapsedRealtime()
        }
        lifecycle.addObserver(observer)
        onDispose { pause(); lifecycle.removeObserver(observer); audio.stop(); speech.manager.stop() }
    }
    fun finish(correct: Boolean) {
        if (busy || detailsBusy) return
        val elapsed = activeTime[0] + if (activeTime[1] == 0L) 0 else android.os.SystemClock.elapsedRealtime() - activeTime[1]
        focus.clearFocus(); keyboard?.hide()
        if (spellingCompleted && onRecallComplete != null) onRecallComplete(correct, elapsed)
        else onComplete(correct, elapsed)
    }
    LaunchedEffect(word) {
        try {
            details = withContext(Dispatchers.IO) { repo.load(word) }
            onlineEntry = withContext(Dispatchers.IO) { dictionary.cached(word) }
            if (details.phrases.isEmpty() && details.forms.isNotEmpty()) selectedTab = 1
        }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { localError = "词条资料暂时无法读取，可继续学习" }
        finally { detailsBusy = false }
    }
    val controlsEnabled = !busy && !detailsBusy
    Box(Modifier.fillMaxSize()) {
        StudyAmbientBackground(if (useWallpaper) wallpaper else null)
        CompositionLocalProvider(LocalContentColor provides StudyInk) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .then(if (typing) Modifier.clearAndSetSemantics { } else Modifier)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    StudyCircle(onPause, "返回", !busy) { Icon(AppSymbols.ArrowBack, "返回") }
                    Surface(color = StudyGlass, contentColor = StudyInk, shape = CircleShape) {
                        Column(Modifier.width(140.dp).padding(horizontal = 24.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${index - 1} / $total", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            LinearProgressIndicator(progress = { (index - 1).toFloat() / total.coerceAtLeast(1) },
                                color = StudyAccent, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.padding(top = 3.dp).fillMaxWidth().height(4.dp), drawStopIndicator = {})
                        }
                    }
                    Box {
                        StudyCircle({ menu = true }, "更多", !busy) { Icon(Icons.Default.MoreHoriz, "更多") }
                        TeaDropdownMenu(menu, { menu = false }, listOf(
                            TeaMenuSection(listOf(
                                TeaMenuOption("查看释义", AppSymbols.Info, enabled = controlsEnabled) { revealed = true; menu = false },
                                TeaMenuOption(if (useWallpaper) "使用应用主题" else "跟随首页壁纸", AppSymbols.Wallpaper) {
                                    useWallpaper = !useWallpaper; prefs.edit().putBoolean("word_card_wallpaper_v2", useWallpaper).apply(); menu = false
                                },
                                TeaMenuOption(if (onlineBusy) "正在查询…" else "在线查询词义 · 有道", AppSymbols.Search, enabled = !onlineBusy) { menu = false; lookupOnline() }
                            )),
                            TeaMenuSection(listOf(
                                TeaMenuOption(if (pronunciation.dialect == PronunciationDialect.UK) "切换美式发音" else "切换英式发音", AppSymbols.VoiceSelection) {
                                    audio.stop(); speech.manager.stop(); speech.setDefault(if (pronunciation.dialect == PronunciationDialect.UK) PronunciationDialect.US else PronunciationDialect.UK); menu = false
                                },
                                TeaMenuOption(if (dictionaryAudio) "发音来源：有道音频" else "发音来源：系统语音", AppSymbols.VolumeUp) {
                                    audio.stop(); speech.manager.stop(); dictionaryAudio = !dictionaryAudio; prefs.edit().putBoolean("dictionary_audio", dictionaryAudio).apply(); menu = false
                                },
                                TeaMenuOption("系统语音设置", AppSymbols.Settings) {
                                    menu = false
                                    try { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
                                    catch (_: Exception) { localError = "无法打开系统语音设置" }
                                }
                            )),
                            TeaMenuSection(listOf(TeaMenuOption("暂停，稍后继续", AppSymbols.ArrowBack) { menu = false; onPause() }))
                        ))
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("immersive-word-content")
                    .pointerInput(word, controlsEnabled) {
                        var distance = 0f
                        detectHorizontalDragGestures(onDragStart = { distance = 0f }, onDragEnd = {
                            if (controlsEnabled && distance < -72.dp.toPx()) { typing = true; answer = ""; typingFeedback = null }
                        }, onHorizontalDrag = { change, delta ->
                            if (controlsEnabled) { distance += delta; change.consume() }
                        })
                    },
                    contentPadding = PaddingValues(horizontal = layout.horizontalPadding, vertical = if (layout.compact) 12.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        Box(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 42.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(word, fontSize = layout.wordSize,
                                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, lineHeight = layout.wordSize * 1.2f)
                                TextButton(onClick = {
                                    pronunciation.dialect?.let { if (dictionaryAudio) audio.play(word, it) else speech.manager.play(word, it) }
                                }, enabled = pronunciation.dialect != null && (dictionaryAudio || playback.phase != com.teameow.teawords.data.pronunciation.SpeechPhase.INITIALIZING),
                                    colors = ButtonDefaults.textButtonColors(contentColor = StudyInk)) {
                                    Text(if (pronunciation.dialect == PronunciationDialect.UK) "英" else "美",
                                        Modifier.background(StudyInk.copy(alpha = .12f), RoundedCornerShape(7.dp)).padding(4.dp), fontSize = 12.sp)
                                    Spacer(Modifier.width(7.dp))
                                    val regional = if (pronunciation.dialect == PronunciationDialect.UK) onlineEntry?.uk else onlineEntry?.us
                                    Text(if (recordingPlaying) "正在播放…" else regional?.takeIf { it.isNotBlank() }?.let { "/${it.trim('/')}/" } ?: "播放发音", fontSize = 15.sp)
                                    Spacer(Modifier.width(5.dp)); Icon(AppSymbols.VolumeUp, "播放发音", Modifier.size(16.dp))
                                }
                                if (onlineEntry == null && details.phonetic.isNotBlank()  )
                                    Text("/${details.phonetic.trim('/')}/", fontSize = 14.sp, color = StudyInk.copy(alpha = .65f))
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    repeat(3) { dot -> Box(Modifier.size(8.dp).background(
                                        if (revealed && dot == 0) StudyInk else StudyInk.copy(alpha = .25f), CircleShape)) }
                                }
                            }
                            Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.CenterHorizontally) {
                                TextButton(onClick = { if (revealed) finish(recognized) else { recognized = true; revealed = true } }, enabled = controlsEnabled,
                                    colors = ButtonDefaults.textButtonColors(contentColor = StudyInk), contentPadding = PaddingValues(0.dp)) {
                                    Text(if (revealed) "继续" else "会", fontSize = if (revealed) 16.sp else 22.sp)
                                }
                                IconButton(enabled = controlsEnabled, onClick = {
                                    detailsBusy = true
                                    scope.launch {
                                        try {
                                            val saved = !details.saved
                                            withContext(Dispatchers.IO) {
                                                if (saved) helper.addVocabulary(word, details.phonetic, definition) else helper.removeVocabulary(word)
                                            }
                                            details = details.copy(saved = saved)
                                        } catch (_: Exception) { localError = "生词本保存失败，请重试" }
                                        finally { detailsBusy = false }
                                    }
                                }) { Icon(if (details.saved) Icons.Default.Star else Icons.Outlined.StarBorder,
                                    if (details.saved) "移出生词本" else "加入生词本", tint = StudyInk) }
                                TextButton(enabled = controlsEnabled, onClick = { typing = true; answer = ""; typingFeedback = null },
                                    colors = ButtonDefaults.textButtonColors(contentColor = StudyInk), contentPadding = PaddingValues(0.dp)) { Text("拼写", fontSize = 14.sp) }
                            }
                        }
                    }
                    if (!revealed) item { Text("← 左滑进入拼写", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        fontSize = 12.sp, color = StudyInk.copy(alpha = .7f)) }
                    (error ?: localError)?.let { message -> item {
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                            Text(message, Modifier.fillMaxWidth().padding(16.dp))
                        }
                    } }
                    if (playback.word == word) playback.message?.let { message -> item { Text(message, color = StudyInk.copy(alpha = .8f), fontSize = 13.sp) } }
                    item {
                        AnimatedVisibility(revealed,
                            enter = fadeIn(tween(240)) + expandVertically(spring(dampingRatio = .85f)),
                            exit = fadeOut(tween(140)) + shrinkVertically()) {
                            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                                Text(definition,
                                    Modifier.fillMaxWidth().padding(20.dp), textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                        item {
                            AnimatedVisibility(revealed, enter = fadeIn(tween(260)) + expandVertically(spring(dampingRatio = .85f)), exit = fadeOut() + shrinkVertically()) {
                            Surface(color = StudyGlass, contentColor = StudyInk, shape = RoundedCornerShape(24.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    onlineEntry?.definition?.takeIf { it.isNotBlank() && it.trim() != definition.trim() }?.let {
                                        TeaCaption("补充释义 · 有道")
                                        Text(it, style = MaterialTheme.typography.bodyLarge)
                                        HorizontalDivider(color = StudyInk.copy(alpha = .08f))
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("短语", "词形").forEachIndexed { tab, title ->
                                            FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab },
                                                label = { Text(title) }, modifier = Modifier.weight(1f))
                                        }
                                    }
                                    val rows = if (selectedTab == 0) onlineEntry?.phrases?.takeIf { it.isNotEmpty() } ?: details.phrases else onlineEntry?.forms?.takeIf { it.isNotEmpty() } ?: details.forms
                                    if (rows.isEmpty()) Text(if (selectedTab == 0) "本地词典暂无短语" else "本地词典暂无词形资料",
                                        fontSize = 14.sp, color = StudyInk.copy(alpha = .65f), modifier = Modifier.padding(vertical = 12.dp))
                                    rows.forEachIndexed { i, pair ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Text(pair.first, Modifier.weight(1f), fontSize = 15.sp)
                                            Text(pair.second, Modifier.weight(1f), fontSize = 15.sp, textAlign = TextAlign.End)
                                        }
                                        if (i != rows.lastIndex) HorizontalDivider(color = StudyInk.copy(alpha = .08f))
                                    }
                                }
                            }
                            }
                        }
                        item {
                            AnimatedVisibility(revealed, enter = fadeIn(tween(260)) + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (onlineBusy) TeaCaption("正在加载在线补充…")
                            else if (onlineEntry == null) TextButton(onClick = { lookupOnline() }) { Text("重试在线补充 · 有道") }
                            Surface(onClick = { noteDraft = details.note; editingNote = true }, enabled = controlsEnabled,
                                color = StudyGlass, contentColor = StudyInk.copy(alpha = .7f), shape = RoundedCornerShape(24.dp)) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(AppSymbols.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                                    Text(details.note.ifBlank { "添加笔记" }, Modifier.weight(1f), fontSize = 14.sp)
                                    Icon(AppSymbols.ChevronRight, null, Modifier.size(20.dp))
                                }
                            }
                            }
                            }
                        }
                }
                Row(Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow,
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp).testTag("immersive-word-actions"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StudyPill(if (revealed) "下一个" else "认识", controlsEnabled, Modifier.weight(1f), primary = true) {
                        if (revealed) finish(recognized) else { recognized = true; revealed = true }
                    }
                    StudyPill(if (revealed) "记错了" else "不认识", controlsEnabled, Modifier.weight(1f)) {
                        if (revealed) finish(false) else { recognized = false; revealed = true }
                    }
                }
            }
        }
    }
    if (typing) PredictiveBackLayer(enterFromRight = true, onBack = { typing = false; focus.clearFocus(); keyboard?.hide() }) { close ->
        StudySpellingScreen(definition, answer, { answer = it }, typingFeedback,
            onlineEntry?.uk?.ifBlank { onlineEntry?.us.orEmpty() }?.ifBlank { details.phonetic } ?: details.phonetic,
            if (useWallpaper) wallpaper else null, controlsEnabled, onClose = close,
            onHint = { typingMissed = true }, onSubmit = {
                if (answer.trim().equals(word, true)) {
                    recognized = !typingMissed; spellingCompleted = true; revealed = true; typing = false
                    focus.clearFocus(); keyboard?.hide()
                } else { typingMissed = true; typingFeedback = "还不对，再试一次；可返回词卡查看答案。" }
            })
    }
    if (editingNote) AlertDialog(onDismissRequest = { if (!detailsBusy) editingNote = false }, title = { Text("$word · 笔记") },
        text = { Column {
            OutlinedTextField(noteDraft, { noteDraft = it.take(4000) }, label = { Text("自己的理解或记忆线索") }, minLines = 3)
            localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(enabled = !detailsBusy, onClick = {
            detailsBusy = true; localError = null
            scope.launch {
                try { withContext(Dispatchers.IO) { repo.saveNote(word, noteDraft) }; details = details.copy(note = noteDraft); editingNote = false }
                catch (_: Exception) { localError = "笔记保存失败，请重试" }
                finally { detailsBusy = false }
            }
        }) { Text("保存") } }, dismissButton = { TextButton(enabled = !detailsBusy, onClick = { editingNote = false }) { Text("取消") } })
}

@Composable
private fun StudyCircle(onClick: () -> Unit, label: String, enabled: Boolean, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    FilledIconButton(onClick, enabled = enabled, interactionSource = interaction,
        modifier = Modifier.size(48.dp).teaElasticPress(interaction, enabled),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = StudyGlass, contentColor = StudyInk), content = content)
}

@Composable
private fun StudyPill(label: String, enabled: Boolean, modifier: Modifier, primary: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val layout = teaLayoutSize()
    Button(onClick, enabled = enabled, interactionSource = interaction,
        modifier = modifier.heightIn(min = layout.actionHeight).teaElasticPress(interaction, enabled), shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) StudyAccent else MaterialTheme.colorScheme.secondaryContainer, contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = StudyGlass.copy(alpha = .35f), disabledContentColor = StudyInk.copy(alpha = .4f))) {
        AnimatedContent(label, transitionSpec = {
            (fadeIn(tween(180)) + slideInVertically { it / 3 }).togetherWith(fadeOut(tween(100)))
        }, label = "study-action-label") {
            Text(it, fontSize = if (layout.compact) 16.sp else 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun StudyAmbientBackground(wallpaper: String?) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    if (!wallpaper.isNullOrBlank()) {
        AsyncImage(ImageRequest.Builder(LocalContext.current).data(wallpaper).size(720, 1600).build(),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(6.dp))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            MaterialTheme.colorScheme.background.copy(alpha = .35f),
            MaterialTheme.colorScheme.background.copy(alpha = .22f),
            MaterialTheme.colorScheme.background.copy(alpha = .48f)
        ))))
    }
}
