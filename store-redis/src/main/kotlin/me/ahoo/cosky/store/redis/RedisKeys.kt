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

package me.ahoo.cosky.store.redis

import me.ahoo.cosky.api.v1.Kind

/**
 * The key layout of one namespace (design §5.1). The hash tag keeps them all in one slot, so scripts can touch them
 * together on a Cluster.
 */
internal class RedisKeys(namespace: String) {
    private val prefix = "cosky:{$namespace}"

    val state = "$prefix:state"
    val epoch = "$prefix:epoch"
    val revision = "$prefix:rev"
    val log = "$prefix:log"
    val lease = "$prefix:lease"
    val leaseTtl = "$prefix:lease:ttl"
    val leaseOwner = "$prefix:lease:owner"
    val leaseAttach = "$prefix:lease:attach"
    val leaseSequence = "$prefix:lease:seq"
    val topology = "$prefix:topology"
    val historyIndex = "$prefix:hist:idx"

    fun objects(kind: Kind): String = "$prefix:obj:${kind.token}"

    fun versions(kind: Kind): String = "$prefix:ver:${kind.token}"

    fun history(name: String): String = "$prefix:hist:config:$name"

    companion object {
        const val HINT_CHANNEL = "cosky:hint"

        /**
         * Every kind that has objects.
         */
        val KINDS: List<Kind> = listOf(
            Kind.KIND_NAMESPACE,
            Kind.KIND_CONFIG,
            Kind.KIND_INSTANCE,
            Kind.KIND_SERVICE_ACCOUNT,
            Kind.KIND_ROLE,
            Kind.KIND_ROLE_BINDING,
        )

        val Kind.token: String
            get() = name.removePrefix("KIND_").lowercase()

        fun lock(name: String): String = "cosky:lock:$name"
    }
}
