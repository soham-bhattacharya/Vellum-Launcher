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

import android.content.Intent
import android.provider.AlarmClock
import android.provider.MediaStore

/**
 * A kind of app, rather than a particular one.
 *
 * Presets are written in terms of roles because the actual packages differ on every device: the
 * messaging app is Google Messages on a Pixel, Samsung Messages on a Galaxy, and something else
 * again on a Xiaomi. A preset that shipped hard-coded component names would arrive half empty on
 * most phones, which is the opposite of what a first-run showcase is for.
 *
 * A role is described as plain strings rather than as ready-made [Intent]s for two reasons: an
 * `Intent` is mutable, so a single instance shared by every caller of a role is an aliasing bug
 * waiting to happen; and building one at class-initialisation time would make the whole catalogue
 * unloadable outside an Android runtime. All of the constants referenced below are compile-time
 * string constants, so nothing here touches the framework until [intents] is asked for.
 *
 * Probes are tried in the order they appear in [intents]: explicit actions first, since those name
 * a capability precisely, then the broader `CATEGORY_APP_*` buckets. [packages] is the last resort.
 */
enum class AppRole(
    private val actions: List<String> = emptyList(),
    private val categories: List<String> = emptyList(),
    val packages: List<String> = emptyList(),
) {
    PHONE(
        // ACTION_DIAL names the dialer exactly; the contacts category is only a fallback for
        // devices where the two are one app and the dial intent has no launchable handler.
        actions = listOf(Intent.ACTION_DIAL),
        categories = listOf(Intent.CATEGORY_APP_CONTACTS),
        packages = listOf(
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.dialer",
            "com.android.contacts",
        ),
    ),

    MESSAGES(
        categories = listOf(Intent.CATEGORY_APP_MESSAGING),
        packages = listOf(
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.messaging",
            "com.android.mms",
        ),
    ),

    BROWSER(
        categories = listOf(Intent.CATEGORY_APP_BROWSER),
        packages = listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.opera.browser",
            "com.sec.android.app.sbrowser",
        ),
    ),

    EMAIL(
        categories = listOf(Intent.CATEGORY_APP_EMAIL),
        packages = listOf(
            "com.google.android.gm",
            "com.microsoft.office.outlook",
            "com.samsung.android.email.provider",
            "ch.protonmail.android",
            "com.fsck.k9",
        ),
    ),

    CALENDAR(
        categories = listOf(Intent.CATEGORY_APP_CALENDAR),
        packages = listOf(
            "com.google.android.calendar",
            "com.samsung.android.calendar",
            "com.android.calendar",
        ),
    ),

    CAMERA(
        actions = listOf(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        packages = listOf(
            "com.google.android.GoogleCamera",
            "com.sec.android.app.camera",
            "com.android.camera2",
            "com.android.camera",
        ),
    ),

    GALLERY(
        categories = listOf(Intent.CATEGORY_APP_GALLERY),
        packages = listOf(
            "com.google.android.apps.photos",
            "com.sec.android.gallery3d",
            "com.android.gallery3d",
        ),
    ),

    MUSIC(
        categories = listOf(Intent.CATEGORY_APP_MUSIC),
        packages = listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.apple.android.music",
            "deezer.android.app",
            "com.soundcloud.android",
        ),
    ),

    MAPS(
        categories = listOf(Intent.CATEGORY_APP_MAPS),
        packages = listOf(
            "com.google.android.apps.maps",
            "com.waze",
            "org.osmand.plus",
        ),
    ),

    CLOCK(
        actions = listOf(AlarmClock.ACTION_SHOW_ALARMS),
        packages = listOf(
            "com.google.android.deskclock",
            "com.sec.android.app.clockpackage",
            "com.android.deskclock",
        ),
    ),

    CALCULATOR(
        categories = listOf(Intent.CATEGORY_APP_CALCULATOR),
        packages = listOf(
            "com.google.android.calculator",
            "com.sec.android.app.popupcalculator",
            "com.android.calculator2",
        ),
    ),

    SEARCH(
        packages = listOf(
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.searchlite",
        ),
    ),

    STORE(
        packages = listOf(
            "com.android.vending",
            "com.sec.android.app.samsungapps",
            "org.fdroid.fdroid",
        ),
    ),

    SOCIAL(
        packages = listOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",
            "com.reddit.frontpage",
            "com.twitter.android",
            "com.facebook.katana",
            "com.pinterest",
        ),
    ),

    CHAT(
        packages = listOf(
            "com.whatsapp",
            "org.telegram.messenger",
            "com.discord",
            "com.Slack",
            "org.thoughtcrime.securesms",
        ),
    ),

    VIDEO(
        packages = listOf(
            "com.google.android.youtube",
            "com.netflix.mediaclient",
            "com.disney.disneyplus",
            "tv.twitch.android.app",
        ),
    ),

    NOTES(
        packages = listOf(
            "com.google.android.keep",
            "com.samsung.android.app.notes",
            "md.obsidian",
            "com.notion.id",
            "com.todoist",
        ),
    ),

    FITNESS(
        packages = listOf(
            "com.google.android.apps.fitness",
            "com.sec.android.app.shealth",
            "com.strava",
            "com.fitbit.FitbitMobile",
        ),
    ),
    ;

    /** True when this role can only be found by name, with no capability to probe for. */
    val isPackageOnly: Boolean get() = actions.isEmpty() && categories.isEmpty()

    /**
     * Fresh probe intents, in the order they should be tried.
     *
     * Built on each access rather than cached: callers hand these to the package manager, and an
     * `Intent` is mutable enough that sharing one instance between roles would be a real hazard for
     * a saving of a few allocations on a path that runs once per look applied.
     */
    val intents: List<Intent>
        get() = actions.map { Intent(it) } +
            categories.map { Intent(Intent.ACTION_MAIN).addCategory(it) }
}
