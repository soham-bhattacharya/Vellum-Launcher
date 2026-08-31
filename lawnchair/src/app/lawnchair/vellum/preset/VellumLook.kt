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

import androidx.annotation.StringRes
import app.lawnchair.vellum.backdrop.BackdropStyle
import app.lawnchair.vellum.surface.VellumSurface
import com.android.launcher3.R

/** One daypart inside a look: its window, its atmosphere, and the kinds of app that belong to it. */
data class LookSurface(
    val id: String,
    val startMinute: Int,
    val endMinute: Int,
    val accent: Int,
    val secondary: Int,
    val backdrop: BackdropStyle,
    val ambientIntensity: Float,
    val roles: List<AppRole>,
)

/**
 * A complete, ready-to-wear configuration of the home screen.
 *
 * Looks exist because the gap between "this launcher is configurable" and "this launcher looks
 * good" is where most launchers lose people. Somebody trying Vellum for ten minutes will not build
 * a palette from scratch; they will tap something that already looks finished, and decide from
 * that whether the app is worth their evening.
 *
 * A look is described in [AppRole]s rather than package names so that it lands complete on any
 * device. See [AppRoleResolver] for why that matters.
 */
data class VellumLook(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val taglineRes: Int,
    val surfaces: List<LookSurface>,
    /** Which daypart the gallery card renders. */
    private val showcaseIndex: Int,
) {
    val showcase: LookSurface get() = surfaces[showcaseIndex]

    /** The look's colours in day order, for the arc of dots under a gallery card. */
    val accents: List<Int> get() = surfaces.map { it.accent }

    companion object {

        /** Apps that suit a morning: what is happening today, and who needs answering. */
        private val MORNING = listOf(
            AppRole.MESSAGES,
            AppRole.CALENDAR,
            AppRole.EMAIL,
            AppRole.BROWSER,
            AppRole.CLOCK,
            AppRole.PHONE,
        )

        /** Apps that suit the working middle of the day. */
        private val DAY = listOf(
            AppRole.EMAIL,
            AppRole.CALENDAR,
            AppRole.BROWSER,
            AppRole.SEARCH,
            AppRole.MAPS,
            AppRole.NOTES,
        )

        /** Apps that suit an evening off. */
        private val EVENING = listOf(
            AppRole.SOCIAL,
            AppRole.VIDEO,
            AppRole.MUSIC,
            AppRole.CHAT,
            AppRole.MESSAGES,
            AppRole.BROWSER,
        )

        /** Apps that suit the last hour before sleep. */
        private val NIGHT = listOf(
            AppRole.CLOCK,
            AppRole.MUSIC,
            AppRole.VIDEO,
            AppRole.MESSAGES,
            AppRole.PHONE,
            AppRole.GALLERY,
        )

        private fun hm(hour: Int) = hour * 60

        /**
         * The shipped gallery.
         *
         * These are chosen to be genuinely different from one another rather than six variations on
         * a glow: there is a light one, a textured one, a nearly-black one and a loud one. A gallery
         * where every entry is the same design in a different hue teaches the user that the feature
         * is cosmetic.
         */
        fun all(): List<VellumLook> = listOf(bloom(), aurora(), dunes(), nocturne(), paper(), signal())

        val Default get() = bloom()

        fun byId(id: String?): VellumLook? = id?.let { wanted -> all().firstOrNull { it.id == wanted } }

        /** The signature: a warm sunrise climbing into a violet evening. */
        private fun bloom() = VellumLook(
            id = "bloom",
            nameRes = R.string.vellum_look_bloom,
            taglineRes = R.string.vellum_look_bloom_tagline,
            showcaseIndex = 2,
            surfaces = listOf(
                LookSurface(VellumSurface.ID_MORNING, hm(5), hm(11), 0xFFF2A65A.toInt(), 0xFFF6C89A.toInt(), BackdropStyle.BLOOM, .55f, MORNING),
                LookSurface(VellumSurface.ID_DAY, hm(11), hm(17), 0xFF5AA9F2.toInt(), 0xFF8FD3F4.toInt(), BackdropStyle.BLOOM, .40f, DAY),
                LookSurface(VellumSurface.ID_EVENING, hm(17), hm(22), 0xFF8C69FF.toInt(), 0xFFC77DFF.toInt(), BackdropStyle.BLOOM, .70f, EVENING),
                LookSurface(VellumSurface.ID_NIGHT, hm(22), hm(5), 0xFF3B4A8C.toInt(), 0xFF5C6BC0.toInt(), BackdropStyle.NOCTURNE, .85f, NIGHT),
            ),
        )

        /** Cold northern light: teal and deep sea, with ribbons instead of a single source. */
        private fun aurora() = VellumLook(
            id = "aurora",
            nameRes = R.string.vellum_look_aurora,
            taglineRes = R.string.vellum_look_aurora_tagline,
            showcaseIndex = 3,
            surfaces = listOf(
                LookSurface(VellumSurface.ID_MORNING, hm(5), hm(11), 0xFF2FB6A8.toInt(), 0xFF7FE3D4.toInt(), BackdropStyle.AURORA, .50f, MORNING),
                LookSurface(VellumSurface.ID_DAY, hm(11), hm(17), 0xFF3D8BFF.toInt(), 0xFF6FD0FF.toInt(), BackdropStyle.AURORA, .42f, DAY),
                LookSurface(VellumSurface.ID_EVENING, hm(17), hm(22), 0xFF6C5CE7.toInt(), 0xFF37E2C0.toInt(), BackdropStyle.AURORA, .72f, EVENING),
                LookSurface(VellumSurface.ID_NIGHT, hm(22), hm(5), 0xFF14324F.toInt(), 0xFF2E8B8B.toInt(), BackdropStyle.NOCTURNE, .88f, NIGHT),
            ),
        )

        /** Warm earth and layered horizons. The calm one. */
        private fun dunes() = VellumLook(
            id = "dunes",
            nameRes = R.string.vellum_look_dunes,
            taglineRes = R.string.vellum_look_dunes_tagline,
            showcaseIndex = 0,
            surfaces = listOf(
                LookSurface(VellumSurface.ID_MORNING, hm(5), hm(11), 0xFFE8A87C.toInt(), 0xFFF5D6B8.toInt(), BackdropStyle.DUNES, .58f, MORNING),
                LookSurface(VellumSurface.ID_DAY, hm(11), hm(17), 0xFFD98E63.toInt(), 0xFFEFC49A.toInt(), BackdropStyle.DUNES, .45f, CALM_DAY),
                LookSurface(VellumSurface.ID_EVENING, hm(17), hm(22), 0xFFB5563F.toInt(), 0xFFE08A5D.toInt(), BackdropStyle.DUNES, .72f, CALM_EVENING),
                LookSurface(VellumSurface.ID_NIGHT, hm(22), hm(5), 0xFF4A3428.toInt(), 0xFF8C5E3C.toInt(), BackdropStyle.DUNES, .80f, NIGHT),
            ),
        )

        /** Almost no light at all, for people who find most launchers too bright. */
        private fun nocturne() = VellumLook(
            id = "nocturne",
            nameRes = R.string.vellum_look_nocturne,
            taglineRes = R.string.vellum_look_nocturne_tagline,
            showcaseIndex = 3,
            surfaces = listOf(
                LookSurface(VellumSurface.ID_MORNING, hm(5), hm(11), 0xFF4C5A7A.toInt(), 0xFF8794B5.toInt(), BackdropStyle.VEIL, .45f, SPARSE_MORNING),
                LookSurface(VellumSurface.ID_DAY, hm(11), hm(17), 0xFF5E6B85.toInt(), 0xFF97A3BD.toInt(), BackdropStyle.VEIL, .32f, SPARSE_DAY),
                LookSurface(VellumSurface.ID_EVENING, hm(17), hm(22), 0xFF34406B.toInt(), 0xFF6272A4.toInt(), BackdropStyle.NOCTURNE, .70f, SPARSE_EVENING),
                LookSurface(VellumSurface.ID_NIGHT, hm(22), hm(5), 0xFF1B2340.toInt(), 0xFF3D4A78.toInt(), BackdropStyle.NOCTURNE, .92f, SPARSE_NIGHT),
            ),
        )

        /** Muted pigment on textured stock. Proof that the ambient layer does not have to glow. */
        private fun paper() = VellumLook(
            id = "paper",
            nameRes = R.string.vellum_look_paper,
            taglineRes = R.string.vellum_look_paper_tagline,
            showcaseIndex = 1,
            surfaces = listOf(
                LookSurface(VellumSurface.ID_MORNING, hm(5), hm(11), 0xFFC9A227.toInt(), 0xFFE3C766.toInt(), BackdropStyle.GRAIN, .50f, FOCUS_MORNING),
                LookSurface(VellumSurface.ID_DAY, hm(11), hm(17), 0xFF8A9A5B.toInt(), 0xFFB5C48E.toInt(), BackdropStyle.GRAIN, .42f, FOCUS_DAY),
                LookSurface(VellumSurface.ID_EVENING, hm(17), hm(22), 0xFFA15C4A.toInt(), 0xFFC98B6B.toInt(), BackdropStyle.GRAIN, .55f, CALM_EVENING),
                LookSurface(VellumSurface.ID_NIGHT, hm(22), hm(5), 0xFF4F5D5A.toInt(), 0xFF7C8C88.toInt(), BackdropStyle.GRAIN, .60f, SPARSE_NIGHT),
            ),
        )

        /** Loud on purpose, in overlapping colour fields. */
        private fun signal() = VellumLook(
            id = "signal",
            nameRes = R.string.vellum_look_signal,
            taglineRes = R.string.vellum_look_signal_tagline,
            showcaseIndex = 2,
            surfaces = listOf(
                LookSurface(VellumSurface.ID_MORNING, hm(5), hm(11), 0xFFFF6B35.toInt(), 0xFFFFD23F.toInt(), BackdropStyle.MESH, .60f, MORNING),
                LookSurface(VellumSurface.ID_DAY, hm(11), hm(17), 0xFF00B4D8.toInt(), 0xFF90E0EF.toInt(), BackdropStyle.MESH, .50f, DAY),
                LookSurface(VellumSurface.ID_EVENING, hm(17), hm(22), 0xFFF72585.toInt(), 0xFF7209B7.toInt(), BackdropStyle.MESH, .78f, LOUD_EVENING),
                LookSurface(VellumSurface.ID_NIGHT, hm(22), hm(5), 0xFF3A0CA3.toInt(), 0xFF4361EE.toInt(), BackdropStyle.MESH, .90f, LOUD_NIGHT),
            ),
        )

        // Curated variations. A look is a point of view about the whole day, not only its colours,
        // so the app selections shift with the mood as well.

        private val CALM_DAY = listOf(AppRole.NOTES, AppRole.CALENDAR, AppRole.BROWSER, AppRole.MUSIC, AppRole.GALLERY)
        private val CALM_EVENING = listOf(AppRole.MUSIC, AppRole.GALLERY, AppRole.NOTES, AppRole.MESSAGES, AppRole.VIDEO)

        private val SPARSE_MORNING = listOf(AppRole.CLOCK, AppRole.MESSAGES, AppRole.CALENDAR)
        private val SPARSE_DAY = listOf(AppRole.EMAIL, AppRole.BROWSER, AppRole.CALENDAR)
        private val SPARSE_EVENING = listOf(AppRole.MUSIC, AppRole.MESSAGES, AppRole.VIDEO)
        private val SPARSE_NIGHT = listOf(AppRole.CLOCK, AppRole.MUSIC, AppRole.PHONE)

        private val FOCUS_MORNING = listOf(AppRole.CALENDAR, AppRole.EMAIL, AppRole.NOTES, AppRole.MESSAGES, AppRole.CLOCK)
        private val FOCUS_DAY = listOf(AppRole.NOTES, AppRole.EMAIL, AppRole.CALENDAR, AppRole.BROWSER, AppRole.CALCULATOR)

        private val LOUD_EVENING = listOf(AppRole.SOCIAL, AppRole.VIDEO, AppRole.MUSIC, AppRole.CHAT, AppRole.CAMERA, AppRole.GALLERY)
        private val LOUD_NIGHT = listOf(AppRole.VIDEO, AppRole.MUSIC, AppRole.SOCIAL, AppRole.CHAT, AppRole.CLOCK)
    }
}
