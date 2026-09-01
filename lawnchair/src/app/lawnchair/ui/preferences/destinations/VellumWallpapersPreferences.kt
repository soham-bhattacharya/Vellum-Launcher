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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.vellum.wallpaper.VellumWallpaper
import app.lawnchair.vellum.wallpaper.VellumWallpaperApplier
import com.android.launcher3.R
import kotlinx.coroutines.launch

/** Gallery of original, icon-safe wallpapers shipped as part of Vellum's visual system. */
@Composable
fun VellumWallpapersPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<VellumWallpaper?>(null) }

    PreferenceLayout(
        label = stringResource(id = R.string.vellum_wallpapers_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
        bottomBar = { SnackbarHost(hostState = snackbarHostState) },
    ) {
        PreferenceGroup(
            heading = stringResource(id = R.string.vellum_wallpapers_heading),
            description = stringResource(id = R.string.vellum_wallpapers_explainer),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                VellumWallpaper.all().forEach { wallpaper ->
                    WallpaperCard(
                        wallpaper = wallpaper,
                        onClick = { pending = wallpaper },
                    )
                }
            }
        }
    }

    pending?.let { wallpaper ->
        val name = stringResource(id = wallpaper.nameRes)
        val successMessage = stringResource(id = R.string.vellum_wallpaper_applied, name)
        val failureMessage = stringResource(id = R.string.vellum_wallpaper_failed)

        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(name) },
            text = { Text(stringResource(id = R.string.vellum_wallpaper_apply_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        scope.launch {
                            val result = VellumWallpaperApplier.apply(context, wallpaper)
                            snackbarHostState.showSnackbar(
                                if (result.isSuccess) successMessage else failureMessage,
                            )
                        }
                    },
                ) {
                    Text(stringResource(id = R.string.vellum_wallpaper_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun WallpaperCard(
    wallpaper: VellumWallpaper,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = stringResource(id = wallpaper.nameRes)
    val description = stringResource(id = wallpaper.descriptionRes)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$name. $description"
                role = Role.Button
            },
    ) {
        Image(
            painter = painterResource(id = wallpaper.drawableRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color(0xD9111218),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xD9FFFFFF),
            )
        }
    }
}
