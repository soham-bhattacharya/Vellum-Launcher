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

package app.lawnchair.vellum.wallpaper

import app.lawnchair.vellum.preset.VellumLook
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Integrity checks for the bundled wallpaper gallery and its Look pairings. */
class VellumWallpaperTest {

    private val wallpapers = VellumWallpaper.all()

    @Test
    fun galleryHasFourDistinctOriginals() {
        assertThat(wallpapers).hasSize(4)
        assertThat(wallpapers.map { it.id }).containsNoDuplicates()
        assertThat(wallpapers.map { it.drawableRes }).containsNoDuplicates()
    }

    @Test
    fun everyPairingNamesAShippedLook() {
        val lookIds = VellumLook.all().map { it.id }.toSet()
        wallpapers.flatMap { it.lookIds }.forEach { lookId ->
            assertThat(lookIds).contains(lookId)
        }
    }

    @Test
    fun everyShippedLookHasOneMatchingWallpaper() {
        VellumLook.all().forEach { look ->
            assertThat(VellumWallpaper.forLook(look.id)).isNotNull()
        }
    }

    @Test
    fun lookupFindsEveryWallpaperAndRejectsUnknownIds() {
        wallpapers.forEach { wallpaper ->
            assertThat(VellumWallpaper.byId(wallpaper.id)).isEqualTo(wallpaper)
        }
        assertThat(VellumWallpaper.byId("not_a_wallpaper")).isNull()
        assertThat(VellumWallpaper.byId(null)).isNull()
    }
}
