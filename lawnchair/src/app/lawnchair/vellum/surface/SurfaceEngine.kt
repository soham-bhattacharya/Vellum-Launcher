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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.lawnchair.preferences2.PreferenceManager2
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Decides which [VellumSurface] is currently active, and keeps that decision current.
 *
 * There is no polling and no periodic wakeup. The engine computes exactly when the light next has
 * to change — the moment the active surface begins leaning toward its successor, or the moment it
 * ends — and schedules a single callback for that, then reschedules. A surface therefore costs two
 * wake-ups for its entire window rather than one per minute. It also listens for the clock being
 * changed out from under it, and re-evaluates whenever the launcher comes back to the foreground,
 * which covers both a device that slept through a boundary and the drift that accumulated while
 * nobody was looking.
 */
class SurfaceEngine(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val preferenceManager2 = PreferenceManager2.getInstance(context)
    private val handler = Handler(Looper.getMainLooper())

    private val _moment = MutableStateFlow<SurfaceMoment?>(null)

    /** The active surface together with how far its light has drifted toward the next one. */
    val moment: StateFlow<SurfaceMoment?> = _moment.asStateFlow()

    /** The surface in effect now, for callers that do not care about the drift. */
    val activeSurface: VellumSurface? get() = _moment.value?.surface

    private var surfaceSet = VellumSurfaceSet()
    private var featureEnabled = false

    /**
     * A surface the user picked by hand. It holds until the next natural boundary, so an override
     * never silently becomes permanent.
     */
    private var manualOverrideId: String? = null

    /**
     * Fires either when the active surface starts leaning toward the next one, or when it ends.
     *
     * The override is only cleared in the second case: a surface beginning to lean is not the day
     * moving on, and cancelling somebody's pinned surface halfway through it would be baffling.
     */
    private val nextChangeRunnable = Runnable {
        if (scheduledSurface()?.id != surfaceIdAtLastSchedule) {
            manualOverrideId = null
        }
        reevaluate()
    }

    /** Which surface was scheduled when the pending callback was set, to tell the two cases apart. */
    private var surfaceIdAtLastSchedule: String? = null

    private val clockChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = reevaluate()
    }

    private var started = false

    fun start() {
        if (started) return
        started = true
        context.registerReceiver(
            clockChangeReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            },
        )
        combine(
            preferenceManager2.vellumSurfacesEnabled.get().distinctUntilChanged(),
            preferenceManager2.vellumSurfaceSet.get().distinctUntilChanged(),
        ) { enabled, set -> enabled to set }
            .onEach { (enabled, set) ->
                featureEnabled = enabled
                surfaceSet = set
                reevaluate()
            }
            .launchIn(scope)
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(nextChangeRunnable)
        runCatching { context.unregisterReceiver(clockChangeReceiver) }
        manualOverrideId = null
        surfaceIdAtLastSchedule = null
    }

    /** Called when the launcher resumes, to catch boundaries crossed while the device slept. */
    fun refresh() {
        if (started) reevaluate()
    }

    val surfaces: List<VellumSurface> get() = surfaceSet.enabledSurfaces()

    /** True when the active surface is a hand-picked one rather than the scheduled one. */
    val isOverridden: Boolean get() = manualOverrideId != null

    /**
     * Pins [id] as the active surface until the next scheduled boundary. Passing null, or the
     * surface that is already scheduled for now, returns to automatic behaviour.
     */
    fun setManualOverride(id: String?) {
        manualOverrideId = if (id == scheduledSurface()?.id) null else id
        reevaluate()
    }

    /** Moves to the next enabled surface in order, wrapping. Used by the gesture and the panel. */
    fun cycleToNextSurface() {
        val candidates = surfaceSet.enabledSurfaces()
        if (candidates.size < 2) return
        val currentIndex = candidates.indexOfFirst { it.id == activeSurface?.id }
        val next = candidates[(currentIndex + 1).mod(candidates.size)]
        setManualOverride(next.id)
    }

    private fun minuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    private fun scheduledSurface(): VellumSurface? = surfaceSet.surfaceAt(minuteOfDay())

    private fun reevaluate() {
        handler.removeCallbacks(nextChangeRunnable)
        if (!featureEnabled) {
            _moment.value = null
            surfaceIdAtLastSchedule = null
            return
        }

        val now = minuteOfDay()
        val scheduled = scheduledSurface()
        val pinned = surfaceSet.byId(manualOverrideId)?.takeIf { it.enabled }
        val resolved = pinned ?: scheduled
        _moment.value = resolved?.let {
            SurfaceMoment.at(surfaceSet, now, it, pinned = pinned != null)
        }

        // Schedule against the *scheduled* surface, not the overridden one, so an override expires
        // exactly when the day would have moved on anyway.
        val boundary = scheduled ?: run {
            surfaceIdAtLastSchedule = null
            return
        }
        surfaceIdAtLastSchedule = boundary.id
        val minutesLeft = SurfaceMoment.minutesUntilNextChange(boundary, now)
        // Land just after the turn of the minute so LocalTime has actually rolled over.
        val delayMillis = minutesLeft * 60_000L - LocalTime.now().second * 1000L + 1_000L
        handler.postAtTime(
            nextChangeRunnable,
            SystemClock.uptimeMillis() + delayMillis.coerceAtLeast(1_000L),
        )
    }
}
