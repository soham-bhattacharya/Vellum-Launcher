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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.VellumLooks
import app.lawnchair.ui.preferences.navigation.VellumSurfaceEditor
import app.lawnchair.vellum.surface.VellumSurface
import app.lawnchair.vellum.surface.VellumSurfaceSet
import app.lawnchair.vellum.surface.formatMinuteOfDay
import com.android.launcher3.R

/**
 * The list of context surfaces. Each row leads to that surface's own editor; the switches here are
 * the fast path for "I do not want a Night surface at all".
 */
@Composable
fun VellumSurfacesPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    val enabledAdapter = prefs2.vellumSurfacesEnabled.getAdapter()
    val setAdapter = prefs2.vellumSurfaceSet.getAdapter()
    val surfaceSet by setAdapter.state

    PreferenceLayout(
        label = stringResource(id = R.string.vellum_surfaces_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup(description = stringResource(id = R.string.vellum_surfaces_explainer)) {
            SwitchPreference(
                adapter = enabledAdapter,
                label = stringResource(id = R.string.vellum_surfaces_label),
                description = stringResource(id = R.string.vellum_surfaces_description),
            )
        }

        PreferenceGroup {
            NavigationActionPreference(
                label = stringResource(id = R.string.vellum_looks_label),
                destination = VellumLooks,
                subtitle = stringResource(id = R.string.vellum_looks_description),
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.vellum_surfaces_heading)) {
            surfaceSet.surfaces.forEach { surface ->
                NavigationActionPreference(
                    label = surface.label(context),
                    destination = VellumSurfaceEditor(surface.id),
                    subtitle = surfaceSubtitle(surface),
                    endWidget = {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Color(surface.accent)),
                        )
                    },
                )
            }
        }

        PreferenceGroup {
            ClickablePreference(
                label = stringResource(id = R.string.vellum_surfaces_reset),
                confirmationText = stringResource(id = R.string.vellum_surfaces_reset_confirm),
                onClick = { setAdapter.onChange(VellumSurfaceSet()) },
            )
        }
    }
}

@Composable
private fun surfaceSubtitle(surface: VellumSurface): String {
    val context = LocalContext.current
    val window = "${formatMinuteOfDay(context, surface.startMinute)} – " +
        formatMinuteOfDay(context, surface.endMinute)
    val apps = context.resources.getQuantityString(
        R.plurals.vellum_surface_app_count,
        surface.apps.size,
        surface.apps.size,
    )
    return if (surface.enabled) {
        "$window · $apps"
    } else {
        "${stringResource(id = R.string.vellum_surface_off)} · $window"
    }
}
