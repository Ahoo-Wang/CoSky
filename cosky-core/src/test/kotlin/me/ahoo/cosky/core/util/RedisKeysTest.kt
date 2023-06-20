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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * @author ahoo wang
 */
class RedisKeysTest {

    @Test
    fun ofKey() {
        var key = ofKey(false, "dev")
        Assertions.assertEquals("dev", key)
        key = ofKey(false, "{dev}")
        Assertions.assertEquals("{dev}", key)
        var clusterKey = ofKey(true, "dev")
        Assertions.assertEquals("{dev}", clusterKey)
        clusterKey = ofKey(true, "{dev}")
        Assertions.assertEquals("{dev}", clusterKey)
    }

    @Test
    fun hasWrap() {
        Assertions.assertFalse(hasWrap("dev"))
        Assertions.assertFalse(hasWrap("{dev"))
        Assertions.assertFalse(hasWrap("dev}"))
        Assertions.assertTrue(hasWrap("{dev}"))
        Assertions.assertTrue(hasWrap("{{dev}"))
        Assertions.assertTrue(hasWrap("{dev}}"))
        Assertions.assertTrue(hasWrap("{{dev}}"))
    }

    @Test
    fun wrap() {
        Assertions.assertEquals("{dev}", wrap("dev"))
    }

    @Test
    fun unwrap() {
        Assertions.assertEquals("dev", unwrap("{dev}"))
        Assertions.assertEquals("dev", unwrap("cosky-{dev}"))
        Assertions.assertEquals("{dev", unwrap("cosky-{{dev}"))
        Assertions.assertEquals("{dev", unwrap("cosky-{{dev}}"))
        Assertions.assertEquals("dev", unwrap("cosky-{dev}-cosky"))
    }
}
