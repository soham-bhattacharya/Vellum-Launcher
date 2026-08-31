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
 * Covers the pure time arithmetic behind context surfaces. Midnight wrap and boundary moves are the
 * two places this logic can silently produce a day that is not fully covered.
 *
 * Plain JVM tests: none of this touches the framework, so it needs no device or Robolectric.
 */
class VellumSurfaceTest {

    private fun hm(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun contains_withinSameDayWindow() {
        val day = VellumSurface(id = "day", startMinute = hm(11), endMinute = hm(17), accent = 0, ambientIntensity = .5f)

        assertThat(day.contains(hm(11))).isTrue()
        assertThat(day.contains(hm(14))).isTrue()
        assertThat(day.contains(hm(16, 59))).isTrue()
        // The end is exclusive so the next surface owns that minute.
        assertThat(day.contains(hm(17))).isFalse()
        assertThat(day.contains(hm(10, 59))).isFalse()
    }

    @Test
    fun contains_acrossMidnight() {
        val night = VellumSurface(id = "night", startMinute = hm(22), endMinute = hm(5), accent = 0, ambientIntensity = .5f)

        assertThat(night.contains(hm(22))).isTrue()
        assertThat(night.contains(hm(23, 59))).isTrue()
        assertThat(night.contains(hm(0))).isTrue()
        assertThat(night.contains(hm(4, 59))).isTrue()
        assertThat(night.contains(hm(5))).isFalse()
        assertThat(night.contains(hm(12))).isFalse()
    }

    @Test
    fun contains_wholeDayWhenStartEqualsEnd() {
        val only = VellumSurface(id = "only", startMinute = hm(9), endMinute = hm(9), accent = 0, ambientIntensity = .5f)

        assertThat(only.contains(hm(9))).isTrue()
        assertThat(only.contains(hm(3))).isTrue()
        assertThat(only.contains(hm(21))).isTrue()
    }

    @Test
    fun minutesUntilEnd_neverReturnsZero() {
        val day = VellumSurface(id = "day", startMinute = hm(11), endMinute = hm(17), accent = 0, ambientIntensity = .5f)

        assertThat(day.minutesUntilEnd(hm(16))).isEqualTo(60)
        assertThat(day.minutesUntilEnd(hm(11))).isEqualTo(360)
        // Standing exactly on the end means a full day until it comes round again, not a busy loop.
        assertThat(day.minutesUntilEnd(hm(17))).isEqualTo(VellumSurface.MINUTES_PER_DAY)
    }

    @Test
    fun minutesUntilEnd_acrossMidnight() {
        val night = VellumSurface(id = "night", startMinute = hm(22), endMinute = hm(5), accent = 0, ambientIntensity = .5f)

        assertThat(night.minutesUntilEnd(hm(23))).isEqualTo(360)
        assertThat(night.minutesUntilEnd(hm(1))).isEqualTo(240)
    }

    @Test
    fun defaults_coverEveryMinuteOfTheDayExactlyOnce() {
        val set = VellumSurfaceSet()

        for (minute in 0 until VellumSurface.MINUTES_PER_DAY) {
            val matches = set.surfaces.filter { it.contains(minute) }
            assertThat(matches).hasSize(1)
        }
    }

    @Test
    fun withEnd_movesTheSharedBoundaryOnBothSides() {
        val set = VellumSurfaceSet()
        val moved = set.withEnd(VellumSurface.ID_MORNING, hm(10))

        assertThat(moved.byId(VellumSurface.ID_MORNING)!!.endMinute).isEqualTo(hm(10))
        assertThat(moved.byId(VellumSurface.ID_DAY)!!.startMinute).isEqualTo(hm(10))
    }

    @Test
    fun withStart_movesThePrecedingBoundary() {
        val set = VellumSurfaceSet()
        val moved = set.withStart(VellumSurface.ID_DAY, hm(12))

        assertThat(moved.byId(VellumSurface.ID_DAY)!!.startMinute).isEqualTo(hm(12))
        assertThat(moved.byId(VellumSurface.ID_MORNING)!!.endMinute).isEqualTo(hm(12))
    }

    @Test
    fun withStart_wrapsFromTheFirstSurfaceToTheLast() {
        val set = VellumSurfaceSet()
        val moved = set.withStart(VellumSurface.ID_MORNING, hm(6))

        assertThat(moved.byId(VellumSurface.ID_MORNING)!!.startMinute).isEqualTo(hm(6))
        assertThat(moved.byId(VellumSurface.ID_NIGHT)!!.endMinute).isEqualTo(hm(6))
    }

    @Test
    fun boundaryMoves_keepTheDayFullyCovered() {
        var set = VellumSurfaceSet()
        set = set.withEnd(VellumSurface.ID_MORNING, hm(9, 30))
        set = set.withStart(VellumSurface.ID_NIGHT, hm(20, 15))
        set = set.withEnd(VellumSurface.ID_DAY, hm(15, 45))

        for (minute in 0 until VellumSurface.MINUTES_PER_DAY) {
            assertThat(set.surfaces.filter { it.contains(minute) }).hasSize(1)
        }
    }

    @Test
    fun surfaceAt_ignoresDisabledSurfaces() {
        val set = VellumSurfaceSet(
            surfaces = VellumSurfaceSet().surfaces.map {
                if (it.id == VellumSurface.ID_DAY) it.copy(enabled = false) else it
            },
        )

        assertThat(set.surfaceAt(hm(14))?.id).isNotEqualTo(VellumSurface.ID_DAY)
        assertThat(set.surfaceAt(hm(23))?.id).isEqualTo(VellumSurface.ID_NIGHT)
    }

    @Test
    fun surfaceAt_returnsNullWhenEverySurfaceIsOff() {
        val set = VellumSurfaceSet(
            surfaces = VellumSurfaceSet().surfaces.map { it.copy(enabled = false) },
        )

        assertThat(set.surfaceAt(hm(14))).isNull()
    }

    @Test
    fun moveBoundary_onUnknownSurfaceIsANoOp() {
        val set = VellumSurfaceSet()

        assertThat(set.withEnd("does-not-exist", hm(3))).isEqualTo(set)
    }
}
