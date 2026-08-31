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

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.BackdropPreview
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.vellum.backdrop.BackdropPalette
import app.lawnchair.vellum.preset.LookApplier
import app.lawnchair.vellum.preset.VellumLook
import com.android.launcher3.R
import kotlinx.coroutines.launch

/**
 * The gallery of complete designs.
 *
 * Each card renders the real backdrop rather than a bundled screenshot, so what is on the card is
 * exactly what applying it produces, including on a device whose accent colour or display size the
 * screenshot could never have anticipated.
 */
@Composable
fun VellumLooksPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs2 = preferenceManager2()
    val lookAdapter = prefs2.vellumLookId.getAdapter()
    val currentLookId by lookAdapter.state
    val scope = rememberCoroutineScope()

    var pending by remember { mutableStateOf<VellumLook?>(null) }

    PreferenceLayout(
        label = stringResource(id = R.string.vellum_looks_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup(
            heading = stringResource(id = R.string.vellum_looks_heading),
            description = stringResource(id = R.string.vellum_looks_explainer),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                VellumLook.all().forEach { look ->
                    LookCard(
                        look = look,
                        isCurrent = look.id == currentLookId,
                        onClick = { pending = look },
                    )
                }
            }
        }
    }

    pending?.let { look ->
        val name = stringResource(id = look.nameRes)

        fun apply(withApps: Boolean) {
            pending = null
            scope.launch {
                val resolvedApps = LookApplier.apply(context, prefs2, look, replaceApps = withApps)
                lookAdapter.onChange(look.id)
                val message = when {
                    // Asking for apps and getting none is a real outcome on a stripped-down or
                    // freshly-flashed device, and saying so beats leaving the user to work out
                    // why the panel is still empty.
                    withApps && resolvedApps == 0 -> context.getString(R.string.vellum_looks_no_apps_found)

                    withApps -> context.getString(R.string.vellum_look_applied_with_apps, name)

                    else -> context.getString(R.string.vellum_look_applied, name)
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }

        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(name) },
            text = { Text(stringResource(id = R.string.vellum_look_apply_apps_confirm)) },
            // Three actions rather than two: filling the surfaces replaces apps the user may have
            // pinned by hand, so "apply the design only" has to be reachable without a second trip
            // through the editor.
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { apply(withApps = false) }) {
                        Text(stringResource(id = R.string.vellum_look_apply))
                    }
                    TextButton(onClick = { apply(withApps = true) }) {
                        Text(stringResource(id = R.string.vellum_look_apply_with_apps))
                    }
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
private fun LookCard(
    look: VellumLook,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showcase = look.showcase
    val name = stringResource(id = look.nameRes)

    val tagline = stringResource(id = look.taglineRes)
    val description = stringResource(id = R.string.vellum_look_preview_accessibility, name)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            // The card is a picture with text drawn over it; without this a screen reader
            // announces the name and tagline as two loose labels and never says it is tappable.
            .semantics(mergeDescendants = true) {
                contentDescription = "$description. $tagline"
                role = Role.Button
            },
    ) {
        BackdropPreview(
            style = showcase.backdrop,
            palette = BackdropPalette.of(showcase.accent, showcase.secondary),
            modifier = Modifier.fillMaxSize(),
        )

        // A scrim so the name stays legible over a design that might be pale at the bottom.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color(0xCC0B0D12),
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            look.accents.forEach { accent ->
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(accent)),
                )
            }
        }

        if (isCurrent) {
            Text(
                text = stringResource(id = R.string.vellum_look_current),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = stringResource(id = look.taglineRes),
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xCCFFFFFF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
