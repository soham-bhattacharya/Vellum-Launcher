/*
 * Copyright 2026 Vellum Launcher contributors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package app.lawnchair.vellum

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.android.launcher3.R
import kotlin.math.sin

/** The one-time, cinematic reveal shown on the first launch of Vellum. */
class VellumWelcomeView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val buttonBounds = RectF()
    private val tempBounds = RectF()
    private val markPath = Path()
    private var phase = 0f
    private var phaseAnimator: ValueAnimator? = null
    private var dismissed = false

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.vellum_welcome_accessibility)
        setLayerType(LAYER_TYPE_HARDWARE, null)
        alpha = 0f
        scaleX = .975f
        scaleY = .975f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ValueAnimator.areAnimatorsEnabled()) {
            phaseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 5200L
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(680L)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        } else {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
        }
    }

    override fun onDetachedFromWindow() {
        phaseAnimator?.cancel()
        phaseAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            h.toFloat(),
            intArrayOf(Color.rgb(10, 12, 19), Color.rgb(19, 15, 34), Color.rgb(8, 14, 23)),
            floatArrayOf(0f, .56f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, backgroundPaint)

        val pulse = .96f + sin(phase * Math.PI * 2).toFloat() * .035f
        val haloX = w * (.73f + sin(phase * Math.PI * 2).toFloat() * .025f)
        val haloY = h * .19f
        haloPaint.shader = RadialGradient(
            haloX,
            haloY,
            w * .72f * pulse,
            intArrayOf(Color.argb(112, 140, 105, 255), Color.argb(40, 53, 179, 255), Color.TRANSPARENT),
            floatArrayOf(0f, .38f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w, h, haloPaint)

        drawWordmark(canvas, w)
        drawMark(canvas, w * .5f, h * .285f, 1f + (pulse - 1f) * .65f)
        drawCopy(canvas, w, h)
        drawFeaturePills(canvas, w, h)
        drawButton(canvas, w, h)
        drawFooter(canvas, w, h)
    }

    private fun drawWordmark(canvas: Canvas, width: Float) {
        textPaint.apply {
            color = Color.argb(190, 255, 255, 255)
            textSize = 12f * density
            letterSpacing = .38f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("VELLUM", width * .5f, 64f * density, textPaint)
    }

    private fun drawMark(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        val radius = 56f * density * scale
        fillPaint.shader = RadialGradient(
            cx - radius * .28f,
            cy - radius * .32f,
            radius * 1.35f,
            intArrayOf(Color.rgb(176, 148, 255), Color.rgb(103, 85, 232), Color.rgb(30, 31, 62)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, fillPaint)

        strokePaint.apply {
            shader = null
            color = Color.argb(92, 255, 255, 255)
            strokeWidth = density
        }
        canvas.drawCircle(cx, cy, radius + 9f * density, strokePaint)
        strokePaint.color = Color.argb(36, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius + 22f * density, strokePaint)

        markPath.reset()
        markPath.moveTo(cx - 25f * density, cy - 20f * density)
        markPath.lineTo(cx, cy + 25f * density)
        markPath.lineTo(cx + 29f * density, cy - 28f * density)
        strokePaint.apply {
            color = Color.WHITE
            strokeWidth = 8f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(markPath, strokePaint)
    }

    private fun drawCopy(canvas: Canvas, width: Float, height: Float) {
        textPaint.apply {
            color = Color.WHITE
            textSize = if (width < 380f * density) 28f * density else 32f * density
            letterSpacing = -.025f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("A quieter kind of home.", width * .5f, height * .49f, textPaint)

        textPaint.apply {
            color = Color.argb(172, 255, 255, 255)
            textSize = 15f * density
            letterSpacing = .015f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        canvas.drawText("Your apps. Your rhythm. Nothing else.", width * .5f, height * .535f, textPaint)
    }

    private fun drawFeaturePills(canvas: Canvas, width: Float, height: Float) {
        val labels = arrayOf("LIGHT-REACTIVE", "PRIVATE", "PIXEL-FAST")
        textPaint.apply {
            textSize = 9f * density
            letterSpacing = .12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val gap = 7f * density
        val widths = labels.map { textPaint.measureText(it) + 24f * density }
        val total = widths.sum() + gap * (labels.size - 1)
        var left = (width - total) / 2f
        val top = height * .59f
        labels.forEachIndexed { index, label ->
            val pillWidth = widths[index]
            tempBounds.set(left, top, left + pillWidth, top + 31f * density)
            fillPaint.shader = null
            fillPaint.color = Color.argb(28, 255, 255, 255)
            canvas.drawRoundRect(tempBounds, 16f * density, 16f * density, fillPaint)
            strokePaint.apply {
                shader = null
                color = Color.argb(48, 255, 255, 255)
                strokeWidth = .75f * density
            }
            canvas.drawRoundRect(tempBounds, 16f * density, 16f * density, strokePaint)
            textPaint.color = if (index == 0) Color.rgb(201, 188, 255) else Color.argb(185, 255, 255, 255)
            canvas.drawText(label, tempBounds.centerX(), tempBounds.centerY() + 3.3f * density, textPaint)
            left += pillWidth + gap
        }
    }

    private fun drawButton(canvas: Canvas, width: Float, height: Float) {
        val horizontalMargin = 24f * density
        val bottom = height - 78f * density
        buttonBounds.set(horizontalMargin, bottom - 62f * density, width - horizontalMargin, bottom)
        fillPaint.shader = LinearGradient(
            buttonBounds.left,
            buttonBounds.top,
            buttonBounds.right,
            buttonBounds.bottom,
            intArrayOf(Color.rgb(175, 149, 255), Color.rgb(102, 157, 255)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(buttonBounds, 22f * density, 22f * density, fillPaint)
        textPaint.apply {
            color = Color.rgb(17, 15, 28)
            textSize = 16f * density
            letterSpacing = .01f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Enter Vellum  →", buttonBounds.centerX(), buttonBounds.centerY() + 5.5f * density, textPaint)
    }

    private fun drawFooter(canvas: Canvas, width: Float, height: Float) {
        textPaint.apply {
            color = Color.argb(105, 255, 255, 255)
            textSize = 10f * density
            letterSpacing = .08f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("OPEN SOURCE  •  BUILT ON LAWNCHAIR", width * .5f, height - 31f * density, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = buttonBounds.contains(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                isPressed = buttonBounds.contains(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_UP -> {
                val activate = isPressed && buttonBounds.contains(event.x, event.y)
                isPressed = false
                if (activate) performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (dismissed) return true
        dismissed = true
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WELCOME_SEEN, true)
            .apply()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        phaseAnimator?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            (parent as? ViewGroup)?.removeView(this)
        } else {
            animate()
                .alpha(0f)
                .scaleX(1.035f)
                .scaleY(1.035f)
                .translationY(-8f * density)
                .setDuration(420L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { (parent as? ViewGroup)?.removeView(this) }
                .start()
        }
        return true
    }

    companion object {
        private const val PREFERENCES_NAME = "vellum_experience"
        private const val KEY_WELCOME_SEEN = "welcome_bloom_seen"

        fun showIfNeeded(parent: ViewGroup) {
            val preferences = parent.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            if (preferences.getBoolean(KEY_WELCOME_SEEN, false)) return
            parent.addView(
                VellumWelcomeView(parent.context),
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
        }
    }
}
