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

import android.app.WallpaperManager
import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An original wallpaper shipped with Vellum and composed around launcher content. */
data class VellumWallpaper(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val drawableRes: Int,
    /** Looks whose palettes and material treatment were drawn against this wallpaper. */
    val lookIds: Set<String>,
) {

    companion object {
        private val wallpapers = listOf(
            VellumWallpaper(
                id = "daybreak",
                nameRes = R.string.vellum_wallpaper_daybreak,
                descriptionRes = R.string.vellum_wallpaper_daybreak_description,
                drawableRes = R.drawable.vellum_wallpaper_daybreak,
                lookIds = setOf("bloom"),
            ),
            VellumWallpaper(
                id = "tidal",
                nameRes = R.string.vellum_wallpaper_tidal,
                descriptionRes = R.string.vellum_wallpaper_tidal_description,
                drawableRes = R.drawable.vellum_wallpaper_tidal,
                lookIds = setOf("aurora", "signal"),
            ),
            VellumWallpaper(
                id = "strata",
                nameRes = R.string.vellum_wallpaper_strata,
                descriptionRes = R.string.vellum_wallpaper_strata_description,
                drawableRes = R.drawable.vellum_wallpaper_strata,
                lookIds = setOf("dunes", "paper"),
            ),
            VellumWallpaper(
                id = "nightfold",
                nameRes = R.string.vellum_wallpaper_nightfold,
                descriptionRes = R.string.vellum_wallpaper_nightfold_description,
                drawableRes = R.drawable.vellum_wallpaper_nightfold,
                lookIds = setOf("nocturne", "index"),
            ),
        )

        fun all(): List<VellumWallpaper> = wallpapers

        fun byId(id: String?): VellumWallpaper? = wallpapers.firstOrNull { it.id == id }

        fun forLook(lookId: String): VellumWallpaper? = wallpapers.firstOrNull { lookId in it.lookIds }
    }
}

/** Applies only the home-screen wallpaper; Vellum never changes the lock screen implicitly. */
object VellumWallpaperApplier {

    suspend fun apply(context: Context, wallpaper: VellumWallpaper): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            WallpaperManager.getInstance(context.applicationContext).setResource(
                wallpaper.drawableRes,
                WallpaperManager.FLAG_SYSTEM,
            )
            Unit
        }
    }
}
