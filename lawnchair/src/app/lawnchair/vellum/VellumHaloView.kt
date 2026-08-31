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
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import app.lawnchair.launcherNullable
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.ui.preferences.PreferenceActivity
import com.android.launcher3.LauncherState

/**
 * Vellum's optional shortcut into All Apps.
 *
 * This is an overlay above the workspace, so it consumes touches over the home screen cell it sits
 * on. That makes it opt-in (see `vellumHaloEnabled`) and it is fully removed from the hierarchy's
 * touch handling — not merely faded — whenever it is disabled or the launcher leaves the home state.
 */
class VellumHaloView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val ringBounds = RectF()
    private val markPath = Path()

    private var accent = resolveAccentColor()

    /**
     * Accent contributed by the active context surface, or null to follow the theme.
     *
     * The Halo is the way into the surface panel, so it reading as a generic theme-coloured button
     * while the home screen behind it is unmistakably Evening was the one place the feature did not
     * hang together.
     */
    private var surfaceAccent: Int? = null

    /** Home-state visibility, 0..1, driven by [VellumStateHandler]. */
    private var stateProgress = 1f

    /** Whether the user has turned the Halo on at all. */
    private var enabledByUser = false

    /** Returns true when it handled the tap; false falls back to opening All Apps. */
    private var onHaloClick: (() -> Boolean)? = null

    private val themeListener = object : ThemeProvider.ColorSchemeChangeListener {
        override fun onColorSchemeChanged() {
            // ThemeProvider may notify from a background dispatcher.
            post {
                accent = resolveAccentColor()
                rebuildShader()
                invalidate()
            }
        }
    }

    init {
        isClickable = true
        isLongClickable = true
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            pulse()
            // Prefer the surface panel when context surfaces are on; otherwise the Halo would just
            // duplicate the swipe-up to All Apps, which is not worth a home screen cell.
            if (onHaloClick?.invoke() != true) {
                context.launcherNullable?.stateManager?.goToState(LauncherState.ALL_APPS, true)
            }
        }
        setOnLongClickListener {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            context.startActivity(Intent(context, PreferenceActivity::class.java))
            true
        }
        applyVisibility()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ThemeProvider.INSTANCE.get(context).addListener(themeListener)
    }

    override fun onDetachedFromWindow() {
        ThemeProvider.INSTANCE.get(context).removeListener(themeListener)
        super.onDetachedFromWindow()
    }

    /** Adopts the active surface's colour. Passing null returns to the theme accent. */
    fun setSurfaceAccent(value: Int?) {
        if (surfaceAccent == value) return
        surfaceAccent = value
        accent = resolveAccentColor()
        rebuildShader()
        invalidate()
    }

    fun setOnHaloClick(action: (() -> Boolean)?) {
        onHaloClick = action
    }

    fun setEnabledByUser(enabled: Boolean) {
        if (enabledByUser == enabled) return
        enabledByUser = enabled
        applyVisibility()
    }

    fun setStateProgress(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        if (clamped == stateProgress) return
        stateProgress = clamped
        applyVisibility()
    }

    private fun applyVisibility() {
        if (!enabledByUser) {
            // GONE, not INVISIBLE: an invisible view still occupies its slot for hit testing
            // purposes in some traversals, and we want zero footprint when it is switched off.
            visibility = GONE
            isClickable = false
            isFocusable = false
            return
        }
        visibility = if (stateProgress <= .001f) INVISIBLE else VISIBLE
        alpha = stateProgress
        // Scale down as it leaves so it reads as receding rather than dissolving.
        val scale = .78f + .22f * stateProgress
        scaleX = scale
        scaleY = scale
        val interactive = stateProgress > .95f
        isClickable = interactive
        isFocusable = interactive
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShader()
    }

    /**
     * Built here rather than in [onDraw]: allocating a shader per frame is the classic way to turn
     * a cheap view into a source of GC pressure the moment it starts animating.
     */
    private fun rebuildShader() {
        if (width <= 0 || height <= 0) return
        ringPaint.shader = SweepGradient(
            width / 2f,
            height / 2f,
            intArrayOf(
                ColorUtils.setAlphaComponent(Color.WHITE, 185),
                ColorUtils.setAlphaComponent(accent, 245),
                ColorUtils.setAlphaComponent(Color.WHITE, 90),
                ColorUtils.setAlphaComponent(accent, 245),
                ColorUtils.setAlphaComponent(Color.WHITE, 185),
            ),
            null,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * .30f

        surfacePaint.color = ColorUtils.setAlphaComponent(Color.BLACK, if (isPressed) 80 else 52)
        canvas.drawCircle(cx, cy, radius + 7f * density, surfacePaint)

        ringPaint.strokeWidth = if (isPressed) 3f * density else 2f * density
        ringBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.save()
        canvas.rotate(-38f, cx, cy)
        canvas.drawArc(ringBounds, 10f, 312f, false, ringPaint)
        canvas.restore()

        markPaint.color = Color.WHITE
        markPaint.strokeWidth = 2.35f * density
        markPath.reset()
        markPath.moveTo(cx - 6.5f * density, cy - 5f * density)
        markPath.lineTo(cx, cy + 6f * density)
        markPath.lineTo(cx + 7.5f * density, cy - 7f * density)
        canvas.drawPath(markPath, markPaint)
    }

    private fun pulse() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        animate().cancel()
        scaleX = .88f
        scaleY = .88f
        animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(320L)
            .setInterpolator(DecelerateInterpolator(1.8f))
            .start()
    }

    /** The surface colour when one is active, otherwise the live Material You accent. */
    private fun resolveAccentColor(): Int {
        surfaceAccent?.let { return it }
        val typedValue = android.util.TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
            typedValue.data
        } else {
            Color.rgb(137, 108, 255)
        }
    }
}
