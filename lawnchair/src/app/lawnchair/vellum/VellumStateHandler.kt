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

package app.lawnchair.vellum

import app.lawnchair.LawnchairLauncher
import com.android.app.animation.Interpolators
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.anim.AnimatedFloat
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.states.StateAnimationConfig

/**
 * Drives the visibility of Vellum's home-screen decorations from the launcher state transition
 * itself.
 *
 * This replaces the previous pair of independent [android.view.ViewPropertyAnimator]s, which ran on
 * their own clock alongside the All Apps / Overview transition and so drifted out of step with it.
 * By writing into the transition's [PendingAnimation], the ambient layer and the Halo now follow the
 * exact same progress — including when the user is dragging the transition by hand and reverses it
 * halfway.
 */
class VellumStateHandler(
    private val launcher: LawnchairLauncher,
) : StateManager.StateHandler<LauncherState> {

    // Resolved on demand rather than with `by lazy`: a lazy would permanently cache a null if it
    // were ever first touched before the content view existed.
    private var ambientView: VellumAmbientView? = null
        get() = field ?: launcher.findViewById<VellumAmbientView?>(R.id.vellum_ambient).also { field = it }

    private var haloView: VellumHaloView? = null
        get() = field ?: launcher.findViewById<VellumHaloView?>(R.id.vellum_halo).also { field = it }

    private val progress = AnimatedFloat(Runnable { applyProgress() })

    private fun applyProgress() {
        ambientView?.setStateProgress(progress.value)
        haloView?.setStateProgress(progress.value)
    }

    /** Vellum's decorations belong to the home screen only. */
    private fun targetFor(state: LauncherState): Float = if (state == LauncherState.NORMAL) 1f else 0f

    override fun setState(state: LauncherState) {
        progress.updateValue(targetFor(state))
    }

    override fun setStateWithAnimation(
        toState: LauncherState,
        config: StateAnimationConfig,
        animation: PendingAnimation,
    ) {
        val target = targetFor(toState)
        if (progress.value == target) return
        // Leaving home hides the decorations early so they never sit on top of the incoming
        // surface; returning home brings them back over the tail of the transition.
        val interpolator = if (target == 1f) Interpolators.DECELERATE else Interpolators.ACCELERATE
        animation.setFloat(progress, AnimatedFloat.VALUE, target, interpolator)
    }

    /** Applies the current state without animating, for setup and configuration changes. */
    fun jumpToCurrentState() {
        progress.updateValue(targetFor(launcher.stateManager.state))
        // updateValue is a no-op when the value already matches, but the views have not been
        // told anything yet at this point, so push the current value unconditionally.
        applyProgress()
    }
}
