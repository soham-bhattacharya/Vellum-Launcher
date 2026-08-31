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
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.content.edit
import com.android.launcher3.R
import kotlin.math.sin

/**
 * The one-time, cinematic reveal shown on the first launch of Vellum.
 *
 * The breathing motion here animates **view properties, never pixels**. The composition is split
 * into four full-bleed children — a static backdrop, the drifting halo, the pulsing mark, and the
 * static copy — and the animator only writes `scale`/`translationX` onto the two moving layers.
 * Nothing is invalidated per frame, so the reveal does not compete for rasterisation bandwidth with
 * the launcher's cold start happening behind it.
 */
class VellumWelcomeView(context: Context) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val buttonBounds = RectF()

    private val backdrop = BackdropLayer(context)
    private val halo = HaloLayer(context)
    private val mark = MarkLayer(context)
    private val content = ContentLayer(context, buttonBounds)

    private var phaseAnimator: ValueAnimator? = null
    private var dismissed = false

    init {
        isClickable = true
        isFocusable = true
        contentDescription = context.getString(R.string.vellum_welcome_accessibility)
        alpha = 0f
        scaleX = .975f
        scaleY = .975f
        listOf(backdrop, halo, mark, content).forEach {
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        // Pivot each moving layer about the feature it carries, so a scale reads as that feature
        // breathing rather than the whole screen zooming.
        halo.pivotX = w * HALO_CENTER_X
        halo.pivotY = h * HALO_CENTER_Y
        mark.pivotX = w * .5f
        mark.pivotY = h * MARK_CENTER_Y

        val horizontalMargin = 24f * density
        val bottom = h - 78f * density
        buttonBounds.set(horizontalMargin, bottom - 62f * density, w - horizontalMargin, bottom)
        content.invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            return
        }

        // Only the two moving layers are promoted; the backdrop and copy are never re-rendered,
        // so a layer would cost memory for nothing.
        halo.setLayerType(LAYER_TYPE_HARDWARE, null)
        mark.setLayerType(LAYER_TYPE_HARDWARE, null)

        phaseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 5200L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                val phase = it.animatedValue as Float
                val wave = sin(phase * Math.PI * 2).toFloat()
                val pulse = .96f + wave * .035f
                halo.scaleX = pulse
                halo.scaleY = pulse
                halo.translationX = wave * width * .025f
                val markScale = 1f + (pulse - 1f) * .65f
                mark.scaleX = markScale
                mark.scaleY = markScale
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
    }

    override fun onDetachedFromWindow() {
        phaseAnimator?.cancel()
        phaseAnimator = null
        halo.setLayerType(LAYER_TYPE_NONE, null)
        mark.setLayerType(LAYER_TYPE_NONE, null)
        super.onDetachedFromWindow()
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
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_WELCOME_SEEN, true)
        }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        phaseAnimator?.cancel()
        phaseAnimator = null
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

    /** Static background wash. Drawn once. */
    private class BackdropLayer(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            paint.shader = LinearGradient(
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
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    /** The coloured halo. Drawn once, then only scaled and translated. */
    private class HaloLayer(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            paint.shader = RadialGradient(
                w * HALO_CENTER_X,
                h * HALO_CENTER_Y,
                w * .72f,
                intArrayOf(Color.argb(112, 140, 105, 255), Color.argb(40, 53, 179, 255), Color.TRANSPARENT),
                floatArrayOf(0f, .38f, 1f),
                Shader.TileMode.CLAMP,
            )
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }
    }

    /** The circular V mark. Drawn once, then only scaled. */
    private class MarkLayer(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val markPath = Path()

        override fun onDraw(canvas: Canvas) {
            if (width == 0 || height == 0) return
            val cx = width * .5f
            val cy = height * MARK_CENTER_Y
            val radius = 56f * density

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
    }

    /** Wordmark, copy, feature pills, button and footer. Entirely static. */
    private class ContentLayer(context: Context, private val buttonBounds: RectF) : View(context) {
        private val density = resources.displayMetrics.density
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        private val tempBounds = RectF()

        override fun onDraw(canvas: Canvas) {
            if (width == 0 || height == 0) return
            val w = width.toFloat()
            val h = height.toFloat()
            drawWordmark(canvas, w)
            drawCopy(canvas, w, h)
            drawFeaturePills(canvas, w, h)
            drawButton(canvas)
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

        private fun drawButton(canvas: Canvas) {
            if (buttonBounds.isEmpty) return
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
            canvas.drawText(
                "Enter Vellum  →",
                buttonBounds.centerX(),
                buttonBounds.centerY() + 5.5f * density,
                textPaint,
            )
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
    }

    companion object {
        private const val PREFERENCES_NAME = "vellum_experience"
        private const val KEY_WELCOME_SEEN = "welcome_bloom_seen"

        private const val HALO_CENTER_X = .73f
        private const val HALO_CENTER_Y = .19f
        private const val MARK_CENTER_Y = .285f

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
