package com.teameow.teawords.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teameow.teawords.data.*
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.abs
import kotlin.math.roundToInt

private val quoteClient by lazy { OkHttpClient() }

@Composable
internal fun installedVersionName(): String {
    val context = LocalContext.current
    return remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    }
}

@Composable
internal fun installedVersionCode(): Long {
    val context = LocalContext.current
    return remember(context) {
        @Suppress("DEPRECATION")
        androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsView(
    pronunciationDialect: PronunciationDialect,
    selectedWordbookIds: Set<String>,
    homeTitle: String,
    homeSubtitle: String,
    homeSubtitleMode: SubtitleMode,
    homeWallpaperMode: HomeWallpaperMode,
    customWallpaperUri: String?,
    hitokotoRefreshInterval: Int,
    themeMode: AppThemeMode,
    onPronunciationDialectChange: (PronunciationDialect) -> Unit,
    onSelectedWordbooksChange: (Set<String>) -> Unit,
    onHomeTitleChange: (String) -> Unit,
    onHomeSubtitleChange: (String) -> Unit,
    onHomeSubtitleModeChange: (SubtitleMode) -> Unit,
    onHomeWallpaperModeChange: (HomeWallpaperMode) -> Unit,
    onCustomWallpaperUriChange: (String?) -> Unit,
    onHitokotoRefreshIntervalChange: (Int) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    studyStrategy: StudyStrategyPreference,
    onStudyStrategyChange: (StudyStrategyPreference) -> Unit,
    dailyNewCapOverride: Int,
    onDailyNewCapChange: (Int) -> Unit,
    targetRetentionOverride: Double,
    onTargetRetentionChange: (Double) -> Unit,
    onBack: () -> Unit,
    pronunciationPreferenceReady: Boolean = true,
    pronunciationPreferenceError: String? = null,
    onRetryPronunciationPreference: () -> Unit = {},
    predictiveBackEnabled: Boolean = false,
    onPredictiveBackChange: (Boolean) -> Unit = {},
    pureBlack: Boolean = false,
    onPureBlackChange: (Boolean) -> Unit = {},
    dailyWallpaperUrl: String? = null
) {
    var currentPage by rememberSaveable { mutableStateOf(SettingsPage.Main) }
    val pageStates = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val mainListState = rememberLazyListState()
    val version = installedVersionName()
    val settingsContext = LocalContext.current
    val learningPrefs = remember(settingsContext) { settingsContext.getSharedPreferences("learning", 0) }
    var roundLimit by remember { mutableIntStateOf(learningPrefs.getInt("expressive_round_limit", 10).takeIf { it in listOf(10, 15, 20) } ?: 10) }
    val translationPrefs = remember(settingsContext) { TranslationPreferences(settingsContext) }
    val translationSummary = remember(currentPage) { translationPrefs.load().engine.label }
    // 动态取色需要 Android 12+；不支持时开关禁用，而不是假装能开。
    val dynamicColorSupported = remember { com.teameow.teawords.ui.theme.supportsDynamicColor() }
    val books = LearningWordbook.available.filter { it.id in selectedWordbookIds }
    val scope = rememberCoroutineScope()
    var quoteLoading by remember { mutableStateOf(false) }
    var quoteFeedback by remember { mutableStateOf<String?>(null) }
    var wallpaperBusy by remember { mutableStateOf(false) }
    var wallpaperError by remember { mutableStateOf<String?>(null) }
    var confirmRemoveWallpaper by remember { mutableStateOf(false) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selected ->
        if (selected != null) {
            wallpaperBusy = true
            wallpaperError = null
            scope.launch {
                try {
                    val saved = CustomWallpaperStore.import(settingsContext, selected)
                    val previous = customWallpaperUri
                    onCustomWallpaperUriChange(saved)
                    CustomWallpaperStore.remove(settingsContext, previous)
                } catch (error: Exception) {
                    wallpaperError = error.message ?: "无法保存图片，请重试"
                } finally {
                    wallpaperBusy = false
                }
            }
        }
    }
    val screen: @Composable (SettingsPage, () -> Unit) -> Unit = { page, back ->
        pageStates.SaveableStateProvider(page.name) {
            if (page == SettingsPage.Translation) TranslationSettingsPage(onBack = back)
            else SettingsPageFrame(
                containerColor = MaterialTheme.colorScheme.background,
                title = when (page) {
                    SettingsPage.Main -> "设置"
                    SettingsPage.Interface -> "主页定制"
                    SettingsPage.Appearance -> "主题"
                    SettingsPage.Strategy -> "学习策略"
                    SettingsPage.Pronunciation -> "发音偏好"
                    SettingsPage.Translation -> "句子与长文翻译"
                    SettingsPage.Wordbooks -> "词书管理"
                    SettingsPage.About -> "关于茶词"
                    SettingsPage.Update -> "检查更新"
                    SettingsPage.Logs -> "日志获取"
                },
                subtitle = when (page) {
                    SettingsPage.Main -> null
                    SettingsPage.About -> "查词、翻译，慢慢记住"
                    SettingsPage.Update -> "下载更新，通过系统确认安装"
                    else -> null
                },
                onBack = back
            ) {
                when (page) {
                    SettingsPage.Main -> Unit
                    SettingsPage.Appearance -> {
                        val light = com.teameow.teawords.ui.theme.settingsColorScheme(settingsContext, false, dynamicColor, false)
                        val dark = com.teameow.teawords.ui.theme.settingsColorScheme(settingsContext, true, dynamicColor, false)
                        val black = com.teameow.teawords.ui.theme.settingsColorScheme(settingsContext, true, dynamicColor, true)
                        item { SettingsLivePreview(MaterialTheme.colorScheme) }
                        item { SettingsThemeControls(themeMode, pureBlack, light, dark, black, onThemeModeChange, onPureBlackChange) }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SettingsOptionHeading(AppSymbols.Palette, "主题色彩")
                                Row(Modifier.fillMaxWidth().toggleable(dynamicColor && dynamicColorSupported,
                                    enabled = dynamicColorSupported, role = Role.Switch, onValueChange = onDynamicColorChange),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("动态取色", style = MaterialTheme.typography.bodyLarge)
                                        TeaCaption(if (dynamicColorSupported) "跟随系统壁纸的颜色；关闭时使用品牌紫" else "需要 Android 12 及以上")
                                    }
                                    Switch(dynamicColor && dynamicColorSupported, onCheckedChange = null, enabled = dynamicColorSupported)
                                }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SettingsOptionHeading(AppSymbols.ArrowBack, "返回手势")
                                Row(Modifier.fillMaxWidth().toggleable(predictiveBackEnabled, role = Role.Switch, onValueChange = onPredictiveBackChange),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("预测性返回", style = MaterialTheme.typography.bodyLarge)
                                        TeaCaption("侧滑预览上一页，松手返回；需要 Android 14 及以上")
                                    }
                                    Switch(predictiveBackEnabled, onCheckedChange = null)
                                }
                            }
                        }
                    }
                    SettingsPage.Logs -> {
                        item { DiagnosticsExport() }
                        item { RepositoryCard() }
                    }
                    SettingsPage.Strategy -> {
                        item {
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                StudyStrategyPreference.entries.forEach { option ->
                                    SettingsChoice(option.label, option.detail, studyStrategy == option) { onStudyStrategyChange(option) }
                                }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("每轮目标", style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(10, 15, 20).forEach { count ->
                                        SettingsValueChip(roundLimit == count, {
                                            roundLimit = count
                                            learningPrefs.edit().putInt("expressive_round_limit", count).apply()
                                        }, label = { Text("$count 个") })
                                    }
                                }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("每日新词上限", style = MaterialTheme.typography.titleMedium)
                                TeaCaption("控制每天新增的学习量，到期复习不受此上限影响。")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0, 5, 10, 20, 40).forEach { value ->
                                        SettingsValueChip(dailyNewCapOverride == value, { onDailyNewCapChange(value) }, label = { Text(if (value == 0) "按策略" else "$value 个") })
                                    }
                                }
                            }
                        }
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("目标记忆保持率", style = MaterialTheme.typography.titleMedium)
                                TeaCaption("目标越高，复习越频繁；选择「按策略」可自动安排。")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0.0, 0.85, 0.90, 0.94).forEach { value ->
                                        // Preferences stores Float: equality with a Double fails after relaunch.
                                        SettingsValueChip(abs(targetRetentionOverride - value) < 0.001, { onTargetRetentionChange(value) }, label = { Text(if (value == 0.0) "按策略" else "${(value * 100).roundToInt()}%") })
                                    }
                                }
                            }
                        }
                    }
                    SettingsPage.Interface -> {
                        item { SettingsHomePreview(when (homeWallpaperMode) {
                            HomeWallpaperMode.NONE -> null
                            HomeWallpaperMode.DAILY -> dailyWallpaperUrl
                            HomeWallpaperMode.CUSTOM -> customWallpaperUri
                        }, homeTitle, homeSubtitle) }
                        item { SettingsOptionHeading(AppSymbols.Wallpaper, "背景") }
                        item {
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SettingsChoice("纯色背景", "让文字和控件更醒目", homeWallpaperMode == HomeWallpaperMode.NONE) {
                                    onHomeWallpaperModeChange(HomeWallpaperMode.NONE)
                                }
                                SettingsChoice("每日壁纸", "从 Bing 获取，每天自动更新", homeWallpaperMode == HomeWallpaperMode.DAILY) {
                                    onHomeWallpaperModeChange(HomeWallpaperMode.DAILY)
                                }
                                SettingsChoice("自定义壁纸", "从设备选择并保存在应用内", homeWallpaperMode == HomeWallpaperMode.CUSTOM) {
                                    if (customWallpaperUri == null) wallpaperPicker.launch("image/*")
                                    else onHomeWallpaperModeChange(HomeWallpaperMode.CUSTOM)
                                }
                            }
                        }
                        if (customWallpaperUri != null) item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { wallpaperPicker.launch("image/*") }, enabled = !wallpaperBusy,
                                    modifier = Modifier.weight(1f)) { Text(if (wallpaperBusy) "正在保存…" else "更换图片") }
                                TextButton(onClick = { confirmRemoveWallpaper = true }, enabled = !wallpaperBusy) { Text("移除") }
                            }
                        }
                        if (wallpaperBusy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        wallpaperError?.let { message -> item { TeaCaption(message) } }
                        item { SettingsHeading("主页文字") }
                        item {
                            TeaCard(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                                OutlinedTextField(homeTitle, { onHomeTitleChange(it.take(40)) },
                                    label = { Text("主页标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    shape = RoundedCornerShape(16.dp))
                                Text("副标题", style = MaterialTheme.typography.titleMedium)
                                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                    SubtitleMode.entries.forEachIndexed { index, mode ->
                                        SegmentedButton(homeSubtitleMode == mode, { onHomeSubtitleModeChange(mode) }, SegmentedButtonDefaults.itemShape(index, 2)) {
                                            Text(if (mode == SubtitleMode.CUSTOM) "自定义文字" else "一言名句")
                                        }
                                    }
                                }
                                if (homeSubtitleMode == SubtitleMode.CUSTOM) {
                                    OutlinedTextField(homeSubtitle, { onHomeSubtitleChange(it.take(160)) },
                                        label = { Text("写一句喜欢的话") }, modifier = Modifier.fillMaxWidth(),
                                        minLines = 2, maxLines = 4, shape = RoundedCornerShape(12.dp))
                                } else {
                                    Text(homeSubtitle, style = MaterialTheme.typography.bodyLarge)
                                    FilledTonalButton(
                                        enabled = !quoteLoading,
                                        onClick = {
                                            quoteLoading = true
                                            quoteFeedback = null
                                            scope.launch {
                                                try {
                                                    val quote = withContext(Dispatchers.IO) {
                                                        quoteClient.newCall(Request.Builder().url("https://v1.hitokoto.cn/?encode=text&c=i").build()).execute().use { response ->
                                                            check(response.isSuccessful)
                                                            response.body?.string()?.trim().also { check(!it.isNullOrBlank()) }!!
                                                        }
                                                    }
                                                    onHomeSubtitleChange(quote)
                                                    quoteFeedback = "名句已更新"
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (_: Exception) {
                                                    quoteFeedback = "暂时无法获取，请检查网络后重试"
                                                } finally {
                                                    quoteLoading = false
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (quoteLoading) "正在获取…" else "换一句")
                                    }
                                    quoteFeedback?.let { TeaCaption(it) }
                                    Text("刷新间隔 · $hitokotoRefreshInterval 分钟", style = MaterialTheme.typography.titleSmall)
                                    Slider(hitokotoRefreshInterval.toFloat(), { onHitokotoRefreshIntervalChange(it.roundToInt()) }, valueRange = 1f..60f, steps = 58)
                                }
                            }
                        }
                    }
                    SettingsPage.Pronunciation -> {
                        item { TeaCaption("查词和学习时，优先使用所选口音播放发音。") }
                        if (!pronunciationPreferenceReady) item { TeaCaption("正在读取已保存的默认口音…") }
                        pronunciationPreferenceError?.let { error -> item {
                            TeaCaption(error)
                            TextButton(onClick = onRetryPronunciationPreference) { Text("重试读取偏好") }
                        } }
                        item {
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PronunciationDialect.entries.forEach { dialect ->
                                    SettingsChoice(dialect.label, if (dialect == PronunciationDialect.US) "US · American English" else "UK · British English", pronunciationPreferenceReady && pronunciationDialect == dialect) { onPronunciationDialectChange(dialect) }
                                }
                            }
                        }
                        item { TeaCaption("默认口音用于学习和快速排雷；词典页仍可分别播放 UK 与 US。") }
                        item { PronunciationResourcePanel() }
                    }
                    SettingsPage.Wordbooks -> {
                        item {
                            TeaCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("已选择 ${books.size} 本词书", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(if (books.isEmpty()) "选择至少一本词书，开启词书学习。" else "可同时选择多本词书；调整选择会保留已有学习记录。", color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        items(LearningWordbook.available, key = { it.id }) { book ->
                            val selected = book.id in selectedWordbookIds
                            Surface(shape = RoundedCornerShape(20.dp), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent) {
                                Row(Modifier.fillMaxWidth().toggleable(selected, role = Role.Checkbox) { checked ->
                                    onSelectedWordbooksChange(if (checked) selectedWordbookIds + book.id else selectedWordbookIds - book.id)
                                }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(book.title, style = MaterialTheme.typography.titleMedium)
                                        Text(book.subtitle, style = MaterialTheme.typography.bodySmall)
                                    }
                                    Checkbox(selected, onCheckedChange = null)
                                }
                            }
                        }
                    }
                    SettingsPage.Translation -> Unit
                    SettingsPage.About -> {
                        item { AboutIdentity() }
                        item { AboutCredits() }
                    }
                    SettingsPage.Update -> {
                        item { UpdateCheckCard(installedVersionName(), installedVersionCode()) }
                        item {
                            TeaCaption(
                                "版本信息取仓库的发布（release）；仓库还没有发布时退回 git 标签。比较只在本机进行。"
                            )
                        }
                    }
                }
            }
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val closeSheet: () -> Unit = { scope.launch { sheetState.hide(); onBack() } }
    var headerDrag by remember { mutableFloatStateOf(0f) }
    var draggingHeader by remember { mutableStateOf(false) }
    var dragFromMain by remember { mutableStateOf(true) }
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }
    val headerOffset by animateFloatAsState(headerDrag,
        if (draggingHeader) snap() else spring(dampingRatio = 1f, stiffness = 500f), label = "settings-header-drag")
    val dragBy: (Float) -> Unit = { delta ->
        if (!draggingHeader) dragFromMain = currentPage == SettingsPage.Main
        draggingHeader = true; headerDrag = (headerDrag + delta).coerceAtLeast(0f)
    }
    val releaseDrag: () -> Unit = {
        if (headerDrag >= threshold) {
            if (currentPage == SettingsPage.Main) closeSheet() else currentPage = SettingsPage.Main
        }
        draggingHeader = false; headerDrag = 0f
    }
    ModalBottomSheet(
        modifier = Modifier.graphicsLayer { if (dragFromMain && currentPage == SettingsPage.Main) translationY = headerOffset },
        onDismissRequest = { if (currentPage == SettingsPage.Main) onBack() else currentPage = SettingsPage.Main },
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = currentPage == SettingsPage.Main)
    ) {
        BackHandler(currentPage != SettingsPage.Main) { currentPage = SettingsPage.Main }
        CompositionLocalProvider(LocalSettingsHeaderDrag provides dragBy, LocalSettingsHeaderRelease provides releaseDrag) {
        Box(Modifier.fillMaxWidth()) {
            val height = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp - 12.dp)
                .coerceAtLeast(240.dp)
            var lastPage by remember { mutableStateOf(SettingsPage.Appearance) }
            if (currentPage != SettingsPage.Main) SideEffect { lastPage = currentPage }
            Box(Modifier.fillMaxWidth().height(height)) {
                CoveredPage(covered = currentPage != SettingsPage.Main) {
                    Column(Modifier.fillMaxSize()) {
                        SettingsPanelHeader("个性化", onClose = closeSheet)
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = mainListState,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        SettingsMenuGroup {
                            SettingsMenuRow(AppSymbols.Palette, "外观模式", themeMode.label) { currentPage = SettingsPage.Appearance }
                            SettingsMenuRow(AppSymbols.Wallpaper, "主页定制", "${homeWallpaperMode.label} · ${if (homeSubtitleMode == SubtitleMode.CUSTOM) "自定义文字" else "一言"}") { currentPage = SettingsPage.Interface }
                        }
                    }
                    item { SettingsMenuHeading("偏好") }
                    item {
                        SettingsMenuGroup {
                            SettingsMenuRow(AppSymbols.Book5, "学习策略", "调整节奏") { currentPage = SettingsPage.Strategy }
                            SettingsMenuRow(AppSymbols.BookmarkFlag, "词书管理", books.joinToString("、") { it.subtitle }.ifEmpty { "未选词书" }) { currentPage = SettingsPage.Wordbooks }
                            SettingsMenuRow(AppSymbols.VoiceSelection, "发音偏好", "美音 · 英音") { currentPage = SettingsPage.Pronunciation }
                            SettingsMenuRow(AppSymbols.Search, "句子与长文翻译", translationSummary) { currentPage = SettingsPage.Translation }
                        }
                    }
                    item { SettingsMenuHeading("关于") }
                    item {
                        SettingsMenuGroup {
                            SettingsMenuRow(AppSymbols.Info, "关于App", "你想知道的") { currentPage = SettingsPage.About }
                            SettingsMenuRow(AppSymbols.Feedback, "日志获取", "便于提交 BUG") { currentPage = SettingsPage.Logs }
                            SettingsMenuRow(AppSymbols.Update, "检查更新", "版本 $version") { currentPage = SettingsPage.Update }
                        }
                    }

                        }
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = currentPage != SettingsPage.Main,
                    enter = slideInVertically(spring(dampingRatio = .9f, stiffness = 450f)) { it / 3 } + fadeIn(),
                    exit = slideOutVertically(spring(dampingRatio = 1f, stiffness = 500f)) { it / 3 } + fadeOut()) {
                    Surface(Modifier.fillMaxSize().graphicsLayer { if (!dragFromMain) translationY = headerOffset },
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 12.dp) {
                        CompositionLocalProvider(LocalSettingsInSheet provides true, LocalReservedBottom provides null) {
                            screen(if (currentPage == SettingsPage.Main) lastPage else currentPage) { currentPage = SettingsPage.Main }
                        }
                    }
                }
            }
        }
    }
    }
    if (confirmRemoveWallpaper) AlertDialog(
        onDismissRequest = { confirmRemoveWallpaper = false },
        title = { Text("移除自定义壁纸？") },
        text = { Text("只删除应用保存的副本，设备相册中的原图会保留。") },
        confirmButton = { TextButton(onClick = {
            val previous = customWallpaperUri
            onCustomWallpaperUriChange(null)
            CustomWallpaperStore.remove(settingsContext, previous)
            confirmRemoveWallpaper = false
        }) { Text("移除") } },
        dismissButton = { TextButton(onClick = { confirmRemoveWallpaper = false }) { Text("取消") } }
    )
}

@Composable
private fun SettingsHeading(text: String) {
    Text(text, Modifier.padding(top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingsChoice(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .4f) else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected, onClick = null)
        }
    }
}

@Composable
private fun SettingsMenuHeading(text: String) {
    Text(text, Modifier.padding(top = 8.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsMenuGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(24.dp)), content = content)
}

@Composable
private fun SettingsMenuRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Column {
        TeaOptionRow(title = title, summary = summary, icon = icon, onClick = onClick)
        HorizontalDivider(Modifier.padding(start = 56.dp), thickness = .5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
    }
}



/** A flat option with a themed pressed fill instead of the default grey ripple/elevation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsValueChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(
        if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = .08f) else Color.Transparent,
        animationSpec = spring(dampingRatio = 1f, stiffness = 900f), label = "settings-option-press")
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        FilterChip(selected, onClick, label = label, interactionSource = interaction,
            shape = MaterialTheme.shapes.small,
            colors = FilterChipDefaults.filterChipColors(containerColor = fill,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer),
            elevation = FilterChipDefaults.filterChipElevation(elevation = 0.dp, pressedElevation = 0.dp,
                focusedElevation = 0.dp, hoveredElevation = 0.dp, draggedElevation = 0.dp, disabledElevation = 0.dp))
    }
}
