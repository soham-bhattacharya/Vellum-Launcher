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

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.vellum.surface.VellumSurface
import app.lawnchair.vellum.surface.VellumSurfaceSet
import com.android.launcher3.util.ComponentKey
import com.patrykmichalik.opto.core.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** How many apps a look is allowed to put on one surface, so the panel stays a glance. */
private const val MAX_APPS_PER_SURFACE = 6

/** Applies a [VellumLook] to the live preferences. */
object LookApplier {

    /**
     * Writes [look] into the surface set.
     *
     * What is preserved and what is overwritten is a deliberate split. A look owns the *atmosphere*
     * — window, colours, backdrop, intensity — because that is what the user chose it for. It does
     * not own the user's structural decisions: a renamed surface keeps its name and a switched-off
     * surface stays off, because silently undoing those would make trying a look feel dangerous.
     *
     * App lists are only touched when [replaceApps] is set, since those can represent real work.
     *
     * Role resolution hits the package manager, so it runs off the main thread.
     */
    suspend fun apply(
        context: Context,
        preferences: PreferenceManager2,
        look: VellumLook,
        replaceApps: Boolean,
    ): Int {
        val resolved: Map<String, List<ComponentKey>> = if (replaceApps) {
            withContext(Dispatchers.IO) {
                val resolver = AppRoleResolver(context)
                look.surfaces.associate { surface ->
                    surface.id to resolver.resolveAll(surface.roles, MAX_APPS_PER_SURFACE)
                }
            }
        } else {
            emptyMap()
        }

        // Applying is several writes that only make sense together: leaving the surfaces updated
        // but the canvas switched off, because the user navigated away mid-apply and took the
        // composition scope with them, would look exactly like a bug.
        withContext(NonCancellable) {
            val current = preferences.vellumSurfaceSet.first()
            preferences.vellumSurfaceSet.set(value = merge(current, look, resolved, replaceApps))

            // A look that lands on a home screen with the ambient canvas switched off would change
            // nothing the user can see, and would read as the button being broken.
            preferences.vellumAmbientEnabled.set(value = true)
            preferences.vellumSurfacesEnabled.set(value = true)
        }

        return resolved.values.sumOf { it.size }
    }

    /**
     * Produces the surface set for [look], carrying the user's own choices across.
     *
     * Kept pure and separate from [apply] so the merge rules can be tested without a device.
     */
    fun merge(
        current: VellumSurfaceSet,
        look: VellumLook,
        resolvedApps: Map<String, List<ComponentKey>>,
        replaceApps: Boolean,
    ): VellumSurfaceSet {
        val byId = current.surfaces.associateBy { it.id }
        val applied = look.surfaces.map { lookSurface ->
            val existing = byId[lookSurface.id]
            VellumSurface(
                id = lookSurface.id,
                startMinute = lookSurface.startMinute,
                endMinute = lookSurface.endMinute,
                accent = lookSurface.accent,
                ambientIntensity = lookSurface.ambientIntensity,
                accentSecondary = lookSurface.secondary,
                backdropId = lookSurface.backdrop.id,
                apps = when {
                    !replaceApps -> existing?.apps.orEmpty()
                    else -> resolvedApps[lookSurface.id].orEmpty()
                },
                customLabel = existing?.customLabel,
                enabled = existing?.enabled ?: true,
            )
        }

        // Anything the user has that the look does not describe is left exactly as it was.
        val untouched = current.surfaces.filterNot { surface ->
            look.surfaces.any { it.id == surface.id }
        }
        return current.copy(surfaces = applied + untouched)
    }
}
