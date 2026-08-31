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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The role catalogue, checked as data.
 *
 * `AppRole.intents` is not touched here: building an `Intent` needs the framework. That the rest of
 * the enum is readable on a plain JVM is the point of keeping the probes as strings.
 */
class AppRoleTest {

    @Test
    fun everyRoleHasSomeWayToBeFound() {
        AppRole.entries.forEach { role ->
            val findable = !role.isPackageOnly || role.packages.isNotEmpty()
            assertThat(findable).isTrue()
        }
    }

    @Test
    fun rolesWithNoCapabilityToProbeForHaveCandidatePackages() {
        // A package-only role that also had no packages would always resolve to nothing, and any
        // look referencing it would quietly come up short on every device.
        AppRole.entries.filter { it.isPackageOnly }.forEach { role ->
            assertThat(role.packages).isNotEmpty()
        }
    }

    @Test
    fun candidatePackagesAreDistinctWithinARole() {
        AppRole.entries.forEach { role ->
            assertThat(role.packages).containsNoDuplicates()
        }
    }

    @Test
    fun candidatePackagesLookLikePackageNames() {
        AppRole.entries.forEach { role ->
            role.packages.forEach { packageName ->
                assertThat(packageName).matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
            }
        }
    }
}
