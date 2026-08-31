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

package app.lawnchair.vellum.preset

import app.lawnchair.vellum.surface.VellumSurface
import app.lawnchair.vellum.surface.VellumSurfaceSet
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The merge rules that decide what applying a look destroys and what it keeps.
 *
 * This is the part of the feature that can lose real user work, so it is tested as pure data.
 * App lists are left empty throughout: constructing a `ComponentKey` needs Android's
 * `ComponentName` and `UserHandle`, which are stubs on the JVM.
 */
class LookApplierTest {

    private val look = VellumLook.byId("signal")!!

    private fun applied(
        current: VellumSurfaceSet = VellumSurfaceSet(),
        replaceApps: Boolean = false,
    ) = LookApplier.merge(current, look, resolvedApps = emptyMap(), replaceApps = replaceApps)

    @Test
    fun appliesTheLooksAtmosphereToEverySurface() {
        val result = applied()
        look.surfaces.forEach { lookSurface ->
            val surface = result.byId(lookSurface.id)!!
            assertThat(surface.accent).isEqualTo(lookSurface.accent)
            assertThat(surface.accentSecondary).isEqualTo(lookSurface.secondary)
            assertThat(surface.backdropId).isEqualTo(lookSurface.backdrop.id)
            assertThat(surface.ambientIntensity).isEqualTo(lookSurface.ambientIntensity)
            assertThat(surface.startMinute).isEqualTo(lookSurface.startMinute)
            assertThat(surface.endMinute).isEqualTo(lookSurface.endMinute)
        }
    }

    @Test
    fun keepsARenamedSurfacesName() {
        val current = withMorning { it.copy(customLabel = "Before work") }
        val result = applied(current)
        assertThat(result.byId(VellumSurface.ID_MORNING)!!.customLabel).isEqualTo("Before work")
    }

    @Test
    fun keepsASwitchedOffSurfaceSwitchedOff() {
        val current = withMorning { it.copy(enabled = false) }
        val result = applied(current)
        assertThat(result.byId(VellumSurface.ID_MORNING)!!.enabled).isFalse()
        assertThat(result.byId(VellumSurface.ID_DAY)!!.enabled).isTrue()
    }

    @Test
    fun leavesAppsAloneWhenNotAskedToReplaceThem() {
        val current = VellumSurfaceSet()
        val result = applied(current, replaceApps = false)
        look.surfaces.forEach { lookSurface ->
            val before = current.byId(lookSurface.id)!!.apps
            assertThat(result.byId(lookSurface.id)!!.apps).isEqualTo(before)
        }
    }

    @Test
    fun replacesAppsWithWhateverTheDeviceResolvedTo() {
        // Nothing resolved, so every surface ends up empty rather than keeping stale pins from the
        // look the user was previously on.
        val result = applied(replaceApps = true)
        result.surfaces.forEach { surface ->
            assertThat(surface.apps).isEmpty()
        }
    }

    @Test
    fun stillTilesTheDayAfterApplying() {
        val result = applied()
        result.surfaces.forEachIndexed { index, surface ->
            val next = result.surfaces[(index + 1) % result.surfaces.size]
            assertThat(surface.endMinute).isEqualTo(next.startMinute)
        }
    }

    @Test
    fun everyMinuteOfTheDayResolvesToASurfaceAfterApplying() {
        val result = applied()
        for (minute in 0 until VellumSurface.MINUTES_PER_DAY) {
            val covering = result.surfaces.filter { it.contains(minute) }
            assertThat(covering).hasSize(1)
        }
    }

    @Test
    fun keepsSurfacesTheLookDoesNotDescribe() {
        val extra = VellumSurface(
            id = "commute",
            startMinute = 8 * 60,
            endMinute = 9 * 60,
            accent = 0xFF102030.toInt(),
            ambientIntensity = .5f,
        )
        val current = VellumSurfaceSet(surfaces = VellumSurface.defaults() + extra)
        val result = applied(current)

        assertThat(result.byId("commute")).isEqualTo(extra)
        assertThat(result.surfaces).hasSize(current.surfaces.size)
    }

    @Test
    fun applyingTwiceIsIdempotent() {
        val once = applied()
        val twice = LookApplier.merge(once, look, resolvedApps = emptyMap(), replaceApps = false)
        assertThat(twice).isEqualTo(once)
    }

    @Test
    fun switchingLooksLeavesNoTraceOfThePreviousOne() {
        val first = LookApplier.merge(VellumSurfaceSet(), VellumLook.byId("paper")!!, emptyMap(), replaceApps = false)
        val second = LookApplier.merge(first, look, emptyMap(), replaceApps = false)
        val fresh = applied()
        assertThat(second.surfaces).isEqualTo(fresh.surfaces)
    }

    private fun withMorning(transform: (VellumSurface) -> VellumSurface): VellumSurfaceSet {
        val set = VellumSurfaceSet()
        return set.copy(
            surfaces = set.surfaces.map { if (it.id == VellumSurface.ID_MORNING) transform(it) else it },
        )
    }
}
