package com.teameow.teawords.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Shared Material 3 building blocks. Every page composes these so that spacing, radii,
 * colour roles and type scale stay identical across the app.
 */

/** Page scaffold: one TopAppBar + one lazy column, with consistent insets and spacing. */
/**
 * Bottom space the host has already reserved for this page.
 *
 * The host is the only component that knows whether the floating dock is composed right now, so it
 * also owns the single reservation for it (see MainScreen). Pages must not reserve the same space
 * again: doing that counted the system gesture inset twice and cut the content off one gesture bar
 * too early. `null` means "no host reservation" — used when a page is rendered outside the app's
 * bottom-bar layout and must fall back to its own system inset.
 */
internal val LocalReservedBottom = staticCompositionLocalOf<Dp?> { null }

@Composable
internal fun teaModalPageHeight(): Dp = (LocalConfiguration.current.screenHeightDp.dp - 12.dp).coerceAtLeast(240.dp)

@Composable
internal fun TeaTopLevelHeader(title: String, actions: @Composable RowScope.() -> Unit) {
    Row(Modifier.testTag("top-level-header-$title").fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
        actions()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TeaListPage(
    title: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backEnabled: Boolean = true,
    navigationContent: (@Composable () -> Unit)? = null,
    floatingActionButton: (@Composable () -> Unit)? = null,
    loading: Boolean = false,
    expandedTitle: Boolean = true,
    embedded: Boolean = false,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    header: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    val layout = teaLayoutSize()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val reservedBottom = LocalReservedBottom.current
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(loading) {
        if (loading) {
            // Fast local reads should not flash a loading bar on every tab switch.
            delay(250)
            showLoading = true
        } else showLoading = false
    }
    Column(
        modifier.fillMaxSize().then(
            // Fixed/custom headers have no collapse limit; attaching this connection would
            // consume upward drags indefinitely before the list can receive them.
            if (expandedTitle && header == null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            else Modifier
        )
            .then(
                // Hosted lists scroll behind the dock; only the list's end clears its buttons.
                if (reservedBottom == null && !embedded) Modifier.navigationBarsPadding() else Modifier
            )
            .background(containerColor)
    ) {
        val heading: @Composable () -> Unit = {
            Text(title, fontSize = if (expandedTitle) layout.titleSize else MaterialTheme.typography.titleLarge.fontSize,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val navigation: @Composable () -> Unit = {
            if (navigationContent != null) navigationContent()
            else if (onBack != null) IconButton(onClick = onBack, enabled = backEnabled) {
                Icon(AppSymbols.ArrowBack, contentDescription = "返回")
            }
        }
        val barColors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor, scrolledContainerColor = containerColor,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (header != null) header()
        else if (expandedTitle) LargeFlexibleTopAppBar(
            title = heading,
            expandedHeight = if (layout.compact) 112.dp else 144.dp,
            subtitle = subtitle?.let { value -> { Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            navigationIcon = navigation, actions = actions, colors = barColors, scrollBehavior = scrollBehavior,
            windowInsets = if (embedded) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets
        ) else TopAppBar(title = {
            Column { heading(); subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        }, navigationIcon = navigation, actions = actions, colors = barColors,
            windowInsets = if (embedded) WindowInsets(0, 0, 0, 0) else TopAppBarDefaults.windowInsets)
        Box(Modifier.weight(1f).fillMaxWidth()) {
        LazyColumn(
            Modifier.fillMaxSize()
                // The hosted dock already includes the keyboard height in reservedBottom.
                .then(if (reservedBottom == null) Modifier.imePadding() else Modifier),
            state = listState,
            contentPadding = PaddingValues(start = layout.horizontalPadding, end = layout.horizontalPadding, top = 8.dp, bottom = (reservedBottom ?: 0.dp) + if (floatingActionButton != null) 88.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
            content = content
        )
        // Overlay the indicator in the existing top gap so loading never moves list items.
        if (showLoading) LinearProgressIndicator(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(4.dp).testTag("page-loading")
        )
        if (floatingActionButton != null) Box(Modifier.align(Alignment.BottomEnd).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = (reservedBottom ?: 0.dp) + 16.dp)) { floatingActionButton() }
        }
    }
}

/** Neutral surface card: the default container for a block of related content. */
@Composable
fun TeaCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        // Content colour is set explicitly: relying on the inherited value left cards rendering
        // their container while the text inside was drawn in a colour indistinguishable from it.
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

/** Same container as [TeaCard] but with a title/subtitle header. */
@Composable
fun LearningCard(
    title: String,
    subtitle: String,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    TeaCard(containerColor = containerColor) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = titleStyle, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotBlank()) Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

/** Indeterminate query feedback; determinate work keeps its progress bar. */
@Composable
fun TeaProgressBar(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        TeaLoadingIndicator()
        Spacer(Modifier.width(8.dp))
        Text("正在查询…", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Section label that separates major regions inside a page. */
@Composable
fun TeaSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, top = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

/** One number with a caption. Used in aligned metric rows. */
@Composable
fun TeaMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Equal-width metric row: avoids the ragged alignment of hand-placed Columns. */
@Composable
fun TeaMetricRow(vararg metrics: Pair<String, String>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        metrics.forEach { (value, label) -> TeaMetric(value, label, Modifier.weight(1f)) }
    }
}

/** Thin progress line with full colour control, independent of the Material indicator API. */
@Composable
fun TeaProgressLine(fraction: Float, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

/** Labelled progress line used by the knowledge profile. */
@Composable
fun TeaProgressRow(
    label: String,
    count: Int,
    total: Int,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$count",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        TeaProgressLine(if (total <= 0) 0f else count.toFloat() / total, accent)
    }
}

/** Small rounded descriptor chip. */
@Composable
fun TeaChip(text: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        modifier,
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.10f),
        contentColor = accent
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

/** Caption line for caveats and evidence disclaimers. */
@Composable
fun TeaCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Progress line for the screening queue, with the remaining count on the right. */
@Composable
fun TeaScreenProgress(done: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("已排查 $done", style = MaterialTheme.typography.labelLarge)
            Text(
                "剩余 ${(total - done).coerceAtLeast(0)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // The counts change on every tap; single-digit steps must not shift the page around.
        TeaProgressLine(fraction = if (total <= 0) 1f else done.toFloat() / total, color = MaterialTheme.colorScheme.primary)
    }
}

/** Centred empty state with an optional icon-free headline. */
@Composable
fun TeaEmptyState(title: String, subtitle: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val entrance = remember(title, subtitle) { Animatable(0f) }
    LaunchedEffect(entrance) { entrance.animateTo(1f, tween(360, easing = TeaEnterEasing)) }
    fun stage(start: Float): Float = ((entrance.value - start) / (1f - start)).coerceIn(0f, 1f)
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon ?: AppSymbols.Book5, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp).graphicsLayer {
                alpha = stage(0f)
                scaleX = 0.92f + 0.08f * stage(0f)
                scaleY = scaleX
                translationY = 12.dp.toPx() * (1f - stage(0f))
            })
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer {
                alpha = stage(0.16f)
                translationY = 8.dp.toPx() * (1f - stage(0.16f))
            })
        Text(
            subtitle,
            modifier = Modifier.graphicsLayer {
                alpha = stage(0.32f)
                translationY = 8.dp.toPx() * (1f - stage(0.32f))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** Material grouped options: independently shaped rows retain their full touch targets. */
@Composable
internal fun TeaSegmentedGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

@Composable
internal fun TeaOptionRow(
    title: String, summary: String? = null, icon: ImageVector? = null,
    enabled: Boolean = true, selected: Boolean? = null,
    trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val background = if (selected == true) colors.secondaryContainer else colors.surfaceContainerHigh
    Surface(shape = RoundedCornerShape(4.dp),
        color = background, contentColor = if (selected == true) colors.onSecondaryContainer else colors.onSurface) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = summary?.let { value -> { Text(value, style = MaterialTheme.typography.bodyMedium) } },
            leadingContent = icon?.let { value -> { Icon(value, null, Modifier.size(24.dp)) } },
            trailingContent = trailing ?: { Icon(AppSymbols.ChevronRight, null) },
            colors = ListItemDefaults.colors(containerColor = background,
                headlineColor = if (selected == true) colors.onSecondaryContainer else colors.onSurface,
                supportingColor = if (selected == true) colors.onSecondaryContainer else colors.onSurfaceVariant),
            modifier = Modifier.heightIn(min = 56.dp).then(
                if (selected != null) Modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                else Modifier.clickable(enabled = enabled, onClick = onClick))
        )
    }
}

@Composable
internal fun TeaStatusMessage(message: String, error: Boolean = false) {
    Surface(shape = RoundedCornerShape(20.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer) {
        Text(message, Modifier.fillMaxWidth().padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Shared fixed header for modal pages; keeps the close target inside the content margins. */
@Composable
internal fun TeaPanelHeader(title: String, onClose: () -> Unit,
    enabled: Boolean = true, closeLabel: String = "关闭$title", onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                onBack?.let { IconButton(onClick = it, enabled = enabled) { Icon(AppSymbols.ArrowBack, "返回") } }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                FilledTonalIconButton(onClick = onClose, enabled = enabled, modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    Icon(AppSymbols.Close, closeLabel)
                }
            }
            HorizontalDivider(thickness = .5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
        }
    }
}
