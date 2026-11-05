package com.teameow.teawords.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Book progress first, then one expressive primary action and two quieter shortcuts. */
@Composable
internal fun StudyOverview(
    bookName: String, total: Int, studied: Int, verified: Int, due: Int, newBudget: Int,
    fuzzy: Int, busy: Boolean, paused: WordStudyResume?,
    onBooks: () -> Unit, onStart: () -> Unit, onReview: () -> Unit, onMore: () -> Unit,
    favorites: Int = 0, onFavorites: () -> Unit = {}, query: String = "",
    onQuery: (String) -> Unit = {}, onSearch: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextField(query, onQuery, Modifier.fillMaxWidth().testTag("study-search"),
            placeholder = { Text("搜索单词") }, leadingIcon = { Icon(AppSymbols.Search, null) },
            trailingIcon = { if (query.isNotBlank()) IconButton(onClick = onSearch) { Icon(AppSymbols.ArrowForward, "搜索学习词汇") } },
            singleLine = true, shape = CircleShape,
            colors = TextFieldDefaults.colors(focusedContainerColor = colors.surfaceContainerHigh,
                unfocusedContainerColor = colors.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch() }))
        Surface(onClick = onBooks, enabled = !busy, shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StudyBookCover(bookName)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bookName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Icon(AppSymbols.ChevronRight, "切换词书", Modifier.size(20.dp))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TeaCaption("已学 $studied")
                        TeaCaption("共 $total 词")
                    }
                    TeaProgressLine(if (total == 0) 0f else studied.toFloat() / total)
                }
            }
        }
        BoxWithConstraints {
            val extra = (48f * (LocalDensity.current.fontScale - 1f).coerceAtLeast(0f)).dp
            val height = ((maxWidth - 16.dp) / 2).coerceIn(152.dp, 216.dp) + extra
            Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(onClick = onStart, enabled = !busy, modifier = Modifier.weight(1f).fillMaxHeight()
                    .testTag("study-learn-entry"), shape = StudyPetalShape,
                    color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Icon(AppSymbols.Book5, null, Modifier.size(44.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("学习", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("${minOf(newBudget, (total - studied).coerceAtLeast(0))}", style = MaterialTheme.typography.titleLarge)
                        paused?.let { Text("续学 ${it.done}/${it.total}", style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StudyShortcut("复习", due, AppSymbols.Refresh, !busy, Modifier.weight(1f)
                        .testTag("study-review-entry"), colors.tertiaryContainer, colors.onTertiaryContainer, onReview)
                    StudyShortcut("收藏", favorites, AppSymbols.Favorite, !busy, Modifier.weight(1f)
                        .testTag("study-favorites-entry"), colors.secondaryContainer, colors.onSecondaryContainer, onFavorites)
                }
            }
        }
        TeaCaption("已验证 $verified 词 · 自评模糊 $fuzzy 词")
        Surface(onClick = onMore, enabled = !busy, shape = RoundedCornerShape(24.dp), color = colors.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("专项练习", style = MaterialTheme.typography.titleMedium)
                    TeaCaption("混合小练习 · 打字记词")
                }
                Icon(AppSymbols.ArrowForward, null)
            }
        }
    }
}

private val StudyPetalShape = GenericShape { size, _ ->
    val w = size.width; val h = size.height
    moveTo(w * .5f, h * .055f)
    cubicTo(w * .75f, -h * .10f, w * 1.10f, h * .25f, w * .945f, h * .5f)
    cubicTo(w * 1.10f, h * .75f, w * .75f, h * 1.10f, w * .5f, h * .945f)
    cubicTo(w * .25f, h * 1.10f, -w * .10f, h * .75f, w * .055f, h * .5f)
    cubicTo(-w * .10f, h * .25f, w * .25f, -h * .10f, w * .5f, h * .055f)
    close()
}

@Composable
internal fun StudyBookCover(label: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    val code = when {
        label.contains("四级") || label.contains("CET4", true) -> "CET-4"
        label.contains("六级") || label.contains("CET6", true) -> "CET-6"
        label.contains("雅思") || label.contains("IELTS", true) -> "IELTS"
        label.contains("托福") || label.contains("TOEFL", true) -> "TOEFL"
        label.contains("GRE", true) -> "GRE"
        label.contains("考研") || label.equals("ky", true) -> "考研"
        label.contains("中考") || label.equals("zk", true) -> "中考"
        label.contains("高考") || label.equals("gk", true) -> "高考"
        else -> "词汇"
    }
    val colors = MaterialTheme.colorScheme
    val tint = when (code) {
        "CET-4", "中考" -> colors.primary
        "CET-6", "考研" -> colors.tertiary
        else -> colors.secondary
    }
    Surface(modifier.width(if (compact) 48.dp else 56.dp).height(if (compact) 68.dp else 80.dp),
        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 7.dp),
        color = tint, contentColor = if (tint == colors.primary) colors.onPrimary else if (tint == colors.tertiary) colors.onTertiary else colors.onSecondary) {
        Box {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                drawRect(Color.Black.copy(alpha = .15f), size = androidx.compose.ui.geometry.Size(size.width * .1f, size.height))
                drawCircle(Color.White.copy(alpha = .13f), radius = size.width * .9f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 1.1f, size.height * .8f))
                drawLine(Color.White.copy(alpha = .45f), androidx.compose.ui.geometry.Offset(size.width * .22f, size.height * .28f),
                    androidx.compose.ui.geometry.Offset(size.width * .82f, size.height * .28f), strokeWidth = 2f)
            }
            Column(Modifier.fillMaxSize().padding(start = 9.dp, end = 4.dp, top = 10.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween) {
                Text("ENGLISH", fontSize = 6.sp, maxLines = 1, softWrap = false)
                Text(code, fontSize = if (code.length > 4) 9.sp else 12.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, softWrap = false)
                Text("VOCABULARY", fontSize = 5.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun StudyShortcut(title: String, count: Int, icon: ImageVector, enabled: Boolean,
    modifier: Modifier, color: Color, ink: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), color = color, contentColor = ink) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(28.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("$count", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

internal data class WordStudyResume(val done: Int, val total: Int)

/** The prompt scrolls independently; the primary action stays within reach above the keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudyFocusPage(
    index: Int, total: Int, phase: String, busy: Boolean, onPause: () -> Unit,
    content: @Composable ColumnScope.() -> Unit, actions: @Composable ColumnScope.() -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
            TopAppBar(title = { Text(phase, style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onPause, enabled = !busy) {
                    Icon(AppSymbols.ArrowBack, "返回")
                } }, actions = {
                    Text("$index / $total", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 20.dp))
                })
            LinearProgressIndicator(progress = { (index - 1).coerceAtLeast(0).toFloat() / total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(4.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("study-focus-content"),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp)) {
                item { Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(18.dp), content = content) }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier.testTag("study-focus-actions")) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp), content = actions)
            }
        }
    }
}
