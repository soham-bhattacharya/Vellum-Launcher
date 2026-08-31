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

package app.lawnchair.vellum.backdrop

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Rules about the backdrop catalogue that user data depends on.
 *
 * These are plain JVM tests: nothing here constructs a [Backdrop], because doing so would touch
 * `Paint` and `Path` from `android.jar`. Everything asserted below is metadata, which is exactly
 * why [BackdropStyle.parallaxScale] lives on the enum rather than on the instance.
 */
class BackdropStyleTest {

    @Test
    fun everyStyleHasAUniqueId() {
        val ids = BackdropStyle.entries.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun idsAreStableSlugs() {
        // Persisted verbatim, so they must not pick up anything a JSON round trip could mangle.
        BackdropStyle.entries.forEach { style ->
            assertThat(style.id).matches("[a-z][a-z0-9_]*")
        }
    }

    @Test
    fun everyIdRoundTrips() {
        BackdropStyle.entries.forEach { style ->
            assertThat(BackdropStyle.fromId(style.id)).isEqualTo(style)
        }
    }

    @Test
    fun unknownIdFallsBackRatherThanThrowing() {
        // A set written by a newer build must still open on an older one.
        assertThat(BackdropStyle.fromId("something_from_the_future")).isEqualTo(BackdropStyle.Default)
        assertThat(BackdropStyle.fromId("")).isEqualTo(BackdropStyle.Default)
        assertThat(BackdropStyle.fromId(null)).isEqualTo(BackdropStyle.Default)
    }

    @Test
    fun maxParallaxScaleCoversEveryStyle() {
        // The ambient canvas sizes its field layer once from this value. If any design drifted
        // further than the bound, translating it would expose an unpainted edge.
        BackdropStyle.entries.forEach { style ->
            assertThat(style.parallaxScale).isAtMost(BackdropStyle.maxParallaxScale)
        }
        assertThat(BackdropStyle.maxParallaxScale).isEqualTo(
            BackdropStyle.entries.maxOf { it.parallaxScale },
        )
    }

    @Test
    fun parallaxScalesStayWithinASensibleRange() {
        // Zero would pin a design to the screen and lose the depth cue entirely; anything much
        // above one costs overscan on every field layer, for every design.
        BackdropStyle.entries.forEach { style ->
            assertThat(style.parallaxScale).isGreaterThan(0f)
            assertThat(style.parallaxScale).isAtMost(1.5f)
        }
    }

    @Test
    fun everyStyleHasDistinctLabelAndDescriptionResources() {
        assertThat(BackdropStyle.entries.map { it.labelRes }).containsNoDuplicates()
        assertThat(BackdropStyle.entries.map { it.descriptionRes }).containsNoDuplicates()
    }
}
