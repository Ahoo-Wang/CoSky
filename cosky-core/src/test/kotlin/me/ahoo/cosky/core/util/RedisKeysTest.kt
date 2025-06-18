/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.ahoo.cosky.core.util

import me.ahoo.cosky.core.util.RedisKeys.hasWrap
import me.ahoo.cosky.core.util.RedisKeys.ofKey
import me.ahoo.cosky.core.util.RedisKeys.unwrap
import me.ahoo.cosky.core.util.RedisKeys.wrap
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test

/**
 * @author ahoo wang
 */
class RedisKeysTest {

    @Test
    fun ofKey() {
        var key = ofKey(false, "dev")
        key.assert().isEqualTo("dev")
        key = ofKey(false, "{dev}")
        key.assert().isEqualTo("{dev}")
        var clusterKey = ofKey(true, "dev")
        clusterKey.assert().isEqualTo("{dev}")
        clusterKey = ofKey(true, "{dev}")
        clusterKey.assert().isEqualTo("{dev}")
    }

    @Test
    fun hasWrap() {
        hasWrap("dev").assert().isFalse()
        hasWrap("{dev").assert().isFalse()
        hasWrap("dev}").assert().isFalse()
        hasWrap("{dev}").assert().isTrue()
        hasWrap("{{dev}").assert().isTrue()
        hasWrap("{dev}}").assert().isTrue()
        hasWrap("{{dev}}").assert().isTrue()
    }

    @Test
    fun wrap() {
        wrap("dev").assert().isEqualTo("{dev}")
    }

    @Test
    fun unwrap() {
        unwrap("{dev}").assert().isEqualTo("dev")
        unwrap("cosky-{dev}").assert().isEqualTo("dev")
        unwrap("cosky-{{dev}").assert().isEqualTo("{dev")
        unwrap("cosky-{{dev}}").assert().isEqualTo("{dev")
        unwrap("cosky-{dev}-cosky").assert().isEqualTo("dev")
    }
}
