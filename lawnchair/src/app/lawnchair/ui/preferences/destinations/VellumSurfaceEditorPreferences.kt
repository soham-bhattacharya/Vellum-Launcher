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

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.PreferenceAdapter
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.TextPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.VellumSurfaceApps
import app.lawnchair.vellum.surface.VellumSurface
import app.lawnchair.vellum.surface.formatMinuteOfDay
import com.android.launcher3.R

/** A palette wide enough to make each surface distinct without asking anyone to mix a colour. */
private val SURFACE_SWATCHES = listOf(
    0xFFF2A65A,
    0xFFEF7B6B,
    0xFFE05C8A,
    0xFF8C69FF,
    0xFF5AA9F2,
    0xFF35C3B4,
    0xFF6FBF5B,
    0xFF3B4A8C,
).map { it.toInt() }

/** Full editor for one context surface: name, window, atmosphere, and its apps. */
@Composable
fun VellumSurfaceEditorPreferences(
    surfaceId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val setAdapter = preferenceManager2().vellumSurfaceSet.getAdapter()
    val surfaceSet by setAdapter.state
    val surface = surfaceSet.surfaces.firstOrNull { it.id == surfaceId }

    if (surface == null) {
        // The surface was removed underneath us (a reset while this screen was open).
        PreferenceLayout(
            label = stringResource(id = R.string.vellum_surfaces_label),
            backArrowVisible = !LocalIsExpandedScreen.current,
            modifier = modifier,
        ) {}
        return
    }

    fun update(transform: (VellumSurface) -> VellumSurface) {
        setAdapter.onChange(
            surfaceSet.copy(
                surfaces = surfaceSet.surfaces.map { if (it.id == surfaceId) transform(it) else it },
            ),
        )
    }

    fun pickTime(initialMinute: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            initialMinute / 60,
            initialMinute % 60,
            DateFormat.is24HourFormat(context),
        ).show()
    }

    PreferenceLayout(
        label = surface.label(context),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            SwitchPreference(
                checked = surface.enabled,
                onCheckedChange = { enabled -> update { it.copy(enabled = enabled) } },
                label = stringResource(id = R.string.vellum_surface_enabled_label),
            )
            TextPreference(
                value = surface.customLabel ?: surface.label(context),
                onChange = { name ->
                    update { it.copy(customLabel = name.trim().ifBlank { null }) }
                },
                label = stringResource(id = R.string.vellum_surface_name_label),
            )
        }

        PreferenceGroup(
            heading = stringResource(id = R.string.vellum_surface_window_heading),
            description = stringResource(id = R.string.vellum_surface_window_description),
        ) {
            ClickablePreference(
                label = stringResource(id = R.string.vellum_surface_starts_at),
                subtitle = formatMinuteOfDay(context, surface.startMinute),
                onClick = {
                    pickTime(surface.startMinute) { minute ->
                        setAdapter.onChange(surfaceSet.withStart(surfaceId, minute))
                    }
                },
            )
            ClickablePreference(
                label = stringResource(id = R.string.vellum_surface_ends_at),
                subtitle = formatMinuteOfDay(context, surface.endMinute),
                onClick = {
                    pickTime(surface.endMinute) { minute ->
                        setAdapter.onChange(surfaceSet.withEnd(surfaceId, minute))
                    }
                },
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.vellum_surface_atmosphere_heading)) {
            SwatchRow(
                selected = surface.accent,
                onSelect = { accent -> update { it.copy(accent = accent) } },
            )
            SliderPreference(
                label = stringResource(id = R.string.vellum_ambient_intensity_label),
                adapter = rememberValueAdapter(surface.ambientIntensity) { value ->
                    update { it.copy(ambientIntensity = value) }
                },
                valueRange = 0f..1f,
                step = .05f,
                showAsPercentage = true,
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.vellum_surface_apps_heading)) {
            NavigationActionPreference(
                label = stringResource(id = R.string.vellum_surface_choose_apps),
                destination = VellumSurfaceApps(surfaceId),
                subtitle = context.resources.getQuantityString(
                    R.plurals.vellum_surface_app_count,
                    surface.apps.size,
                    surface.apps.size,
                ),
            )
        }
    }
}

@Composable
private fun SwatchRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SURFACE_SWATCHES.forEach { swatch ->
            val isSelected = swatch == selected
            Box(
                Modifier
                    .size(if (isSelected) 34.dp else 28.dp)
                    .clip(CircleShape)
                    .background(Color(swatch))
                    .then(
                        if (isSelected) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(swatch) },
            )
        }
    }
}

/**
 * Bridges a plain value plus setter to the [PreferenceAdapter] the shared preference controls
 * expect. Surface fields live inside one serialized set rather than in their own DataStore keys, so
 * they have no adapter of their own.
 */
@Composable
private fun <T> rememberValueAdapter(value: T, onChange: (T) -> Unit): PreferenceAdapter<T> {
    val currentValue = rememberUpdatedState(value)
    val currentOnChange = rememberUpdatedState(onChange)
    return remember {
        object : PreferenceAdapter<T> {
            override val state: State<T> get() = currentValue
            override fun onChange(newValue: T) = currentOnChange.value(newValue)
        }
    }
}
