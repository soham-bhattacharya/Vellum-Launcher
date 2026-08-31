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

import android.content.Context
import app.lawnchair.util.ComponentKeySerializer
import com.android.launcher3.R
import com.android.launcher3.util.ComponentKey
import kotlinx.serialization.Serializable

/**
 * One context surface: a named stretch of the day with its own atmosphere and its own short list of
 * apps.
 *
 * Surfaces are deliberately **additive**. They never rearrange the workspace grid — icons stay
 * exactly where the user put them. What a surface changes is the ambient colour of the home screen
 * and the contents of the surface panel, so the phone can feel different at 07:00 and at 23:00
 * without anyone ever losing track of where their things are.
 */
@Serializable
data class VellumSurface(
    val id: String,
    /** Minute of day the surface begins, inclusive, 0..1439. */
    val startMinute: Int,
    /** Minute of day the surface ends, exclusive. A value at or below [startMinute] wraps midnight. */
    val endMinute: Int,
    /** Ambient accent for this surface, as an ARGB int. */
    val accent: Int,
    /** Ambient strength for this surface, 0..1. */
    val ambientIntensity: Float,
    val apps: List<
        @Serializable(ComponentKeySerializer::class)
        ComponentKey,
        > = emptyList(),
    /** Set when the user renames a surface; otherwise the built-in name is used. */
    val customLabel: String? = null,
    val enabled: Boolean = true,
) {

    fun label(context: Context): String = customLabel ?: context.getString(labelRes(id))

    /** True when [minuteOfDay] falls inside this surface's window, handling midnight wrap. */
    fun contains(minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> true
        startMinute < endMinute -> minuteOfDay >= startMinute && minuteOfDay < endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute
    }

    /** Minutes from [minuteOfDay] until this surface ends. Always in 1..1440. */
    fun minutesUntilEnd(minuteOfDay: Int): Int {
        val delta = (endMinute - minuteOfDay + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return if (delta == 0) MINUTES_PER_DAY else delta
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        const val ID_MORNING = "morning"
        const val ID_DAY = "day"
        const val ID_EVENING = "evening"
        const val ID_NIGHT = "night"

        private fun labelRes(id: String): Int = when (id) {
            ID_MORNING -> R.string.vellum_surface_morning
            ID_DAY -> R.string.vellum_surface_day
            ID_EVENING -> R.string.vellum_surface_evening
            else -> R.string.vellum_surface_night
        }

        private fun hm(hour: Int, minute: Int = 0) = hour * 60 + minute

        /**
         * The shipped set. Colours climb from a warm sunrise through daylight into a violet
         * evening and a deep, low-luminance night, so the ambient layer reads as the time of day
         * rather than as decoration.
         */
        fun defaults(): List<VellumSurface> = listOf(
            VellumSurface(
                id = ID_MORNING,
                startMinute = hm(5),
                endMinute = hm(11),
                accent = 0xFFF2A65A.toInt(),
                ambientIntensity = .55f,
            ),
            VellumSurface(
                id = ID_DAY,
                startMinute = hm(11),
                endMinute = hm(17),
                accent = 0xFF5AA9F2.toInt(),
                ambientIntensity = .40f,
            ),
            VellumSurface(
                id = ID_EVENING,
                startMinute = hm(17),
                endMinute = hm(22),
                accent = 0xFF8C69FF.toInt(),
                ambientIntensity = .70f,
            ),
            VellumSurface(
                id = ID_NIGHT,
                startMinute = hm(22),
                endMinute = hm(5),
                accent = 0xFF3B4A8C.toInt(),
                ambientIntensity = .85f,
            ),
        )
    }
}

/** The persisted set of surfaces, plus whether the whole feature is on. */
@Serializable
data class VellumSurfaceSet(
    val surfaces: List<VellumSurface> = VellumSurface.defaults(),
) {
    fun enabledSurfaces(): List<VellumSurface> = surfaces.filter { it.enabled }

    fun byId(id: String?): VellumSurface? = id?.let { wanted -> surfaces.firstOrNull { it.id == wanted } }

    /**
     * Moves the boundary between [surfaceId] and the neighbour that follows it.
     *
     * Surfaces tile the day: each one's end is the next one's start. Editing only one side would
     * leave a gap (a time no surface covers) or an overlap (a time two claim), so both sides move
     * together and the day stays exactly covered.
     */
    fun withEnd(surfaceId: String, minute: Int): VellumSurfaceSet = moveBoundary(surfaceId, minute, boundaryAfter = true)

    /** Moves the boundary between [surfaceId] and the neighbour that precedes it. */
    fun withStart(surfaceId: String, minute: Int): VellumSurfaceSet = moveBoundary(surfaceId, minute, boundaryAfter = false)

    private fun moveBoundary(surfaceId: String, minute: Int, boundaryAfter: Boolean): VellumSurfaceSet {
        val index = surfaces.indexOfFirst { it.id == surfaceId }
        if (index < 0) return this
        val wrapped = minute.mod(VellumSurface.MINUTES_PER_DAY)
        val neighbour = (if (boundaryAfter) index + 1 else index - 1).mod(surfaces.size)
        return copy(
            surfaces = surfaces.mapIndexed { position, surface ->
                when {
                    // A single surface covers the whole day; both of its ends are the same instant.
                    position == index && position == neighbour ->
                        surface.copy(startMinute = wrapped, endMinute = wrapped)

                    position == index && boundaryAfter -> surface.copy(endMinute = wrapped)

                    position == index -> surface.copy(startMinute = wrapped)

                    position == neighbour && boundaryAfter -> surface.copy(startMinute = wrapped)

                    position == neighbour -> surface.copy(endMinute = wrapped)

                    else -> surface
                }
            },
        )
    }

    /** The surface whose window covers [minuteOfDay], or null when none is enabled. */
    fun surfaceAt(minuteOfDay: Int): VellumSurface? {
        val candidates = enabledSurfaces()
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.contains(minuteOfDay) } ?: candidates.first()
    }
}
