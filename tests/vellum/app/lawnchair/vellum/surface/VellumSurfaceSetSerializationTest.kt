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

import app.lawnchair.vellum.backdrop.BackdropStyle
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

private val testJson = Json { ignoreUnknownKeys = true }

/**
 * Persistence round-trip for [VellumSurfaceSet]. Uses the same [kotlinxJson]
 * configuration as [app.lawnchair.preferences2.PreferenceManager2] (ignoreUnknownKeys = true),
 * so this catches accidental schema drift that would wipe user surfaces.
 *
 * Keeps apps empty to avoid instantiating Android's ComponentName/UserHandle on JVM.
 */
class VellumSurfaceSetSerializationTest {

    @Test
    fun defaults_roundTripThroughJsonPreservesAllSurfaces() {
        val original = VellumSurfaceSet()
        val json = testJson.encodeToString(original)
        val restored: VellumSurfaceSet = testJson.decodeFromString(json)

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun modifiedSet_withCustomLabelAndDisabled_roundTrips() {
        val original = VellumSurfaceSet().let { set ->
            set.copy(
                surfaces = set.surfaces.map { surface ->
                    when (surface.id) {
                        VellumSurface.ID_EVENING -> surface.copy(
                            customLabel = "Golden hour",
                            accent = 0xFF123456.toInt(),
                            ambientIntensity = 0.33f,
                            enabled = false,
                        )
                        else -> surface
                    }
                },
            )
        }
        val json = testJson.encodeToString(original)
        val restored: VellumSurfaceSet = testJson.decodeFromString(json)

        assertThat(restored).isEqualTo(original)
        assertThat(restored.byId(VellumSurface.ID_EVENING)!!.customLabel).isEqualTo("Golden hour")
        assertThat(restored.byId(VellumSurface.ID_EVENING)!!.enabled).isFalse()
    }

    @Test
    fun boundaryMove_afterRoundTrip_stillTilesDay() {
        val original = VellumSurfaceSet()
            .withEnd(VellumSurface.ID_MORNING, 600)
            .withStart(VellumSurface.ID_NIGHT, 1300)
        val json = testJson.encodeToString(original)
        val restored: VellumSurfaceSet = testJson.decodeFromString(json)

        for (minute in 0 until VellumSurface.MINUTES_PER_DAY) {
            assertThat(restored.surfaces.filter { it.contains(minute) }).hasSize(1)
        }
    }

    @Test
    fun deserialization_ignoresUnknownKeys() {
        // Simulate a future app version that adds a field; old code must not crash.
        val rawJson = """
            {
              "surfaces": [
                {
                  "id": "morning",
                  "startMinute": 300,
                  "endMinute": 660,
                  "accent": -123,
                  "ambientIntensity": 0.55,
                  "apps": [],
                  "customLabel": null,
                  "enabled": true,
                  "futureField": "should be ignored"
                }
              ],
              "unknownTopLevel": 42
            }
        """.trimIndent()
        val restored: VellumSurfaceSet = testJson.decodeFromString(rawJson)

        assertThat(restored.surfaces).hasSize(1)
        assertThat(restored.surfaces[0].id).isEqualTo("morning")
    }

    @Test
    fun emptyAppsList_survivesRoundTrip() {
        val original = VellumSurfaceSet(
            surfaces = listOf(
                VellumSurface(id = "only", startMinute = 0, endMinute = 0, accent = 0xFFABCDEF.toInt(), ambientIntensity = 0.5f, apps = emptyList()),
            ),
        )
        val json = testJson.encodeToString(original)
        val restored: VellumSurfaceSet = testJson.decodeFromString(json)

        assertThat(restored.surfaces[0].apps).isEmpty()
    }

    @Test
    fun allEnabledFlags_falseStillDeserializesToNullSurfaceAt() {
        val disabled = VellumSurfaceSet(
            surfaces = VellumSurfaceSet().surfaces.map { it.copy(enabled = false) },
        )
        val json = testJson.encodeToString(disabled)
        val restored: VellumSurfaceSet = testJson.decodeFromString(json)

        assertThat(restored.surfaceAt(500)).isNull()
    }

    @Test
    fun surfaceSetSavedBeforeBackdropsExisted_stillLoads() {
        // This is the exact shape PreferenceManager2 wrote before background designs were added.
        // Anybody upgrading has one of these on disk, and a deserialisation failure here means
        // their surfaces are silently replaced by the defaults on first launch after the update.
        val legacyJson = """
            {
              "surfaces": [
                {
                  "id": "morning",
                  "startMinute": 300,
                  "endMinute": 660,
                  "accent": -893606,
                  "ambientIntensity": 0.55,
                  "apps": [],
                  "customLabel": "Early",
                  "enabled": true
                },
                {
                  "id": "night",
                  "startMinute": 660,
                  "endMinute": 300,
                  "accent": -12892532,
                  "ambientIntensity": 0.85,
                  "apps": [],
                  "customLabel": null,
                  "enabled": false
                }
              ]
            }
        """.trimIndent()

        val restored: VellumSurfaceSet = testJson.decodeFromString(legacyJson)

        assertThat(restored.surfaces).hasSize(2)
        assertThat(restored.byId("morning")!!.customLabel).isEqualTo("Early")
        assertThat(restored.byId("night")!!.enabled).isFalse()
        // The new fields take their defaults rather than failing the parse.
        assertThat(restored.byId("morning")!!.accentSecondary).isNull()
        assertThat(restored.byId("morning")!!.backdropId)
            .isEqualTo(BackdropStyle.Default.id)
    }

    @Test
    fun unrecognisedBackdropId_degradesToTheDefaultDesign() {
        val json = """
            {
              "surfaces": [
                {
                  "id": "morning",
                  "startMinute": 300,
                  "endMinute": 660,
                  "accent": -893606,
                  "ambientIntensity": 0.55,
                  "backdropId": "a_design_from_a_newer_build"
                }
              ]
            }
        """.trimIndent()

        val restored: VellumSurfaceSet = testJson.decodeFromString(json)

        // The raw value is preserved so a downgrade-then-upgrade does not lose the choice, but the
        // resolved design falls back to something that can actually be drawn.
        assertThat(restored.surfaces[0].backdropId).isEqualTo("a_design_from_a_newer_build")
        assertThat(restored.surfaces[0].backdrop).isEqualTo(BackdropStyle.Default)
    }

    @Test
    fun backdropAndSecondaryAccent_surviveRoundTrip() {
        val original = VellumSurfaceSet().let { set ->
            set.copy(
                surfaces = set.surfaces.map {
                    it.copy(backdropId = BackdropStyle.GRAIN.id, accentSecondary = 0xFF00FF00.toInt())
                },
            )
        }
        val json = testJson.encodeToString(original)
        val restored: VellumSurfaceSet = testJson.decodeFromString(json)

        assertThat(restored).isEqualTo(original)
        assertThat(restored.surfaces.first().backdrop).isEqualTo(BackdropStyle.GRAIN)
    }
}
