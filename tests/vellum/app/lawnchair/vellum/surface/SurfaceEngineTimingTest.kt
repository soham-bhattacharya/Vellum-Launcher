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
 * Pure arithmetic behind [SurfaceEngine.reevaluate]'s delay computation.
 *
 * The engine's handler delay is:
 *   delay = minutesLeft * 60_000 - second * 1000 + 1000
 * clamped to >= 1000.
 *
 * Landing just after the minute boundary ensures LocalTime has rolled over;
 * clamping prevents a tight loop when standing exactly on a boundary.
 *
 * Extracted here as pure math so it can be verified without Robolectric or a Handler.
 */
class SurfaceEngineTimingTest {

    private fun computeDelayMillis(minutesLeft: Int, second: Int): Long {
        val raw = minutesLeft * 60_000L - second * 1000L + 1_000L
        return raw.coerceAtLeast(1_000L)
    }

    @Test
    fun delay_atTopOfHourMinusOneSecondLandsOneSecondAfterBoundary() {
        // Surface ends at 11:00, now is 10:59:00 -> 1 minute left, second=0 -> 61_000ms
        assertThat(computeDelayMillis(minutesLeft = 1, second = 0)).isEqualTo(61_000L)
        // Same minute left but 45 seconds in -> 16_000ms
        assertThat(computeDelayMillis(minutesLeft = 1, second = 45)).isEqualTo(16_000L)
        // Last second of the minute -> 2_000ms (1s past boundary)
        assertThat(computeDelayMillis(minutesLeft = 1, second = 59)).isEqualTo(2_000L)
    }

    @Test
    fun delay_forSixHourSurface_isClampedAtLeastOneSecond() {
        // Worst case: surface lasts 8 hours (e.g. night 22:00-05:00 partially),
        // second=59 still yields huge delay but never zero.
        val eightHours = 8 * 60
        assertThat(computeDelayMillis(eightHours, 59)).isEqualTo(eightHours * 60_000L - 59_000L + 1_000L)
        assertThat(computeDelayMillis(eightHours, 59)).isGreaterThan(0L)
    }

    @Test
    fun delay_whenStandingExactlyOnBoundary_yieldsFullDayMinusSecond() {
        // minutesUntilEnd returns 1440 when on the end instant, not 0.
        // With second=30 the delay is a full day minus 30s +1s.
        val minutesLeft = VellumSurface.MINUTES_PER_DAY // 1440
        assertThat(computeDelayMillis(minutesLeft, 30)).isEqualTo(1440 * 60_000L - 30_000L + 1_000L)
        // Even at second=59 it is one day -58s, not zero.
        assertThat(computeDelayMillis(minutesLeft, 59)).isEqualTo(1440 * 60_000L - 59_000L + 1_000L)
    }

    @Test
    fun delay_clampsToOneSecond_whenRawWouldBeZeroOrNegative() {
        // Artificial: if minutesLeft were 0 (should never happen), raw = -second*1000+1000.
        // Engine clamps to 1000 to avoid spin.
        assertThat(computeDelayMillis(0, 59)).isEqualTo(1_000L)
        assertThat(computeDelayMillis(0, 0)).isEqualTo(1_000L)
        assertThat(computeDelayMillis(1, 60)).isEqualTo(1_000L) // 1 min but second overshoot
    }

    @Test
    fun minutesUntilEnd_integratesWithDelay_neverSchedulesInPast() {
        // For every minute of the day, the computed delay for the scheduled surface
        // must be >= 1000 and <= 1440*60_000.
        val set = VellumSurfaceSet()
        for (minute in 0 until VellumSurface.MINUTES_PER_DAY) {
            val surface = set.surfaceAt(minute)!!
            val minutesLeft = surface.minutesUntilEnd(minute)
            // second=0 and second=59 bracket the extremes
            val delayAt0 = computeDelayMillis(minutesLeft, 0)
            val delayAt59 = computeDelayMillis(minutesLeft, 59)
            assertThat(delayAt0).isAtLeast(1_000L)
            assertThat(delayAt59).isAtLeast(1_000L)
            assertThat(delayAt0).isAtMost(1440 * 60_000L + 1_000L)
        }
    }

    @Test
    fun delay_acrossMidnight_surfaceEndingAt0500_from2330() {
        // Night 22:00-05:00, now 23:30 -> 5.5h left = 330 mins
        val night = VellumSurface(id = "night", startMinute = 22 * 60, endMinute = 5 * 60, accent = 0, ambientIntensity = 0.5f)
        val minutesLeft = night.minutesUntilEnd(23 * 60 + 30) // 330
        assertThat(minutesLeft).isEqualTo(330)
        // At second=0, delay = 330*60000+1000
        assertThat(computeDelayMillis(minutesLeft, 0)).isEqualTo(330 * 60_000L + 1_000L)
        // At second=30, 30s less.
        assertThat(computeDelayMillis(minutesLeft, 30)).isEqualTo(330 * 60_000L - 30_000L + 1_000L)
    }
}
