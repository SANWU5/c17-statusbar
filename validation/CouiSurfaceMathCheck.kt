package dev.puitheme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/** Real production color math; covers changing content behind the translucent navigation surface. */
fun main() {
    var checks = 0
    fun requireCheck(value: Boolean, message: String) {
        checks++
        check(value) { "COUI surface $checks: $message" }
    }
    val accents = listOf(Color(0xFF0066FF), Color(0xFF247CFF), Color(0xFF34C759),
        Color(0xFFFF9500), Color(0xFFFF2D55), Color(0xFFFFCC00), Color(0xFFAF52DE),
        Color(0xFF8BD3DD), Color(0xFF0A1C4E), Color(0xFF888888), Color.White, Color.Black)
    val content = listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue,
        Color(0xFF5D2038), Color(0xFF12CDAD), Color(0xFFEAC092))
    for (dark in listOf(false, true)) for (accent in accents) {
        // C17FloatingBarColors: ColorOS 17 Gallery capsule frost; the overlay range also covers press states.
        val tint = if (dark) Color(0xFF262628).copy(alpha = .78f) else Color.White.copy(alpha = .82f)
        val strongest = if (dark) .12f else .06f
        val pillInk = if (dark) Color.White else Color.Black
        val surfaces = c17GlassSurfaces(tint, pillInk, strongest)
        val originalInk = if (dark) Color.White.copy(alpha = .4f) else Color.Black.copy(alpha = .4f)
        val ink = c17ReadableInk(accent, surfaces)
        val idle = c17ReadableInk(originalInk, surfaces)
        for (underlay in content) for (overlay in listOf(0f, strongest * .5f, strongest)) {
            val surface = pillInk.copy(alpha = overlay).compositeOver(tint.compositeOver(underlay))
            requireCheck(c17ColorContrast(ink, surface) >= 4.499f, "active on $underlay, dark=$dark, accent=$accent")
            requireCheck(c17ColorContrast(idle, surface) >= 4.499f, "idle on $underlay, dark=$dark, accent=$accent")
        }
        val target = if (dark) Color.White else Color.Black
        requireCheck(c17ReadableInk(target, surfaces) == target, "already readable ink is preserved")
        requireCheck(c17ReadableInk(ink, surfaces) == ink, "a readable result is stable")
        requireCheck(ink.alpha in 0f..1f && idle.alpha in 0f..1f, "output alpha remains bounded")
        for (fraction in listOf(0f, .25f, .5f, .75f, 1f)) {
            val animatedInk = lerp(idle, ink, fraction)
            requireCheck(surfaces.all { c17ColorContrast(animatedInk, it) >= 4.49f }, "selection transition remains readable")
        }
    }
    requireCheck(kotlin.math.abs(c17ColorContrast(Color.Black, Color.White) - 21f) < .001f, "black on white is 21:1")
    requireCheck(kotlin.math.abs(c17ColorContrast(Color.White, Color.White) - 1f) < .001f, "same color is 1:1")
    println("COUI production glass contrast: $checks checks passed; no Android device or renderer invoked.")
}
