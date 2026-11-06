package com.teameow.teawords.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.teameow.teawords.data.AppPreferences
import com.teameow.teawords.data.AppThemeMode
import com.teameow.teawords.ui.theme.seededColorScheme
import com.teameow.teawords.ui.theme.settingsColorScheme
import kotlin.math.roundToInt

private val ThemeSeeds = listOf(
    "茶紫" to AppPreferences.DEFAULT_THEME_SEED,
    "蔷薇" to 0xFF9C4668.toInt(), "海蓝" to 0xFF286EA0.toInt(),
    "青绿" to 0xFF286A5B.toInt(), "橄榄" to 0xFF64732E.toInt(),
    "暖杏" to 0xFF91632A.toInt(), "岩灰" to 0xFF69616E.toInt()
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsAppearanceOptions(
    mode: AppThemeMode, onMode: (AppThemeMode) -> Unit,
    pureBlack: Boolean, onPureBlack: (Boolean) -> Unit,
    dynamic: Boolean, dynamicSupported: Boolean, onDynamic: (Boolean) -> Unit,
    seed: Int, onSeed: (Int) -> Unit, fontScale: Float?, onFontScale: (Float?) -> Unit
) {
    var colorDialog by remember { mutableStateOf(false) }
    var hex by remember { mutableStateOf("") }
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsOptionHeading(Icons.Outlined.LightMode, "主题模式")
            AppearanceSegments(AppThemeMode.entries.map { it.label }, AppThemeMode.entries.indexOf(mode), "theme-mode") {
                onMode(AppThemeMode.entries[it])
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SettingsOptionHeading(AppSymbols.Palette, "主题色彩") }
                FilterChip(selected = dynamic && dynamicSupported, enabled = dynamicSupported,
                    onClick = { onDynamic(!dynamic) }, label = { Text("动态取色") })
            }
            FlowRow(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeSeeds.forEach { (label, color) ->
                    val colors = remember(color) { seededColorScheme(color, false) }
                    ThemeColorDisc(label, !dynamic && seed == color, colors) { onSeed(color); onDynamic(false) }
                }
                if (dynamicSupported) {
                    ThemeColorDisc("壁纸取色", dynamic, settingsColorScheme(context, false, true, false)) { onDynamic(true) }
                }
                val custom = !dynamic && ThemeSeeds.none { it.second == seed }
                FilledTonalIconButton(onClick = { hex = "%06X".format(seed and 0xFFFFFF); colorDialog = true },
                    modifier = Modifier.size(56.dp).then(if (custom) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)) {
                    Icon(Icons.Default.Add, "自定义主题颜色")
                }
            }
            if (!dynamicSupported) TeaCaption("动态取色需要 Android 12 及以上，预设与自定义配色均可使用。")
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsOptionHeading(Icons.Outlined.DesktopWindows, "纯黑模式")
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("标准", "纯黑").forEachIndexed { index, label ->
                    val selected = pureBlack == (index == 1)
                    Surface(shape = RoundedCornerShape(20.dp),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).selectable(selected, role = Role.RadioButton, onClick = { onPureBlack(index == 1) })) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(Modifier.size(34.dp, 26.dp), shape = RoundedCornerShape(8.dp),
                                color = if (index == 1) Color.Black else MaterialTheme.colorScheme.surfaceContainerHighest) {
                                Box(contentAlignment = Alignment.Center) { if (selected) Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = if (index == 1) Color.White else MaterialTheme.colorScheme.onSurface) }
                            }
                            Text(label)
                        }
                    }
                }
            }
            if (mode == AppThemeMode.LIGHT) TeaCaption("深色主题下生效")
        }
        FontScaleOptions(fontScale, onFontScale)
    }
    if (colorDialog) {
        val parsed = hex.removePrefix("#").takeIf { it.matches(Regex("[a-fA-F0-9]{6}")) }
            ?.toLongOrNull(16)?.toInt()?.or(0xFF000000.toInt())
        AlertDialog(onDismissRequest = { colorDialog = false }, title = { Text("自定义主题颜色") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("输入六位十六进制颜色，将自动生成浅色与深色配色。")
                OutlinedTextField(hex, { hex = it.take(7) }, label = { Text("颜色代码") }, prefix = { Text("#") },
                    singleLine = true, isError = parsed == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.testTag("theme-hex-input"))
                parsed?.let { Surface(Modifier.fillMaxWidth().height(48.dp), color = Color(it), shape = RoundedCornerShape(16.dp)) {} }
            }
        }, confirmButton = { TextButton(enabled = parsed != null, onClick = { onSeed(checkNotNull(parsed)); onDynamic(false); colorDialog = false }) { Text("应用颜色") } },
            dismissButton = { TextButton(onClick = { colorDialog = false }) { Text("取消") } })
    }
}

@Composable
private fun FontScaleOptions(fontScale: Float?, onFontScale: (Float?) -> Unit) {
    val density = LocalDensity.current
    // Keep drag updates in this small preview; applying the preference remeasures the whole app.
    var previewScale by remember(fontScale) { mutableFloatStateOf(fontScale ?: density.fontScale) }
    val previewDensity = Density(density.density, if (fontScale == null) density.fontScale else previewScale)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("文字缩放", style = MaterialTheme.typography.titleMedium)
        AppearanceSegments(listOf("跟随系统", "自定义"), if (fontScale == null) 0 else 1, "font-scale-mode") {
            onFontScale(if (it == 0) null else density.fontScale.coerceIn(.75f, 1.6f))
        }
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().testTag("font-scale-preview")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    CompositionLocalProvider(LocalDensity provides previewDensity) {
                        Text("Aa", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text("${(previewDensity.fontScale * 100).roundToInt()}%", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge)
                    }
                }
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.CenterStart) {
                    CompositionLocalProvider(LocalDensity provides previewDensity) {
                        Text("应用内的文字将以这个大小显示", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (fontScale != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("A", style = MaterialTheme.typography.labelSmall)
                        Slider(previewScale, { previewScale = it }, valueRange = .75f..1.6f,
                            onValueChangeFinished = { onFontScale((previewScale * 100).roundToInt() / 100f) },
                            modifier = Modifier.weight(1f).testTag("font-scale-slider").semantics { contentDescription = "自定义文字缩放" })
                        Text("A", style = MaterialTheme.typography.titleLarge)
                    }
                    TeaCaption("松手后应用到全部页面")
                } else TeaCaption("使用系统字号，在系统显示设置中调整")
            }
        }
    }
}

@Composable
private fun AppearanceSegments(labels: List<String>, selected: Int, tag: String, onSelected: (Int) -> Unit) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(4.dp).selectableGroup().testTag(tag)) {
            labels.forEachIndexed { index, label ->
                Surface(shape = CircleShape,
                    color = if (index == selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    modifier = Modifier.weight(1f).clip(CircleShape).selectable(index == selected, role = Role.RadioButton, onClick = { onSelected(index) })) {
                    Box(Modifier.heightIn(min = 44.dp).padding(horizontal = 8.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeColorDisc(label: String, selected: Boolean, colors: ColorScheme, onClick: () -> Unit) {
    Box(Modifier.size(56.dp).clip(CircleShape).selectable(selected, role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = label }.then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
        .padding(5.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val half = size.width / 2f
            drawRect(colors.primaryContainer, size = Size(half, size.height))
            drawRect(colors.secondaryContainer, topLeft = Offset(half, 0f), size = Size(half, size.height / 2))
            drawRect(colors.tertiaryContainer, topLeft = Offset(half, size.height / 2), size = Size(half, size.height / 2))
        }
        if (selected) Surface(shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary) {
            Icon(Icons.Default.Check, null, Modifier.padding(5.dp).size(20.dp))
        }
    }
}
