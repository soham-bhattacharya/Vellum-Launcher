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

package app.lawnchair.vellum.surface

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.launcher
import app.lawnchair.theme.color.tokens.ColorTokens
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.VellumSurfaceApps
import com.android.app.animation.Interpolators
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Insettable
import com.android.launcher3.R
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The surface panel: a bottom sheet showing the apps for the current context surface, plus a row of
 * chips for moving between surfaces by hand.
 *
 * This is the one place in Vellum where surfaces are visible as a list rather than as atmosphere,
 * so it doubles as the feature's discovery surface: opening it explains what surfaces are simply by
 * showing them.
 */
class SurfacePanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr),
    Insettable {

    private val launcher = context.launcher
    private val density = resources.displayMetrics.density

    private lateinit var sheet: LinearLayout
    private lateinit var grabber: View
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var chipRow: LinearLayout
    private lateinit var appGrid: GridLayout
    private lateinit var emptyState: TextView

    private var bottomInset = 0

    override fun onFinishInflate() {
        super.onFinishInflate()
        sheet = findViewById(R.id.surface_sheet)
        grabber = findViewById(R.id.surface_grabber)
        title = findViewById(R.id.surface_title)
        subtitle = findViewById(R.id.surface_subtitle)
        chipRow = findViewById(R.id.surface_chips)
        appGrid = findViewById(R.id.surface_app_grid)
        emptyState = findViewById(R.id.surface_empty)

        setBackgroundColor(ColorUtils.setAlphaComponent(Color.BLACK, SCRIM_ALPHA))
        isClickable = true
        setOnClickListener { close(true) }
        // Without this the sheet's own taps fall through to the scrim and dismiss the panel.
        sheet.isClickable = true
        contentDescription = context.getString(R.string.vellum_surface_panel_accessibility)
    }

    override fun setInsets(insets: android.graphics.Rect) {
        bottomInset = insets.bottom
        sheet.setPadding(
            sheet.paddingStart,
            sheet.paddingTop,
            sheet.paddingEnd,
            (28 * density).toInt() + bottomInset,
        )
    }

    private fun styleChrome(accent: Int) {
        val surface = ColorTokens.ColorBackgroundFloating.resolveColor(context)
        sheet.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = FloatArray(8) { index -> if (index < 4) 28f * density else 0f }
            colors = intArrayOf(
                ColorUtils.blendARGB(surface, accent, .12f),
                surface,
            )
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
        }
        grabber.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 2f * density
            setColor(ColorUtils.setAlphaComponent(ColorTokens.TextColorSecondary.resolveColor(context), 80))
        }
        title.setTextColor(ColorTokens.TextColorPrimary.resolveColor(context))
        subtitle.setTextColor(ColorTokens.TextColorSecondary.resolveColor(context))
        emptyState.setTextColor(ColorTokens.TextColorSecondary.resolveColor(context))
    }

    private var boundEngine: SurfaceEngine? = null
    private var collectJob: Job? = null

    /**
     * Follows the engine for as long as the panel is open, so tapping a chip re-renders the sheet
     * instead of leaving it showing the surface that was active when it opened.
     */
    fun observe(engine: SurfaceEngine) {
        boundEngine = engine
        collectJob?.cancel()
        collectJob = launcher.lifecycleScope.launch {
            engine.activeSurface.collect { surface ->
                if (surface == null) close(true) else bind(engine, surface)
            }
        }
    }

    override fun onDetachedFromWindow() {
        collectJob?.cancel()
        collectJob = null
        boundEngine = null
        super.onDetachedFromWindow()
    }

    /** Renders [surface] and the chips for every enabled surface. */
    fun bind(engine: SurfaceEngine, surface: VellumSurface) {
        styleChrome(surface.accent)
        title.text = surface.label(context)
        subtitle.text = buildSubtitle(engine, surface)
        bindChips(engine, surface)
        bindApps(surface)
    }

    private fun buildSubtitle(engine: SurfaceEngine, surface: VellumSurface): String {
        val window = "${formatMinuteOfDay(context, surface.startMinute)} – ${formatMinuteOfDay(context, surface.endMinute)}"
        return if (engine.isOverridden) {
            context.getString(R.string.vellum_surface_pinned, formatMinuteOfDay(context, surface.endMinute))
        } else {
            "${context.getString(R.string.vellum_surface_scheduled)} · $window"
        }
    }

    private fun bindChips(engine: SurfaceEngine, active: VellumSurface) {
        chipRow.removeAllViews()
        engine.surfaces.forEach { candidate ->
            val selected = candidate.id == active.id
            val chip = TextView(context).apply {
                text = candidate.label(context)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding((16 * density).toInt(), (9 * density).toInt(), (16 * density).toInt(), (9 * density).toInt())
                setTextColor(
                    if (selected) {
                        ColorTokens.TextColorPrimaryInverse.resolveColor(context)
                    } else {
                        ColorTokens.TextColorSecondary.resolveColor(context)
                    },
                )
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 18f * density
                    if (selected) {
                        setColor(candidate.accent)
                    } else {
                        setColor(Color.TRANSPARENT)
                        setStroke((1 * density).toInt(), ColorUtils.setAlphaComponent(candidate.accent, 110))
                    }
                }
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    engine.setManualOverride(candidate.id)
                }
            }
            chipRow.addView(
                chip,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = (8 * density).toInt() },
            )
        }
    }

    private fun bindApps(surface: VellumSurface) {
        appGrid.removeAllViews()
        val store = launcher.appsView?.appsStore
        val resolved = if (store == null) {
            emptyList()
        } else {
            surface.apps.mapNotNull { key: ComponentKey -> store.getApp(key) }
        }

        emptyState.isVisible = resolved.isEmpty()
        appGrid.isVisible = resolved.isNotEmpty()
        if (resolved.isEmpty()) {
            // Make the empty state the fix for itself rather than only a description of the problem.
            emptyState.isClickable = true
            emptyState.setOnClickListener {
                close(true)
                context.startActivity(
                    PreferenceActivity.createIntent(context, VellumSurfaceApps(surface.id)),
                )
            }
            return
        }

        val columns = appGrid.columnCount
        val inflater = LayoutInflater.from(context)
        resolved.forEachIndexed { index, info: AppInfo ->
            val icon = inflater.inflate(R.layout.all_apps_icon, appGrid, false) as BubbleTextView
            icon.applyFromApplicationInfo(info)
            icon.setOnClickListener {
                close(true)
                launcher.itemOnClickListener.onClick(it)
            }
            icon.setOnLongClickListener(launcher.allAppsItemLongClickListener)
            appGrid.addView(
                icon,
                GridLayout.LayoutParams(
                    GridLayout.spec(index / columns),
                    GridLayout.spec(index % columns, 1f),
                ).apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    topMargin = (6 * density).toInt()
                    bottomMargin = (6 * density).toInt()
                },
            )
        }
    }

    private fun animateOpen() {
        mIsOpen = true
        alpha = 0f
        sheet.translationY = sheet.height.toFloat().takeIf { it > 0f } ?: (320 * density)
        animate().alpha(1f).setDuration(OPEN_DURATION).setInterpolator(Interpolators.LINEAR).start()
        sheet.animate()
            .translationY(0f)
            .setDuration(OPEN_DURATION)
            .setInterpolator(Interpolators.EMPHASIZED_DECELERATE)
            .start()
    }

    override fun handleClose(animate: Boolean) {
        if (!mIsOpen) return
        if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
            mIsOpen = false
            removeFromParent()
            return
        }
        this.animate().alpha(0f).setDuration(CLOSE_DURATION).start()
        sheet.animate()
            .translationY(sheet.height.toFloat())
            .setDuration(CLOSE_DURATION)
            .setInterpolator(Interpolators.EMPHASIZED_ACCELERATE)
            .setListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        mIsOpen = false
                        removeFromParent()
                    }
                },
            )
            .start()
    }

    private fun removeFromParent() {
        (parent as? ViewGroup)?.removeView(this)
    }

    override fun isOfType(type: Int): Boolean = type and TYPE_VELLUM_SURFACE_PANEL != 0

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean = false

    companion object {
        private const val SCRIM_ALPHA = 130
        private const val OPEN_DURATION = 340L
        private const val CLOSE_DURATION = 220L

        /** Shows the panel for the currently active surface, if surfaces are on. */
        fun show(launcher: LawnchairLauncher, engine: SurfaceEngine): SurfacePanel? {
            val surface = engine.activeSurface.value ?: return null
            getOpenView<SurfacePanel>(launcher, TYPE_VELLUM_SURFACE_PANEL)?.close(false)
            val panel = LayoutInflater.from(launcher)
                .inflate(R.layout.vellum_surface_panel, launcher.dragLayer, false) as SurfacePanel
            launcher.dragLayer.addView(panel)
            panel.setInsets(launcher.dragLayer.insets)
            panel.bind(engine, surface)
            panel.observe(engine)
            panel.post { panel.animateOpen() }
            return panel
        }
    }
}
