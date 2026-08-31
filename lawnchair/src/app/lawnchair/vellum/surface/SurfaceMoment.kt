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

import androidx.core.graphics.ColorUtils
import app.lawnchair.vellum.backdrop.BackdropPalette
import app.lawnchair.vellum.backdrop.VellumAtmosphere
import kotlin.math.ceil

/**
 * The state of the day at one instant: which surface is in effect, and how far the light has
 * already leaned toward whatever comes next.
 *
 * Without this, a day of four surfaces is four flat colours and three hard cuts. Each surface holds
 * its own colour for most of its window — so Morning still reads as Morning — and then eases toward
 * its successor over the last stretch, which means the boundary itself arrives with the colours
 * already matched and only the background design left to change.
 *
 * The arithmetic here is pure and has no notion of the current time; the engine supplies the minute.
 * That is what makes it testable, and it is why nothing in this file needs a clock or a timer.
 */
data class SurfaceMoment(
    /** The surface in effect. Its name and app list are used as-is; only its light drifts. */
    val surface: VellumSurface,
    /** The surface being leaned toward, or null while [surface] still holds its own colour. */
    val next: VellumSurface?,
    /** How far the lean has gone, 0..1. */
    val blend: Float,
) {

    /** The light at this instant, with [surface] and [next] mixed by [blend]. */
    fun atmosphere(): VellumAtmosphere {
        val target = next
        val from = surface.palette()
        if (target == null || blend <= 0f) {
            return VellumAtmosphere(from, surface.backdrop, surface.ambientIntensity)
        }
        val to = target.palette()
        return VellumAtmosphere(
            palette = BackdropPalette.of(
                ColorUtils.blendARGB(from.accent, to.accent, blend),
                ColorUtils.blendARGB(from.secondary, to.secondary, blend),
            ),
            // The design itself never cross-fades. Two backdrops are different compositions, not
            // two values of one, so there is no meaningful halfway point between them; the swap
            // happens at the boundary, by which time the colours have already met.
            style = surface.backdrop,
            intensity = surface.ambientIntensity + (target.ambientIntensity - surface.ambientIntensity) * blend,
        )
    }

    companion object {

        /**
         * The proportion of a surface's window during which it keeps its own colour exactly.
         *
         * Drifting across the whole window would leave every surface a moving average of its
         * neighbours and none of them recognisable. Holding for most of it and easing late keeps
         * each part of the day identifiable while removing the hard cut between them.
         */
        const val HOLD_FRACTION = .72f

        /**
         * Resolves the moment at [minuteOfDay].
         *
         * A [pinned] surface never drifts: the user asked for that specific atmosphere, and sliding
         * it toward the next one would quietly undo the request.
         */
        fun at(
            set: VellumSurfaceSet,
            minuteOfDay: Int,
            surface: VellumSurface,
            pinned: Boolean,
        ): SurfaceMoment {
            if (pinned) return SurfaceMoment(surface, null, 0f)

            val phase = surface.phaseAt(minuteOfDay)
            if (phase < HOLD_FRACTION) return SurfaceMoment(surface, null, 0f)

            val next = set.surfaceAfter(surface) ?: return SurfaceMoment(surface, null, 0f)
            val raw = ((phase - HOLD_FRACTION) / (1f - HOLD_FRACTION)).coerceIn(0f, 1f)
            return SurfaceMoment(surface, next, smoothStep(raw))
        }

        /**
         * Minutes until the light next needs recomputing: either the end of the hold, or the end of
         * the surface, whichever comes first. Always at least 1.
         *
         * This is what keeps the engine to a fixed, tiny number of wake-ups. A surface costs two
         * scheduled callbacks for its whole window — one when it begins to lean, one when it ends —
         * rather than one per minute of drift. Between those, the value is recomputed whenever the
         * launcher resumes, which is the only time anybody can see it.
         */
        fun minutesUntilNextChange(surface: VellumSurface, minuteOfDay: Int): Int {
            val untilEnd = surface.minutesUntilEnd(minuteOfDay)
            val holdEndsAt = ceil(surface.lengthMinutes * HOLD_FRACTION).toInt()
            val untilHoldEnds = holdEndsAt - surface.elapsedAt(minuteOfDay)
            return if (untilHoldEnds in 1 until untilEnd) untilHoldEnds else untilEnd
        }

        /** Smoothstep, so the lean starts and finishes gently rather than switching on. */
        private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)
    }
}
