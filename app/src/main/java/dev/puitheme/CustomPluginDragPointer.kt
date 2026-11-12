// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange

/** Finish dispatching DOWN before the long-press detector starts checking consumed movements. */
internal suspend fun AwaitPointerEventScope.awaitCustomPluginHandleHold(
    onDown: (PointerInputChange) -> Boolean,
): PointerInputChange? {
    // Starting in Initial would make awaitLongPressOrCancellation read the same DOWN in
    // Main and treat a child's ordinary press consumption as a cancelled long press.
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
    if (!onDown(down)) return null
    return awaitLongPressOrCancellation(down.id)
}

/** The stable parent claims movement only after its handle long-press has succeeded. */
internal suspend fun AwaitPointerEventScope.awaitCustomPluginDrag(
    pointerId: PointerId,
    isActive: () -> Boolean,
    onPosition: (Offset) -> Unit,
): Boolean {
    while (isActive()) {
        // Lazy scrolling sees Main before its parent. Claim Initial so it cannot take the
        // first movement and cancel this already-started drag, including after recycling.
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return false
        if (!isActive() || change.isConsumed || event.changes.any { it.id != pointerId && it.pressed })
            return false
        change.consume()
        onPosition(change.position)
        if (!isActive()) return false
        if (!change.pressed) return change.previousPressed
    }
    return false
}
