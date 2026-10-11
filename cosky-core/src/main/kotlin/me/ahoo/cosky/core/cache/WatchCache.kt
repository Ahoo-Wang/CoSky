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
package me.ahoo.cosky.core.cache

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * An immutable, versioned view of one cached key.
 *
 * @param version increases by one every time [value] changes.
 * @param value `null` when the key does not exist.
 */
data class Snapshot<V : Any>(val version: Long, val value: V?)

/**
 * A local cache kept consistent with its source by change events.
 *
 * @author ahoo wang
 */
interface WatchCache<K : Any, V : Any> : AutoCloseable {
    /**
     * The current value, empty when the key does not exist.
     *
     * The first access of a key starts watching it.
     */
    fun get(key: K): Mono<V>

    /**
     * The current snapshot followed by every subsequent one.
     */
    fun watch(key: K): Flux<Snapshot<V>>
}
