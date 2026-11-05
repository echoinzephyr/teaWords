package com.teameow.teawords.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** A focused spelling surface; hints never disclose the written answer. */
@Composable
internal fun StudySpellingScreen(
    definition: String, answer: String, onAnswer: (String) -> Unit,
    feedback: String?, phonetic: String, wallpaper: String?, enabled: Boolean,
    onClose: () -> Unit, onHint: () -> Unit, onSubmit: () -> Unit
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(320)
        focus.requestFocus(); keyboard?.show()
    }
    val ink = MaterialTheme.colorScheme.onBackground
    Box(Modifier.fillMaxSize()) {
        StudyAmbientBackground(wallpaper)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalIconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(AppSymbols.Close, "返回词卡") }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(definition, Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleLarge, color = ink, textAlign = TextAlign.Center)
                BasicTextField(answer, onAnswer, enabled = enabled, singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium.copy(color = ink, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (enabled && answer.isNotBlank()) onSubmit() }),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp).heightIn(min = 56.dp)
                        .focusRequester(focus).testTag("spelling-input"))
                feedback?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
            }
            Column(Modifier.align(Alignment.End), horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
