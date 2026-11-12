// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Random

/** Vertical room around the looping lanes, and between two lanes. Shared with the lane budget below. */
private const val WALL_PADDING_DP = 12
private const val LANE_GAP_DP = 10

/** Every colour of the wall in one place, so the special card, the lanes, the buttons and the detail share one gold. */
private class WallPalette(val dark: Boolean) {
    val gold = if (dark) Color(0xFFE0C58F) else Color(0xFF8C6B35)
    val goldBright = if (dark) Color(0xFFF4DDA6) else Color(0xFFC9973F)
    val goldDeep = if (dark) Color(0xFFB08944) else Color(0xFF6B4F22)
    val alt = if (dark) Color(0xFFC39A61) else Color(0xFFA37B40)
    val ink = if (dark) Color(0xFFF5EBDD) else Color(0xFF43382A)
    val muted = if (dark) Color(0xFFC9BDA7) else Color(0xFF79684F)
    val surface = if (dark) Color(0xFF242320) else Color(0xFFFFFBF3)
    val container = if (dark) Color(0xFF302D26) else Color(0xFFF6EDD9)
    val wall = if (dark) Color(0xFF2B2720) else Color(0xFFFAF1DC)
    val soft = if (dark) Color(0xFF493922) else Color(0xFFF0DFBF)
    val goldFill = if (dark) Color(0xFFE0C58F) else Color(0xFFD7AE5A)
    val onGold = if (dark) Color(0xFF2B1E08) else Color(0xFF3B2A0C)
    val cardStart = if (dark) Color(0xFF3B3122) else Color(0xFFFFF8E6)
    val cardMid = if (dark) Color(0xFF4B3C23) else Color(0xFFFBE6AE)
    val cardEnd = if (dark) Color(0xFF30281C) else Color(0xFFFFF1CF)
    val shine = if (dark) Color(0x33FFF1CC) else Color(0x80FFFFFF)
}

/** The palette follows the actual surface, so it is the same inside the dialog host and in the detail window. */
@Composable
private fun wallPalette(): WallPalette {
    val dark = MiuixTheme.colorScheme.surface.luminance() < .3f
    return remember(dark) { WallPalette(dark) }
}

/** Centres each line's glyphs in its line box; the avatar and the name then share one centre line in every chip. */
private val ChipText = TextStyle(lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None))

/**
 * A gold thanks wall: the special card is the strongest element, and the other names loop in lanes without needing to scroll
 * through a list. The dialog host composes its content outside the caller's theme, so the gold scheme is applied again inside it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ThanksWallDialog(app: ModernMainActivity) {
    val wall = app.thanksWall ?: remember { ThanksWall.load(app) }
    val window = LocalWindowInfo.current.containerDpSize
    val featured = remember(wall) { wall.featured() }
    val others = remember(wall) { wall.groups.flatMap { group -> wall.members(group.id).map { group to it } } }
    val native = MiuixTheme.colorScheme
    val dark = native.surface.luminance() < .3f
    val palette = remember(dark) { WallPalette(dark) }
    val random = remember { Random() }
    var previousMessage by remember { mutableIntStateOf(-1) }
    var detail by remember { mutableStateOf<SupporterDetail?>(null) }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val showPerson: (ThanksWall.Person) -> Unit = { person ->
        previousMessage = ThanksMessages.choose(random, previousMessage)
        detail = SupporterDetail(wall, person, ThanksMessages.message(previousMessage))
    }
    var running by remember(app) { mutableStateOf(app.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(app) {
        val observer = LifecycleEventObserver { _, _ -> running = app.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        app.lifecycle.addObserver(observer)
        onDispose { app.lifecycle.removeObserver(observer) }
    }
    val goldScheme = native.copy(primary = palette.gold, onPrimary = palette.onGold,
        primaryVariant = palette.gold, onPrimaryVariant = palette.ink, primaryContainer = palette.soft,
        secondaryVariant = palette.soft, onSecondaryVariant = palette.ink,
        disabledSecondaryVariant = if (dark) Color(0xFF332B22) else Color(0xFFF3E7D4),
        disabledOnSecondaryVariant = if (dark) Color(0xFFCABA9D) else Color(0xFF766344),
        onPrimaryContainer = palette.ink, surface = palette.surface,
        surfaceContainer = palette.container, onSurface = palette.ink, onSurfaceVariantSummary = palette.muted)
    MiuixTheme(colors = goldScheme) {
      C17OverlayDialog(show = true, title = "鸣谢墙", summary = "感谢每一位伙伴",
        titleColor = palette.ink, summaryColor = palette.muted, backgroundColor = palette.surface,
        modifier = Modifier.heightIn(max = (window.height * .88f).coerceAtMost(760.dp)),
        insideMargin = DpSize(18.dp, 18.dp), outsideMargin = DpSize(16.dp, 12.dp),
        onDismissRequest = { app.thanksOpen = false }) {
        // The overlay host owns this composition: without the scheme here every colour below would fall back to the app's own blue.
        MiuixTheme(colors = goldScheme) {
            WallBody(app, wall, featured, others, running && app.thanksOpen && detail == null && !pressed, interactions, showPerson)
        }
      }
      detail?.let { shown -> SupporterDetailDialog(app, shown) { detail = null } }
    }
}

@Composable
private fun WallBody(app: ModernMainActivity, wall: ThanksWall.Wall, featured: List<ThanksWall.Person>,
                     others: List<Pair<ThanksWall.Group, ThanksWall.Person>>, active: Boolean,
                     interactions: MutableInteractionSource, onPerson: (ThanksWall.Person) -> Unit) {
    val palette = wallPalette()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BoxWithConstraints(Modifier.weight(1f, fill = false)) {
            val density = LocalDensity.current
            var headingHeight by remember { mutableIntStateOf(0) }
            var metadataHeight by remember { mutableIntStateOf(0) }
            // One height for every chip, scaled with the user's font size; the lane budget measures the same value.
            val chipHeight = ThanksChipGeometry.height(with(density) { 18.sp.toDp().value }).dp
            val availableHeight = with(density) { maxHeight.roundToPx() }
            // The measured blocks do not depend on the number of lanes, so this settles without resizing back and forth.
            val lanes = if (headingHeight == 0 || metadataHeight == 0) minOf(2, others.size).coerceAtLeast(1)
                else with(density) { ThanksLaneBudget.lanes(others.size,
                    availableHeight - headingHeight - metadataHeight - 2 * 12.dp.roundToPx(),
                    chipHeight.roundToPx(), LANE_GAP_DP.dp.roundToPx(), 2 * WALL_PADDING_DP.dp.roundToPx()) }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.onSizeChanged { headingHeight = it.height }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    featured.forEach { person -> FeaturedCard(app, wall, person, active) { onPerson(person) } }
                    if (others.isNotEmpty()) WallHeader(wall, others.size)
                }
                if (others.isNotEmpty()) DanmakuWall(app, wall, others, lanes, chipHeight, active, interactions, onPerson)
                Text((if (wall.revision > 0) trf("第 %1\$s 版 · %2\$s", wall.revision, wall.updatedAt) else "内置名单") + " · " + app.thanksStatus,
                    fontSize = 12.sp, lineHeight = 17.sp, color = palette.muted,
                    modifier = Modifier.onSizeChanged { metadataHeight = it.height }.padding(start = 4.dp, top = 2.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            C17TextButton(text = if (app.thanksRefreshing) "正在刷新" else "刷新", enabled = !app.thanksRefreshing,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { app.refreshThanks() },
                colors = ButtonDefaults.textButtonColors(color = palette.soft, disabledColor = palette.soft.copy(alpha = .55f),
                    textColor = palette.ink, disabledTextColor = palette.muted))
            C17TextButton(text = "关闭", modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = { app.thanksOpen = false },
                colors = ButtonDefaults.textButtonColorsPrimary(color = palette.goldFill, disabledColor = palette.goldFill.copy(alpha = .55f),
                    textColor = palette.onGold, disabledTextColor = palette.muted))
        }
    }
}

/** Keep the selected list snapshot while a manual refresh replaces the wall behind it. */
private data class SupporterDetail(val wall: ThanksWall.Wall, val person: ThanksWall.Person, val message: String)

@Composable
private fun SupporterDetailDialog(app: ModernMainActivity, detail: SupporterDetail, onDismiss: () -> Unit) {
    val window = LocalWindowInfo.current.containerDpSize
    val palette = wallPalette()
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 400.dp).fillMaxWidth().heightIn(max = (window.height * .82f).coerceAtMost(640.dp))
            .clip(shape).background(palette.surface)
            .border(.75.dp, palette.gold.copy(alpha = .28f), shape).padding(24.dp)
            .semantics { paneTitle = "鸣谢详情" },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(if (detail.person.featured) "特别鸣谢" else "谢谢你的支持", fontSize = 12.sp,
                    fontWeight = FontWeight.Medium, color = palette.gold)
                PortraitFrame(app, detail.wall, detail.person, 100.dp)
                Text(detail.person.name, Modifier.fillMaxWidth().semantics { heading() }, fontSize = 23.sp,
                    lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, color = palette.ink, textAlign = TextAlign.Center)
                Box(Modifier.width(36.dp).height(.75.dp).background(palette.gold.copy(alpha = .45f)))
                Text(detail.message, Modifier.fillMaxWidth(), fontSize = 15.sp, lineHeight = 25.sp,
                    color = palette.muted, textAlign = TextAlign.Center)
                if (detail.person.featured && detail.person.note.isNotBlank()) {
                    Text(detail.person.note, Modifier.fillMaxWidth().padding(top = 2.dp), fontSize = 13.sp,
                        lineHeight = 21.sp, color = if (palette.dark) Color(0xFFE4D4B7) else Color(0xFF856C43), textAlign = TextAlign.Center)
                }
            }
            C17TextButton(text = "返回鸣谢墙", modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(color = palette.soft, textColor = palette.ink))
        }
    }
}

/** The section title for everyone below the special card: a heading, how many, and the support groups with their gold shades. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WallHeader(wall: ThanksWall.Wall, count: Int) {
    val palette = wallPalette()
    Column(Modifier.padding(start = 4.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("感谢每一位伙伴"), Modifier.semantics { heading() }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.ink)
            Text(trf("%s 位", count), Modifier.clip(CircleShape).background(palette.gold.copy(alpha = .14f))
                .padding(horizontal = 9.dp, vertical = 2.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.gold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            wall.groups.forEachIndexed { index, group ->
                val members = wall.members(group.id).size
                if (members > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(groupTint(index, 1f)))
                    Text("${group.title} $members", fontSize = 12.sp, color = palette.muted)
                }
            }
        }
    }
}

/** Two shades of gold keep the existing support groups distinguishable. */
@Composable
private fun groupTint(index: Int, alpha: Float): Color {
    val palette = wallPalette()
    return (if (index == 0) palette.gold else palette.alt).copy(alpha = alpha)
}

/** A four-pointed sparkle: its arms curve through the centre, so it stays crisp from a few dp to a dozen. */
private fun sparklePath(cx: Float, cy: Float, radius: Float): Path = Path().apply {
    moveTo(cx, cy - radius)
    quadraticBezierTo(cx, cy, cx + radius, cy)
    quadraticBezierTo(cx, cy, cx, cy + radius)
    quadraticBezierTo(cx, cy, cx - radius, cy)
    quadraticBezierTo(cx, cy, cx, cy - radius)
    close()
}

/**
 * The strongest element of the wall. A warm gold surface with two soft glows and sparkles, a gold edge, a larger ringed portrait and a
 * pill label; while the wall is visible a light sweep crosses it every few seconds, and stops when the app is not in front.
 */
@Composable
private fun FeaturedCard(app: ModernMainActivity, wall: ThanksWall.Wall, person: ThanksWall.Person, running: Boolean, onClick: () -> Unit) {
    val palette = wallPalette()
    val shape = RoundedCornerShape(28.dp)
    val fontScale = LocalDensity.current.fontScale
    val shine = remember { Animatable(0f) }
    LaunchedEffect(running) {
        if (!running) { shine.snapTo(0f); return@LaunchedEffect }
        while (isActive) {
            shine.snapTo(0f)
            shine.animateTo(1f, tween(2400, easing = LinearEasing))
            delay(3200)
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().clip(shape)
        .drawBehind {
            drawRect(Brush.linearGradient(listOf(palette.cardStart, palette.cardMid, palette.cardEnd),
                start = Offset.Zero, end = Offset(size.width, size.height)))
            drawRect(Brush.radialGradient(listOf(palette.goldBright.copy(alpha = .38f), Color.Transparent),
                center = Offset(size.width * .14f, size.height * .1f), radius = size.width * .55f))
            drawRect(Brush.radialGradient(listOf(palette.goldBright.copy(alpha = .22f), Color.Transparent),
                center = Offset(size.width * .95f, size.height * .98f), radius = size.width * .45f))
            // Kept in the label row's height, above the name, so they never sit behind the text.
            drawPath(sparklePath(size.width - 26.dp.toPx(), 28.dp.toPx(), 9.dp.toPx()), palette.goldBright.copy(alpha = .8f))
            drawPath(sparklePath(size.width - 52.dp.toPx(), 40.dp.toPx(), 5.dp.toPx()), palette.goldBright.copy(alpha = .55f))
            drawPath(sparklePath(size.width - 18.dp.toPx(), 48.dp.toPx(), 3.5f.dp.toPx()), palette.goldBright.copy(alpha = .45f))
        }
        .drawWithContent {
            drawContent()
            val progress = shine.value
            if (progress > 0f && progress < 1f) {
                val band = size.width * .38f
                val start = -band + (size.width + 2f * band) * progress
                drawRect(Brush.linearGradient(listOf(Color.Transparent, palette.shine, Color.Transparent),
                    start = Offset(start, 0f), end = Offset(start + band, band * .55f)))
            }
        }
        .border(1.dp, Brush.linearGradient(listOf(palette.goldBright.copy(alpha = .85f), palette.gold.copy(alpha = .35f),
            palette.goldBright.copy(alpha = .75f))), shape)
        .clickable(onClickLabel = "查看特别鸣谢详情", role = Role.Button, onClick = onClick)
        .padding(horizontal = 18.dp, vertical = 16.dp)) {
        val availableWidth = maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(palette.goldBright, palette.goldFill)))
                    .padding(start = 9.dp, end = 13.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(12.dp).drawBehind { drawPath(sparklePath(size.width / 2f, size.height / 2f, size.width / 2f), palette.onGold) })
                    Text(tr("特别鸣谢"), Modifier.semantics { heading() }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.onGold)
                }
            }
            val dedication: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(person.name, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = palette.ink)
                    Box(Modifier.width(44.dp).height(2.dp).clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(palette.goldBright, palette.goldBright.copy(alpha = 0f)))))
                    if (person.note.isNotBlank()) Text(person.note, fontSize = 13.sp, lineHeight = 21.sp, color = palette.muted)
                }
            }
            // Neither an enlarged font nor a narrow window truncates the featured name or dedication.
            if (availableWidth < 240.dp || fontScale > 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { PortraitFrame(app, wall, person, 76.dp); dedication() }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PortraitFrame(app, wall, person, 76.dp)
                    Box(Modifier.weight(1f)) { dedication() }
                }
            }
        }
    }
}

/** A portrait in a gold sweep ring with a soft glow behind it; the same frame is used by the special card and the detail window. */
@Composable
private fun PortraitFrame(app: ModernMainActivity, wall: ThanksWall.Wall, person: ThanksWall.Person, size: Dp) {
    val palette = wallPalette()
    Box(Modifier.size(size + 22.dp).drawBehind {
        drawCircle(Brush.radialGradient(listOf(palette.goldBright.copy(alpha = .4f), Color.Transparent),
            center = center, radius = this.size.minDimension / 2f), radius = this.size.minDimension / 2f)
    }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(size + 14.dp).clip(CircleShape).background(palette.surface.copy(alpha = .85f))
            .border(2.dp, Brush.sweepGradient(listOf(palette.goldBright, palette.gold, palette.goldDeep, palette.goldBright,
                palette.gold, palette.goldBright)), CircleShape).padding(5.dp), contentAlignment = Alignment.Center) {
            SupporterAvatar(app, wall, person, size, palette.gold.copy(alpha = .10f), palette.gold)
        }
    }
}

/** The original lane order and speeds. Pressing a name holds the lanes so its click target does not move under the finger. */
@Composable
private fun DanmakuWall(app: ModernMainActivity, wall: ThanksWall.Wall, people: List<Pair<ThanksWall.Group, ThanksWall.Person>>, lanes: Int,
                        chipHeight: Dp, running: Boolean, interactions: MutableInteractionSource, onClick: (ThanksWall.Person) -> Unit) {
    val palette = wallPalette()
    val rows = remember(people, lanes) { List(lanes) { lane -> people.filterIndexed { index, _ -> index % lanes == lane } } }
    val groupIndex = remember(wall) { wall.groups.mapIndexed { index, group -> group.id to index }.toMap() }
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(palette.wall)
        .background(Brush.verticalGradient(listOf(palette.gold.copy(alpha = .08f), palette.gold.copy(alpha = .02f))))
        .border(.75.dp, palette.gold.copy(alpha = .22f), shape)
        .drawWithContent {
            drawContent()
            // Names fade out at both ends instead of being cut in half by the rounded edge.
            val edge = 26.dp.toPx()
            drawRect(Brush.horizontalGradient(listOf(palette.wall, Color.Transparent), startX = 0f, endX = edge),
                size = Size(edge, size.height))
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, palette.wall), startX = size.width - edge, endX = size.width),
                topLeft = Offset(size.width - edge, 0f), size = Size(edge, size.height))
        }.padding(vertical = WALL_PADDING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(LANE_GAP_DP.dp)) {
        rows.forEachIndexed { lane, row ->
            if (row.isNotEmpty()) DanmakuLane(app, wall, row, groupIndex, chipHeight, running, interactions, onClick,
                speed = 26f + 7f * ((lane * 3) % 5), phase = (lane * .37f) % 1f)
        }
    }
}

@Composable
private fun DanmakuLane(app: ModernMainActivity, wall: ThanksWall.Wall, people: List<Pair<ThanksWall.Group, ThanksWall.Person>>,
                        groupIndex: Map<String, Int>, height: Dp, running: Boolean, interactions: MutableInteractionSource,
                        onClick: (ThanksWall.Person) -> Unit, speed: Float, phase: Float) {
    var loopWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val duration = if (loopWidth > 0) ((loopWidth / density.density) / speed * 1000f).toInt().coerceIn(6000, 120000) else 20000
    val progress = remember { Animatable(0f) }
    // Cancelling the effect pauses at its current position; returning to the app continues from there. Closing disposes it entirely.
    LaunchedEffect(running, duration, loopWidth) {
        if (!running || loopWidth <= 0) return@LaunchedEffect
        while (isActive) {
            progress.animateTo(1f, tween((duration * (1f - progress.value)).toInt().coerceAtLeast(1), easing = LinearEasing))
            progress.snapTo(0f)
        }
    }
    Box(Modifier.fillMaxWidth().height(height).clipToBounds()) {
        Row(Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
            .graphicsLayer { translationX = -((progress.value + phase) % 1f) * loopWidth }) {
            repeat(2) { copy ->
                Row(Modifier.onSizeChanged { if (copy == 0) loopWidth = it.width }.padding(start = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    people.forEach { (group, person) -> DanmakuChip(app, wall, person, groupIndex[group.id] ?: 0, height, interactions) { onClick(person) } }
                }
            }
        }
    }
}

/**
 * One name. Every chip has the same height and a full-capsule corner; the avatar sits in the start cap with the same inset on three
 * sides, the name is centred on the avatar's line, and a long name ends with an ellipsis rather than stretching the lane.
 */
@Composable
private fun DanmakuChip(app: ModernMainActivity, wall: ThanksWall.Wall, person: ThanksWall.Person, group: Int, height: Dp,
                        interactions: MutableInteractionSource, onClick: () -> Unit) {
    val palette = wallPalette()
    Row(Modifier.height(height).widthIn(min = ThanksChipGeometry.MIN_WIDTH.dp).clip(CircleShape)
        .background(groupTint(group, .10f)).border(.75.dp, groupTint(group, .30f), CircleShape)
        .clickable(interactionSource = interactions, indication = null, onClickLabel = "查看鸣谢详情", role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = person.name }
        .padding(start = ThanksChipGeometry.AVATAR_INSET.dp, end = ThanksChipGeometry.END_PADDING.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ThanksChipGeometry.TEXT_GAP.dp)) {
        SupporterAvatar(app, wall, person, ThanksChipGeometry.avatarSize(height.value).dp, ring = groupTint(group, .35f))
        Text(person.name, Modifier.widthIn(max = ThanksChipGeometry.MAX_TEXT_WIDTH.dp), fontSize = 13.sp, lineHeight = 18.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = ChipText, color = palette.ink)
    }
}

@Composable
internal fun SupporterAvatar(app: ModernMainActivity, wall: ThanksWall.Wall, person: ThanksWall.Person, size: Dp,
                             background: Color? = null, foreground: Color? = null, ring: Color? = null) {
    var bitmap by remember(wall.revision, person.id) { mutableStateOf(ThanksWall.cachedAvatar(wall, person)) }
    LaunchedEffect(wall.revision, person.id) {
        if (bitmap == null && person.avatar != null) ThanksWall.avatar(app, wall, person) { bitmap = it }
    }
    Box(Modifier.size(size).clip(CircleShape).background(background ?: MiuixTheme.colorScheme.primary.copy(alpha = .12f))
        .then(if (ring == null) Modifier else Modifier.border(.5.dp, ring, CircleShape))
        .clearAndSetSemantics { },
        contentAlignment = Alignment.Center) {
        val shown = bitmap
        if (shown != null) Image(shown.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            val name = person.name.trimStart('@')
            val initial = if (name.isEmpty()) "?" else String(Character.toChars(name.codePointAt(0)))
            Text(initial, fontSize = (size.value * .38f).sp, color = foreground ?: MiuixTheme.colorScheme.primary)
        }
    }
}
