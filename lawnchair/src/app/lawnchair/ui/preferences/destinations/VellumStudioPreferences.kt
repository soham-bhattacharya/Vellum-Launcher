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

package app.lawnchair.ui.preferences.destinations

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.VellumIconPacks
import app.lawnchair.ui.preferences.navigation.VellumLooks
import app.lawnchair.ui.preferences.navigation.VellumSurfaces
import app.lawnchair.ui.preferences.navigation.VellumWallpapers
import com.android.launcher3.R

/** The front door for Vellum's opinionated design and context features. */
@Composable
fun VellumStudioPreferences(
    modifier: Modifier = Modifier,
) {
    val prefs2 = preferenceManager2()

    PreferenceLayout(
        label = stringResource(id = R.string.vellum_studio_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup(heading = stringResource(id = R.string.vellum_studio_design_heading)) {
            NavigationActionPreference(
                label = stringResource(id = R.string.vellum_looks_label),
                destination = VellumLooks,
                subtitle = stringResource(id = R.string.vellum_looks_description),
            )
            NavigationActionPreference(
                label = stringResource(id = R.string.vellum_wallpapers_label),
                destination = VellumWallpapers,
                subtitle = stringResource(id = R.string.vellum_wallpapers_description),
            )
            NavigationActionPreference(
                label = stringResource(id = R.string.vellum_packs_label),
                destination = VellumIconPacks,
                subtitle = stringResource(id = R.string.vellum_packs_description),
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.vellum_studio_atmosphere_heading)) {
            val ambientEnabledAdapter = prefs2.vellumAmbientEnabled.getAdapter()
            SwitchPreference(
                adapter = ambientEnabledAdapter,
                label = stringResource(id = R.string.vellum_ambient_label),
                description = stringResource(id = R.string.vellum_ambient_description),
            )
            ExpandAndShrink(visible = ambientEnabledAdapter.state.value) {
                SliderPreference(
                    label = stringResource(id = R.string.vellum_ambient_intensity_label),
                    adapter = prefs2.vellumAmbientIntensity.getAdapter(),
                    valueRange = 0f..1f,
                    step = .05f,
                    showAsPercentage = true,
                )
            }
            SwitchPreference(
                adapter = prefs2.vellumHaloEnabled.getAdapter(),
                label = stringResource(id = R.string.vellum_halo_label),
                description = stringResource(id = R.string.vellum_halo_preference_description),
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.vellum_studio_context_heading)) {
            val surfacesEnabledAdapter = prefs2.vellumSurfacesEnabled.getAdapter()
            SwitchPreference(
                adapter = surfacesEnabledAdapter,
                label = stringResource(id = R.string.vellum_surfaces_label),
                description = stringResource(id = R.string.vellum_surfaces_description),
            )
            ExpandAndShrink(visible = surfacesEnabledAdapter.state.value) {
                NavigationActionPreference(
                    label = stringResource(id = R.string.vellum_surfaces_heading),
                    destination = VellumSurfaces,
                    subtitle = stringResource(id = R.string.vellum_surfaces_edit_subtitle),
                )
            }
        }

        PreferenceGroup(heading = stringResource(id = R.string.vellum_studio_layout_heading)) {
            SwitchPreference(
                adapter = prefs2.vellumColumnDrawer.getAdapter(),
                label = stringResource(id = R.string.vellum_column_drawer_label),
                description = stringResource(id = R.string.vellum_column_drawer_description),
            )
        }
    }
}
