// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private val legendGold = Color(0xFFE8C170)
private val legendBright = Color(0xFFFFE6A8)
private val legendPale = Color(0xFFFFF7E3)
private val legendDeep = Color(0xFF8C6420)
private val legendShadow = Color(0xFF3B2A0C)
/** Brushed metal: dark and light bands alternate, so a gradient reads as polished gold rather than flat yellow. */
private val legendMetal = listOf(legendDeep, legendBright, legendGold, legendPale, legendGold, legendDeep, legendBright, legendDeep)

/** Gilding starts on the landed card. */
private const val LEGEND_GILD = 8.15f
/** The finale: everything settles, the frame holds, and only the thanks line is left. */
private const val LEGEND_FINALE = 10.7f
/**
 * Maps the icon's brightness onto gold: navy becomes antique gold, white becomes pale gold, so the C17 mark survives.
 * A colour matrix works on every supported API level, unlike the newer blend modes.
 */
private val legendGilded = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    .194f, .382f, .074f, 0f, 89f,
    .212f, .417f, .081f, 0f, 61f,
    .212f, .417f, .081f, 0f, 13f,
    0f, 0f, 0f, 1f, 0f)))

/** The gacha screen: a limited banner, the single-pull button pressed, "您进行了单抽". Everything after starts here. */
private const val LEGEND_INTRO = 2f
/** The orb bursts into a white-out and the reveal begins. */
private const val LEGEND_IGNITE = 6.6f
/** The card lands face up. */
private const val LEGEND_LANDED = 7.8f
/** Heartbeat pulses that raise the rarity: blue, purple, gold. */
private val legendPulses = floatArrayOf(3.4f, 4.5f, 5.6f)
private val legendStageColors = listOf(Color(0xFF4DA3FF), Color(0xFFB36BFF), Color(0xFFFFC94D))
private val legendStageNames = listOf("稀有", "史诗", "传说")
private val legendStageTags = listOf("RARE", "EPIC", "LEGEND")
/** The voice-over, shown as film subtitles in the lower letterbox bar. */
private val legendCaptions = listOf(Triple(3.45f, 4.35f, "这不是故障。"), Triple(4.55f, 5.45f, "每一次点亮屏幕，它都在悄悄积蓄光芒。"),
    Triple(5.65f, 6.55f, "现在——它醒了。"))

/**
 * 鎏金传说 (hidden, 5%): a gold hairline draws a sigil out of the dark, the sigil collapses into an anamorphic flare,
 * and a legendary card spins out of the light. Brushed-gold frame, filigree, holographic sheen, a helix of gold dust,
 * bokeh, willow fireworks and serif titling: restraint and polish rather than a pile of effects.
 */
internal class LegendEgg(icon: ImageBitmap, measurer: TextMeasurer, private val days: Int, private val serial: Int) :
    EggScene(icon, measurer) {
    private class Bokeh(val x: Float, val y: Float, val r: Float, val drift: Float, val phase: Float)
    private class Dust(val angle: Float, val height: Float, val speed: Float, val size: Float)
    private class Ember(val angle: Float, val speed: Float, val size: Float)
    private class Leaf(val x: Float, val delay: Float, val fall: Float, val spin: Float, val size: Float)
    private class Rise(val x: Float, val phase: Float, val speed: Float, val size: Float)
    private class Crystal(val x: Float, val y: Float, val size: Float, val phase: Float, val spin: Float)

    private val bokeh: List<Bokeh>
    private val dust: List<Dust>
    private val embers: List<Ember>
    private val leaves: List<Leaf>
    private val rises: List<Rise>
    private val crystals: List<Crystal>
    private val willows = listOf(Triple(.24f, .13f, 8.4f), Triple(.78f, .1f, 9.4f))

    init {
        val random = Random(5)
        bokeh = List(18) { Bokeh(random.nextFloat(), random.nextFloat(), 14f + random.nextFloat() * 34f, .01f + random.nextFloat() * .02f,
            random.nextFloat() * EGG_TAU) }
        dust = List(90) { Dust(random.nextFloat() * EGG_TAU, random.nextFloat() - .5f, .5f + random.nextFloat() * .7f,
            .6f + random.nextFloat() * 1.4f) }
        embers = List(36) { index -> Ember(index / 36f * EGG_TAU + random.nextFloat() * .1f, 90f + random.nextFloat() * 60f,
            1.2f + random.nextFloat() * 1.2f) }
        rises = List(80) { Rise(random.nextFloat(), random.nextFloat(), .25f + random.nextFloat() * .45f, .8f + random.nextFloat() * 1.8f) }
        crystals = List(10) { index ->
            val side = if (index % 2 == 0) .12f + random.nextFloat() * .2f else .68f + random.nextFloat() * .2f
            Crystal(side, .12f + random.nextFloat() * .7f, 10f + random.nextFloat() * 16f, random.nextFloat() * EGG_TAU, (random.nextFloat() - .5f) * 40f)
        }
        leaves = List(14) { Leaf(random.nextFloat(), random.nextFloat() * 4f, .05f + random.nextFloat() * .05f,
            (random.nextFloat() - .5f) * 6f, 4f + random.nextFloat() * 4f) }
    }

    override fun DrawScope.draw(t: Float, fade: Float) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h * .38f)
        drawRect(Color(0xFF050403), alpha = .99f * fade)
        // Every impact shakes the whole frame; the shake decays within a few frames.
        val shake = shakeAmount(t) * density
        translate((eggNoise(71, (t * 60f).toInt()) - .5f) * 2f * shake, (eggNoise(72, (t * 60f).toInt()) - .5f) * 2f * shake) {
            val warm = eggIn(t, LEGEND_IGNITE, 1.2f)
            drawRect(Brush.radialGradient(listOf(Color(0xFF3A2808), Color(0xFF140D04), Color.Transparent), center, size.maxDimension * .7f),
                alpha = warm * fade)
            volumetric(t, fade * warm, center)
            bokehField(t, fade * warm, w, h)
            gachaIntro(t, fade, w, h)
            upgradeAmbience(t, fade, w, h, center)
            glitchIntro(t, fade, w, h, center)
            orb(t, fade, w, center)
            tunnel(t, fade, center)
            helix(t, fade, center, front = false)
            card(t, fade, center)
            helix(t, fade, center, front = true)
            glints(t, fade, center)
            willowFireworks(t, fade, w, h)
            goldLeaf(t, fade, w, h)
            goldDust(t, fade, w, h)
            finale(t, fade)
            titles(t, fade, w, center)
        }
        cinema(t, fade, w, h)
        val whiteout = eggWindow(t, LEGEND_IGNITE - .08f, LEGEND_IGNITE + .45f, .08f, .4f)
        if (whiteout > 0f) drawRect(Color.White, alpha = whiteout * fade)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xCC000000)), center, size.maxDimension * .78f), alpha = fade)
    }

    /** Impacts: three heartbeats, the burst and the landing, each a decaying jolt in dp. */
    private fun shakeAmount(t: Float): Float {
        var amount = 0f
        val hits = floatArrayOf(legendPulses[0], legendPulses[1], legendPulses[2], LEGEND_IGNITE, LEGEND_LANDED)
        val power = floatArrayOf(4f, 6f, 9f, 16f, 11f)
        for (i in hits.indices) {
            val age = t - hits[i]
            if (age >= 0f) amount += power[i] * exp(-age * 9f)
        }
        return amount
    }

    /** Soft shafts of light turning slowly behind the card. */
    private fun DrawScope.volumetric(t: Float, show: Float, center: Offset) {
        if (show <= 0f) return
        val reach = size.maxDimension * .9f
        // Gold shafts, joined by rainbow ones once the card has landed.
        val festive = eggIn(t, LEGEND_LANDED, .6f)
        rotate(t * 9f, center) {
            for (i in 0 until 12) {
                val tint = if (i % 2 == 0) legendBright else legendGold
                val strength = if (i % 2 == 0) .16f else .1f * festive
                if (strength <= 0f) continue
                drawArc(Brush.radialGradient(listOf(tint.copy(alpha = strength), Color.Transparent), center, reach), i * 30f, 12f,
                    true, center - Offset(reach, reach), Size(reach * 2f, reach * 2f), alpha = show, blendMode = BlendMode.Plus)
            }
        }
    }

    /** Out-of-focus gold lights drifting upward, as if seen through a lens. */
    private fun DrawScope.bokehField(t: Float, show: Float, w: Float, h: Float) {
        if (show <= 0f) return
        for (b in bokeh) {
            val y = (((b.y - t * b.drift) % 1f) + 1f) % 1f * h
            val x = b.x * w + sin(t * .6f + b.phase) * 12f * density
            val r = b.r * density
            drawCircle(Brush.radialGradient(listOf(legendGold.copy(alpha = .22f), legendGold.copy(alpha = .08f), Color.Transparent),
                Offset(x, y), r), r, Offset(x, y), alpha = show * (.5f + .5f * eggWave(t, 1.2f, b.phase)), blendMode = BlendMode.Plus)
        }
    }

    /** Gold dust spiralling around the card; the near half is drawn after the card so it passes in front. */
    private fun DrawScope.helix(t: Float, fade: Float, center: Offset, front: Boolean) {
        val show = eggIn(t, LEGEND_IGNITE + .4f, 1f) * fade
        if (show <= 0f) return
        for (d in dust) {
            val a = d.angle + t * d.speed
            val depth = sin(a)
            if ((depth > 0f) != front) continue
            val p = Offset(center.x + cos(a) * 138f * density, center.y + d.height * 330f * density + depth * 18f * density)
            drawCircle(legendBright, d.size * density * (1f + .5f * depth), p, alpha = show * (.45f + .45f * depth),
                blendMode = BlendMode.Plus)
        }
    }

    /** Star glints flash on the card corners in turn. */
    private fun DrawScope.glints(t: Float, fade: Float, center: Offset) {
        if (t < LEGEND_LANDED) return
        val spots = listOf(Offset(-96f, -138f), Offset(96f, 138f), Offset(96f, -138f), Offset(-96f, 138f), Offset(46f, -92f))
        spots.forEachIndexed { index, spot ->
            val phase = ((t - LEGEND_LANDED + index * .37f) % 1.85f) / .5f
            if (phase > 1f) return@forEachIndexed
            val s = sin(phase * Math.PI.toFloat()) * 16f * density
            val p = center + spot * density
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, legendPale, Color.Transparent), p.x - s, p.x + s),
                Offset(p.x - s, p.y), Offset(p.x + s, p.y), 1.4f * density, alpha = fade, blendMode = BlendMode.Plus)
            drawLine(Brush.verticalGradient(listOf(Color.Transparent, legendPale, Color.Transparent), p.y - s, p.y + s),
                Offset(p.x, p.y - s), Offset(p.x, p.y + s), 1.4f * density, alpha = fade, blendMode = BlendMode.Plus)
            drawCircle(legendPale, s * .14f, p, alpha = fade, blendMode = BlendMode.Plus)
        }
    }

    /** A few flakes of gold leaf tumble down once the card has landed. */
    private fun DrawScope.goldLeaf(t: Float, fade: Float, w: Float, h: Float) {
        for (leaf in leaves) {
            val age = t - LEGEND_LANDED - leaf.delay * .6f
            if (age < 0f) continue
            val y = -12f * density + age * leaf.fall * h * 1.6f
            if (y > h) continue
            val x = leaf.x * w + sin(age * 1.4f + leaf.x * 9f) * 18f * density
            val s = leaf.size * density
            val flip = abs(cos(age * leaf.spin)).coerceAtLeast(.12f)
            rotate(age * leaf.spin * 30f, Offset(x, y)) {
                drawRect(Brush.linearGradient(listOf(legendDeep, legendBright, legendGold), Offset(x - s, y - s), Offset(x + s, y + s)),
                    Offset(x - s * flip / 2f, y - s / 2f), Size(s * flip, s), alpha = .9f * fade)
            }
        }
    }

    private fun DrawScope.card(t: Float, fade: Float, center: Offset) {
        val enter = LEGEND_IGNITE + .15f
        if (t < enter) return
        // The card flies out of the tunnel from far away, one full turn, and slams down face up.
        val grow = .04f + .96f * eggOvershoot((t - enter) / (LEGEND_LANDED - enter))
        val turn = (1f + eggEaseInOut((t - enter) / (LEGEND_LANDED - enter)) * 2f) * Math.PI.toFloat()
        val sx = cos(turn)
        val bob = if (t > LEGEND_LANDED) sin((t - LEGEND_LANDED) * 1.6f) * 4f * density else 0f
        val c = center + Offset(0f, bob)
        val cw = 196f * density
        val ch = 280f * density
        val topLeft = Offset(c.x - cw / 2f, c.y - ch / 2f)
        val alpha = eggIn(t, enter, .2f) * fade
        scale(1f, .12f, Offset(c.x, c.y + ch * .62f)) {
            drawCircle(Brush.radialGradient(listOf(Color(0xAA000000), Color.Transparent), Offset(c.x, c.y + ch * .62f), cw * .7f), cw * .7f,
                Offset(c.x, c.y + ch * .62f), alpha = alpha * abs(sx).coerceAtLeast(.4f))
        }
        scale(abs(sx).coerceAtLeast(.015f) * grow, grow, c) {
            if (sx > 0f) cardBack(t, alpha, c, topLeft, cw, ch) else cardFront(t, alpha, c, topLeft, cw, ch)
        }
    }

    /** Brushed-gold frame with an inner hairline and filigree in every corner. */
    private fun DrawScope.frame(t: Float, alpha: Float, topLeft: Offset, cw: Float, ch: Float) {
        val slide = (t * 40f * density) % (cw * 2f)
        drawRoundRect(Brush.linearGradient(legendMetal, topLeft + Offset(slide, 0f), topLeft + Offset(slide + cw * .6f, ch * .6f), TileMode.Mirror),
            topLeft, Size(cw, ch), CornerRadius(14f * density), alpha = alpha, style = Stroke(6f * density))
        val inset = 11f * density
        drawRoundRect(legendGold, topLeft + Offset(inset, inset), Size(cw - inset * 2f, ch - inset * 2f), CornerRadius(6f * density),
            alpha = .8f * alpha, style = Stroke(.9f * density))
        val arm = 22f * density
        val k = 17f * density
        for (sxSign in listOf(-1f, 1f)) for (sySign in listOf(-1f, 1f)) {
            val corner = Offset(if (sxSign < 0f) topLeft.x + k else topLeft.x + cw - k, if (sySign < 0f) topLeft.y + k else topLeft.y + ch - k)
            drawLine(legendBright, corner, corner + Offset(-sxSign * arm, 0f), 1.2f * density, alpha = alpha)
            drawLine(legendBright, corner, corner + Offset(0f, -sySign * arm), 1.2f * density, alpha = alpha)
            drawArc(legendGold, if (sxSign < 0f) (if (sySign < 0f) 0f else 270f) else (if (sySign < 0f) 90f else 180f), 90f, false,
                corner - Offset(7f * density, 7f * density), Size(14f * density, 14f * density), alpha = alpha, style = Stroke(density))
            rotate(45f, corner) {
                drawRect(legendPale, corner - Offset(2.5f * density, 2.5f * density), Size(5f * density, 5f * density), alpha = alpha)
            }
        }
    }

    /** Guilloché: fine concentric gold rings, the engraving of banknotes and watches. */
    private fun DrawScope.guilloche(c: Offset, alpha: Float, rings: Int) {
        for (i in 0 until rings) {
            drawCircle(legendGold, (34f + i * 9f) * density, c, alpha = .12f * alpha, style = Stroke(.7f * density))
        }
    }

    private fun DrawScope.cardBack(t: Float, alpha: Float, c: Offset, topLeft: Offset, cw: Float, ch: Float) {
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF151A2E), Color(0xFF0B0D18), Color(0xFF151A2E)), topLeft, topLeft + Offset(cw, ch)),
            topLeft, Size(cw, ch), CornerRadius(14f * density), alpha = alpha)
        clipRect(topLeft.x, topLeft.y, topLeft.x + cw, topLeft.y + ch) { guilloche(c, alpha, 14) }
        for (tri in 0 until 2) for (k in 0 until 3) {
            val a0 = (-90f + tri * 60f + k * 120f) * Math.PI.toFloat() / 180f
            val a1 = (-90f + tri * 60f + (k + 1) * 120f) * Math.PI.toFloat() / 180f
            drawLine(legendGold, c + Offset(cos(a0), sin(a0)) * 62f * density, c + Offset(cos(a1), sin(a1)) * 62f * density, density,
                alpha = .6f * alpha)
        }
        drawCircle(Color(0xFF0B0D18), 30f * density, c, alpha = alpha)
        drawCircle(legendGold, 30f * density, c, alpha = alpha, style = Stroke(1.5f * density))
        val mark = layout("C17", 20.sp, FontWeight.Bold, FontFamily.Serif)
        centred(mark, c.x, c.y - mark.size.height / 2f, Brush.verticalGradient(listOf(legendPale, legendGold, legendDeep), c.y - 12f * density,
            c.y + 12f * density), alpha)
        frame(t, alpha, topLeft, cw, ch)
    }

    private fun DrawScope.cardFront(t: Float, alpha: Float, c: Offset, topLeft: Offset, cw: Float, ch: Float) {
        val iconCenter = c - Offset(0f, 22f * density)
        drawRoundRect(Brush.radialGradient(listOf(Color(0xFF3A2A0C), Color(0xFF120D05), Color(0xFF0A0703)), iconCenter, ch * .75f),
            topLeft, Size(cw, ch), CornerRadius(14f * density), alpha = alpha)
        clipRect(topLeft.x, topLeft.y, topLeft.x + cw, topLeft.y + ch) {
            guilloche(iconCenter, alpha, 12)
            // Holographic sheen: a soft prism band and a bright edge cross the card every 2.4 s.
            val phase = ((t - LEGEND_LANDED) % 2.4f) / 1f
            if (t > LEGEND_LANDED && phase <= 1f) {
                val x = topLeft.x - cw * .6f + phase * cw * 2.2f
                rotate(28f, c) {
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x33FF8FC8), Color(0x44FFE6A8), Color(0x337FE7FF),
                        Color.Transparent), x - 60f * density, x + 60f * density), Offset(x - 60f * density, c.y - ch), Size(120f * density, ch * 2f),
                        alpha = alpha, blendMode = BlendMode.Plus)
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xAAFFFFFF), Color.Transparent), x - 8f * density,
                        x + 8f * density), Offset(x - 8f * density, c.y - ch), Size(16f * density, ch * 2f), alpha = alpha,
                        blendMode = BlendMode.Plus)
                }
            }
        }
        drawCircle(Brush.radialGradient(listOf(legendPale.copy(alpha = .5f), legendGold.copy(alpha = .14f), Color.Transparent), iconCenter,
            96f * density), 96f * density, iconCenter, alpha = alpha, blendMode = BlendMode.Plus)
        drawCircle(legendGold, 66f * density, iconCenter, alpha = .7f * alpha, style = Stroke(density))
        gild(t, alpha, iconCenter, 108f * density)
        // The 隐藏款 plate at the top edge.
        val plate = Size(78f * density, 22f * density)
        val plateTop = Offset(c.x - plate.width / 2f, topLeft.y + 18f * density)
        drawRoundRect(Color(0xFF120D05), plateTop, plate, CornerRadius(3f * density), alpha = alpha)
        drawRoundRect(legendGold, plateTop, plate, CornerRadius(3f * density), alpha = alpha, style = Stroke(.8f * density))
        val label = layout("隐藏款", 11.sp, FontWeight.Bold, letterSpacing = 3.sp)
        centred(label, c.x, plateTop.y + (plate.height - label.size.height) / 2f, Brush.verticalGradient(listOf(legendPale, legendGold),
            plateTop.y, plateTop.y + plate.height), alpha)
        // Name plate: divider, name, and six rarity diamonds lighting in turn.
        val bottom = topLeft.y + ch
        val divider = bottom - 66f * density
        drawLine(legendGold, Offset(c.x - 62f * density, divider), Offset(c.x - 9f * density, divider), .8f * density, alpha = alpha)
        drawLine(legendGold, Offset(c.x + 9f * density, divider), Offset(c.x + 62f * density, divider), .8f * density, alpha = alpha)
        rotate(45f, Offset(c.x, divider)) {
            drawRect(legendBright, Offset(c.x - 3f * density, divider - 3f * density), Size(6f * density, 6f * density), alpha = alpha)
        }
        val name = layout("Status C17", 17.sp, FontWeight.Bold, FontFamily.Serif, 1.sp)
        val nameTop = divider + 7f * density
        centred(name, c.x, nameTop, Brush.verticalGradient(listOf(legendPale, legendBright, legendGold, legendDeep), nameTop,
            nameTop + name.size.height), alpha)
        for (i in 0 until 6) {
            val lit = eggOvershoot((t - LEGEND_LANDED - .15f - i * .09f) / .3f)
            val p = Offset(c.x + (i - 2.5f) * 13f * density, bottom - 22f * density)
            rotate(45f, p) {
                drawRect(legendShadow, p - Offset(3f * density, 3f * density), Size(6f * density, 6f * density), alpha = alpha)
                if (lit > 0f) drawRect(legendBright, p - Offset(3f * density * lit, 3f * density * lit), Size(6f * density * lit, 6f * density * lit),
                    alpha = alpha)
            }
        }
        frame(t, alpha, topLeft, cw, ch)
    }

    /** Willow fireworks: slow gold bursts whose sparks droop and leave fading trails. */
    private fun DrawScope.willowFireworks(t: Float, fade: Float, w: Float, h: Float) {
        for ((bx, by, born) in willows) {
            val origin = Offset(bx * w, by * h + 40f * density)
            val rise = t - (born - .6f)
            if (rise in 0f..0.6f) {
                val p = eggEase(rise / .6f)
                drawCircle(legendPale, 1.6f * density, Offset(origin.x, h * .55f + (origin.y - h * .55f) * p), alpha = (1f - p * .5f) * fade,
                    blendMode = BlendMode.Plus)
            }
            val age = t - born
            if (age < 0f || age > 2.4f) continue
            val life = (1f - age / 2.4f).let { it * it } * fade
            for (ember in embers) {
                val dir = Offset(cos(ember.angle), sin(ember.angle))
                for (k in 0 until 5) {
                    val a = age - k * .07f
                    if (a < 0f) break
                    val out = (1f - exp(-2.4f * a)) / 2.4f
                    val p = origin + dir * ember.speed * out * density * 1.15f + Offset(0f, 28f * a * a * density)
                    drawCircle(if (k == 0) legendPale else legendBright, ember.size * density * (1f - k * .16f), p,
                        alpha = life * (1f - k * .2f), blendMode = BlendMode.Plus)
                }
            }
            if (age < .25f) drawCircle(Brush.radialGradient(listOf(legendPale, Color.Transparent), origin, 46f * density), 46f * density,
                origin, alpha = (1f - age / .25f) * fade, blendMode = BlendMode.Plus)
        }
    }

    /** Two layers only: the headline, then this user's own line. At the finale both step back for the thanks line. */
    private fun DrawScope.titles(t: Float, fade: Float, w: Float, center: Offset) {
        val cx = w / 2f
        val base = center.y + 140f * density
        val ornament = eggIn(t, LEGEND_LANDED + .3f, .6f) * fade
        if (ornament <= 0f) return
        val settle = 1f - .55f * eggEase((t - LEGEND_FINALE) / .6f)
        val span = 80f * density * eggEaseInOut((t - LEGEND_LANDED - .3f) / .7f)
        val dy = base + 24f * density
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, legendGold), cx - span - 10f * density, cx - 10f * density),
            Offset(cx - span - 10f * density, dy), Offset(cx - 10f * density, dy), .8f * density, alpha = ornament * settle)
        drawLine(Brush.horizontalGradient(listOf(legendGold, Color.Transparent), cx + 10f * density, cx + span + 10f * density),
            Offset(cx + 10f * density, dy), Offset(cx + span + 10f * density, dy), .8f * density, alpha = ornament * settle)
        rotate(45f, Offset(cx, dy)) {
            drawRect(legendBright, Offset(cx - 3f * density, dy - 3f * density), Size(6f * density, 6f * density), alpha = ornament * settle)
        }
        val show = eggIn(t, LEGEND_LANDED + .5f, .8f) * fade * settle
        if (show <= 0f) return
        val top = dy + 12f * density
        val title = layout("鎏金传说", 34.sp, FontWeight.Bold, FontFamily.Serif, 6.sp)
        val metal = Brush.verticalGradient(listOf(legendPale, legendBright, legendGold, legendDeep, legendGold), top, top + title.size.height)
        centred(title, cx + 1.5f * density, top + 2f * density, Color(0xAA000000), show)
        centred(title, cx, top, metal, show)
        val sweep = ((t - LEGEND_LANDED - .8f) % 2.6f) / 1.1f
        if (sweep in 0f..1f) {
            val x = cx - title.size.width / 2f - 30f * density + sweep * (title.size.width + 60f * density)
            centred(title, cx, top, Brush.horizontalGradient(listOf(Color.Transparent, Color(0xCCFFFFFF), Color.Transparent),
                x - 26f * density, x + 26f * density), show, BlendMode.Plus)
        }
        // This user's own line appears once the gold has set.
        val mine = layout(EasterEggMemento.daysLine(days) + "  ·  " + EasterEggMemento.serialLine(serial), 14.sp, FontWeight.Medium, letterSpacing = 1.sp)
        val mineTop = top + title.size.height + 6f * density
        centred(mine, cx, mineTop, legendPale.copy(alpha = .9f), eggIn(t, LEGEND_GILD + 1.5f, .7f) * fade * settle)
        // The last beat belongs to the thanks line.
        val thanks = eggIn(t, LEGEND_FINALE + .2f, .8f) * fade
        if (thanks > 0f) {
            val line = layout(EGG_THANKS, 16.sp, FontWeight.Medium, letterSpacing = 2.sp)
            centred(line, cx, mineTop + mine.size.height + 18f * density + (1f - thanks) * 8f * density, legendBright, thanks)
        }
    }

    /** After the landing, fine gold dust drifts down through the light. */
    private fun DrawScope.goldDust(t: Float, fade: Float, w: Float, h: Float) {
        val show = eggIn(t, LEGEND_LANDED, 1f) * fade
        if (show <= 0f) return
        for (mote in rises) {
            val y = ((mote.phase + t * mote.speed * .3f) % 1f) * h
            val x = mote.x * w + sin(t * .8f + mote.phase * 9f) * 14f * density
            drawCircle(legendBright, mote.size * .8f * density, Offset(x, y), alpha = .55f * show * eggWave(t, 2.5f, mote.phase * 11f),
                blendMode = BlendMode.Plus)
        }
    }

    /** The hold: the edges darken towards the card and one slow band of gold light crosses the frame. */
    private fun DrawScope.finale(t: Float, fade: Float) {
        val hold = eggEase((t - LEGEND_FINALE) / .6f) * fade
        if (hold <= 0f) return
        val c = Offset(size.width / 2f, size.height * .42f)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xB3000000)), c, size.maxDimension * .55f), alpha = hold)
        val sweep = (t - LEGEND_FINALE) / 1.4f
        if (sweep in 0f..1f) {
            val x = -size.width * .4f + sweep * size.width * 1.8f
            rotate(20f, c) {
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, legendGold.copy(alpha = .35f), Color.Transparent), x - 70f * density, x + 70f * density),
                    Offset(x - 70f * density, -size.height), Size(140f * density, size.height * 3f), alpha = fade, blendMode = BlendMode.Plus)
            }
        }
    }

    /** A fake malfunction: glitch bars, a typed warning, then a crack of light down the middle. */
    private fun DrawScope.glitchIntro(time: Float, fade: Float, w: Float, h: Float, center: Offset) {
        // Local clock: the malfunction starts right after the gacha screen.
        val t = time - LEGEND_INTRO
        if (t < 0f || t > 1.5f) return
        val step = (t * 16f).toInt()
        if (t in .1f..1.05f) for (i in 0 until 7) {
            if (eggNoise(i, step) < .45f) continue
            val y = eggNoise(i + 10, step) * h
            val tint = when (i % 3) { 0 -> Color(0xFF00F0FF); 1 -> Color(0xFFFF2BD6); else -> Color.White }
            drawRect(tint, Offset((eggNoise(i + 30, step) - .5f) * 40f * density, y), Size(w, (2f + eggNoise(i + 20, step) * 18f) * density),
                alpha = .22f * fade, blendMode = BlendMode.Plus)
        }
        val typed = ((t - .3f) * 12f).toInt().coerceIn(0, 7)
        val words = eggWindow(t, .3f, 1.35f, .05f, .2f) * fade
        if (words > 0f) {
            val cursor = if (((t * 4f).toInt() and 1) == 0) "▌" else " "
            val warning = layout("检测到异常信号".take(typed) + cursor, 15.sp, FontWeight.Medium, FontFamily.Monospace, 2.sp)
            centred(warning, center.x, center.y + 120f * density, Color(0xFF9FB4C8), words)
        }
        val crack = eggWindow(t, .85f, 1.45f, .3f, .2f) * fade
        if (crack > 0f) {
            val half = eggEase((t - .85f) / .4f) * 120f * density
            drawLine(Brush.verticalGradient(listOf(Color.Transparent, Color.White, Color.Transparent), center.y - half, center.y + half),
                Offset(center.x, center.y - half), Offset(center.x, center.y + half), 2f * density, alpha = crack, blendMode = BlendMode.Plus)
            scale(.12f, 1f, center) {
                drawCircle(Brush.radialGradient(listOf(Color(0x88FFFFFF), Color.Transparent), center, half + 1f), half + 1f, center,
                    alpha = crack, blendMode = BlendMode.Plus)
            }
        }
    }

    private fun stage(t: Float): Int = legendPulses.count { t >= it } - 1

    /** The rarity orb: it swallows light, and every heartbeat upgrades its colour until it cracks open in rainbow. */
    private fun DrawScope.orb(t: Float, fade: Float, w: Float, center: Offset) {
        if (t < LEGEND_INTRO + 1.1f || t > LEGEND_IGNITE + .1f) return
        val index = stage(t)
        val color = if (index < 0) legendStageColors[0].copy(alpha = .6f) else legendStageColors[index]
        val tremble = eggIn(t, LEGEND_IGNITE - .5f, .5f)
        val jitter = Offset((eggNoise(80, (t * 50f).toInt()) - .5f), (eggNoise(81, (t * 50f).toInt()) - .5f)) * 10f * density * tremble
        val c = center + jitter
        val r = (22f + 12f * (index + 1) + 3f * sin(t * 14f)) * density * eggIn(t, LEGEND_INTRO + 1.1f, .3f)
        if (r < 1f) return
        stageBackdrop(t, fade, center, index)
        charge(t, fade, c, r)
        for (ember in embers) {
            val phase = ((t * .9f + ember.angle) % 1f)
            val p = c + Offset(cos(ember.angle * 3f), sin(ember.angle * 3f)) * 230f * density * (1f - phase)
            drawCircle(color, ember.size * density, p, alpha = phase * fade, blendMode = BlendMode.Plus)
        }
        drawCircle(Brush.radialGradient(listOf(Color.White, color.copy(alpha = .6f), Color.Transparent), c, r * 3.6f), r * 3.6f, c,
            alpha = fade, blendMode = BlendMode.Plus)
        drawCircle(Color.White, r * .55f, c, alpha = fade)
        // The icon sleeps inside the light and wakes a little more with every heartbeat.
        drawIcon(icon, c, r * 1.05f, (.3f + .22f * (index + 1)) * fade)
        drawCircle(color, r * .8f, c, alpha = fade, style = Stroke(2.5f * density), blendMode = BlendMode.Plus)
        pulseHits(t, fade, w, c)
        if (index >= 0) rarityBanner(t, fade, w, center, index)
        val crack = eggEase((t - (LEGEND_IGNITE - .45f)) / .4f)
        if (crack > 0f) for (i in 0 until 12) {
            val a = i / 12f * EGG_TAU + eggNoise(i, 90) * .3f
            val length = crack * (160f + eggNoise(i, 91) * 140f) * density
            val dir = Offset(cos(a), sin(a))
            val side = Offset(-dir.y, dir.x)
            val p1 = c + dir * length * .35f + side * (eggNoise(i, 92) - .5f) * 26f * density
            val p2 = c + dir * length * .7f + side * (eggNoise(i, 93) - .5f) * 26f * density
            val tint = if (i % 2 == 0) legendPale else legendBright
            drawLine(tint, c, p1, 2.5f * density, StrokeCap.Round, alpha = fade, blendMode = BlendMode.Plus)
            drawLine(tint, p1, p2, 2f * density, StrokeCap.Round, alpha = fade, blendMode = BlendMode.Plus)
            drawLine(tint, p2, c + dir * length, 1.5f * density, StrokeCap.Round, alpha = fade, blendMode = BlendMode.Plus)
        }
    }

    /** Right after the white-out: gold rings rush at the viewer like a tunnel, with radial speed streaks. */
    private fun DrawScope.tunnel(t: Float, fade: Float, center: Offset) {
        val show = eggWindow(t, LEGEND_IGNITE, LEGEND_IGNITE + 1.5f, .1f, .6f) * fade
        if (show <= 0f) return
        val travel = (t - LEGEND_IGNITE) * 1.15f
        for (i in 0 until 16) {
            val z = ((i / 16f - travel) % 1f + 1f) % 1f * .95f + .05f
            val r = 26f * density / z
            if (r > size.maxDimension * 1.2f) continue
            val color = if (i % 4 == 0) legendPale else if (i % 2 == 0) legendBright else legendGold
            drawCircle(color, r, center, alpha = (1f - z) * show, style = Stroke((.6f / z).coerceAtMost(9f) * density),
                blendMode = BlendMode.Plus)
        }
        for (i in 0 until 28) {
            val a = i / 28f * EGG_TAU + eggNoise(i, 60) * .2f
            val start = (40f + ((travel * 900f + eggNoise(i, 61) * 600f) % 600f)) * density
            val dir = Offset(cos(a), sin(a))
            drawLine(legendPale, center + dir * start, center + dir * (start + 90f * density), 1.5f * density, StrokeCap.Round,
                alpha = .6f * show, blendMode = BlendMode.Plus)
        }
    }

    /** Each rarity floods the screen with its colour; rays multiply and spin faster at every stage. */
    private fun DrawScope.stageBackdrop(t: Float, fade: Float, center: Offset, index: Int) {
        if (index < 0) return
        val color = legendStageColors[index]
        val show = eggIn(t, legendPulses[index], .2f) * (1f - eggEase((t - (LEGEND_IGNITE - .3f)) / .3f)) * fade
        if (show <= 0f) return
        drawRect(Brush.radialGradient(listOf(color.copy(alpha = .38f), color.copy(alpha = .1f), Color.Transparent), center,
            size.maxDimension * .8f), alpha = show)
        val reach = size.maxDimension
        val count = 8 + index * 4
        rotate(t * (20f + index * 25f), center) {
            for (i in 0 until count) drawArc(Brush.radialGradient(listOf(color.copy(alpha = .45f), Color.Transparent), center, reach),
                i * 360f / count, 6f, true, center - Offset(reach, reach), Size(reach * 2f, reach * 2f), alpha = (.4f + .2f * index) * show,
                blendMode = BlendMode.Plus)
        }
        if (index == 2) rotate(-t * 35f, center) {
            for (i in 0 until 10) drawArc(Brush.radialGradient(listOf(legendPale.copy(alpha = .3f), Color.Transparent), center, reach),
                i * 36f + 18f, 3f, true, center - Offset(reach, reach), Size(reach * 2f, reach * 2f), alpha = show, blendMode = BlendMode.Plus)
        }
    }

    /** Before every heartbeat the orb charges: the frame darkens, a ring tightens and lightning crackles around it. */
    private fun DrawScope.charge(t: Float, fade: Float, c: Offset, r: Float) {
        val next = legendPulses.indexOfFirst { it > t }
        if (next < 0) return
        val k = eggEaseInOut((t - (legendPulses[next] - .55f)) / .55f)
        if (k <= 0f) return
        val color = legendStageColors[next]
        drawRect(Color.Black, alpha = .35f * k * fade)
        for (ring in 0 until 2) {
            val radius = r + (170f - ring * 40f) * density * (1f - k)
            drawCircle(color, radius, c, alpha = k * fade, style = Stroke((2f + 4f * k) * density), blendMode = BlendMode.Plus)
        }
        val step = (t * 20f).toInt()
        for (j in 0 until 6) {
            if (eggNoise(j + next * 10, step) < .35f) continue
            val a = eggNoise(j + 40, step) * EGG_TAU
            val reach = r + (40f + eggNoise(j + 50, step) * 90f) * density
            var from = c + Offset(cos(a), sin(a)) * r * .6f
            for (seg in 1..5) {
                val f = seg / 5f
                val side = (eggNoise(j * 7 + seg, step) - .5f) * 22f * density
                val to = c + Offset(cos(a), sin(a)) * (r * .6f + (reach - r * .6f) * f) + Offset(-sin(a), cos(a)) * side
                drawLine(if (seg % 2 == 0) Color.White else color, from, to, 1.6f * density, StrokeCap.Round, alpha = k * fade,
                    blendMode = BlendMode.Plus)
                from = to
            }
        }
    }

    /** The heartbeat itself: colour flash, a pillar of light, two shockwaves and crystal shards flying out. */
    private fun DrawScope.pulseHits(t: Float, fade: Float, w: Float, c: Offset) {
        legendPulses.forEachIndexed { i, pulse ->
            val age = t - pulse
            if (age < 0f || age > 1.2f) return@forEachIndexed
            val color = legendStageColors[i]
            if (age < .3f) drawRect(color, alpha = .55f * (1f - age / .3f) * fade, blendMode = BlendMode.Plus)
            if (age < .12f) drawRect(Color.White, alpha = .5f * (1f - age / .12f) * fade)
            val beam = 1f - eggEase(age / .8f)
            if (beam > 0f) {
                val half = (60f + 30f * i) * density * beam + 4f * density
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = .9f), Color.White, color.copy(alpha = .9f),
                    Color.Transparent), c.x - half, c.x + half), Offset(c.x - half, 0f), Size(half * 2f, size.height), alpha = beam * fade,
                    blendMode = BlendMode.Plus)
            }
            drawCircle(color, eggEase(age / .9f) * w * .95f + 1f, c, alpha = (1f - age / .9f).coerceAtLeast(0f) * fade,
                style = Stroke(6f * density * (1f - age / 1.2f) + .5f), blendMode = BlendMode.Plus)
            drawCircle(Color.White, eggEase(age / 1.2f) * w * .6f + 1f, c, alpha = (1f - age / 1.2f) * .8f * fade,
                style = Stroke(2f * density), blendMode = BlendMode.Plus)
            val travel = (1f - exp(-3.5f * age)) / 3.5f
            for (j in 0 until 14) {
                val a = j / 14f * EGG_TAU + eggNoise(j + i * 20, 5) * .4f
                val p = c + Offset(cos(a), sin(a)) * travel * (380f + eggNoise(j + i * 20, 6) * 300f) * density
                rotate(Math.toDegrees(a.toDouble()).toFloat() + age * 200f, p) {
                    drawRect(if (j % 3 == 0) Color.White else color, p - Offset(8f * density, 2f * density), Size(16f * density, 4f * density),
                        alpha = (1f - age / 1.2f) * fade, blendMode = BlendMode.Plus)
                }
            }
        }
    }

    /** The rarity name slams down from 2.6x with ghost trails onto an unrolling banner; its stars light one by one. */
    private fun DrawScope.rarityBanner(t: Float, fade: Float, w: Float, center: Offset, index: Int) {
        val age = t - legendPulses[index]
        val words = (1f - eggEase((t - (LEGEND_IGNITE - .45f)) / .35f)) * fade
        if (words <= 0f) return
        val color = legendStageColors[index]
        val bandTop = center.y + 100f * density
        val bandH = 64f * density
        val bandW = w * eggEase(age / .25f)
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, color.copy(alpha = .55f), color.copy(alpha = .75f), color.copy(alpha = .55f),
            Color.Transparent), w / 2f - bandW / 2f, w / 2f + bandW / 2f), Offset(w / 2f - bandW / 2f, bandTop), Size(bandW, bandH), alpha = words)
        drawLine(Color.White, Offset(w / 2f - bandW / 2f, bandTop), Offset(w / 2f + bandW / 2f, bandTop), density, alpha = .7f * words)
        drawLine(Color.White, Offset(w / 2f - bandW / 2f, bandTop + bandH), Offset(w / 2f + bandW / 2f, bandTop + bandH), density, alpha = .7f * words)
        val upgrade = layout("稀有度提升", 12.sp, FontWeight.Medium, letterSpacing = 6.sp)
        centred(upgrade, center.x, bandTop - 22f * density, Color.White, .75f * words)
        val tag = layout(legendStageTags[index], 10.sp, FontWeight.Bold, letterSpacing = 10.sp)
        centred(tag, center.x + 5f * density, bandTop + 4f * density, Color.White, .8f * words)
        val name = layout(legendStageNames[index], (44 + index * 6).sp, FontWeight.Black, FontFamily.Serif, 10.sp)
        val pivot = Offset(center.x, bandTop + bandH / 2f + 6f * density)
        val nameTop = pivot.y - name.size.height / 2f
        val slam = if (age < .18f) { val k = age / .18f; 2.6f - 1.6f * k * k * k } else 1f + .06f * exp(-(age - .18f) * 10f) * sin((age - .18f) * 40f)
        val metal = Brush.verticalGradient(listOf(Color.White, color, color.eggMix(Color.Black, .35f)), nameTop, nameTop + name.size.height)
        if (age < .3f) for (g in 1..3) {
            val ghost = slam + g * .25f * (1f - age / .3f)
            scale(ghost, ghost, pivot) { centred(name, center.x, nameTop, color, .25f / g * words, BlendMode.Plus) }
        }
        scale(slam, slam, pivot) {
            centred(name, center.x + 2f * density, nameTop + 3f * density, Color.Black, .5f * words)
            centred(name, center.x, nameTop, metal, words)
        }
        val star = layout("★", 20.sp, FontWeight.Normal)
        val count = index + 3
        for (j in 0 until count) {
            val pop = eggOvershoot((age - .25f - j * .08f) / .25f)
            if (pop <= 0f) continue
            val x = center.x + (j - (count - 1) / 2f) * 26f * density
            val y = bandTop + bandH + 8f * density
            scale(pop, pop, Offset(x, y + star.size.height / 2f)) {
                centred(star, x, y, if (index == 2) legendBright else Color.White, words)
            }
        }
    }

    /** A floating crystal, unit half-height. */
    private val crystalShape = Path().apply {
        moveTo(0f, -1f)
        lineTo(.38f, 0f)
        lineTo(0f, 1f)
        lineTo(-.38f, 0f)
        close()
    }

    /**
     * The upgrade stage is never empty: a turning summoning circle on the floor, rising streams of energy, floating
     * crystals, distant light pillars that flare on every heartbeat and drifting nebulae, all in the current rarity colour.
     */
    private fun DrawScope.upgradeAmbience(t: Float, fade: Float, w: Float, h: Float, center: Offset) {
        val start = LEGEND_INTRO + 1f
        val show = eggIn(t, start, .6f) * (1f - eggEase((t - (LEGEND_IGNITE - .25f)) / .3f)) * fade
        if (show <= 0f) return
        val index = stage(t)
        val color = legendStageColors[index.coerceAtLeast(0)]
        val power = if (index < 0) .45f else .7f + .15f * index
        val beat = legendPulses.fold(0f) { acc, p -> if (t >= p) acc + exp(-(t - p) * 3f) else acc }.coerceAtMost(1.4f)
        // Nebulae in the corners.
        for ((k, spot) in listOf(Offset(.1f, .15f), Offset(.9f, .3f), Offset(.2f, .85f)).withIndex()) {
            val c = Offset(spot.x * w + sin(t * .3f + k) * 20f * density, spot.y * h)
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .3f), Color.Transparent), c, w * .55f), w * .55f, c, alpha = show * power,
                blendMode = BlendMode.Plus)
        }
        // Distant pillars of light that flare with each beat.
        for ((k, x) in floatArrayOf(.08f, .27f, .73f, .92f, .5f).withIndex()) {
            val half = (14f + k * 6f) * density
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, color.copy(alpha = .22f), Color.Transparent), 0f, h),
                Offset(x * w - half, 0f), Size(half * 2f, h), alpha = show * (.35f + .65f * beat).coerceAtMost(1f), blendMode = BlendMode.Plus)
        }
        // The summoning circle on the floor, seen at an angle.
        val floor = Offset(center.x, h * .8f)
        scale(1f, .3f, floor) {
            rotate(t * (18f + 12f * index.coerceAtLeast(0)), floor) {
                for ((ring, r) in floatArrayOf(170f, 140f, 80f).withIndex()) {
                    drawCircle(color, r * density, floor, alpha = (.7f - ring * .15f) * show * power, style = Stroke((2.5f - ring * .5f) * density),
                        blendMode = BlendMode.Plus)
                }
                for (i in 0 until 24) {
                    val a = i / 24f * EGG_TAU
                    val dir = Offset(cos(a), sin(a))
                    drawLine(color, floor + dir * 140f * density, floor + dir * (if (i % 3 == 0) 170f else 155f) * density, 1.5f * density,
                        alpha = .7f * show * power, blendMode = BlendMode.Plus)
                }
                for (k in 0 until 2) for (v in 0 until 3) {
                    val a0 = (-90f + k * 60f + v * 120f) * Math.PI.toFloat() / 180f
                    val a1 = (-90f + k * 60f + (v + 1) * 120f) * Math.PI.toFloat() / 180f
                    drawLine(color, floor + Offset(cos(a0), sin(a0)) * 140f * density, floor + Offset(cos(a1), sin(a1)) * 140f * density, 1.3f * density,
                        alpha = .6f * show * power, blendMode = BlendMode.Plus)
                }
            }
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .5f), Color.Transparent), floor, 190f * density), 190f * density, floor,
                alpha = show * (.4f + .6f * beat).coerceAtMost(1f), blendMode = BlendMode.Plus)
        }
        // Energy rising from the floor towards the orb.
        for (rise in rises) {
            val p = (rise.phase + t * rise.speed) % 1f
            val y = h * (1.02f - p * 1.1f)
            val x = rise.x * w + sin(p * 9f + rise.x * 20f) * 10f * density
            val length = (6f + 10f * rise.speed) * density * (1f + beat)
            drawLine(color, Offset(x, y), Offset(x, y + length), rise.size * density, StrokeCap.Round, alpha = sin(p * Math.PI.toFloat()) * show,
                blendMode = BlendMode.Plus)
        }
        // Floating crystals, bobbing and turning slowly.
        for (crystal in crystals) {
            val c = Offset(crystal.x * w, crystal.y * h + sin(t * 1.4f + crystal.phase) * 10f * density)
            val s = crystal.size * density
            rotate(crystal.spin * t + crystal.phase * 57f, c) {
                translate(c.x, c.y) {
                    scale(s, s, Offset.Zero) {
                        drawPath(crystalShape, color, alpha = .18f * show)
                        drawPath(crystalShape, color, alpha = .85f * show, style = Stroke(1.2f * density / s), blendMode = BlendMode.Plus)
                    }
                }
                drawLine(Color.White, Offset(c.x, c.y - s), Offset(c.x, c.y + s), .8f * density, alpha = .5f * show)
            }
        }
    }

    /** Film letterbox bars close in after the gacha screen and open at the reveal; the voice-over runs in the lower bar. */
    private fun DrawScope.cinema(t: Float, fade: Float, w: Float, h: Float) {
        val bar = h * .1f * eggIn(t, LEGEND_INTRO, .4f) * (1f - eggEase((t - LEGEND_IGNITE) / .5f))
        if (bar <= .5f) return
        drawRect(Color.Black, Offset.Zero, Size(w, bar), alpha = fade)
        drawRect(Color.Black, Offset(0f, h - bar), Size(w, bar), alpha = fade)
        for ((start, end, line) in legendCaptions) {
            val show = eggWindow(t, start, end, .25f, .25f) * fade
            if (show <= 0f) continue
            val words = layout(line, 15.sp, FontWeight.Medium, letterSpacing = 2.sp)
            val rise = (1f - eggEase((t - start) / .4f)) * 6f * density
            centred(words, w / 2f, h - bar / 2f - words.size.height / 2f + rise, Color(0xFFEDE6D6), show)
        }
    }

    /**
     * The opening looks like an ordinary wish screen: a full key-visual banner, the single pull pressed, a ticket spent
     * and "您进行了单抽". A plain blue light drops in, and nobody expects what comes next.
     */
    private fun DrawScope.gachaIntro(t: Float, fade: Float, w: Float, h: Float) {
        if (t > LEGEND_INTRO + .3f) return
        val leave = eggEase((t - (LEGEND_INTRO - .35f)) / .5f)
        val show = eggIn(t, 0f, .25f) * (1f - leave) * fade
        if (show > 0f) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF070B1E), Color(0xFF151036), Color(0xFF070B1E)), 0f, h), alpha = show)
            for (i in 0 until 46) {
                drawCircle(Color.White, (.5f + eggNoise(i, 202)) * density, Offset(eggNoise(i, 200) * w, eggNoise(i, 201) * h),
                    alpha = show * (.25f + .5f * eggWave(t, 3f, i.toFloat())))
            }
            scale(1f + .12f * leave, 1f + .12f * leave, Offset(w / 2f, h / 2f)) {
                gachaBanner(t, show, w, h)
                gachaControls(t, show, w, h)
            }
        }
        // A plain blue light drops to the centre: just an ordinary pull... for now.
        val drop = (t - (LEGEND_INTRO - .3f)) / .5f
        if (drop in 0f..1f) {
            val y = eggEaseInOut(drop) * h * .38f
            drawLine(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF4DA3FF)), y - 120f * density, y), Offset(w / 2f, y - 120f * density),
                Offset(w / 2f, y), 3f * density, StrokeCap.Round, alpha = fade, blendMode = BlendMode.Plus)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF4DA3FF), Color.Transparent), Offset(w / 2f, y), 24f * density), 24f * density,
                Offset(w / 2f, y), alpha = fade, blendMode = BlendMode.Plus)
        }
    }

    /** The limited banner: key visual with the icon as the featured "character", title, rate-up tag and countdown. */
    private fun DrawScope.gachaBanner(t: Float, show: Float, w: Float, h: Float) {
        val top = Offset(16f * density, h * .1f)
        val panel = Size(w - 32f * density, h * .5f)
        val radius = CornerRadius(22f * density)
        drawRoundRect(Brush.linearGradient(listOf(Color(0xFF4A2BB0), Color(0xFF2346A8), Color(0xFF0F1A4D)), top, top + Offset(panel.width, panel.height)),
            top, panel, radius, alpha = show)
        val hero = Offset(top.x + panel.width * .64f, top.y + panel.height * .44f)
        clipRect(top.x, top.y, top.x + panel.width, top.y + panel.height) {
            rotate(t * 12f, hero) {
                for (i in 0 until 12) drawArc(Brush.radialGradient(listOf(Color(0x55FFE6A8), Color.Transparent), hero, panel.height * .8f), i * 30f, 9f, true,
                    hero - Offset(panel.height * .8f, panel.height * .8f), Size(panel.height * 1.6f, panel.height * 1.6f), alpha = show,
                    blendMode = BlendMode.Plus)
            }
            drawCircle(Brush.radialGradient(listOf(Color(0xAAFFFFFF), Color(0x557FB8FF), Color.Transparent), hero, 130f * density), 130f * density, hero,
                alpha = show, blendMode = BlendMode.Plus)
            for (i in 0 until 18) {
                val p = Offset(top.x + eggNoise(i, 210) * panel.width, top.y + ((eggNoise(i, 211) - t * .08f) % 1f + 1f) % 1f * panel.height)
                drawCircle(legendBright, (1f + eggNoise(i, 212) * 1.5f) * density, p, alpha = show * eggWave(t, 4f, i.toFloat()), blendMode = BlendMode.Plus)
            }
            // A soft floor shadow and a lower fade so the text column stays readable.
            drawRect(Brush.horizontalGradient(listOf(Color(0xCC0B1033), Color.Transparent), top.x, top.x + panel.width * .6f), top,
                Size(panel.width * .6f, panel.height), alpha = show)
        }
        drawIcon(icon, hero + Offset(0f, sin(t * 2f) * 4f * density), 150f * density, show)
        drawRoundRect(Brush.linearGradient(legendMetal, top, top + Offset(panel.width, panel.height)), top, panel, radius, alpha = .8f * show,
            style = Stroke(1.4f * density))
        val left = top.x + 20f * density
        val tag = layout("限定祈愿", 12.sp, FontWeight.Bold, letterSpacing = 3.sp)
        drawRoundRect(Color(0xFFFF4D6D), Offset(left, top.y + 20f * density), Size(tag.size.width + 18f * density, tag.size.height + 6f * density),
            CornerRadius(4f * density), alpha = show)
        centred(tag, left + 9f * density + tag.size.width / 2f, top.y + 23f * density, Color.White, show)
        val title = layout("星辉\n之约", 40.sp, FontWeight.Black, FontFamily.Serif, 6.sp)
        val titleTop = top.y + 58f * density
        drawText(title, Color(0x88000000), Offset(left + 2f * density, titleTop + 3f * density), show)
        drawText(title, Brush.verticalGradient(listOf(Color.White, legendBright, legendGold), titleTop, titleTop + title.size.height),
            Offset(left, titleTop), show)
        val sub = layout("Status C17 · 限定卡池", 12.sp, FontWeight.Medium)
        drawText(sub, Color(0xDDFFFFFF), Offset(left, titleTop + title.size.height + 8f * density), show)
        val up = layout("UP  隐藏款 5%", 11.sp, FontWeight.Bold)
        val upTop = titleTop + title.size.height + 32f * density
        drawRoundRect(Brush.horizontalGradient(listOf(legendGold, legendDeep), left, left + up.size.width + 16f * density), Offset(left, upTop),
            Size(up.size.width + 16f * density, up.size.height + 6f * density), CornerRadius(10f * density), alpha = show)
        drawText(up, Color(0xFF2A1A04), Offset(left + 8f * density, upTop + 3f * density), show)
        val clock = layout(trf("距结束  13天 07:42:%s", (59 - (t * 1f).toInt()).toString().padStart(2, '0')), 11.sp, FontWeight.Medium)
        drawText(clock, Color(0xAAFFFFFF), Offset(left, top.y + panel.height - clock.size.height - 16f * density), show)
        for (i in 0 until 3) {
            drawCircle(if (i == 0) Color.White else Color(0x66FFFFFF), (if (i == 0) 4f else 3f) * density,
                Offset(w / 2f + (i - 1) * 14f * density, top.y + panel.height + 16f * density), alpha = show)
        }
    }

    /** Currency pill, the single / ten pull buttons (ten is greyed: only one ticket), the press and the result strip. */
    private fun DrawScope.gachaControls(t: Float, show: Float, w: Float, h: Float) {
        val pressAt = .7f
        val pressed = t >= pressAt + .1f
        // Currency pill, with the spent ticket floating away.
        val pill = Size(84f * density, 30f * density)
        val pillAt = Offset(w - pill.width - 16f * density, 36f * density)
        drawRoundRect(Color(0x99000000), pillAt, pill, CornerRadius(15f * density), alpha = show)
        drawRoundRect(legendGold, pillAt, pill, CornerRadius(15f * density), alpha = .6f * show, style = Stroke(density))
        drawCircle(Brush.radialGradient(listOf(legendPale, legendGold, legendDeep), pillAt + Offset(16f * density, 15f * density), 9f * density),
            9f * density, pillAt + Offset(16f * density, 15f * density), alpha = show)
        val count = layout(if (pressed) "0" else "1", 15.sp, FontWeight.Bold)
        centred(count, pillAt.x + pill.width - 26f * density, pillAt.y + (pill.height - count.size.height) / 2f, Color.White, show)
        val spent = (t - pressAt - .1f) / .7f
        if (spent in 0f..1f) {
            val minus = layout("−1", 14.sp, FontWeight.Bold)
            centred(minus, pillAt.x + pill.width - 26f * density, pillAt.y + pill.height + 4f * density - spent * 18f * density, Color(0xFFFF6B81),
                (1f - spent) * show)
        }
        // Two buttons: single pull (gold) and ten pulls (unaffordable, greyed).
        val buttonW = (w - 48f * density) / 2f
        val buttonH = 64f * density
        val y = h * .76f
        val single = Offset(16f * density, y)
        val ten = Offset(32f * density + buttonW, y)
        val press = if (t in pressAt..pressAt + .25f) 1f - .07f * sin((t - pressAt) / .25f * Math.PI.toFloat()) else 1f
        val singleCenter = single + Offset(buttonW / 2f, buttonH / 2f)
        scale(press, press, singleCenter) {
            drawRoundRect(Brush.verticalGradient(listOf(legendPale, legendGold, legendDeep), single.y, single.y + buttonH), single, Size(buttonW, buttonH),
                CornerRadius(16f * density), alpha = show)
            drawRoundRect(Color.White, single + Offset(3f * density, 3f * density), Size(buttonW - 6f * density, buttonH * .42f),
                CornerRadius(13f * density), alpha = .18f * show)
            val label = layout("单抽", 20.sp, FontWeight.Black, letterSpacing = 4.sp)
            centred(label, singleCenter.x, single.y + 9f * density, Color(0xFF2A1A04), show)
            val cost = layout("✦ × 1", 12.sp, FontWeight.Bold)
            centred(cost, singleCenter.x, single.y + 38f * density, Color(0xFF5A3D0E), show)
        }
        drawRoundRect(Color(0xFF2A2F45), ten, Size(buttonW, buttonH), CornerRadius(16f * density), alpha = show)
        drawRoundRect(Color(0x66FFFFFF), ten, Size(buttonW, buttonH), CornerRadius(16f * density), alpha = show, style = Stroke(density))
        val tenLabel = layout("十连", 20.sp, FontWeight.Black, letterSpacing = 4.sp)
        centred(tenLabel, ten.x + buttonW / 2f, ten.y + 9f * density, Color(0x88FFFFFF), show)
        val tenCost = layout("✦ × 10", 12.sp, FontWeight.Bold)
        centred(tenCost, ten.x + buttonW / 2f, ten.y + 38f * density, Color(0x66FFFFFF), show)
        // Tap indicator: a finger-sized circle shrinks onto the button, then a ripple.
        val tap = (t - (pressAt - .2f)) / .25f
        if (tap in 0f..1f) drawCircle(Color.White, (36f - 20f * tap) * density, singleCenter, alpha = tap * .5f * show, style = Stroke(2f * density))
        val ripple = (t - pressAt) / .6f
        if (ripple in 0f..1f) drawCircle(Color.White, 24f * density + ripple * 150f * density, singleCenter, alpha = (1f - ripple) * .45f * show,
            style = Stroke(2f * density))
        // Result strip across the screen.
        val strip = eggWindow(t, pressAt + .15f, LEGEND_INTRO - .2f, .18f, .25f) * show
        if (strip > 0f) {
            val bandH = 70f * density
            val bandTop = h * .63f - bandH / 2f
            val open = eggEase((t - pressAt - .15f) / .25f)
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xEE0A0A18), Color(0xEE0A0A18), Color.Transparent)),
                Offset(w / 2f - w * open / 2f, bandTop), Size(w * open, bandH), alpha = strip)
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, legendGold, Color.Transparent)), Offset(0f, bandTop), Offset(w, bandTop),
                density, alpha = strip * open)
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, legendGold, Color.Transparent)), Offset(0f, bandTop + bandH),
                Offset(w, bandTop + bandH), density, alpha = strip * open)
            val words = layout("您进行了单抽", 20.sp, FontWeight.Bold, letterSpacing = 4.sp)
            centred(words, w / 2f, bandTop + 12f * density, Color.White, strip)
            val note = layout("消耗 星辉券 × 1", 11.sp, FontWeight.Normal, letterSpacing = 2.sp)
            centred(note, w / 2f, bandTop + 12f * density + words.size.height + 2f * density, legendBright.copy(alpha = .8f), strip)
        }
    }

    /**
     * 鎏金 happens on screen: molten gold flows down over the icon with a wavy, glowing front and a few drips, turning
     * it into a gilded version of itself; then a highlight runs across the new gold.
     */
    private fun DrawScope.gild(t: Float, alpha: Float, c: Offset, side: Float) {
        drawIcon(icon, c, side, alpha)
        val flow = eggEaseInOut((t - LEGEND_GILD) / 1.4f)
        if (flow <= 0f) return
        val left = c.x - side / 2f
        val top = c.y - side / 2f
        val front = top - 6f * density + (side + 14f * density) * flow
        val wave = 4f * density * (1f - flow * .7f)
        val segments = 14
        val molten = Path().apply {
            moveTo(left - 2f, top - 2f)
            lineTo(left + side + 2f, top - 2f)
            for (k in segments downTo 0) {
                val x = left + side * k / segments
                lineTo(x, front + sin(k * 1.3f + t * 8f) * wave)
            }
            close()
        }
        clipPath(molten) { drawIcon(icon, c, side, alpha, filter = legendGilded) }
        if (flow < 1f) {
            // The glowing front of the molten gold, and drips running ahead of it.
            for (k in 0 until segments) {
                val x0 = left + side * k / segments
                val x1 = left + side * (k + 1) / segments
                drawLine(legendPale, Offset(x0, front + sin(k * 1.3f + t * 8f) * wave), Offset(x1, front + sin((k + 1) * 1.3f + t * 8f) * wave),
                    2f * density, StrokeCap.Round, alpha = alpha, blendMode = BlendMode.Plus)
            }
            for (d in 0 until 3) {
                val x = left + side * (.25f + d * .25f)
                val length = (6f + 10f * eggNoise(d, 300)) * density * sin(flow * Math.PI.toFloat())
                if (front < top || front + length > top + side) continue
                drawRoundRect(legendGold, Offset(x - 2f * density, front), Size(4f * density, length), CornerRadius(2f * density), alpha = alpha)
                drawCircle(legendBright, 2.6f * density, Offset(x, front + length), alpha = alpha)
            }
        }
        // Once gilded: a highlight sweeps the icon and the card flares softly.
        val sweep = (t - LEGEND_GILD - 1.45f) / .7f
        if (sweep in 0f..1f) clipRect(left, top, left + side, top + side) {
            val x = left - side * .3f + sweep * side * 1.6f
            rotate(25f, c) {
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xCCFFFFFF), Color.Transparent), x - 14f * density, x + 14f * density),
                    Offset(x - 14f * density, c.y - side), Size(28f * density, side * 2f), alpha = alpha, blendMode = BlendMode.Plus)
            }
        }
        val flare = 1f - eggEase((t - LEGEND_GILD - 1.4f) / .8f)
        if (t > LEGEND_GILD + 1.4f && flare > 0f) drawCircle(Brush.radialGradient(listOf(legendPale, legendGold.copy(alpha = .3f), Color.Transparent), c,
            side * 1.3f), side * 1.3f, c, alpha = flare * alpha, blendMode = BlendMode.Plus)
    }
}
