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

package app.lawnchair.vellum.backdrop

import android.graphics.Canvas
import android.graphics.Color
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import com.android.launcher3.R

/**
 * Where a backdrop is allowed to paint.
 *
 * A backdrop is drawn across two views: a wash that never moves and a field that is translated as
 * the workspace pages. The field is deliberately larger than the screen so that translating it can
 * never expose an empty edge, which is why the layer size and the visible content rect are tracked
 * separately: gradients must fill the whole layer, but composition (where the glow sits, where the
 * horizon falls) has to be measured against the part the user can actually see.
 */
data class BackdropGeometry(
    val layerWidth: Float,
    val layerHeight: Float,
    val contentLeft: Float,
    val contentTop: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val density: Float,
) {
    val isEmpty: Boolean get() = layerWidth <= 0f || layerHeight <= 0f

    /** Maps a fraction of the visible screen to a horizontal coordinate in the layer. */
    fun x(fraction: Float): Float = contentLeft + contentWidth * fraction

    /** Maps a fraction of the visible screen to a vertical coordinate in the layer. */
    fun y(fraction: Float): Float = contentTop + contentHeight * fraction

    /** Density-independent pixels, in this geometry's pixels. */
    fun dp(value: Float): Float = value * density

    companion object {
        val Empty = BackdropGeometry(0f, 0f, 0f, 0f, 0f, 0f, 1f)

        /** Geometry for a surface with no parallax overscan, such as a settings preview. */
        fun flat(width: Float, height: Float, density: Float) = BackdropGeometry(
            layerWidth = width,
            layerHeight = height,
            contentLeft = 0f,
            contentTop = 0f,
            contentWidth = width,
            contentHeight = height,
            density = density,
        )
    }
}

/**
 * The colours a backdrop paints with.
 *
 * Every backdrop is handed the same set of roles, so a colour chosen once in the surface editor
 * reads correctly whichever backdrop it is paired with.
 */
class BackdropPalette private constructor(
    /** The colour the user picked. */
    val accent: Int,
    /** A companion hue. Derived from [accent] when a surface has not specified one. */
    val secondary: Int,
) {
    /** [accent] lifted toward white; used for highlights and the brightest part of a glow. */
    val soft: Int = ColorUtils.blendARGB(accent, Color.WHITE, .24f)

    /** [accent] sunk toward a cool violet; used for shadow and depth. */
    val deep: Int = ColorUtils.blendARGB(accent, DEEP_TINT, .46f)

    /** [secondary] lifted toward white. */
    val secondarySoft: Int = ColorUtils.blendARGB(secondary, Color.WHITE, .2f)

    fun accent(alpha: Int): Int = ColorUtils.setAlphaComponent(accent, alpha)

    fun soft(alpha: Int): Int = ColorUtils.setAlphaComponent(soft, alpha)

    fun deep(alpha: Int): Int = ColorUtils.setAlphaComponent(deep, alpha)

    fun secondary(alpha: Int): Int = ColorUtils.setAlphaComponent(secondary, alpha)

    fun secondarySoft(alpha: Int): Int = ColorUtils.setAlphaComponent(secondarySoft, alpha)

    override fun equals(other: Any?): Boolean = other is BackdropPalette &&
        other.accent == accent &&
        other.secondary == secondary

    override fun hashCode(): Int = 31 * accent + secondary

    companion object {
        private val DEEP_TINT = Color.rgb(66, 45, 120)

        val Fallback = of(Color.rgb(137, 108, 255), null)

        /**
         * Builds a palette, deriving the companion hue when a surface has not chosen one.
         *
         * The derivation rotates hue by a fixed amount rather than picking a true complement: the
         * complement of a warm sunrise is a cold blue, which reads as a mistake. A shorter rotation
         * stays inside the same mood while still giving multi-hue backdrops something to blend to.
         */
        fun of(accent: Int, secondary: Int?): BackdropPalette = BackdropPalette(
            accent = accent,
            secondary = secondary ?: rotateHue(accent, HUE_ROTATION),
        )

        private const val HUE_ROTATION = 38f

        private fun rotateHue(color: Int, degrees: Float): Int {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color, hsl)
            hsl[0] = (hsl[0] + degrees).mod(360f)
            // Nudge toward a slightly richer companion so the pair reads as intentional.
            hsl[1] = (hsl[1] * 1.05f).coerceAtMost(1f)
            return ColorUtils.HSLToColor(hsl)
        }
    }
}

/**
 * One background design.
 *
 * Backdrops are stateful because their shaders and paths are sized to a particular view. They are
 * built once per size or palette change in [onConfigure] and then only replayed, which is what
 * keeps paging free: the ambient view never invalidates while scrolling, it only translates the
 * field, so everything below has to be resolution-dependent but frame-independent.
 *
 * Nothing in a backdrop may depend on the current time, on a random source consulted at draw time,
 * or on an animator. Two draws with the same geometry and palette must produce the same image.
 */
abstract class Backdrop {

    protected var geometry: BackdropGeometry = BackdropGeometry.Empty
        private set

    protected var palette: BackdropPalette = BackdropPalette.Fallback
        private set

    fun configure(geometry: BackdropGeometry, palette: BackdropPalette) {
        this.geometry = geometry
        this.palette = palette
        if (!geometry.isEmpty) onConfigure()
    }

    /** Rebuilds shaders and paths for the current [geometry] and [palette]. */
    protected open fun onConfigure() = Unit

    /** The part that stays put. Anything that should feel attached to the glass belongs here. */
    open fun drawWash(canvas: Canvas) = Unit

    /** The part that drifts as the workspace pages. The subject of the composition belongs here. */
    open fun drawField(canvas: Canvas) = Unit

    /** Draws the whole design into one canvas, for previews. */
    fun drawComplete(canvas: Canvas) {
        drawWash(canvas)
        drawField(canvas)
    }
}

/**
 * The catalogue of background designs.
 *
 * The [id] is what gets persisted, not the ordinal, so reordering this enum or dropping a style
 * cannot silently repoint somebody's saved surface at a different design.
 */
enum class BackdropStyle(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    /**
     * Multiplier on the standard parallax distance.
     *
     * Metadata on the style rather than on the [Backdrop] instance, so that the ambient canvas can
     * size its field layer without constructing every design first.
     */
    val parallaxScale: Float,
    private val factory: () -> Backdrop,
) {
    BLOOM(
        id = "bloom",
        labelRes = R.string.vellum_backdrop_bloom,
        descriptionRes = R.string.vellum_backdrop_bloom_description,
        parallaxScale = 1f,
        factory = ::BloomBackdrop,
    ),
    AURORA(
        id = "aurora",
        labelRes = R.string.vellum_backdrop_aurora,
        descriptionRes = R.string.vellum_backdrop_aurora_description,
        parallaxScale = 1.25f,
        factory = ::AuroraBackdrop,
    ),
    DUNES(
        id = "dunes",
        labelRes = R.string.vellum_backdrop_dunes,
        descriptionRes = R.string.vellum_backdrop_dunes_description,
        parallaxScale = .8f,
        factory = ::DunesBackdrop,
    ),
    MESH(
        id = "mesh",
        labelRes = R.string.vellum_backdrop_mesh,
        descriptionRes = R.string.vellum_backdrop_mesh_description,
        parallaxScale = 1.1f,
        factory = ::MeshBackdrop,
    ),
    NOCTURNE(
        id = "nocturne",
        labelRes = R.string.vellum_backdrop_nocturne,
        descriptionRes = R.string.vellum_backdrop_nocturne_description,
        parallaxScale = .55f,
        factory = ::NocturneBackdrop,
    ),
    GRAIN(
        id = "grain",
        labelRes = R.string.vellum_backdrop_grain,
        descriptionRes = R.string.vellum_backdrop_grain_description,
        parallaxScale = .9f,
        factory = ::GrainBackdrop,
    ),
    VEIL(
        id = "veil",
        labelRes = R.string.vellum_backdrop_veil,
        descriptionRes = R.string.vellum_backdrop_veil_description,
        parallaxScale = 1f,
        factory = ::VeilBackdrop,
    ),
    ;

    fun create(): Backdrop = factory()

    companion object {
        /** The design a surface uses when it has not chosen one, and the fallback for unknown ids. */
        val Default = BLOOM

        /**
         * The largest [Backdrop.parallaxScale] any design asks for.
         *
         * The ambient canvas sizes its field layer from this once, rather than per design, so that
         * switching design never triggers a relayout. Derived rather than declared so that adding a
         * design that drifts further cannot leave the field too small to cover its own travel.
         */
        val maxParallaxScale: Float get() = entries.maxOf { it.parallaxScale }

        /**
         * Resolves a persisted id. An unknown id falls back rather than throwing, so a surface set
         * written by a newer build and then opened by an older one degrades to the signature design
         * instead of crashing on launch.
         */
        fun fromId(id: String?): BackdropStyle = entries.firstOrNull { it.id == id } ?: Default
    }
}
