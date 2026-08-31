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

/**
 * Everything the ambient canvas needs in order to paint, resolved for one instant.
 *
 * This is deliberately separate from the surface it came from. A surface is a stretch of the day
 * with a name and a list of apps; an atmosphere is the light *right now*, which late in a surface
 * is partway toward the surface that follows it. Handing the canvas a resolved atmosphere rather
 * than a surface keeps the drift arithmetic in one place and stops the view from needing to know
 * that surfaces exist at all.
 */
data class VellumAtmosphere(
    val palette: BackdropPalette,
    val style: BackdropStyle,
    val intensity: Float,
)
