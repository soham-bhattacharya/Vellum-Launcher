/*
 * Copyright 2026 Vellum Launcher contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.vellum

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.animation.addListener
import androidx.core.graphics.ColorUtils
import androidx.core.view.updateLayoutParams
import app.lawnchair.theme.ThemeProvider
import com.android.launcher3.Workspace
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Vellum's signature light field, drawn behind every interactive home element.
 *
 * The important property of this view is that **workspace scrolling never repaints it**.
 * The content is split into two children: a wash that never moves, and a field that carries the
 * halo, orbit lines and particles. Paging only assigns `translationX`/`translationY` to the field,
 * which the GPU composites without re-rasterising anything. The field is promoted to a hardware
 * layer for the duration of a scroll and released once the workspace settles, so a page swipe
 * costs one texture blit per frame instead of a full-screen gradient blend.
 *
 * There is no timer and no background work anywhere in this class.
 */
class VellumAmbientView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs),
    ViewTreeObserver.OnScrollChangedListener {

    private val palette = Palette(context)
    private val washLayer = WashLayer(context, palette)
    private val fieldLayer = FieldLayer(context, palette)

    private var workspace: Workspace<*>? = null
    private var pagePhase = 0f

    /** Home-state visibility, 0..1, driven by [VellumStateHandler] off the real state transition. */
    private var stateProgress = 1f

    /** User-controlled strength of the whole effect. */
    private var intensity = 1f

    /** Whether the user has the ambient canvas switched on at all. */
    private var enabledByUser = true

    /** Ambient strength contributed by the active context surface, 0..1. */
    private var surfaceIntensity = 1f

    /** Drives the dip-and-return used when the active surface changes. */
    private var crossfade = 1f
    private var crossfadeAnimator: ValueAnimator? = null

    private var scrolling = false
    private val releaseLayerRunnable = Runnable { setScrolling(false) }

    private val themeListener = object : ThemeProvider.ColorSchemeChangeListener {
        override fun onColorSchemeChanged() {
            // ThemeProvider may notify from a background dispatcher.
            post {
                palette.refresh(context)
                washLayer.onPaletteChanged()
                fieldLayer.onPaletteChanged()
            }
        }
    }

    init {
        setWillNotDraw(true)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(washLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(fieldLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        applyAlpha()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ThemeProvider.INSTANCE.get(context).addListener(themeListener)
    }

    override fun onDetachedFromWindow() {
        ThemeProvider.INSTANCE.get(context).removeListener(themeListener)
        removeCallbacks(releaseLayerRunnable)
        super.onDetachedFromWindow()
    }

    fun bindWorkspace(target: Workspace<*>) {
        unbindWorkspace()
        workspace = target
        if (target.viewTreeObserver.isAlive) {
            target.viewTreeObserver.addOnScrollChangedListener(this)
        }
        target.post(::updateFromWorkspace)
    }

    fun unbindWorkspace() {
        workspace?.viewTreeObserver?.takeIf(ViewTreeObserver::isAlive)
            ?.removeOnScrollChangedListener(this)
        workspace = null
    }

    override fun onScrollChanged() = updateFromWorkspace()

    private fun updateFromWorkspace() {
        val current = workspace ?: return
        val lastPage = current.pageCount - 1
        val maxScroll = if (lastPage > 0) current.getScrollForPage(lastPage) else 0
        val newPhase = if (maxScroll == 0) 0f else (current.scrollX / maxScroll.toFloat()).coerceIn(0f, 1f)
        if (abs(newPhase - pagePhase) < .0005f) return
        pagePhase = newPhase

        setScrolling(true)
        removeCallbacks(releaseLayerRunnable)
        postDelayed(releaseLayerRunnable, SCROLL_SETTLE_MS)

        applyParallax()
    }

    /**
     * Moves the field. Deliberately no [invalidate] call: assigning a translation only updates the
     * parent's transform, so nothing is re-recorded or re-rasterised.
     */
    private fun applyParallax() {
        if (width == 0) return
        fieldLayer.translationX = -(pagePhase - .5f) * width * PARALLAX_X
        fieldLayer.translationY = pagePhase * height * PARALLAX_Y
    }

    /**
     * Promotes the field to a hardware layer only while the workspace is actually moving. Holding a
     * full-screen layer permanently would cost several megabytes of GPU memory for no benefit,
     * because at rest nothing invalidates and nothing moves.
     */
    private fun setScrolling(value: Boolean) {
        if (scrolling == value) return
        scrolling = value
        fieldLayer.setLayerType(if (value) LAYER_TYPE_HARDWARE else LAYER_TYPE_NONE, null)
    }

    fun setStateProgress(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped == stateProgress) return
        stateProgress = clamped
        applyAlpha()
    }

    fun setEnabledByUser(enabled: Boolean) {
        if (enabledByUser == enabled) return
        enabledByUser = enabled
        if (enabled) {
            workspace?.let(::bindWorkspace)
        } else {
            setScrolling(false)
            removeCallbacks(releaseLayerRunnable)
        }
        applyAlpha()
    }

    fun setIntensity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (clamped == intensity) return
        intensity = clamped
        applyAlpha()
    }

    /**
     * Adopts the atmosphere of a context surface. Passing null returns to the theme accent.
     *
     * The change is a dip and return rather than a per-frame colour interpolation: the palette is
     * swapped once, at the bottom of the dip, so a surface change costs exactly one repaint instead
     * of one per frame of the transition.
     */
    fun setSurface(accent: Int?, intensity: Float, animate: Boolean = true) {
        val newIntensity = intensity.coerceIn(0f, 1f)
        if (palette.surfaceAccent == accent && surfaceIntensity == newIntensity) return

        crossfadeAnimator?.cancel()
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || visibility != VISIBLE) {
            commitSurface(accent, newIntensity)
            crossfade = 1f
            applyAlpha()
            return
        }

        crossfadeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SURFACE_CROSSFADE_MS
            interpolator = AccelerateDecelerateInterpolator()
            var committed = false
            addUpdateListener {
                val t = it.animatedValue as Float
                if (t < SURFACE_DIP_POINT) {
                    crossfade = 1f - t / SURFACE_DIP_POINT
                } else {
                    if (!committed) {
                        committed = true
                        commitSurface(accent, newIntensity)
                    }
                    crossfade = (t - SURFACE_DIP_POINT) / (1f - SURFACE_DIP_POINT)
                }
                applyAlpha()
            }
            addListener(
                onEnd = {
                    if (!committed) commitSurface(accent, newIntensity)
                    crossfade = 1f
                    applyAlpha()
                    crossfadeAnimator = null
                },
            )
            start()
        }
    }

    private fun commitSurface(accent: Int?, intensity: Float) {
        palette.surfaceAccent = accent
        surfaceIntensity = intensity
        palette.refresh(context)
        washLayer.onPaletteChanged()
        fieldLayer.onPaletteChanged()
    }

    private fun applyAlpha() {
        if (!enabledByUser) {
            visibility = GONE
            return
        }
        val target = stateProgress * intensity * surfaceIntensity * crossfade
        alpha = target
        // Fully transparent views still get their display lists replayed; skip that entirely.
        visibility = if (target <= .001f) INVISIBLE else VISIBLE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resizeFieldLayer(w, h)
        applyParallax()
    }

    /**
     * The field is oversized by exactly the parallax range and offset by negative margins, so that
     * translating it can never expose an empty edge.
     */
    private fun resizeFieldLayer(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val overscanX = (w * PARALLAX_X * .5f).roundToInt() + 1
        val overscanY = (h * PARALLAX_Y).roundToInt() + 1
        fieldLayer.setOverscan(overscanX.toFloat(), overscanY.toFloat())
        fieldLayer.updateLayoutParams<LayoutParams> {
            width = w + overscanX * 2
            height = h + overscanY * 2
            leftMargin = -overscanX
            topMargin = -overscanY
        }
    }

    /** Accent colours, resolved once per theme or surface change rather than once per process. */
    private class Palette(context: Context) {
        var accent: Int = 0
            private set
        var soft: Int = 0
            private set
        var deep: Int = 0
            private set

        /** When a context surface is active, its accent replaces the theme accent. */
        var surfaceAccent: Int? = null

        init {
            refresh(context)
        }

        fun refresh(context: Context) {
            val typedValue = android.util.TypedValue()
            accent = surfaceAccent ?: if (context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
                typedValue.data
            } else {
                FALLBACK_ACCENT
            }
            soft = ColorUtils.blendARGB(accent, Color.WHITE, .24f)
            deep = ColorUtils.blendARGB(accent, Color.rgb(66, 45, 120), .46f)
        }
    }

    /** Static full-screen wash. Never moves, never invalidated by scrolling. */
    private class WashLayer(context: Context, private val palette: Palette) : View(context) {

        private val density = resources.displayMetrics.density
        private val washPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun onPaletteChanged() {
            rebuildShader()
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuildShader()
        }

        private fun rebuildShader() {
            if (width <= 0 || height <= 0) return
            washPaint.shader = LinearGradient(
                0f,
                height.toFloat(),
                width.toFloat(),
                0f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(palette.deep, 15),
                    Color.TRANSPARENT,
                    ColorUtils.setAlphaComponent(palette.soft, 10),
                ),
                floatArrayOf(0f, .54f, 1f),
                Shader.TileMode.CLAMP,
            )
        }

        override fun onDraw(canvas: Canvas) {
            if (width == 0 || height == 0) return
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), washPaint)

            linePaint.color = ColorUtils.setAlphaComponent(palette.accent, 24)
            linePaint.strokeWidth = .75f * density
            canvas.drawLine(16f * density, height * .24f, 16f * density, height * .68f, linePaint)
        }
    }

    /** Halo, orbit lines and particles. Translated during paging; drawn only when the size or theme changes. */
    private class FieldLayer(context: Context, private val palette: Palette) : View(context) {

        private val density = resources.displayMetrics.density
        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val orbitBounds = RectF()

        private var overscanX = 0f
        private var overscanY = 0f

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun setOverscan(x: Float, y: Float) {
            if (overscanX == x && overscanY == y) return
            overscanX = x
            overscanY = y
            rebuildShader()
            invalidate()
        }

        fun onPaletteChanged() {
            rebuildShader()
            invalidate()
        }

        /** Screen width, i.e. this view's width minus the overscan added on both sides. */
        private val contentWidth get() = width - overscanX * 2
        private val contentHeight get() = height - overscanY * 2

        private fun haloCenterX() = overscanX + contentWidth * .79f
        private fun haloCenterY() = overscanY + contentHeight * .16f

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuildShader()
        }

        private fun rebuildShader() {
            if (width <= 0 || height <= 0) return
            glowPaint.shader = RadialGradient(
                haloCenterX(),
                haloCenterY(),
                max(contentWidth, contentHeight) * .48f,
                intArrayOf(
                    ColorUtils.setAlphaComponent(palette.soft, 82),
                    ColorUtils.setAlphaComponent(palette.accent, 36),
                    Color.TRANSPARENT,
                ),
                floatArrayOf(0f, .34f, 1f),
                Shader.TileMode.CLAMP,
            )
        }

        override fun onDraw(canvas: Canvas) {
            if (width == 0 || height == 0) return

            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glowPaint)

            val cx = haloCenterX()
            val cy = haloCenterY()
            linePaint.color = ColorUtils.setAlphaComponent(palette.soft, 34)
            linePaint.strokeWidth = density
            for (index in 0..2) {
                val radius = (46f + index * 22f) * density
                orbitBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
                canvas.drawArc(orbitBounds, 205f + index * 15f, 86f - index * 9f, false, linePaint)
            }

            dotPaint.color = ColorUtils.setAlphaComponent(palette.soft, 42)
            var index = 0
            while (index < PARTICLE_SEEDS.size - 1) {
                val x = overscanX + PARTICLE_SEEDS[index] * contentWidth
                val y = overscanY + PARTICLE_SEEDS[index + 1] * contentHeight
                canvas.drawCircle(x, y, if (index % 4 == 0) 1.15f * density else .7f * density, dotPaint)
                index += 2
            }
        }
    }

    private companion object {
        /** Horizontal parallax as a fraction of screen width, applied across the full page range. */
        const val PARALLAX_X = .22f

        /** Vertical drift as a fraction of screen height. */
        const val PARALLAX_Y = .035f

        /** How long after the last scroll event the hardware layer is released. */
        const val SCROLL_SETTLE_MS = 200L

        /** Length of the dip-and-return played when the active context surface changes. */
        const val SURFACE_CROSSFADE_MS = 620L

        /** Point in that animation at which the palette is swapped. */
        const val SURFACE_DIP_POINT = .34f

        val FALLBACK_ACCENT = Color.rgb(137, 108, 255)

        val PARTICLE_SEEDS = floatArrayOf(
            .08f, .17f, .82f, .11f, .66f, .24f, .20f, .31f, .91f, .38f,
            .13f, .49f, .74f, .55f, .34f, .64f, .87f, .71f, .18f, .79f,
            .58f, .86f, .94f, .91f,
        )
    }
}
