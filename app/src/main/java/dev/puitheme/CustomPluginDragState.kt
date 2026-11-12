// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/** UI-only drag draft. The repository receives at most one complete order, on a successful drop. */
internal class CustomPluginDragState internal constructor(internal val gridState: LazyStaggeredGridState) {
    private val session = CustomPluginDragGeometry.Session()
    private data class Handle(val owner: Any, val bounds: Rect)
    private val handles = mutableMapOf<String, Handle>()
    private var ids by mutableStateOf(emptyList<String>())
    private var allowed = false
    private var commit: (List<String>) -> Unit = {}
    private var viewport = IntSize.Zero
    private var gridOrigin = Offset.Zero
    private var targetGapPx = 0f
    private var diagnosticsLeft = 48
    private var gestureDiagnostic: StringBuilder? = null
    internal var edgePx = 0f
    internal var maxScrollPx = 0f

    var draggingId by mutableStateOf<String?>(null); private set
    var hoverTargetId by mutableStateOf<String?>(null); private set
    var dragTopLeft by mutableStateOf(Offset.Zero); private set
    var dragSize by mutableStateOf(IntSize.Zero); private set
    val isDragging get() = draggingId != null
    val order get() = ids

    internal fun update(order: List<String>, enabled: Boolean, onCommit: (List<String>) -> Unit) {
        if (!enabled || !session.matches(order)) {
            if (isDragging) trace("cancel update enabled=$enabled sameOrder=${session.matches(order)}")
            cancel()
        }
        ids = order.toList(); allowed = enabled; commit = onCommit
        handles.keys.retainAll(ids.toSet())
    }
    internal fun metrics(edge: Float, speed: Float, gap: Float) {
        edgePx = edge; maxScrollPx = speed; targetGapPx = gap
    }
    internal fun container(origin: Offset, size: IntSize) {
        if (isDragging && (origin != gridOrigin || size != viewport)) {
            trace("cancel container old=$gridOrigin/$viewport new=$origin/$size")
            cancel()
        }
        gridOrigin = origin; viewport = size
    }
    internal fun handle(id: String, owner: Any, bounds: Rect) { handles[id] = Handle(owner, bounds) }
    internal fun removeHandle(id: String, owner: Any) { if (handles[id]?.owner === owner) handles.remove(id) }
    internal fun sourceAt(point: Offset): String? {
        if (diagnosticsLeft > 0 && android.util.Log.isLoggable("C17SortDrag", android.util.Log.DEBUG))
            gestureDiagnostic = StringBuilder()
        val selected = if (allowed && !isDragging) ids.firstOrNull { id ->
            handles[id]?.bounds?.contains(point + gridOrigin) == true
        } else null
        trace("down point=$point root=${point + gridOrigin} origin=$gridOrigin size=$viewport enabled=$allowed ids=${ids.size} handles=${handles.size} hit=${selected != null}")
        return selected
    }

    internal fun trace(message: String) {
        gestureDiagnostic?.let {
            if (it.length < 1500) { if (it.isNotEmpty()) it.append("; "); it.append(message) }
        }
    }
    internal fun traceEnd() {
        // One opt-in line per gesture, bounded per dialog; no labels, specs or stored values.
        gestureDiagnostic?.let { diagnosticsLeft--; android.util.Log.d("C17SortDrag", it.toString()) }
        gestureDiagnostic = null
    }

    private fun visibleCells() = gridState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
        val id = item.key as? String ?: return@mapNotNull null
        if (id !in ids || item.size.width <= 0 || item.size.height <= 0) return@mapNotNull null
        CustomPluginDragGeometry.Cell(id, item.offset.x.toFloat(), item.offset.y.toFloat(),
            item.size.width.toFloat(), item.size.height.toFloat())
    }
    internal fun start(id: String, point: Offset): Boolean {
        if (!allowed || id !in ids) { trace("start rejected enabled=$allowed known=${id in ids}"); return false }
        val source = visibleCells().firstOrNull { it.id == id }
        if (source == null) { trace("start rejected no visible cell"); return false }
        val result = session.start(ids, id, source, point.x, point.y, viewport.width.toFloat(), viewport.height.toFloat())
        trace("start result=$result point=$point cell=${source.left},${source.top},${source.width},${source.height}")
        refreshLayout(); return result
    }
    internal fun position(point: Offset) {
        val active = isDragging
        session.position(point.x, point.y); refreshLayout()
        if (active && !isDragging) trace("cancel position outside viewport=$point")
    }
    internal fun refreshLayout() {
        session.layout(visibleCells(), viewport.width.toFloat(), viewport.height.toFloat(), targetGapPx)
        sync()
    }
    internal fun scrollSpeed(): Float = session.scrollSpeed(edgePx, maxScrollPx)
    internal fun finish() {
        refreshLayout()
        val result = session.finish(ids, allowed)
        sync()
        if (result != null) commit(result)
    }
    fun cancel() { session.cancel(); sync() }
    private fun sync() {
        draggingId = session.dragged(); hoverTargetId = session.target()
        dragTopLeft = Offset(session.left(), session.top())
        dragSize = IntSize(session.width().roundToInt(), session.height().roundToInt())
    }
}

@Composable
internal fun rememberCustomPluginDragState(
    gridState: LazyStaggeredGridState,
    ids: List<String>,
    enabled: Boolean,
    onCommit: (List<String>) -> Unit,
): CustomPluginDragState {
    val state = remember(gridState) { CustomPluginDragState(gridState) }
    val latestCommit by rememberUpdatedState(onCommit)
    val density = LocalDensity.current
    SideEffect {
        state.update(ids, enabled) { latestCommit(it) }
        with(density) { state.metrics(56.dp.toPx(), 720.dp.toPx(), 12.dp.toPx()) }
    }
    DisposableEffect(state) { onDispose { state.cancel() } }
    LaunchedEffect(state) {
        snapshotFlow { state.isDragging }.collectLatest { active ->
            if (active) {
                var previous = withFrameNanos { it }
                while (state.isDragging) {
                    val now = withFrameNanos { it }
                    state.refreshLayout()
                    val delta = state.scrollSpeed() * ((now - previous).coerceAtMost(50_000_000L) / 1_000_000_000f)
                    previous = now
                    if (delta != 0f) { gridState.scrollBy(delta); state.refreshLayout() }
                }
            }
        }
    }
    return state
}

/** Stable parent owns the gesture so recycling an offscreen source card does not cancel it. */
internal fun Modifier.customPluginDragContainer(state: CustomPluginDragState): Modifier =
    onGloballyPositioned { state.container(it.positionInRoot(), it.size) }.pointerInput(state) {
        try {
            awaitEachGesture {
                try {
                // Inspect only the handle. A short touch or a body long-press remains normal scrolling.
                var id: String? = null
                var downTime = 0L
                val held = awaitCustomPluginHandleHold { down ->
                    downTime = android.os.SystemClock.uptimeMillis()
                    id = state.sourceAt(down.position)
                    id != null
                }
                state.trace("hold accepted=${held != null} elapsed=${android.os.SystemClock.uptimeMillis() - downTime} hit=${id != null}")
                if (held == null) return@awaitEachGesture
                val selected = id ?: return@awaitEachGesture
                if (!state.start(selected, held.position)) return@awaitEachGesture
                held.consume()
                try {
                    val released = awaitCustomPluginDrag(held.id, { state.isDragging }, state::position)
                    state.trace("drag released=$released active=${state.isDragging}")
                    if (released) {
                        state.finish()
                    } else state.cancel()
                } finally {
                    state.cancel()
                }
                } finally {
                    state.traceEnd()
                }
            }
        } finally {
            state.cancel()
        }
    }

/** Original card remains as a dim placeholder while the overlay carries the moving copy. */
internal fun Modifier.customPluginDragItem(state: CustomPluginDragState, id: String): Modifier =
    graphicsLayer { alpha = if (state.draggingId == id) .25f else 1f }

/** Apply to the complete 40dp handle touch target, not just the three-bar glyph. */
internal fun Modifier.customPluginDragHandle(state: CustomPluginDragState, id: String): Modifier = composed {
    val owner = remember(state, id) { Any() }
    DisposableEffect(state, id) { onDispose { state.removeHandle(id, owner) } }
    onGloballyPositioned {
        val origin = it.positionInRoot()
        val bounds = Rect(origin, androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()))
        state.handle(id, owner, bounds)
    }
}

/** Place alongside the full-size grid in the same Box; content must not register a second handle. */
@Composable
internal fun BoxScope.CustomPluginDragOverlay(state: CustomPluginDragState, content: @Composable (String) -> Unit) {
    val id = state.draggingId ?: return
    val width = with(LocalDensity.current) { state.dragSize.width.toDp() }
    Box(Modifier.offset { IntOffset(state.dragTopLeft.x.roundToInt(), state.dragTopLeft.y.roundToInt()) }
        .width(width).zIndex(10f).graphicsLayer { shadowElevation = 12.dp.toPx() }) { content(id) }
}
