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
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.animation.addListener
import androidx.core.view.updateLayoutParams
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.vellum.backdrop.Backdrop
import app.lawnchair.vellum.backdrop.BackdropGeometry
import app.lawnchair.vellum.backdrop.BackdropPalette
import app.lawnchair.vellum.backdrop.BackdropStyle
import app.lawnchair.vellum.surface.VellumSurface
import com.android.launcher3.Workspace
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Vellum's ambient canvas, drawn behind every interactive home element.
 *
 * The important property of this view is that **workspace scrolling never repaints it**.
 * The content is split into two children: a wash that never moves, and a field that carries the
 * subject of whichever [BackdropStyle] is active. Paging only assigns `translationX`/`translationY`
 * to the field, which the GPU composites without re-rasterising anything. The field is promoted to
 * a hardware layer for the duration of a scroll and released once the workspace settles, so a page
 * swipe costs one texture blit per frame instead of a full-screen gradient blend.
 *
 * There is no timer and no background work anywhere in this class.
 */
class VellumAmbientView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs),
    ViewTreeObserver.OnScrollChangedListener {

    private val washLayer = BackdropLayer(context, isField = false)
    private val fieldLayer = BackdropLayer(context, isField = true)

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

    /** The surface currently being painted, or null when no context surface is active. */
    private var activeSurface: VellumSurface? = null

    /** Drives the dip-and-return used when the active surface changes. */
    private var crossfade = 1f
    private var crossfadeAnimator: ValueAnimator? = null

    private var scrolling = false
    private val releaseLayerRunnable = Runnable { setScrolling(false) }

    private val themeListener = object : ThemeProvider.ColorSchemeChangeListener {
        override fun onColorSchemeChanged() {
            // ThemeProvider may notify from a background dispatcher.
            post { applyAppearance() }
        }
    }

    init {
        setWillNotDraw(true)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(washLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(fieldLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        applyAppearance()
        applyAlpha()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ThemeProvider.INSTANCE.get(context).addListener(themeListener)
    }

    override fun onDetachedFromWindow() {
        ThemeProvider.INSTANCE.get(context).removeListener(themeListener)
        removeCallbacks(releaseLayerRunnable)
        crossfadeAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    fun bindWorkspace(target: Workspace<*>) {
        if (workspace === target && target.viewTreeObserver.isAlive) return
        unbindWorkspace()
        workspace = target
        if (target.viewTreeObserver.isAlive) {
            target.viewTreeObserver.addOnScrollChangedListener(this)
        }
        // When a grid reload swaps the Workspace instance but reuses the activity,
        // the old ViewTreeObserver is dead and scroll events would silently stop.
        // Attaching a layout listener as fallback ensures we reattach once the
        // new Workspace is attached.
        if (!target.viewTreeObserver.isAlive) {
            target.addOnAttachStateChangeListener(
                object : OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        target.removeOnAttachStateChangeListener(this)
                        bindWorkspace(target)
                    }

                    override fun onViewDetachedFromWindow(v: View) = Unit
                },
            )
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
        val scale = fieldLayer.parallaxScale
        fieldLayer.translationX = -(pagePhase - .5f) * width * PARALLAX_X * scale
        fieldLayer.translationY = pagePhase * height * PARALLAX_Y * scale
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
     * Adopts the atmosphere of a context surface. Passing null returns to the theme accent and the
     * default design.
     *
     * The change is a dip and return rather than a per-frame colour interpolation: the whole
     * appearance is swapped once, at the bottom of the dip, so a surface change costs exactly one
     * repaint instead of one per frame of the transition. That matters more here than it did when
     * only a colour changed, because a backdrop switch also rebuilds every shader and path.
     */
    fun setSurface(surface: VellumSurface?, animate: Boolean = true) {
        val newIntensity = surface?.ambientIntensity?.coerceIn(0f, 1f) ?: 1f
        if (sameAppearance(activeSurface, surface) && surfaceIntensity == newIntensity) return

        crossfadeAnimator?.cancel()
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || visibility != VISIBLE) {
            commitSurface(surface, newIntensity)
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
                        commitSurface(surface, newIntensity)
                    }
                    crossfade = (t - SURFACE_DIP_POINT) / (1f - SURFACE_DIP_POINT)
                }
                applyAlpha()
            }
            addListener(
                onEnd = {
                    if (!committed) commitSurface(surface, newIntensity)
                    crossfade = 1f
                    applyAlpha()
                    crossfadeAnimator = null
                },
            )
            start()
        }
    }

    /** Whether two surfaces would paint identically, ignoring anything the canvas does not use. */
    private fun sameAppearance(a: VellumSurface?, b: VellumSurface?): Boolean = when {
        a == null || b == null -> a == null && b == null

        else ->
            a.accent == b.accent &&
                a.accentSecondary == b.accentSecondary &&
                a.backdropId == b.backdropId
    }

    private fun commitSurface(surface: VellumSurface?, intensity: Float) {
        activeSurface = surface
        surfaceIntensity = intensity
        applyAppearance()
    }

    /** Pushes the current style and palette into both layers, rebuilding their shaders once. */
    private fun applyAppearance() {
        val surface = activeSurface
        val style = surface?.backdrop ?: BackdropStyle.Default
        val palette = surface?.palette() ?: BackdropPalette.of(themeAccent(), null)
        washLayer.setAppearance(style, palette)
        fieldLayer.setAppearance(style, palette)
        applyParallax()
    }

    private fun themeAccent(): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
            typedValue.data
        } else {
            FALLBACK_ACCENT
        }
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
     *
     * The range is computed from the largest parallax scale any design asks for, not from the
     * current one, so that switching to a design that drifts further never has to relayout.
     */
    private fun resizeFieldLayer(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val maxScale = BackdropStyle.maxParallaxScale
        val overscanX = (w * PARALLAX_X * maxScale * .5f).roundToInt() + 1
        val overscanY = (h * PARALLAX_Y * maxScale).roundToInt() + 1
        fieldLayer.setOverscan(overscanX.toFloat(), overscanY.toFloat())
        fieldLayer.updateLayoutParams<LayoutParams> {
            width = w + overscanX * 2
            height = h + overscanY * 2
            leftMargin = -overscanX
            topMargin = -overscanY
        }
    }

    /**
     * One half of a backdrop.
     *
     * The two halves hold separate [Backdrop] instances of the same style because a backdrop caches
     * shaders sized to the view it draws into, and the field is deliberately larger than the wash.
     */
    private class BackdropLayer(context: Context, private val isField: Boolean) : View(context) {

        private var backdrop: Backdrop = BackdropStyle.Default.create()
        private var style: BackdropStyle = BackdropStyle.Default
        private var palette: BackdropPalette = BackdropPalette.Fallback

        private var overscanX = 0f
        private var overscanY = 0f

        val parallaxScale: Float get() = style.parallaxScale

        init {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun setAppearance(style: BackdropStyle, palette: BackdropPalette) {
            val styleChanged = style != this.style
            if (!styleChanged && palette == this.palette) return
            this.style = style
            this.palette = palette
            if (styleChanged) backdrop = style.create()
            reconfigure()
            invalidate()
        }

        fun setOverscan(x: Float, y: Float) {
            if (overscanX == x && overscanY == y) return
            overscanX = x
            overscanY = y
            reconfigure()
            invalidate()
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            reconfigure()
        }

        private fun reconfigure() {
            if (width <= 0 || height <= 0) return
            backdrop.configure(
                BackdropGeometry(
                    layerWidth = width.toFloat(),
                    layerHeight = height.toFloat(),
                    contentLeft = overscanX,
                    contentTop = overscanY,
                    contentWidth = width - overscanX * 2f,
                    contentHeight = height - overscanY * 2f,
                    density = resources.displayMetrics.density,
                ),
                palette,
            )
        }

        override fun onDraw(canvas: Canvas) {
            if (width == 0 || height == 0) return
            if (isField) backdrop.drawField(canvas) else backdrop.drawWash(canvas)
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

        /** Point in that animation at which the appearance is swapped. */
        const val SURFACE_DIP_POINT = .34f

        val FALLBACK_ACCENT = Color.rgb(137, 108, 255)
    }
}
