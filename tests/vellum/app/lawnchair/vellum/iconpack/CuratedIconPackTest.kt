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

package app.lawnchair.vellum.iconpack

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Integrity of the recommended icon pack list.
 *
 * The failure mode this guards against is not a crash: it is a card that says "Get", sends the user
 * to a dead link, and never notices they already had the pack installed. That is invisible to every
 * other kind of testing, so the internal consistency is checked here instead.
 */
class CuratedIconPackTest {

    private val packs = CuratedIconPack.all()

    @Test
    fun listIsNotEmptyAndIdsAreUnique() {
        assertThat(packs).isNotEmpty()
        assertThat(packs.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun everyPackNamesAtLeastOnePackage() {
        packs.forEach { pack ->
            assertThat(pack.packages).isNotEmpty()
        }
    }

    @Test
    fun packageNamesAreDistinctWithinAndAcrossPacks() {
        // A package claimed by two entries would make the same installed pack show up twice.
        val all = packs.flatMap { it.packages }
        assertThat(all).containsNoDuplicates()
        packs.forEach { pack ->
            assertThat(pack.packages).containsNoDuplicates()
        }
    }

    @Test
    fun packageNamesLookLikePackageNames() {
        packs.forEach { pack ->
            pack.packages.forEach { packageName ->
                assertThat(packageName).matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
            }
        }
    }

    @Test
    fun storeLinksAreHttpsAndPointAtOneOfThePacksOwnPackages() {
        // Catches the copy-paste error where an entry keeps the previous pack's store link.
        packs.forEach { pack ->
            assertThat(pack.storeUrl).startsWith("https://")
            val target = pack.storeUrl.substringAfter("id=", missingDelimiterValue = "")
            assertThat(target).isNotEmpty()
            assertThat(pack.packages).contains(target)
        }
    }

    @Test
    fun accentsAreFullyOpaque() {
        packs.forEach { pack ->
            assertThat(pack.accent ushr 24).isEqualTo(0xFF)
        }
    }

    @Test
    fun byIdFindsEveryPackAndNothingElse() {
        packs.forEach { pack ->
            assertThat(CuratedIconPack.byId(pack.id)).isEqualTo(pack)
        }
        assertThat(CuratedIconPack.byId("not_a_pack")).isNull()
        assertThat(CuratedIconPack.byId(null)).isNull()
    }
}
