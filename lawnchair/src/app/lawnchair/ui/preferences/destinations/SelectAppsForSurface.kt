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

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.AppItemPlaceholder
import app.lawnchair.ui.preferences.components.layout.PreferenceLazyColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceScaffold
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import app.lawnchair.util.App
import app.lawnchair.util.appComparator
import app.lawnchair.util.appsState
import com.android.launcher3.R
import com.android.launcher3.util.MSDLPlayerWrapper
import com.google.android.msdl.data.model.MSDLToken
import java.util.Comparator.comparing

/**
 * Picks the apps that appear on one surface's panel.
 *
 * Selected apps sort to the top, the same convention the hidden apps screen uses, so a surface with
 * six apps does not require scrolling the whole drawer to review what is on it.
 */
@Composable
fun SelectAppsForSurface(
    surfaceId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mMSDLPlayerWrapper = MSDLPlayerWrapper.INSTANCE.get(context)
    val setAdapter = preferenceManager2().vellumSurfaceSet.getAdapter()
    val surfaceSet by setAdapter.state
    val surface = surfaceSet.surfaces.firstOrNull { it.id == surfaceId }
    val selected = surface?.apps.orEmpty().toSet()

    val apps by appsState(comparator = surfaceAppsComparator(selected.map(Any::toString).toSet()))
    val state = rememberLazyListState()

    val pageTitle = if (selected.isEmpty()) {
        stringResource(id = R.string.vellum_surface_choose_apps)
    } else {
        context.resources.getQuantityString(
            R.plurals.vellum_surface_app_count,
            selected.size,
            selected.size,
        )
    }

    PreferenceScaffold(
        label = pageTitle,
        modifier = modifier,
        isExpandedScreen = LocalIsExpandedScreen.current,
    ) {
        Crossfade(targetState = apps.isNotEmpty(), label = "") { present ->
            if (present) {
                PreferenceLazyColumn(it, state = state) {
                    preferenceGroupItems(
                        items = apps,
                        isFirstChild = true,
                    ) { _, app ->
                        AppItem(
                            app = app,
                            onClick = {
                                mMSDLPlayerWrapper.playToken(MSDLToken.TAP_MEDIUM_EMPHASIS)
                                val current = surfaceSet.surfaces
                                    .firstOrNull { candidate -> candidate.id == surfaceId }
                                    ?: return@AppItem
                                val updatedApps = if (app.key in current.apps) {
                                    current.apps - app.key
                                } else {
                                    current.apps + app.key
                                }
                                setAdapter.onChange(
                                    surfaceSet.copy(
                                        surfaces = surfaceSet.surfaces.map { candidate ->
                                            if (candidate.id == surfaceId) {
                                                candidate.copy(apps = updatedApps)
                                            } else {
                                                candidate
                                            }
                                        },
                                    ),
                                )
                            },
                        ) {
                            Checkbox(
                                checked = app.key in selected,
                                onCheckedChange = null,
                            )
                        }
                    }
                }
            } else {
                PreferenceLazyColumn(it, enabled = false) {
                    preferenceGroupItems(
                        count = 20,
                        isFirstChild = true,
                    ) {
                        AppItemPlaceholder {
                            Spacer(Modifier.width(24.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun surfaceAppsComparator(selected: Set<String>): Comparator<App> = comparing<App, Boolean> { app -> app.key.toString() !in selected }.then(appComparator)
