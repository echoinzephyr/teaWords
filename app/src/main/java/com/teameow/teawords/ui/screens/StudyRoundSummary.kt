package com.teameow.teawords.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teameow.teawords.data.WordLearningSession

/** Completion celebrates actual practice without treating recognition as a failed recall test. */
@Composable
internal fun StudyRoundSummary(session: WordLearningSession?, completed: Int, recalled: Int,
    onBack: () -> Unit, onContinue: (() -> Unit)?) {
    var showWords by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppSymbols.ArrowBack, "返回学习") }
            Text("学习小结", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(Modifier.size(68.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Icon(AppSymbols.Check, null, Modifier.size(36.dp)) }
                    }
                    Text("本轮完成", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("$completed", fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text(if ((session?.exerciseVersion ?: 1) >= 2) "个单词完成三项练习" else "个单词已练习", style = MaterialTheme.typography.titleMedium)
                    Text("进度已保存，按自己的节奏继续。", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
            item {
                TeaCard {
                    if ((session?.practiced ?: 0) > 0) Text(if (session!!.exerciseVersion >= 2)
                        "${session.practiced} 个词使用过提示或重练，已保存辅助学习记录。" else "已完成认读练习 ${session.practiced} 个，可继续用拼写巩固。")
                    if (recalled > 0) Text("独立回忆正确 $recalled 个", style = MaterialTheme.typography.titleMedium)
                    val missed = session?.missed.orEmpty()
                    if (missed.isNotEmpty()) Text("${missed.size} 个词需要再巩固", color = MaterialTheme.colorScheme.primary)
                    else Text("下一轮，继续积累。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (session?.tasks?.isNotEmpty() == true) TextButton(onClick = { showWords = !showWords }) {
                        Text(if (showWords) "收起本轮单词" else "回顾本轮单词")
                    }
                }
            }
            if (showWords) items(session?.tasks.orEmpty(), key = { it.word }) { task ->
                TeaCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(task.word, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        if (task.word in session?.missed.orEmpty()) Text("待巩固", color = MaterialTheme.colorScheme.primary)
                    }
                    Text(task.definition, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onContinue ?: onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (onContinue == null) "回到学习" else "继续下一轮")
            }
            if (onContinue != null) TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("先休息一下") }
        }
    }
}
