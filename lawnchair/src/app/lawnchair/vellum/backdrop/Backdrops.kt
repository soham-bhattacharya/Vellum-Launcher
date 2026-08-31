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

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import java.util.Random
import kotlin.math.max

/**
 * Shared drawing helpers.
 *
 * Every paint here is allocated once per backdrop instance and mutated between draws rather than
 * recreated, because a backdrop is re-recorded on rotation and on every surface change, and paint
 * allocation during a display-list recording shows up as jank on the frame that triggers it.
 */
private fun fillPaint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

private fun strokePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
}

/** Fills the entire layer with [paint], whatever shader it is currently carrying. */
private fun Canvas.fillLayer(geometry: BackdropGeometry, paint: Paint) {
    drawRect(0f, 0f, geometry.layerWidth, geometry.layerHeight, paint)
}

/**
 * Vellum's signature design: a single warm source in the upper right, with orbit lines and a
 * scatter of motes reading as depth around it.
 *
 * This is the composition the launcher shipped with, kept intact so that upgrading does not change
 * the home screen of anybody who never opens the gallery.
 */
class BloomBackdrop : Backdrop() {

    private val washPaint = fillPaint()
    private val glowPaint = fillPaint()
    private val linePaint = strokePaint()
    private val dotPaint = fillPaint()
    private val orbitBounds = RectF()

    override fun onConfigure() {
        washPaint.shader = LinearGradient(
            0f,
            geometry.layerHeight,
            geometry.layerWidth,
            0f,
            intArrayOf(palette.deep(15), Color.TRANSPARENT, palette.soft(10)),
            floatArrayOf(0f, .54f, 1f),
            Shader.TileMode.CLAMP,
        )
        glowPaint.shader = RadialGradient(
            geometry.x(.79f),
            geometry.y(.16f),
            max(geometry.contentWidth, geometry.contentHeight) * .48f,
            intArrayOf(palette.soft(82), palette.accent(36), Color.TRANSPARENT),
            floatArrayOf(0f, .34f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun drawWash(canvas: Canvas) {
        canvas.fillLayer(geometry, washPaint)
        linePaint.shader = null
        linePaint.color = palette.accent(24)
        linePaint.strokeWidth = geometry.dp(.75f)
        canvas.drawLine(
            geometry.x(0f) + geometry.dp(16f),
            geometry.y(.24f),
            geometry.x(0f) + geometry.dp(16f),
            geometry.y(.68f),
            linePaint,
        )
    }

    override fun drawField(canvas: Canvas) {
        canvas.fillLayer(geometry, glowPaint)

        val cx = geometry.x(.79f)
        val cy = geometry.y(.16f)
        linePaint.shader = null
        linePaint.color = palette.soft(34)
        linePaint.strokeWidth = geometry.density
        for (index in 0..2) {
            val radius = geometry.dp(46f + index * 22f)
            orbitBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(orbitBounds, 205f + index * 15f, 86f - index * 9f, false, linePaint)
        }

        dotPaint.shader = null
        dotPaint.color = palette.soft(42)
        forEachMote { index, x, y ->
            canvas.drawCircle(x, y, if (index % 4 == 0) geometry.dp(1.15f) else geometry.dp(.7f), dotPaint)
        }
    }

    private inline fun forEachMote(block: (index: Int, x: Float, y: Float) -> Unit) {
        var index = 0
        while (index < MOTE_SEEDS.size - 1) {
            block(index, geometry.x(MOTE_SEEDS[index]), geometry.y(MOTE_SEEDS[index + 1]))
            index += 2
        }
    }
}

/**
 * Ribbons of light sweeping across the upper half, thinning as they rise.
 *
 * The bands are drawn back to front with falling alpha, which is what produces the sense that they
 * are at different distances without any blur being involved.
 */
class AuroraBackdrop : Backdrop() {

    private val washPaint = fillPaint()
    private val ribbonPaint = fillPaint()
    private val path = Path()

    override fun onConfigure() {
        washPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            geometry.layerHeight,
            intArrayOf(palette.deep(30), Color.TRANSPARENT, palette.secondary(14)),
            floatArrayOf(0f, .55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun drawWash(canvas: Canvas) = canvas.fillLayer(geometry, washPaint)

    override fun drawField(canvas: Canvas) {
        for (index in RIBBON_ALPHAS.indices) {
            val alpha = RIBBON_ALPHAS[index]
            val baseY = geometry.y(.08f + .125f * index)
            val amplitude = geometry.contentHeight * (.07f - .012f * index)
            val thickness = geometry.contentHeight * (.11f - .017f * index)

            buildRibbon(baseY, amplitude, thickness)
            ribbonPaint.shader = LinearGradient(
                0f,
                0f,
                geometry.layerWidth,
                0f,
                intArrayOf(
                    palette.secondary((alpha * .55f).toInt()),
                    palette.accent(alpha),
                    palette.secondarySoft((alpha * .7f).toInt()),
                ),
                floatArrayOf(0f, .48f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(path, ribbonPaint)
        }
    }

    /** A band between two waves, closed so it can be filled in one pass. */
    private fun buildRibbon(baseY: Float, amplitude: Float, thickness: Float) {
        val right = geometry.layerWidth
        path.reset()
        path.moveTo(0f, baseY)
        path.cubicTo(right * .32f, baseY - amplitude, right * .68f, baseY + amplitude, right, baseY - amplitude * .4f)
        path.lineTo(right, baseY - amplitude * .4f + thickness)
        path.cubicTo(
            right * .68f,
            baseY + amplitude + thickness,
            right * .32f,
            baseY - amplitude + thickness,
            0f,
            baseY + thickness,
        )
        path.close()
    }
}

/**
 * Layered horizons receding into haze, with a low sun behind them.
 *
 * Nearer layers are more opaque, so the stack reads as distance rather than as four flat shapes.
 */
class DunesBackdrop : Backdrop() {

    private val washPaint = fillPaint()
    private val sunPaint = fillPaint()
    private val dunePaint = fillPaint()
    private val path = Path()

    override fun onConfigure() {
        washPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            geometry.layerHeight,
            intArrayOf(Color.TRANSPARENT, palette.deep(24)),
            floatArrayOf(.25f, 1f),
            Shader.TileMode.CLAMP,
        )
        sunPaint.shader = RadialGradient(
            geometry.x(.7f),
            geometry.y(.34f),
            max(geometry.contentWidth, geometry.contentHeight) * .34f,
            intArrayOf(palette.soft(74), palette.accent(30), Color.TRANSPARENT),
            floatArrayOf(0f, .42f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun drawWash(canvas: Canvas) = canvas.fillLayer(geometry, washPaint)

    override fun drawField(canvas: Canvas) {
        canvas.fillLayer(geometry, sunPaint)

        for (index in DUNE_ALPHAS.indices) {
            val crest = geometry.y(.52f + .11f * index)
            val amplitude = geometry.contentHeight * (.045f + .012f * index)
            val alpha = DUNE_ALPHAS[index]
            val top = if (index % 2 == 0) palette.deep(alpha) else palette.secondary(alpha)
            val bottom = if (index % 2 == 0) palette.accent(alpha) else palette.deep(alpha)

            buildDune(crest, amplitude, index)
            dunePaint.shader = LinearGradient(
                0f,
                crest,
                0f,
                geometry.layerHeight,
                intArrayOf(top, bottom),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(path, dunePaint)
        }
    }

    /** A crest that runs off both sides of the layer and closes against the bottom edge. */
    private fun buildDune(crest: Float, amplitude: Float, index: Int) {
        val right = geometry.layerWidth
        val lean = if (index % 2 == 0) 1f else -1f
        path.reset()
        path.moveTo(0f, crest + amplitude * .5f)
        path.cubicTo(
            right * .28f,
            crest - amplitude * lean,
            right * .66f,
            crest + amplitude * lean,
            right,
            crest - amplitude * .3f,
        )
        path.lineTo(right, geometry.layerHeight)
        path.lineTo(0f, geometry.layerHeight)
        path.close()
    }
}

/**
 * Overlapping colour fields, in the manner of a mesh gradient.
 *
 * Each blob is a radial that falls all the way to transparent, so the blend between them happens in
 * the alpha channel and stays smooth without any of them needing a hard edge.
 */
class MeshBackdrop : Backdrop() {

    private val basePaint = fillPaint()
    private val blobPaint = fillPaint()
    private var blobs: List<Shader> = emptyList()

    override fun onConfigure() {
        basePaint.shader = null
        basePaint.color = palette.deep(12)

        val reach = max(geometry.contentWidth, geometry.contentHeight)
        blobs = BLOBS.map { blob ->
            RadialGradient(
                geometry.x(blob.x),
                geometry.y(blob.y),
                reach * blob.radius,
                intArrayOf(blob.color(palette), Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
    }

    override fun drawWash(canvas: Canvas) = canvas.fillLayer(geometry, basePaint)

    override fun drawField(canvas: Canvas) {
        blobs.forEach { shader ->
            blobPaint.shader = shader
            canvas.fillLayer(geometry, blobPaint)
        }
    }

    private class Blob(val x: Float, val y: Float, val radius: Float, val color: (BackdropPalette) -> Int)

    private companion object {
        val BLOBS = listOf(
            Blob(.16f, .10f, .62f) { it.accent(96) },
            Blob(.88f, .20f, .55f) { it.secondary(88) },
            Blob(.74f, .66f, .50f) { it.secondarySoft(62) },
            Blob(.10f, .80f, .58f) { it.deep(78) },
            Blob(.48f, .44f, .40f) { it.soft(40) },
        )
    }
}

/**
 * A near-dark field: a vignette, a distant horizon glow, and stars.
 *
 * The stars barely move with paging. That is deliberate; a small parallax scale on a distant
 * element is the cheapest convincing depth cue there is.
 */
class NocturneBackdrop : Backdrop() {

    private val vignettePaint = fillPaint()
    private val horizonPaint = fillPaint()
    private val starPaint = fillPaint()
    private val arcPaint = strokePaint()
    private val arcBounds = RectF()

    override fun onConfigure() {
        val reach = max(geometry.contentWidth, geometry.contentHeight)
        vignettePaint.shader = RadialGradient(
            geometry.x(.5f),
            geometry.y(.4f),
            reach * .78f,
            intArrayOf(Color.TRANSPARENT, palette.deep(30), palette.deep(62)),
            floatArrayOf(0f, .6f, 1f),
            Shader.TileMode.CLAMP,
        )
        horizonPaint.shader = RadialGradient(
            geometry.x(.5f),
            geometry.y(1.02f),
            reach * .5f,
            intArrayOf(palette.accent(58), palette.deep(24), Color.TRANSPARENT),
            floatArrayOf(0f, .45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun drawWash(canvas: Canvas) = canvas.fillLayer(geometry, vignettePaint)

    override fun drawField(canvas: Canvas) {
        canvas.fillLayer(geometry, horizonPaint)

        starPaint.shader = null
        var index = 0
        while (index < STAR_SEEDS.size - 2) {
            starPaint.color = palette.secondarySoft((70f * STAR_SEEDS[index + 2]).toInt() + 26)
            canvas.drawCircle(
                geometry.x(STAR_SEEDS[index]),
                geometry.y(STAR_SEEDS[index + 1]),
                geometry.dp(.5f + STAR_SEEDS[index + 2] * 1.1f),
                starPaint,
            )
            index += 3
        }

        val radius = max(geometry.contentWidth, geometry.contentHeight) * .3f
        val cx = geometry.x(.82f)
        val cy = geometry.y(.13f)
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        arcPaint.color = palette.soft(46)
        arcPaint.strokeWidth = geometry.dp(1.4f)
        canvas.drawArc(arcBounds, 128f, 96f, false, arcPaint)
    }
}

/**
 * A flat duotone with real paper grain over it.
 *
 * The noise is tiled from a small bitmap generated once for the whole process, and it lives in the
 * wash rather than the field so the texture stays fixed to the glass while the light behind it
 * drifts. Grain that slides around with the content reads as a rendering bug.
 */
class GrainBackdrop : Backdrop() {

    private val duotonePaint = fillPaint()
    private val grainPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val highlightPaint = fillPaint()

    override fun onConfigure() {
        duotonePaint.shader = LinearGradient(
            0f,
            0f,
            geometry.layerWidth,
            geometry.layerHeight,
            intArrayOf(palette.accent(30), palette.secondary(20)),
            null,
            Shader.TileMode.CLAMP,
        )
        grainPaint.shader = BitmapShader(noiseTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        highlightPaint.shader = RadialGradient(
            geometry.x(.3f),
            geometry.y(.18f),
            max(geometry.contentWidth, geometry.contentHeight) * .55f,
            intArrayOf(palette.soft(42), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
    }

    override fun drawWash(canvas: Canvas) {
        canvas.fillLayer(geometry, duotonePaint)
        canvas.fillLayer(geometry, grainPaint)
    }

    override fun drawField(canvas: Canvas) = canvas.fillLayer(geometry, highlightPaint)

    private companion object {
        const val TILE = 96

        @Volatile
        private var tile: Bitmap? = null

        /**
         * A deterministic monochrome noise tile, built at most once per process.
         *
         * The seed is fixed so the texture is identical across launches; regenerating it with a
         * fresh seed would make the home screen subtly different every cold start.
         */
        fun noiseTile(): Bitmap = tile ?: synchronized(this) {
            tile ?: createBitmap(TILE, TILE).also { bitmap ->
                val random = Random(0x5EED)
                val pixels = IntArray(TILE * TILE) {
                    Color.argb(random.nextInt(26), 255, 255, 255)
                }
                bitmap.setPixels(pixels, 0, TILE, 0, 0, TILE, TILE)
                tile = bitmap
            }
        }
    }
}

/**
 * Almost nothing: one soft source in a corner.
 *
 * Included on purpose. A launcher that can only be loud is not customisable, and this is the design
 * to pick when the wallpaper is meant to be the subject.
 */
class VeilBackdrop : Backdrop() {

    private val veilPaint = fillPaint()

    override fun onConfigure() {
        veilPaint.shader = RadialGradient(
            geometry.x(.92f),
            geometry.y(.04f),
            max(geometry.contentWidth, geometry.contentHeight) * .82f,
            intArrayOf(palette.soft(54), palette.accent(20), Color.TRANSPARENT),
            floatArrayOf(0f, .38f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun drawField(canvas: Canvas) = canvas.fillLayer(geometry, veilPaint)
}

/** Fixed mote positions, as alternating x/y fractions of the visible screen. */
private val MOTE_SEEDS = floatArrayOf(
    .08f, .17f, .82f, .11f, .66f, .24f, .20f, .31f, .91f, .38f,
    .13f, .49f, .74f, .55f, .34f, .64f, .87f, .71f, .18f, .79f,
    .58f, .86f, .94f, .91f,
)

/** Fixed star positions as x/y/brightness triples. */
private val STAR_SEEDS = floatArrayOf(
    .06f, .09f, .82f, .19f, .21f, .35f, .31f, .07f, .61f, .44f, .17f, .24f,
    .55f, .30f, .91f, .68f, .12f, .48f, .77f, .26f, .67f, .89f, .09f, .38f,
    .12f, .38f, .55f, .27f, .45f, .19f, .41f, .34f, .74f, .58f, .41f, .30f,
    .72f, .49f, .86f, .84f, .36f, .22f, .95f, .44f, .59f, .09f, .58f, .31f,
    .24f, .63f, .47f, .37f, .72f, .21f, .52f, .81f, .63f, .66f, .69f, .40f,
    .81f, .59f, .77f, .93f, .68f, .29f, .04f, .74f, .43f, .16f, .88f, .52f,
)

/** Falling alpha, back to front. */
private val RIBBON_ALPHAS = intArrayOf(74, 56, 42, 28)

/** Rising alpha, far to near. */
private val DUNE_ALPHAS = intArrayOf(30, 44, 58, 74)
