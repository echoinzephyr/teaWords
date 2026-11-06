package com.teameow.teawords.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal data class StudySpellingResult(val word: String, val correct: Boolean, val revealed: Boolean)

/** A focused spelling surface shared by word cards and lookup-history review. */
@Composable
internal fun StudySpellingScreen(
    definition: String, answer: String, onAnswer: (String) -> Unit,
    feedback: String?, phonetic: String, wallpaper: String?, enabled: Boolean,
    onClose: () -> Unit, onHint: () -> Unit, onSubmit: () -> Unit,
    progress: Pair<Int, Int>? = null, closeLabel: String = "返回词卡",
    result: StudySpellingResult? = null, onReveal: (() -> Unit)? = null, onContinue: (() -> Unit)? = null,
    completedSteps: Int? = null
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var hint by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { focusManager.clearFocus(force = true); keyboard?.hide() } }
    LaunchedEffect(result != null) {
        if (result == null) {
            kotlinx.coroutines.delay(320)
            focus.requestFocus(); keyboard?.show()
        } else { focusManager.clearFocus(); keyboard?.hide() }
    }
    val ink = MaterialTheme.colorScheme.onBackground
    Box(Modifier.fillMaxSize()) {
        StudyAmbientBackground(wallpaper)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onClose, enabled = enabled, modifier = Modifier.size(48.dp)) { Icon(AppSymbols.Close, closeLabel) }
                progress?.let { (index, total) ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer) {
                            Text("$index / $total", Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(Modifier.width(48.dp))
                }
            }
            completedSteps?.let { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { WordExerciseDots(it) } }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                result?.let {
                    Icon(if (it.correct && !it.revealed) AppSymbols.Check else AppSymbols.Info, null,
                        Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(it.word, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.headlineLarge)
                }
                Text(definition, Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleLarge, color = ink, textAlign = TextAlign.Center)
                if (result == null) BasicTextField(answer, onAnswer, enabled = enabled, singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(color = ink, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (enabled && answer.isNotBlank()) onSubmit() }),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp).heightIn(min = 56.dp)
                        .focusRequester(focus).testTag("spelling-input"))
                else Text(when {
                    result.revealed -> "本次作为练习保存，不计入独立回忆正确"
                    result.correct -> "回忆正确"
                    else -> "再记一次，下次继续巩固"
                }, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                feedback?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
            }
            if (result != null) {
                Button(onClick = { onContinue?.invoke() }, enabled = enabled && onContinue != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (enabled) "保存并继续" else "正在保存…") }
            } else Column(Modifier.align(Alignment.End), horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                onReveal?.let { reveal -> FilledTonalIconButton(onClick = reveal, enabled = enabled, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.Visibility, "查看答案")
                } }
                if (hint) Surface(shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .9f)) {
                    Text(phonetic.takeIf { it.isNotBlank() }?.let { "/${it.trim('/')}/" } ?: "暂无音标",
                        Modifier.padding(horizontal = 18.dp, vertical = 12.dp), color = ink)
                } else FilledTonalIconButton(onClick = { hint = true; onHint() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Outlined.Lightbulb, "显示音标提示")
                }
                FilledIconButton(onClick = onSubmit, enabled = enabled && answer.isNotBlank(), modifier = Modifier.size(56.dp)) {
                    Icon(AppSymbols.Check, "检查拼写", Modifier.size(32.dp))
                }
            }
        }
    }
}
