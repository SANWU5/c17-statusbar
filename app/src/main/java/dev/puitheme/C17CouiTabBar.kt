// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 aiingjie
package dev.puitheme

import android.annotation.SuppressLint
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.ComposeView
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * The COUI navigation as a real view, like the Gallery's BlurButtonWrapperView: a capsule FrameLayout centred at the
 * bottom of the activity root, with the tabs as Compose content. Being a view in the window lets ColorOS render its
 * system glass over whatever page is behind it, in ordinary and glass mode alike. Without the system material (other
 * systems, saver, the glass switch off) it is a solid capsule of the same size, outline and shadow.
 */
@SuppressLint("ViewConstructor")
internal class C17CouiTabBar(private val activity: ModernMainActivity) {
    private val density = activity.resources.displayMetrics.density
    private var dark = false
    private var glass = false
    private var materialApplied = false

    val view: FrameLayout = FrameLayout(activity).apply {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }
        // COUI level-three outer shadow with the floating navigation shadow colour.
        elevation = 12f * density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            outlineSpotShadowColor = 0x8A000000.toInt()
            outlineAmbientShadowColor = 0x8A000000.toInt()
        }
        visibility = View.GONE
        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) refreshSurface()
            activity.reportCouiNavigationHeight(((bottom - top) + (layoutParams as? FrameLayout.LayoutParams)?.bottomMargin.orZero()) / density)
        }
    }

    private val content = ComposeView(activity).apply {
        consumeWindowInsets = false
        setContent {
            C17Theme(activity.ui) {
                val dark = LocalC17Dark.current
                val glass = activity.glassNavigation && !rememberC17PowerSave()
                SideEffect { updateSurface(dark, glass) }
                C17CouiTabContent(activity.page, activity::selectPage, ::drag, ::release)
            }
        }
    }

    init {
        view.addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
    }

    fun attach(root: FrameLayout) {
        root.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = (16 * density).roundToInt()
        })
    }

    /** 16 dp above the screen edge as in the Gallery, or above a taller navigation bar. */
    fun setBottomInset(insetPx: Int) {
        val params = view.layoutParams as? FrameLayout.LayoutParams ?: return
        val margin = maxOf((16 * density).roundToInt(), insetPx)
        if (params.bottomMargin != margin) { params.bottomMargin = margin; view.layoutParams = params }
    }

    fun setShown(shown: Boolean) {
        val target = if (shown) View.VISIBLE else View.GONE
        if (view.visibility != target) view.visibility = target
    }

    private fun updateSurface(dark: Boolean, glass: Boolean) {
        if (this.dark == dark && this.glass == glass && (materialApplied || view.background != null)) return
        this.dark = dark; this.glass = glass
        refreshSurface()
    }

    private fun refreshSurface() {
        if (view.height <= 0) return
        val radius = view.height / 2f
        materialApplied = glass && ColorOsMaterial.applyBottomBar(view, dark, radius)
        if (materialApplied) {
            view.background = null
        } else {
            if (glass) ColorOsMaterial.clear(view)
            view.background = GradientDrawable().apply {
                cornerRadius = radius
                setColor(if (dark) 0xFF262628.toInt() else 0xFFF7F7F7.toInt())
                setStroke(maxOf(1, density.roundToInt() / 2), if (dark) 0x1AFFFFFF else 0x99FFFFFF.toInt())
            }
        }
    }

    /** The Gallery capsule follows a horizontal drag with growing resistance and springs back on release. */
    private fun drag(travelled: Float) {
        val limit = 10f * density
        val damped = sign(travelled) * limit * (1f - 1f / (1f + abs(travelled) / (limit * 3f)))
        view.animate().cancel()
        view.translationX = damped
        view.scaleX = 1f + .02f * sqrt(abs(damped) / limit)
        view.scaleY = view.scaleX
    }

    private fun release() {
        view.animate().translationX(0f).scaleX(1f).scaleY(1f).setDuration(380)
            .setInterpolator(PathInterpolator(.3f, 1.35f, .5f, 1f)).start()
    }

    private fun Int?.orZero() = this ?: 0
}
