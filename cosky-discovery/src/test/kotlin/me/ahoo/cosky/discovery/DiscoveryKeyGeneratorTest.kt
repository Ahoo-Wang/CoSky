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
package me.ahoo.cosky.discovery

import me.ahoo.cosky.core.CoSky
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getInstanceIdxKey
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getInstanceKey
import me.ahoo.cosky.discovery.DiscoveryKeyGenerator.getServiceIdxKey
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.Test

/**
 * @author ahoo wang
 */
class DiscoveryKeyGeneratorTest {
    @Test
    fun getServiceIdxKey() {
        val key = getServiceIdxKey(CoSky.COSKY)
        assertThat(key, equalTo("cosky:svc_idx"))
    }

    @Test
    fun serviceInstanceIdxKey() {
        val key = getInstanceIdxKey(CoSky.COSKY, "order_service")
        assertThat(key, equalTo("cosky:svc_itc_idx:order_service"))
    }

    @Test
    fun getInstanceKey() {
        val key = getInstanceKey(CoSky.COSKY, "http#127.0.0.1#8080@order_service")
        assertThat(key, equalTo("cosky:svc_itc:http#127.0.0.1#8080@order_service"))
    }
}
