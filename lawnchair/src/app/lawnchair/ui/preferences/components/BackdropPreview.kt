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

package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import app.lawnchair.vellum.backdrop.Backdrop
import app.lawnchair.vellum.backdrop.BackdropGeometry
import app.lawnchair.vellum.backdrop.BackdropPalette
import app.lawnchair.vellum.backdrop.BackdropStyle

/**
 * Draws a background design exactly as the home screen will draw it.
 *
 * This calls straight into the same [Backdrop] the ambient canvas uses rather than approximating it
 * in Compose. A preview that is a separate drawing of the same idea drifts from the real thing the
 * first time either is touched, and then the gallery is quietly lying to the user.
 *
 * The base and the icon stand-ins matter for honesty too: a backdrop is translucent and is designed
 * to sit over a wallpaper, so showing it against the settings background would make every design
 * look far weaker than it does in place.
 */
@Composable
fun BackdropPreview(
    style: BackdropStyle,
    palette: BackdropPalette,
    modifier: Modifier = Modifier,
    showChrome: Boolean = true,
) {
    // Held outside Compose state on purpose: this is a rasterisation cache, not UI state, and
    // writing to it during the draw pass must not schedule a recomposition.
    val holder = remember(style) { BackdropHolder(style.create()) }

    Canvas(modifier) {
        drawRect(BASE)
        holder.prepare(size, density, palette)
        drawIntoCanvas { holder.backdrop.drawComplete(it.nativeCanvas) }
        if (showChrome) drawIconChrome()
    }
}

/**
 * Keeps one backdrop configured for the size it is currently being drawn at.
 *
 * Reconfiguring rebuilds every shader in the design, so doing it unconditionally would rebuild
 * five radial gradients per card per frame while the settings list is being scrolled.
 */
private class BackdropHolder(val backdrop: Backdrop) {
    private var width = 0f
    private var height = 0f
    private var density = 0f
    private var palette: BackdropPalette? = null

    fun prepare(size: Size, density: Float, palette: BackdropPalette) {
        if (size.width == width && size.height == height && density == this.density && palette == this.palette) return
        width = size.width
        height = size.height
        this.density = density
        this.palette = palette
        backdrop.configure(BackdropGeometry.flat(size.width, size.height, density), palette)
    }
}

/** A suggestion of home screen icons, so a design is judged against something rather than nothing. */
private fun DrawScope.drawIconChrome() {
    val columns = 4
    val rows = 2
    val cell = size.width / (columns + 1f)
    val icon = cell * .52f
    val topInset = size.height * .30f
    val rowGap = cell * .92f

    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val left = cell * .5f + column * cell + (cell - icon) / 2f
            val top = topInset + row * rowGap
            drawRoundRect(
                color = CHROME,
                topLeft = Offset(left, top),
                size = Size(icon, icon),
                cornerRadius = CornerRadius(icon * .3f),
            )
        }
    }
}

/** A neutral dark stand-in for the wallpaper. */
private val BASE = Color(0xFF14161C)

private val CHROME = Color(0x1FFFFFFF)
