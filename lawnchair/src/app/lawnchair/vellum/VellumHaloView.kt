/*
 * Copyright 2026 Vellum Launcher contributors
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import app.lawnchair.ui.preferences.PreferenceActivity
import com.android.launcher3.LauncherState

/** A tiny, tactile shortcut into All Apps and the visual anchor of the Vellum home screen. */
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
    private val accent = resolveAccentColor()

    init {
        isClickable = true
        isLongClickable = true
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            pulse()
            context.launcherNullable?.stateManager?.goToState(LauncherState.ALL_APPS, true)
        }
        setOnLongClickListener {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            context.startActivity(Intent(context, PreferenceActivity::class.java))
            true
        }
    }

    fun setHomeVisible(visible: Boolean, animate: Boolean = true) {
        isClickable = visible
        isFocusable = visible
        val targetAlpha = if (visible) 1f else 0f
        val targetScale = if (visible) 1f else .78f
        this.animate().cancel()
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            alpha = targetAlpha
            scaleX = targetScale
            scaleY = targetScale
            return
        }
        this.animate()
            .alpha(targetAlpha)
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(if (visible) 380L else 150L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) * .27f

        surfacePaint.color = ColorUtils.setAlphaComponent(Color.BLACK, if (isPressed) 80 else 52)
        canvas.drawCircle(cx, cy, radius + 9f * density, surfacePaint)

        ringPaint.shader = SweepGradient(
            cx,
            cy,
            intArrayOf(
                ColorUtils.setAlphaComponent(Color.WHITE, 185),
                ColorUtils.setAlphaComponent(accent, 245),
                ColorUtils.setAlphaComponent(Color.WHITE, 90),
                ColorUtils.setAlphaComponent(accent, 245),
                ColorUtils.setAlphaComponent(Color.WHITE, 185),
            ),
            null,
        )
        ringPaint.strokeWidth = if (isPressed) 3f * density else 2f * density
        ringBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.save()
        canvas.rotate(-38f, cx, cy)
        canvas.drawArc(ringBounds, 10f, 312f, false, ringPaint)
        canvas.restore()

        markPaint.shader = null
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

    private fun resolveAccentColor(): Int {
        val typedValue = android.util.TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)) {
            typedValue.data
        } else {
            Color.rgb(137, 108, 255)
        }
    }
}
