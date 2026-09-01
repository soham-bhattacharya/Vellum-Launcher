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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.BackdropPreview
import app.lawnchair.ui.preferences.components.colorpreference.pickers.CustomColorPicker
import app.lawnchair.ui.preferences.components.layout.BottomSpacer
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.vellum.backdrop.BackdropPalette
import com.android.launcher3.R

/** Exact palette editor for one half of a Context Surface's two-colour atmosphere. */
@Composable
fun VellumSurfaceColorPreferences(
    surfaceId: String,
    companion: Boolean,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val setAdapter = preferenceManager2().vellumSurfaceSet.getAdapter()
    val surfaceSet by setAdapter.state
    val surface = surfaceSet.byId(surfaceId)

    if (surface == null) {
        PreferenceLayout(
            label = stringResource(id = R.string.vellum_surfaces_label),
            backArrowVisible = !LocalIsExpandedScreen.current,
            modifier = modifier,
        ) {}
        return
    }

    val initialColor = if (companion) surface.palette().secondary else surface.accent
    val selectedColor = rememberSaveable(surfaceId, companion) { mutableIntStateOf(initialColor) }
    val colorLabel = stringResource(
        id = if (companion) {
            R.string.vellum_surface_companion_colour
        } else {
            R.string.vellum_surface_primary_colour
        },
    )
    val description = stringResource(
        id = if (companion) {
            R.string.vellum_surface_colour_picker_companion
        } else {
            R.string.vellum_surface_colour_picker_primary
        },
    )
    val palette = if (companion) {
        BackdropPalette.of(surface.accent, selectedColor.intValue)
    } else {
        BackdropPalette.of(selectedColor.intValue, null)
    }

    fun updateSurface(accent: Int = surface.accent, accentSecondary: Int? = surface.accentSecondary) {
        setAdapter.onChange(
            surfaceSet.copy(
                surfaces = surfaceSet.surfaces.map {
                    if (it.id == surfaceId) {
                        it.copy(accent = accent, accentSecondary = accentSecondary)
                    } else {
                        it
                    }
                },
            ),
        )
        navController.popBackStack()
    }

    PreferenceLayout(
        label = colorLabel,
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (companion) {
                            OutlinedButton(
                                onClick = { updateSurface(accentSecondary = null) },
                                enabled = surface.accentSecondary != null,
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(id = R.string.vellum_surface_colour_use_automatic))
                            }
                        }
                        Button(
                            onClick = {
                                if (companion) {
                                    updateSurface(accentSecondary = selectedColor.intValue)
                                } else {
                                    // A newly chosen lead colour gets a freshly derived companion
                                    // so a palette inherited from another Look cannot accidentally
                                    // linger.
                                    updateSurface(accent = selectedColor.intValue, accentSecondary = null)
                                }
                            },
                            enabled = if (companion) {
                                surface.accentSecondary != selectedColor.intValue
                            } else {
                                surface.accent != selectedColor.intValue
                            },
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(id = R.string.action_apply))
                        }
                    }
                    BottomSpacer()
                }
            }
        },
    ) {
        BackdropPreview(
            style = surface.backdrop,
            palette = palette,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(22.dp)),
        )
        PreferenceGroup(description = description) {}
        CustomColorPicker(
            selectedColor = selectedColor.intValue,
            onSelect = { selectedColor.intValue = it },
        )
    }
}
