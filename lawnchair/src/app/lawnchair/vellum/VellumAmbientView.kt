/*
 * Copyright 2026 Vellum Launcher contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
import androidx.core.graphics.ColorUtils
import com.android.launcher3.Workspace
import kotlin.math.abs
import kotlin.math.max

/**
 * A zero-touch, event-driven light layer that gives Vellum its signature depth.
 *
 * There is no timer and no background work: the view redraws only when the workspace moves,
 * changes size, or enters/leaves the home state. That keeps the effect cheap even on older phones.
 */
class VellumAmbientView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs),
    ViewTreeObserver.OnScrollChangedListener {

    private val density = resources.displayMetrics.density
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val washPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orbitBounds = RectF()
    private val particleSeeds = floatArrayOf(
        .08f, .17f, .82f, .11f, .66f, .24f, .20f, .31f, .91f, .38f,
        .13f, .49f, .74f, .55f, .34f, .64f, .87f, .71f, .18f, .79f,
        .58f, .86f, .94f, .91f,
    )

    private val accent = resolveAccentColor()
    private val accentSoft = ColorUtils.blendARGB(accent, Color.WHITE, .24f)
    private val accentDeep = ColorUtils.blendARGB(accent, Color.rgb(66, 45, 120), .46f)

    private var workspace: Workspace<*>? = null
    private var pagePhase = 0f
    private var haloShader: RadialGradient? = null
    private var washShader: LinearGradient? = null

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
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
        if (abs(newPhase - pagePhase) > .001f) {
            pagePhase = newPhase
            invalidate()
        }
    }

    fun setHomeVisible(visible: Boolean, animate: Boolean = true) {
        val targetAlpha = if (visible) 1f else 0f
        isEnabled = visible
        this.animate().cancel()
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            alpha = targetAlpha
            return
        }
        this.animate()
            .alpha(targetAlpha)
            .setDuration(if (visible) 420L else 180L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShaders()
        updateFromWorkspace()
    }

    private fun rebuildShaders() {
        if (width <= 0 || height <= 0) return
        val centerX = width * .79f
        val centerY = height * .16f
        val radius = max(width, height) * .48f
        haloShader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(
                ColorUtils.setAlphaComponent(accentSoft, 82),
                ColorUtils.setAlphaComponent(accent, 36),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, .34f, 1f),
            Shader.TileMode.CLAMP,
        )
        washShader = LinearGradient(
            0f,
            height.toFloat(),
            width.toFloat(),
            0f,
            intArrayOf(
                ColorUtils.setAlphaComponent(accentDeep, 15),
                Color.TRANSPARENT,
                ColorUtils.setAlphaComponent(accentSoft, 10),
            ),
            floatArrayOf(0f, .54f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        washPaint.shader = washShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), washPaint)

        val shift = (pagePhase - .5f) * width * .22f
        glowPaint.shader = haloShader
        canvas.save()
        canvas.translate(-shift, pagePhase * height * .035f)
        canvas.drawRect(-width.toFloat(), -height * .1f, width * 2f, height * 1.1f, glowPaint)
        canvas.restore()

        val cx = width * .79f - shift
        val cy = height * .16f + pagePhase * height * .035f
        linePaint.shader = null
        linePaint.color = ColorUtils.setAlphaComponent(accentSoft, 34)
        linePaint.strokeWidth = density
        for (index in 0..2) {
            val radius = (46f + index * 22f) * density
            orbitBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(
                orbitBounds,
                205f + pagePhase * 32f + index * 15f,
                86f - index * 9f,
                false,
                linePaint,
            )
        }

        dotPaint.color = ColorUtils.setAlphaComponent(accentSoft, 42)
        particleSeeds.forEachIndexed { index, seed ->
            if (index % 2 == 0) {
                val x = ((seed + pagePhase * .045f) % 1f) * width
                val y = particleSeeds[index + 1] * height
                canvas.drawCircle(x, y, if (index % 4 == 0) 1.15f * density else .7f * density, dotPaint)
            }
        }

        linePaint.color = ColorUtils.setAlphaComponent(accent, 24)
        linePaint.strokeWidth = .75f * density
        canvas.drawLine(16f * density, height * .24f, 16f * density, height * .68f, linePaint)
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
