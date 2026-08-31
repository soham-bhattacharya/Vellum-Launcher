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

package app.lawnchair.vellum.surface

import android.content.Context
import android.text.format.DateFormat
import java.util.Calendar

/**
 * Formats a minute-of-day using the device's own 12/24-hour setting and locale.
 *
 * Uses [DateFormat.getTimeFormat] rather than a `java.time` pattern so that a user who has forced
 * 24-hour time on a locale that normally uses 12-hour time still sees what they asked for.
 */
fun formatMinuteOfDay(context: Context, minuteOfDay: Int): String {
    val wrapped = minuteOfDay.mod(VellumSurface.MINUTES_PER_DAY)
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, wrapped / 60)
        set(Calendar.MINUTE, wrapped % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}
