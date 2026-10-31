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

package me.ahoo.cosky.server.observe

import io.micrometer.core.instrument.Timer
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.Store

/**
 * Times the writes of a store (design §7.9).
 */
class MeteredStore(private val delegate: Store, private val metrics: CoSkyMetrics) : Store by delegate {
    override suspend fun apply(namespace: String, mutations: List<Mutation>, actor: String): ApplyResult {
        val sample = Timer.start()
        try {
            return delegate.apply(namespace, mutations, actor)
        } finally {
            sample.stop(metrics.applyTimer)
        }
    }
}
