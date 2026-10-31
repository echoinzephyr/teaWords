package com.teameow.teawords.ui.screens

import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teameow.teawords.data.AppThemeMode

internal val LocalSettingsHeaderDrag = staticCompositionLocalOf<(Float) -> Unit> { {} }
internal val LocalSettingsHeaderRelease = staticCompositionLocalOf<() -> Unit> { {} }
internal val LocalSettingsInSheet = staticCompositionLocalOf { false }

/** Embedded settings use the sheet's insets and never inherit the home dock's reservation. */
@Composable
internal fun SettingsPageFrame(title: String, subtitle: String? = null, containerColor: Color = MaterialTheme.colorScheme.background,
    onBack: () -> Unit, backEnabled: Boolean = true, content: LazyListScope.() -> Unit) {
    val inSheet = LocalSettingsInSheet.current
    if (!inSheet) {
        TeaListPage(title = title, subtitle = subtitle, onBack = onBack, backEnabled = backEnabled,
            containerColor = containerColor, content = content)
    } else {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).imePadding()) {
            SettingsPanelHeader(title, onBack = onBack, onClose = onBack, enabled = backEnabled)
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }

}

@Composable
internal fun SettingsOptionHeading(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SettingsThemeControls(mode: AppThemeMode, pureBlack: Boolean,
    light: ColorScheme, dark: ColorScheme, black: ColorScheme,
    onMode: (AppThemeMode) -> Unit, onPureBlack: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SettingsOptionHeading(Icons.Outlined.LightMode, "主题模式")
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppThemeMode.entries.forEach { option ->
                ThemePreviewChoice(if (option == AppThemeMode.SYSTEM) "自动" else option.label, mode == option, if (option == AppThemeMode.LIGHT) light else if (pureBlack) black else dark,
                    split = option == AppThemeMode.SYSTEM, lightScheme = light) { onMode(option) }
            }
        }
        Spacer(Modifier.height(4.dp))
        SettingsOptionHeading(Icons.Outlined.DesktopWindows, "纯黑模式")
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePreviewChoice("标准", !pureBlack, dark) { onPureBlack(false) }
            ThemePreviewChoice("纯黑", pureBlack, black) { onPureBlack(true) }
        }
        if (pureBlack && mode == AppThemeMode.LIGHT) TeaCaption("纯黑模式在深色主题下生效")
    }
}

@Composable
private fun ThemePreviewChoice(label: String, selected: Boolean, scheme: ColorScheme,
    split: Boolean = false, lightScheme: ColorScheme = scheme, onClick: () -> Unit) {
    Surface(color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp,
            if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.selectable(selected, role = Role.RadioButton, onClick = onClick).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniSettingsPhone(scheme, Modifier.size(42.dp, 34.dp), split, lightScheme)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SettingsLivePreview(scheme: ColorScheme) {
    Surface(shape = RoundedCornerShape(28.dp), color = if (LocalSettingsInSheet.current) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().height((teaLayoutSize().height * .32f).dp.coerceIn(180.dp, 300.dp)).testTag("settings-live-preview")
            .semantics { contentDescription = "当前主题预览" }) {
        MaterialTheme(colorScheme = scheme) {
            HomeMiniature(null, "茶词", "查词、翻译，慢慢记住。")
        }
    }
}

@Composable
internal fun SettingsHomePreview(wallpaper: String?, title: String, subtitle: String) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp),
        color = if (LocalSettingsInSheet.current) colors.surfaceContainer else colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
            .height((teaLayoutSize().height * .32f).dp.coerceIn(180.dp, 300.dp))
            .clearAndSetSemantics { contentDescription = "主页外观预览" }) {
        HomeMiniature(wallpaper, title, subtitle)
    }
}

/** Render the actual home and dock at the current window size, then scale their drawing only. */
@Composable
private fun HomeMiniature(wallpaper: String?, title: String, subtitle: String) {
    val window = teaLayoutSize()
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val height = maxHeight - 24.dp
        val ratio = window.width.toFloat() / window.height.coerceAtLeast(1)
        Surface(Modifier.height(height).aspectRatio(ratio), shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.background) {
            Box {
                Layout(content = {
                    Box(Modifier.fillMaxSize()) {
                        HomeView("", {}, {}, emptyList(), false, {}, emptyList(), title, subtitle,
                            {}, {}, {}, {}, wallpaperUrl = wallpaper, bottomClearance = 90.dp)
                        TeaNavigationDockRow(AppTab.TRANSLATE, {}, {},
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 21.dp))
                    }
                }, modifier = Modifier.fillMaxSize()) { children, constraints ->
                    val originalWidth = window.width.dp.roundToPx().coerceAtLeast(1)
                    val originalHeight = window.height.dp.roundToPx().coerceAtLeast(1)
                    val child = children.single().measure(Constraints.fixed(originalWidth, originalHeight))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        child.placeWithLayer(0, 0) {
                            scaleX = constraints.maxWidth.toFloat() / originalWidth
                            scaleY = constraints.maxHeight.toFloat() / originalHeight
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                    }
                }
                // The preview is visual only. This overlay catches taps while allowing list drags.
                Box(Modifier.matchParentSize().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null) {})
            }
        }
    }
}

@Composable
private fun MiniSettingsPhone(scheme: ColorScheme, modifier: Modifier, split: Boolean = false, lightScheme: ColorScheme = scheme) {
    Canvas(modifier) {
        fun phone(colors: ColorScheme) {
            val w = size.width; val h = size.height
            fun rectangle(x: Float, y: Float, width: Float, height: Float, color: Color, radius: Float = .045f) {
                drawRoundRect(color, Offset(w * x, h * y), Size(w * width, h * height), CornerRadius(w * radius))
            }
            rectangle(0f, 0f, 1f, 1f, colors.background, .15f)
            drawRoundRect(colors.outlineVariant, Offset.Zero, size, CornerRadius(w * .15f), style = Stroke(1.dp.toPx()))
            rectangle(.08f, .035f, .35f, .025f, colors.onSurfaceVariant)
            rectangle(.07f, .105f, .86f, .14f, colors.surfaceContainer)
            drawCircle(colors.primaryContainer, w * .055f, Offset(w * .17f, h * .175f))
            rectangle(.29f, .143f, .31f, .024f, colors.onSurfaceVariant.copy(alpha = .6f))
            rectangle(.29f, .19f, .18f, .018f, colors.onSurfaceVariant.copy(alpha = .4f))
            rectangle(.07f, .275f, .41f, .11f, colors.surfaceContainer)
            rectangle(.52f, .275f, .41f, .11f, colors.secondaryContainer)
            repeat(3) { rectangle(.07f, .42f + it * .155f, .86f, .125f, colors.surfaceContainer) }
            rectangle(.07f, .875f, .67f, .085f, colors.surfaceContainer, .085f)
            rectangle(.095f, .889f, .14f, .057f, colors.secondaryContainer)
            repeat(3) { drawCircle(colors.onSurfaceVariant, w * .019f, Offset(w * (.16f + it * .2f), h * .918f)) }
            drawCircle(colors.secondaryContainer, w * .078f, Offset(w * .85f, h * .918f))
        }
        phone(scheme)
        if (split) clipRect(right = size.width / 2) { phone(lightScheme) }
    }
}

@Composable
internal fun SettingsPanelHeader(title: String, onBack: (() -> Unit)? = null,
    onClose: () -> Unit, enabled: Boolean = true) {
    val drag = LocalSettingsHeaderDrag.current
    val release = LocalSettingsHeaderRelease.current
    val currentDrag by rememberUpdatedState(drag)
    val currentRelease by rememberUpdatedState(release)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = .97f)) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .testTag("settings-panel-header")
                .pointerInput(Unit) {
                    detectVerticalDragGestures(onDragEnd = { currentRelease() }, onDragCancel = { currentRelease() }) { change, amount ->
                        change.consume(); currentDrag(amount)
                    }
                }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (onBack != null) IconButton(onClick = onBack, enabled = enabled) { Icon(AppSymbols.ArrowBack, "返回") }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                FilledTonalIconButton(onClick = onClose, enabled = enabled, modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Icon(AppSymbols.Close, if (onBack == null) "关闭设置" else "关闭子设置")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f), thickness = .5.dp)
        }
    }
}
