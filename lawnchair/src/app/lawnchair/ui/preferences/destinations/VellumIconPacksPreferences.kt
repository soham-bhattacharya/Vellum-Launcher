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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.GeneralIconPack
import app.lawnchair.vellum.iconpack.CuratedIconPack
import com.android.launcher3.R
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A short, opinionated list of icon packs worth having.
 *
 * Vellum does not bundle any of these — they are separately licensed and individually larger than
 * the launcher. What this screen fixes is discovery: the stock picker can only ever show what you
 * already installed, which is no use to somebody who has none.
 *
 * A pack already on the device can be applied from here in one tap. One that is not is a link out,
 * and is never presented as though it were ready to use.
 */
@Composable
fun VellumIconPacksPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val iconPackAdapter = prefs.iconPackPackage.getAdapter()
    val currentPackage by iconPackAdapter.state

    PreferenceLayout(
        label = stringResource(id = R.string.vellum_packs_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup(
            heading = stringResource(id = R.string.vellum_packs_heading),
            description = stringResource(id = R.string.vellum_packs_explainer),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CuratedIconPack.all().forEach { pack ->
                    CuratedPackCard(
                        pack = pack,
                        currentPackage = currentPackage,
                        onApply = { packageName ->
                            iconPackAdapter.onChange(packageName)
                            Toast.makeText(
                                context,
                                context.getString(
                                    R.string.vellum_pack_applied,
                                    context.getString(pack.nameRes),
                                ),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onGet = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pack.storeUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                // A device with no browser and no store is unusual but real.
                                Toast.makeText(
                                    context,
                                    R.string.vellum_pack_no_store,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                    )
                }
            }
        }

        PreferenceGroup {
            NavigationActionPreference(
                label = stringResource(id = R.string.vellum_packs_all_installed),
                destination = GeneralIconPack,
                subtitle = stringResource(id = R.string.vellum_packs_all_installed_subtitle),
            )
        }
    }
}

@Composable
private fun CuratedPackCard(
    pack: CuratedIconPack,
    currentPackage: String,
    onApply: (String) -> Unit,
    onGet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Package manager work, kept off the composition thread. Keyed on the pack so a recomposition
    // does not re-query every card.
    val installed by produceState<String?>(initialValue = null, pack.id) {
        value = withContext(Dispatchers.IO) { pack.installedPackage(context.packageManager) }
    }
    val icon by produceState<Drawable?>(initialValue = null, installed) {
        val packageName = installed
        value = if (packageName == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
            }
        }
    }

    val inUse = installed != null && installed == currentPackage

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable {
                installed?.let(onApply) ?: onGet()
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(pack.accent)),
            contentAlignment = Alignment.Center,
        ) {
            // An installed pack shows its own launcher icon; one that is not installed has no
            // assets to show, and pretending otherwise would misrepresent what tapping does.
            icon?.let {
                Icon(
                    painter = rememberDrawablePainter(it),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(id = pack.nameRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(id = pack.taglineRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (pack.isOpenSource) {
                Text(
                    text = stringResource(id = R.string.vellum_pack_open_source),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Text(
            text = stringResource(
                id = when {
                    inUse -> R.string.vellum_pack_in_use
                    installed != null -> R.string.vellum_pack_apply
                    else -> R.string.vellum_pack_get
                },
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (inUse) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
}
