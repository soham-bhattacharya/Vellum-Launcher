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

package app.lawnchair.vellum.iconpack

import android.content.pm.PackageManager
import androidx.annotation.StringRes
import app.lawnchair.util.isPackageInstalled
import com.android.launcher3.R

/**
 * An icon pack Vellum recommends but does not ship.
 *
 * Icon packs cannot be bundled: they are separately licensed works, and the good ones are tens of
 * megabytes. What Vellum can usefully do is solve *discovery* — the settings screen already lists
 * the packs you have installed, which is no help at all if you have none and no idea which are
 * worth having.
 *
 * Every entry here has been checked to exist under the package names listed. Nothing is guessed:
 * a wrong package name would show an installed pack as missing, and send the user to a dead link.
 */
data class CuratedIconPack(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val taglineRes: Int,
    /**
     * Known package names for this pack, most preferred first.
     *
     * Several of these ship as a family — a Play build and an F-Droid build, or a set of themed
     * variants — under different package names. Treating them as one entry means a user who
     * already has any variant is told they have it, instead of being sent to install a duplicate.
     */
    val packages: List<String>,
    /** Where to get it when none of [packages] is present. */
    val storeUrl: String,
    /** Card colour. Chosen to evoke the pack rather than to match it exactly. */
    val accent: Int,
    val isOpenSource: Boolean,
) {
    /** The variant actually present on this device, or null when none is. */
    fun installedPackage(packageManager: PackageManager): String? = packages.firstOrNull(packageManager::isPackageInstalled)

    companion object {

        fun all(): List<CuratedIconPack> = listOf(
            // Lawnicons is first on purpose: it is the icon pack of the project Vellum is built
            // from, it is open source, and it is the closest thing to a house style Vellum has.
            CuratedIconPack(
                id = "lawnicons",
                nameRes = R.string.vellum_pack_lawnicons,
                taglineRes = R.string.vellum_pack_lawnicons_tagline,
                packages = listOf("app.lawnchair.lawnicons", "app.lawnchair.lawnicons.play"),
                storeUrl = "https://play.google.com/store/apps/details?id=app.lawnchair.lawnicons.play",
                accent = 0xFF7BC96F.toInt(),
                isOpenSource = true,
            ),
            CuratedIconPack(
                id = "arcticons",
                nameRes = R.string.vellum_pack_arcticons,
                taglineRes = R.string.vellum_pack_arcticons_tagline,
                packages = listOf(
                    "com.donnnno.arcticons",
                    "com.donnnno.arcticons.you",
                    "com.donnnno.arcticons.daynight",
                    "com.donnnno.arcticons.light",
                ),
                storeUrl = "https://play.google.com/store/apps/details?id=com.donnnno.arcticons",
                accent = 0xFF5AA9F2.toInt(),
                isOpenSource = true,
            ),
            CuratedIconPack(
                id = "delta",
                nameRes = R.string.vellum_pack_delta,
                taglineRes = R.string.vellum_pack_delta_tagline,
                packages = listOf("website.leifs.delta", "website.leifs.delta.foss"),
                storeUrl = "https://play.google.com/store/apps/details?id=website.leifs.delta",
                accent = 0xFFEF7B6B.toInt(),
                isOpenSource = true,
            ),
            CuratedIconPack(
                id = "zwart",
                nameRes = R.string.vellum_pack_zwart,
                taglineRes = R.string.vellum_pack_zwart_tagline,
                packages = listOf("com.blackiconpack.zwart"),
                storeUrl = "https://play.google.com/store/apps/details?id=com.blackiconpack.zwart",
                accent = 0xFF3A3A3A.toInt(),
                isOpenSource = false,
            ),
        )

        fun byId(id: String?): CuratedIconPack? = id?.let { wanted -> all().firstOrNull { it.id == wanted } }
    }
}
