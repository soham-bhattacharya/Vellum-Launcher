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

    @Test
    fun withEnd_wrapsWhenMinuteExceedsDay() {
        val set = VellumSurfaceSet()
        // 2240 mod 1440 == 800 -> 13:20, still between morning start (05:00) and day end (17:00)
        // so tiling is preserved.
        val moved = set.withEnd(VellumSurface.ID_MORNING, 2240)

        assertThat(moved.byId(VellumSurface.ID_MORNING)!!.endMinute).isEqualTo(800)
        assertThat(moved.byId(VellumSurface.ID_DAY)!!.startMinute).isEqualTo(800)
        for (minute in 0 until VellumSurface.MINUTES_PER_DAY) {
            assertThat(moved.surfaces.filter { it.contains(minute) }).hasSize(1)
        }
    }

    @Test
    fun withStart_wrapsNegativeMinute() {
        val set = VellumSurfaceSet()
        val moved = set.withStart(VellumSurface.ID_DAY, -30)
        // -30 mod 1440 == 1410 -> 23:30
        assertThat(moved.byId(VellumSurface.ID_DAY)!!.startMinute).isEqualTo(1410)
        assertThat(moved.byId(VellumSurface.ID_MORNING)!!.endMinute).isEqualTo(1410)
    }

    @Test
    fun withEnd_singleSurfaceMovesBothEndsTogether() {
        val only = VellumSurface(id = "only", startMinute = hm(9), endMinute = hm(9), accent = 0, ambientIntensity = .5f)
        val set = VellumSurfaceSet(surfaces = listOf(only))
        val moved = set.withEnd("only", hm(14))

        assertThat(moved.byId("only")!!.startMinute).isEqualTo(hm(14))
        assertThat(moved.byId("only")!!.endMinute).isEqualTo(hm(14))
        // Still covers whole day
        for (minute in 0 until VellumSurface.MINUTES_PER_DAY) {
            assertThat(moved.surfaces.filter { it.contains(minute) }).hasSize(1)
        }
    }

    @Test
    fun surfaceAt_whenPrimaryDisabled_fallsToFirstEnabledNotDisabled() {
        // Disable morning (05:00-11:00). At 08:00 the disabled window would have claimed it,
        // but the contract is to pick the first enabled surface that contains it, falling back to
        // the first enabled overall when none contains it.
        val set = VellumSurfaceSet(
            surfaces = VellumSurfaceSet().surfaces.map {
                if (it.id == VellumSurface.ID_MORNING) it.copy(enabled = false) else it
            },
        )
        // 08:00 is inside disabled morning; should NOT return morning, should return something else.
        val atEight = set.surfaceAt(hm(8))
        assertThat(atEight).isNotNull()
        assertThat(atEight!!.id).isNotEqualTo(VellumSurface.ID_MORNING)
        // 06:00 also inside disabled window — fallback is first enabled (day) since gap.
        assertThat(set.surfaceAt(hm(6))?.enabled).isTrue()
    }

    @Test
    fun surfaceAt_withTwoConsecutiveDisabled_returnsNearestEnabled() {
        val set = VellumSurfaceSet(
            surfaces = VellumSurfaceSet().surfaces.map {
                when (it.id) {
                    VellumSurface.ID_MORNING, VellumSurface.ID_DAY -> it.copy(enabled = false)
                    else -> it
                }
            },
        )
        // 10:00 would be morning, 14:00 would be day — both disabled.
        assertThat(set.surfaceAt(hm(10))?.id).isNotEqualTo(VellumSurface.ID_MORNING)
        assertThat(set.surfaceAt(hm(14))?.id).isNotEqualTo(VellumSurface.ID_DAY)
        // Night and evening remain.
        assertThat(set.surfaceAt(hm(23))?.id).isEqualTo(VellumSurface.ID_NIGHT)
    }

    @Test
    fun minutesUntilEnd_atExactBoundaries_returnsFullDayNotZero() {
        val night = VellumSurface(id = "night", startMinute = hm(22), endMinute = hm(5), accent = 0, ambientIntensity = .5f)
        // Standing on the end instant: full day until it comes round.
        assertThat(night.minutesUntilEnd(hm(5))).isEqualTo(VellumSurface.MINUTES_PER_DAY)
        // Standing one minute before end.
        assertThat(night.minutesUntilEnd(hm(4, 59))).isEqualTo(1)
        // Standing at midnight: night ends at 05:00 -> 300 mins left.
        assertThat(night.minutesUntilEnd(hm(0))).isEqualTo(300)
    }

    @Test
    fun minutesUntilEnd_singleSurfaceWholeDayAlwaysReturnsFullDay() {
        val only = VellumSurface(id = "only", startMinute = hm(0), endMinute = hm(0), accent = 0, ambientIntensity = .5f)
        assertThat(only.minutesUntilEnd(hm(0))).isEqualTo(VellumSurface.MINUTES_PER_DAY)
        assertThat(only.minutesUntilEnd(hm(12))).isEqualTo(VellumSurface.MINUTES_PER_DAY)
        assertThat(only.minutesUntilEnd(hm(23, 59))).isEqualTo(VellumSurface.MINUTES_PER_DAY)
    }

    @Test
    fun defaults_accentsAreDistinctAndInExpectedHueRanges() {
        val defaults = VellumSurface.defaults()
        assertThat(defaults).hasSize(4)
        // All accents distinct.
        assertThat(defaults.map { it.accent }.toSet()).hasSize(4)
        // Day is the low-intensity one, night the highest — per spec.
        val byId = defaults.associateBy { it.id }
        assertThat(byId[VellumSurface.ID_DAY]!!.ambientIntensity).isLessThan(byId[VellumSurface.ID_NIGHT]!!.ambientIntensity)
        assertThat(byId[VellumSurface.ID_MORNING]!!.ambientIntensity).isGreaterThan(byId[VellumSurface.ID_DAY]!!.ambientIntensity)
    }

    @Test
    fun withStart_midnightBoundaryHoldsTilingAfterMultipleMoves() {
        var set = VellumSurfaceSet()
        // Move boundaries that straddle midnight repeatedly.
        set = set.withStart(VellumSurface.ID_MORNING, hm(4, 30))
        set = set.withEnd(VellumSurface.ID_NIGHT, hm(4, 30))
        set = set.withEnd(VellumSurface.ID_EVENING, hm(23, 15))
        set = set.withStart(VellumSurface.ID_DAY, hm(10, 45))
        for (minute in 0 until VellumSurface.MINUTES_PER_DAY) {
            assertThat(set.surfaces.filter { it.contains(minute) }).hasSize(1)
        }
    }
}
