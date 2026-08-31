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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The arithmetic behind the light drifting through a surface.
 *
 * Colours are not asserted here: mixing them goes through `ColorUtils`, which is an `android.jar`
 * stub on the JVM. Everything that decides *how far* the light has moved, and *when* the engine
 * next has to wake up, is pure — which is exactly why it lives in [SurfaceMoment] rather than in
 * the view or the engine.
 */
class SurfaceMomentTest {

    private val set = VellumSurfaceSet()
    private val morning = set.byId(VellumSurface.ID_MORNING)!!
    private val night = set.byId(VellumSurface.ID_NIGHT)!!

    private fun hm(hour: Int, minute: Int = 0) = hour * 60 + minute

    private fun momentAt(minute: Int, surface: VellumSurface = morning, pinned: Boolean = false) = SurfaceMoment.at(set, minute, surface, pinned)

    // --- window arithmetic ---

    @Test
    fun windowLengthHandlesMidnightWrap() {
        assertThat(morning.lengthMinutes).isEqualTo(hm(6))
        // 22:00 to 05:00 is seven hours, not a negative number.
        assertThat(night.lengthMinutes).isEqualTo(hm(7))
    }

    @Test
    fun aSurfaceCoveringTheWholeDayIsAFullDayLong() {
        val everything = VellumSurface(id = "all", startMinute = 400, endMinute = 400, accent = 0, ambientIntensity = .5f)
        assertThat(everything.lengthMinutes).isEqualTo(VellumSurface.MINUTES_PER_DAY)
    }

    @Test
    fun phaseRunsFromZeroToOneAcrossAWindow() {
        assertThat(morning.phaseAt(hm(5))).isEqualTo(0f)
        assertThat(morning.phaseAt(hm(8))).isWithin(1e-4f).of(.5f)
        assertThat(morning.phaseAt(hm(10, 59))).isGreaterThan(.99f)
    }

    @Test
    fun phaseIsCorrectAcrossMidnight() {
        assertThat(night.phaseAt(hm(22))).isEqualTo(0f)
        // 01:30 is three and a half hours into a seven hour window.
        assertThat(night.phaseAt(hm(1, 30))).isWithin(1e-4f).of(.5f)
    }

    // --- drift ---

    @Test
    fun earlyInASurfaceTheLightIsEntirelyItsOwn() {
        val moment = momentAt(hm(6))
        assertThat(moment.next).isNull()
        assertThat(moment.blend).isEqualTo(0f)
    }

    @Test
    fun lateInASurfaceTheLightLeansTowardTheNextOne() {
        val moment = momentAt(hm(10, 45))
        assertThat(moment.next?.id).isEqualTo(VellumSurface.ID_DAY)
        assertThat(moment.blend).isGreaterThan(0f)
    }

    @Test
    fun theLeanIsAlmostCompleteByTheBoundary() {
        assertThat(momentAt(hm(10, 59)).blend).isGreaterThan(.9f)
    }

    @Test
    fun theLeanNeverGoesBackwards() {
        var previous = -1f
        for (minute in hm(5) until hm(11)) {
            val blend = momentAt(minute).blend
            assertThat(blend).isAtLeast(previous)
            assertThat(blend).isAtLeast(0f)
            assertThat(blend).isAtMost(1f)
            previous = blend
        }
    }

    @Test
    fun theLeanCrossesMidnightIntoTheMorning() {
        // Night runs 22:00 to 05:00, so it should be reaching for Morning shortly before five.
        val moment = SurfaceMoment.at(set, hm(4, 45), night, pinned = false)
        assertThat(moment.next?.id).isEqualTo(VellumSurface.ID_MORNING)
        assertThat(moment.blend).isGreaterThan(0f)
    }

    @Test
    fun aPinnedSurfaceNeverDrifts() {
        // The user asked for this exact atmosphere; sliding it toward the next one would undo that.
        val moment = momentAt(hm(10, 59), pinned = true)
        assertThat(moment.next).isNull()
        assertThat(moment.blend).isEqualTo(0f)
    }

    @Test
    fun theOnlySurfaceLeftHasNothingToLeanToward() {
        val soloSet = VellumSurfaceSet(
            surfaces = VellumSurfaceSet().surfaces.map { it.copy(enabled = it.id == VellumSurface.ID_MORNING) },
        )
        val solo = soloSet.byId(VellumSurface.ID_MORNING)!!
        val moment = SurfaceMoment.at(soloSet, hm(10, 59), solo, pinned = false)
        assertThat(moment.next).isNull()
        assertThat(moment.blend).isEqualTo(0f)
    }

    @Test
    fun theLightNeverLeansTowardASwitchedOffSurface() {
        // Switching one off leaves the day no longer tiled. Whatever the successor resolves to in
        // that state, it must never be a surface the user has turned off, and never the surface
        // itself -- either would make the light lean toward something that is not coming.
        VellumSurfaceSet().surfaces.forEach { disabled ->
            val set = VellumSurfaceSet(
                surfaces = VellumSurfaceSet().surfaces.map {
                    if (it.id == disabled.id) it.copy(enabled = false) else it
                },
            )
            set.enabledSurfaces().forEach { surface ->
                val successor = set.surfaceAfter(surface)
                if (successor != null) {
                    assertThat(successor.enabled).isTrue()
                    assertThat(successor.id).isNotEqualTo(surface.id)
                }
            }
        }
    }

    @Test
    fun aGapInTheDayProducesNoLeanRatherThanAWrongOne() {
        // With Day switched off nothing covers 11:00 to 17:00, and surfaceAt falls back to the
        // first enabled surface -- which is Morning itself. Reporting "no successor" is the honest
        // answer there: leaning toward a surface that is not actually next would be worse than
        // simply holding Morning's own colour until Evening arrives.
        val withoutDay = VellumSurfaceSet(
            surfaces = VellumSurfaceSet().surfaces.map {
                if (it.id == VellumSurface.ID_DAY) it.copy(enabled = false) else it
            },
        )
        assertThat(withoutDay.surfaceAfter(withoutDay.byId(VellumSurface.ID_MORNING)!!)).isNull()
        // Evening is unaffected: Night still follows it.
        assertThat(withoutDay.surfaceAfter(withoutDay.byId(VellumSurface.ID_EVENING)!!)?.id)
            .isEqualTo(VellumSurface.ID_NIGHT)
    }

    // --- wake-up scheduling ---

    @Test
    fun theEngineWakesForTheLeanBeforeItWakesForTheBoundary() {
        val minute = hm(6)
        val untilChange = SurfaceMoment.minutesUntilNextChange(morning, minute)
        val untilEnd = morning.minutesUntilEnd(minute)
        assertThat(untilChange).isLessThan(untilEnd)
        // Landing on that wake-up should be the moment the lean is allowed to start.
        assertThat(momentAt(minute + untilChange).blend).isAtLeast(0f)
        assertThat(momentAt(minute + untilChange).next).isNotNull()
    }

    @Test
    fun onceLeaningTheOnlyRemainingWakeUpIsTheBoundary() {
        val minute = hm(10, 45)
        assertThat(SurfaceMoment.minutesUntilNextChange(morning, minute))
            .isEqualTo(morning.minutesUntilEnd(minute))
    }

    @Test
    fun everyMinuteOfEverySurfaceSchedulesAPositiveDelay() {
        // A zero or negative delay would busy-loop the handler.
        set.surfaces.forEach { surface ->
            for (minute in 0 until VellumSurface.MINUTES_PER_DAY) {
                val delay = SurfaceMoment.minutesUntilNextChange(surface, minute)
                assertThat(delay).isAtLeast(1)
                assertThat(delay).isAtMost(VellumSurface.MINUTES_PER_DAY)
            }
        }
    }

    @Test
    fun aWholeDaySurfaceStillSchedulesSensibly() {
        val everything = VellumSurface(id = "all", startMinute = 400, endMinute = 400, accent = 0, ambientIntensity = .5f)
        for (minute in 0 until VellumSurface.MINUTES_PER_DAY) {
            assertThat(SurfaceMoment.minutesUntilNextChange(everything, minute)).isAtLeast(1)
        }
    }

    // --- resolved atmosphere ---

    @Test
    fun theBackgroundDesignNeverBlends() {
        // Two backdrops are different compositions, not two values of one, so there is no halfway
        // point. The design has to stay the current surface's until the boundary swaps it.
        val leaning = momentAt(hm(10, 55))
        assertThat(leaning.next?.backdrop).isNotNull()
        assertThat(leaning.atmosphere().style).isEqualTo(morning.backdrop)
    }

    @Test
    fun intensityMovesWithTheLean() {
        val held = momentAt(hm(6)).atmosphere().intensity
        assertThat(held).isEqualTo(morning.ambientIntensity)

        val leaning = momentAt(hm(10, 59)).atmosphere().intensity
        val target = set.byId(VellumSurface.ID_DAY)!!.ambientIntensity
        // Morning is .55 and Day is .40, so a late lean must have moved down toward Day.
        assertThat(leaning).isLessThan(morning.ambientIntensity)
        assertThat(leaning).isGreaterThan(target)
    }
}
