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

package app.lawnchair.vellum.drawer

import android.content.Context
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached

/**
 * Whether the app drawer is drawn as a single alphabetical column rather than a grid.
 *
 * Column mode is the ergonomic idea Niagara popularised: one app per row, name beside icon, read
 * top to bottom, with the alphabet rail under your thumb. Launcher3 already supplies the two hard
 * parts — an A–Z fast scroller and a `BubbleTextView` that can lay itself out horizontally — so
 * what Vellum adds is only the decision to use them together.
 *
 * This is read from Java, once per adapter, which is why it is a plain static rather than a flow.
 * Changing the preference reloads the grid, which rebuilds the adapter, so a cached read cannot go
 * stale.
 */
object VellumDrawerStyle {

    /**
     * Reads the in-memory preference snapshot.
     *
     * Guarded because this is called during view binding: if the preference manager is somehow not
     * available yet, a grid drawer is the safe answer, not a crash on the drawer opening.
     */
    @JvmStatic
    fun isColumnMode(context: Context): Boolean = runCatching {
        val preferences = PreferenceManager2.getInstance(context)
        preferences.vellumColumnDrawer.firstCached(preferences)
    }.getOrDefault(false)
}
