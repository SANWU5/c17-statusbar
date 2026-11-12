package dev.puitheme

import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.IntSize
import java.util.concurrent.CancellationException
import kotlin.coroutines.*

/** Runs production code with real Compose events; the nested scroller competes in Main. */
private class DragScope(private val events: List<PointerEvent>, private val beforeEvent: (Int) -> Unit = {}) : AwaitPointerEventScope {
    override val density = 1f
    override val fontScale = 1f
    override val size = IntSize(300, 320)
    override val viewConfiguration: ViewConfiguration get() = error("post-long-press flow must not read gesture thresholds")
    override var currentEvent = event(change(0, 40f, previousY = 40f))
    val passes = mutableListOf<PointerEventPass>()
    var childScrolls = 0
    private var pendingInitial: PointerEvent? = null
    private var index = 0
    private fun childMain(event: PointerEvent) {
        event.changes.filter { it.pressed && it.position != it.previousPosition && !it.isConsumed }.forEach {
            childScrolls++
            it.consume()
        }
    }
    fun flush() { pendingInitial?.let(::childMain); pendingInitial = null }
    override suspend fun awaitPointerEvent(pass: PointerEventPass): PointerEvent {
        flush()
        beforeEvent(index)
        check(index < events.size) { "unexpected extra pointer wait" }
        currentEvent = events[index++]
        passes += pass
        if (pass == PointerEventPass.Initial) pendingInitial = currentEvent else childMain(currentEvent)
        return currentEvent
    }
    fun <T> run(block: suspend AwaitPointerEventScope.() -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(this, object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        flush()
        return checkNotNull(result) { "unexpected asynchronous wait" }.getOrThrow()
    }
}

private fun change(id: Long = 0, y: Float, previousY: Float = 40f, pressed: Boolean = true,
                   previousPressed: Boolean = true, consumed: Boolean = false) = PointerInputChange(
    id = PointerId(id), uptimeMillis = 1000, position = Offset(270f, y), pressed = pressed,
    previousUptimeMillis = 900, previousPosition = Offset(270f, previousY),
    previousPressed = previousPressed, isInitiallyConsumed = consumed,
)
private fun event(vararg changes: PointerInputChange) = PointerEvent(changes.toList())

/** Event passes share one DOWN; only a subsequent pass cycle advances physical time. */
private class HoldScope(private val frames: List<Pair<Long, PointerEvent>>) : AwaitPointerEventScope {
    override val density = 1f
    override val fontScale = 1f
    override val size = IntSize(300, 320)
    override val viewConfiguration = object : ViewConfiguration {
        override val longPressTimeoutMillis = 400L
        override val doubleTapTimeoutMillis = 300L
        override val doubleTapMinTimeMillis = 40L
        override val touchSlop = 8f
    }
    override var currentEvent = event()
    private var index = -1
    private var lastPass = PointerEventPass.Final
    private var deadline = Long.MAX_VALUE
    private var childSawMain = false
    var childDowns = 0
    var childScrolls = 0
    var timeouts = 0
    private fun childMain() {
        if (childSawMain) return
        childSawMain = true
        currentEvent.changes.filter { !it.isConsumed }.forEach {
            if (it.pressed && !it.previousPressed) { childDowns++; it.consume() }
            else if (it.pressed && it.position != it.previousPosition) { childScrolls++; it.consume() }
        }
    }
    override suspend fun awaitPointerEvent(pass: PointerEventPass): PointerEvent {
        val next = if (index < 0 || pass.ordinal <= lastPass.ordinal) index + 1 else index
        if (next != index) {
            childMain()
            if (deadline != Long.MAX_VALUE && (next >= frames.size || frames[next].first > deadline)) {
                timeouts++
                throw PointerEventTimeoutCancellationException(viewConfiguration.longPressTimeoutMillis)
            }
            check(next < frames.size) { "unexpected pointer wait after final release" }
            index = next; currentEvent = frames[index].second; childSawMain = false
        }
        if (pass.ordinal >= PointerEventPass.Main.ordinal) childMain()
        lastPass = pass
        return currentEvent
    }
    override suspend fun <T> withTimeout(timeMillis: Long, block: suspend AwaitPointerEventScope.() -> T): T {
        deadline = frames[index].first + timeMillis
        try { return block() } finally { deadline = Long.MAX_VALUE }
    }
    fun <T> run(block: suspend AwaitPointerEventScope.() -> T): T {
        var completed: Result<T>? = null
        block.startCoroutine(this, object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { completed = result }
        })
        childMain()
        return checkNotNull(completed).getOrThrow()
    }
}

fun main() {
    var checks = 0
    fun verify(value: Boolean, message: String) { checks++; check(value) { "Drag pointer $checks: $message" } }
    fun cells(first: String = "a") = listOf(first, "b", "c").mapIndexed { i, id ->
        CustomPluginDragGeometry.Cell(id, 0f, i * 88f, 300f, 80f)
    }
    val order = listOf("a", "b", "c", "d")
    fun session(): CustomPluginDragGeometry.Session = CustomPluginDragGeometry.Session().apply {
        check(start(order, "a", cells()[0], 270f, 40f, 300f, 320f))
        layout(cells(), 300f, 320f, 12f)
    }
    fun movement() = listOf(event(change(y = 132f)), event(change(y = 132f, previousY = 132f, pressed = false)))

    fun holdFrames() = listOf(0L to event(change(y = 40f, previousPressed = false)),
        1200L to event(change(y = 132f)), 1250L to event(change(y = 132f, previousY = 132f, pressed = false)))
    val earlyDown = HoldScope(holdFrames())
    val cancelledHold = earlyDown.run {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        awaitLongPressOrCancellation(down.id)
    }
    verify(cancelledHold == null && earlyDown.timeouts == 0, "old Initial DOWN cancels in its own Main before long-press timeout")
    verify(earlyDown.childDowns == 1, "ordinary child press consumed the same DOWN, not a drag movement")
    val fullState = session()
    val complete = HoldScope(holdFrames())
    var handleChecks = 0
    val completeDrop = complete.run {
        val held = awaitCustomPluginHandleHold { handleChecks++; it.position == Offset(270f, 40f) }
        if (held == null) false else {
            held.consume()
            awaitCustomPluginDrag(held.id, { fullState.dragged() != null }) {
                fullState.position(it.x, it.y); fullState.layout(cells(), 300f, 320f, 12f)
            }
        }
    }
    verify(completeDrop && handleChecks == 1, "production DOWN through long press, movement and UP completes")
    verify(complete.timeouts == 1 && complete.childDowns == 1, "child press is preserved and long-press timeout reached")
    verify(complete.childScrolls == 0, "confirmed long press keeps subsequent movement from child scroller")
    verify(fullState.finish(order, true) == listOf("b", "a", "c", "d"), "complete physical sequence changes draft order")
    val short = HoldScope(listOf(0L to event(change(y = 40f, previousPressed = false)),
        100L to event(change(y = 40f, pressed = false))))
    verify(short.run { awaitCustomPluginHandleHold { true } } == null && short.timeouts == 0, "short tap does not begin drag")
    val body = HoldScope(holdFrames())
    verify(body.run { awaitCustomPluginHandleHold { false } } == null && body.timeouts == 0, "card body never waits for a handle long press")
    val scroll = HoldScope(listOf(0L to event(change(y = 40f, previousPressed = false)),
        100L to event(change(y = 100f)), 200L to event(change(y = 100f, previousY = 100f, pressed = false))))
    verify(scroll.run { awaitCustomPluginHandleHold { true } } == null && scroll.childScrolls == 1,
        "moving before long press remains ordinary list scrolling")

    // Exact old production primitive: the child consumes the first Main movement, so no drop occurs.
    val oldScope = DragScope(movement())
    var oldMoves = 0
    verify(!oldScope.run { drag(PointerId(0)) { oldMoves++; it.consume() } }, "old Main drag loses to child scroll")
    verify(oldMoves == 0 && oldScope.childScrolls == 1, "old drag cancels before first position update")

    val good = session()
    val goodEvents = movement()
    val goodScope = DragScope(goodEvents)
    var moves = 0
    val released = goodScope.run { awaitCustomPluginDrag(PointerId(0), { good.dragged() != null }) {
        moves++; good.position(it.x, it.y); good.layout(cells(), 300f, 320f, 12f)
    } }
    verify(released, "Initial drag reaches its release")
    verify(goodScope.childScrolls == 0, "child cannot start scrolling after owned long press")
    verify(goodScope.passes.all { it == PointerEventPass.Initial }, "ownership stays in parent Initial")
    verify(goodEvents.flatMap { it.changes }.all { it.isConsumed }, "move and release consumed before child Main")
    verify(moves == 2, "final release coordinate is included")
    verify(good.finish(order, true) == listOf("b", "a", "c", "d"), "successful drop updates only draft order")
    verify(good.finish(order, true) == null, "drop can commit only once")

    fun reject(name: String, events: List<PointerEvent>, before: (CustomPluginDragGeometry.Session, Int) -> Unit = { _, _ -> }) {
        val state = session()
        val scope = DragScope(events) { before(state, it) }
        var drop: List<String>? = null
        val done = scope.run {
            try {
                val ok = awaitCustomPluginDrag(PointerId(0), { state.dragged() != null }) {
                    state.position(it.x, it.y); state.layout(cells(), 300f, 320f, 12f)
                }
                if (ok) drop = state.finish(order, true)
                ok
            } finally { state.cancel() }
        }
        verify(!done && drop == null, "$name does not commit")
        verify(state.dragged() == null, "$name clears draft drag")
    }
    reject("ancestor takes gesture", listOf(event(change(y = 132f, consumed = true))))
    reject("missing original pointer", listOf(event(change(id = 7, y = 132f))))
    reject("second pointer", listOf(event(change(y = 132f), change(id = 7, y = 80f))))
    reject("synthetic consumed cancel/up", listOf(event(change(y = 132f, pressed = false, consumed = true))))
    reject("release is not an up transition", listOf(event(change(y = 132f, pressed = false, previousPressed = false))))
    reject("pointer leaves viewport", listOf(event(change(y = 340f))))
    reject("disabled while waiting", movement()) { state, _ -> state.cancel() }
    reject("window resized", movement()) { state, _ -> state.layout(cells(), 310f, 320f, 12f) }

    val inactiveScope = DragScope(emptyList())
    verify(!inactiveScope.run { awaitCustomPluginDrag(PointerId(0), { false }) { error("inactive callback") } }, "inactive draft does not await")
    verify(inactiveScope.passes.isEmpty(), "inactive draft consumes nothing")

    // The source item can be recycled during edge scrolling; only the stable parent owns this flow.
    val recycled = session()
    val recycleScope = DragScope(listOf(event(change(y = 300f)), event(change(y = 132f, previousY = 300f)),
        event(change(y = 132f, previousY = 132f, pressed = false))))
    var step = 0
    verify(recycleScope.run { awaitCustomPluginDrag(PointerId(0), { recycled.dragged() != null }) {
        recycled.position(it.x, it.y)
        if (step++ == 0) verify(recycled.scrollSpeed(56f, 720f) > 0, "owned drag still requests edge scrolling")
        recycled.layout(listOf(CustomPluginDragGeometry.Cell("b", 0f, 0f, 300f, 80f),
            CustomPluginDragGeometry.Cell("c", 0f, 88f, 300f, 80f),
            CustomPluginDragGeometry.Cell("d", 0f, 176f, 300f, 80f)), 300f, 320f, 12f)
    } }, "recycling source does not cancel parent gesture")
    verify(recycled.finish(order, true) == listOf("b", "c", "a", "d"), "drop uses newly visible target after scrolling")
    verify(recycleScope.childScrolls == 0, "automatic scroll does not surrender pointer ownership")

    val cancelled = session()
    val cancelScope = DragScope(movement()) { throw CancellationException("detached pointer input") }
    var cancelledDrop = false
    try {
        cancelScope.run {
            try { cancelledDrop = awaitCustomPluginDrag(PointerId(0), { true }) {} }
            finally { cancelled.cancel() }
        }
        error("cancellation must propagate")
    } catch (_: CancellationException) {
        verify(!cancelledDrop && cancelled.dragged() == null, "coroutine disposal cancels without drop")
    }
    println("Custom plugin Compose pointer: $checks checks passed; old Main cancellation reproduced, Initial ownership verified.")
}
