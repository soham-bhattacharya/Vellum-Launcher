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

import app.lawnchair.vellum.iconpack.CuratedIconPack
import app.lawnchair.vellum.surface.VellumSurface
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Integrity of the shipped look gallery.
 *
 * A broken look is not a crash, it is a home screen with a dead patch of day in it, which is much
 * harder to notice and much worse to ship. These tests check the properties that a look has to
 * satisfy for the surface engine to behave.
 */
class VellumLookTest {

    private val looks = VellumLook.all()

    @Test
    fun galleryIsNotEmptyAndIdsAreUnique() {
        assertThat(looks).isNotEmpty()
        assertThat(looks.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun everyLookDescribesExactlyTheKnownSurfaces() {
        val expected = listOf(
            VellumSurface.ID_MORNING,
            VellumSurface.ID_DAY,
            VellumSurface.ID_EVENING,
            VellumSurface.ID_NIGHT,
        )
        looks.forEach { look ->
            assertThat(look.surfaces.map { it.id }).containsExactlyElementsIn(expected).inOrder()
        }
    }

    @Test
    fun everyLookTilesTheWholeDay() {
        // Each surface has to end exactly where the next one starts, cyclically. A gap is a minute
        // no surface covers; an overlap is a minute two surfaces claim.
        looks.forEach { look ->
            look.surfaces.forEachIndexed { index, surface ->
                val next = look.surfaces[(index + 1) % look.surfaces.size]
                assertThat(surface.endMinute).isEqualTo(next.startMinute)
            }
        }
    }

    @Test
    fun everyWindowIsAValidMinuteOfDay() {
        looks.forEach { look ->
            look.surfaces.forEach { surface ->
                assertThat(surface.startMinute).isIn(0 until VellumSurface.MINUTES_PER_DAY)
                assertThat(surface.endMinute).isIn(0 until VellumSurface.MINUTES_PER_DAY)
            }
        }
    }

    @Test
    fun everySurfaceCoversANonZeroStretchOfTime() {
        looks.forEach { look ->
            look.surfaces.forEach { surface ->
                assertThat(surface.startMinute).isNotEqualTo(surface.endMinute)
            }
        }
    }

    @Test
    fun everySurfaceHasAppRolesToOffer() {
        // A surface with no roles produces an empty panel, which is the state the panel treats as
        // "not set up yet" and prompts the user to fix.
        looks.forEach { look ->
            look.surfaces.forEach { surface ->
                assertThat(surface.roles).isNotEmpty()
            }
        }
    }

    @Test
    fun rolesWithinASurfaceAreDistinct() {
        looks.forEach { look ->
            look.surfaces.forEach { surface ->
                assertThat(surface.roles).containsNoDuplicates()
            }
        }
    }

    @Test
    fun intensitiesAreInRange() {
        looks.forEach { look ->
            look.surfaces.forEach { surface ->
                assertThat(surface.ambientIntensity).isAtLeast(0f)
                assertThat(surface.ambientIntensity).isAtMost(1f)
            }
        }
    }

    @Test
    fun accentsAreFullyOpaque() {
        // A translucent accent would fight the alpha the ambient canvas already applies, so the
        // same surface would look different depending on the state transition it arrived through.
        looks.forEach { look ->
            look.surfaces.forEach { surface ->
                assertThat(surface.accent ushr 24).isEqualTo(0xFF)
                assertThat(surface.secondary ushr 24).isEqualTo(0xFF)
            }
        }
    }

    @Test
    fun showcaseIsOneOfTheLooksOwnSurfaces() {
        looks.forEach { look ->
            assertThat(look.surfaces).contains(look.showcase)
        }
    }

    @Test
    fun accentsListMatchesTheSurfaceOrder() {
        looks.forEach { look ->
            assertThat(look.accents).isEqualTo(look.surfaces.map { it.accent })
        }
    }

    @Test
    fun byIdFindsEveryShippedLookAndNothingElse() {
        looks.forEach { look ->
            assertThat(VellumLook.byId(look.id)).isEqualTo(look)
        }
        assertThat(VellumLook.byId("not_a_look")).isNull()
        assertThat(VellumLook.byId(null)).isNull()
    }

    @Test
    fun defaultLookIsInTheGallery() {
        assertThat(looks.map { it.id }).contains(VellumLook.Default.id)
    }

    @Test
    fun shippedSurfaceDefaultsMatchTheDefaultLook() {
        // VellumSurface.defaults() deliberately duplicates the Bloom look rather than calling into
        // it, to avoid a class-initialisation cycle. This is the guard that keeps the copy honest.
        val defaults = VellumSurface.defaults()
        val look = VellumLook.Default

        assertThat(defaults.map { it.id }).isEqualTo(look.surfaces.map { it.id })
        defaults.zip(look.surfaces).forEach { (surface, lookSurface) ->
            assertThat(surface.startMinute).isEqualTo(lookSurface.startMinute)
            assertThat(surface.endMinute).isEqualTo(lookSurface.endMinute)
            assertThat(surface.accent).isEqualTo(lookSurface.accent)
            assertThat(surface.accentSecondary).isEqualTo(lookSurface.secondary)
            assertThat(surface.ambientIntensity).isEqualTo(lookSurface.ambientIntensity)
            assertThat(surface.backdropId).isEqualTo(lookSurface.backdrop.id)
        }
    }

    @Test
    fun anyIconPackALookNamesIsOneVellumActuallyRecommends() {
        // A look pointing at a pack that is not in the curated list would silently apply nothing,
        // because LookApplier resolves the id through CuratedIconPack before touching preferences.
        looks.mapNotNull { it.iconPackId }.forEach { id ->
            assertThat(CuratedIconPack.byId(id)).isNotNull()
        }
    }

    @Test
    fun exactlyOneLookChangesTheShapeOfTheDrawer() {
        // Column mode is a wholesale change to how the launcher reads. More than one look opting
        // into it would make the gallery feel like it had two defaults rather than one alternative.
        assertThat(looks.filter { it.columnDrawer }.map { it.id }).containsExactly("index")
    }

    @Test
    fun theListLookIsTheSpareOne() {
        // Guards the intent of the Index look: if it ever picks up a loud backdrop or a long app
        // list, it stops being the thing somebody reaches for when they want less.
        val index = VellumLook.byId("index")!!
        assertThat(index.columnDrawer).isTrue()
        index.surfaces.forEach { surface ->
            assertThat(surface.roles.size).isAtMost(4)
        }
    }
}
