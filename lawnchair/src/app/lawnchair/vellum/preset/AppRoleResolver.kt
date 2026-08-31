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
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log
import com.android.launcher3.util.ComponentKey

/**
 * Turns [AppRole]s into components that exist on *this* device.
 *
 * Resolution deliberately starts from the set of apps the launcher can already launch, obtained
 * from [LauncherApps], rather than from raw package manager queries. A launcher is always allowed
 * to see every launchable app, so this sidesteps Android 11 package visibility entirely and
 * guarantees that anything returned can actually be tapped: a role that resolves to a package with
 * no launcher entry (a headless provider, a disabled system stub) is treated as unresolved rather
 * than becoming a dead icon.
 *
 * Building an instance queries the package manager. Construct it off the main thread.
 */
class AppRoleResolver(private val context: Context) {

    private val user = Process.myUserHandle()

    /** Package name to its first launchable component, for everything on the current profile. */
    private val launchable: Map<String, ComponentKey> = buildLaunchableIndex()

    /** True when the device exposed no launchable apps at all, which makes every role unresolvable. */
    val isEmpty: Boolean get() = launchable.isEmpty()

    private fun buildLaunchableIndex(): Map<String, ComponentKey> {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
            ?: return emptyMap()
        val activities = runCatching { launcherApps.getActivityList(null, user) }
            .onFailure { Log.w(TAG, "Could not list launchable apps", it) }
            .getOrNull()
            .orEmpty()

        val index = LinkedHashMap<String, ComponentKey>(activities.size)
        activities.forEach { info ->
            val packageName = info.componentName.packageName
            // Never offer the launcher itself as the answer to a role.
            if (packageName == context.packageName) return@forEach
            index.getOrPut(packageName) { ComponentKey(info.componentName, user) }
        }
        return index
    }

    /**
     * Finds the app that fills [role], or null when this device has nothing suitable.
     *
     * Callers are expected to tolerate null. A phone with no calendar app should end up with a
     * surface that has one fewer app on it, not with a broken icon.
     */
    fun resolve(role: AppRole): ComponentKey? = systemDefault(role)
        ?: role.intents.firstNotNullOfOrNull(::fromIntent)
        ?: role.packages.firstNotNullOfOrNull(launchable::get)

    /**
     * Resolves [roles] in order, dropping the ones this device cannot fill and any app that an
     * earlier role already claimed, so a surface never shows the same icon twice.
     */
    fun resolveAll(roles: List<AppRole>, limit: Int = Int.MAX_VALUE): List<ComponentKey> {
        val result = mutableListOf<ComponentKey>()
        val claimed = mutableSetOf<String>()
        for (role in roles) {
            if (result.size >= limit) break
            val key = resolve(role) ?: continue
            if (claimed.add(key.componentName.packageName)) result += key
        }
        return result
    }

    /**
     * The system's own answer, where one exists. These beat any heuristic: if the user has chosen a
     * default dialer, that is the phone app, whatever its package name happens to be.
     */
    private fun systemDefault(role: AppRole): ComponentKey? = when (role) {
        AppRole.PHONE -> runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        }.getOrNull()?.let(launchable::get)

        AppRole.MESSAGES -> runCatching {
            Telephony.Sms.getDefaultSmsPackage(context)
        }.getOrNull()?.let(launchable::get)

        else -> null
    }

    /**
     * The first app that both answers [intent] and has a launcher entry.
     *
     * Queried without [android.content.pm.PackageManager.MATCH_DEFAULT_ONLY] on purpose: the
     * `CATEGORY_APP_*` filters are not required to declare `CATEGORY_DEFAULT`, and requiring it
     * silently drops perfectly good OEM apps.
     */
    private fun fromIntent(intent: Intent): ComponentKey? = runCatching {
        context.packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { it.activityInfo?.packageName }
            .mapNotNull(launchable::get)
            .firstOrNull()
    }.getOrNull()

    private companion object {
        const val TAG = "AppRoleResolver"
    }
}
