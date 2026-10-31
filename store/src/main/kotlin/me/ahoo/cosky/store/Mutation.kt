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

package me.ahoo.cosky.store

import me.ahoo.cosky.api.kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.api.validation.Validator

sealed interface Mutation {
    val key: ResourceKey

    /**
     * [NO_CHECK], [MUST_NOT_EXIST], or the revision the object must have (compare-and-set).
     */
    val expectedRevision: Long

    data class Put(val resource: Resource, override val expectedRevision: Long = NO_CHECK) : Mutation {
        override val key: ResourceKey
            get() = resource.key
    }

    data class Delete(override val key: ResourceKey, override val expectedRevision: Long = NO_CHECK) : Mutation

    companion object {
        const val NO_CHECK = 0L
        const val MUST_NOT_EXIST = Validator.MUST_NOT_EXIST
    }
}

val Resource.key: ResourceKey
    get() = ResourceKey.newBuilder()
        .setNamespace(meta.namespace)
        .setKind(kind)
        .setName(meta.name)
        .build()

/**
 * The resource as a client wrote it: the store fills the read-only metadata, so it never counts as a change.
 */
fun Resource.withoutReadOnlyMeta(): Resource = toBuilder().apply {
    metaBuilder
        .clearRevision()
        .clearCreateRevision()
        .clearCreatedAt()
        .clearUpdatedAt()
        .clearUpdatedBy()
}.build()

/**
 * The batch contract every store shares: all keys lie in [namespace], and none repeats, since a batch has one
 * revision and each object changes once in it.
 */
fun requireWellFormed(namespace: String, mutations: List<Mutation>) {
    val keys = mutations.map { it.key }
    require(keys.all { it.namespace == namespace }) { "Every mutation must belong to namespace $namespace." }
    require(keys.distinct().size == keys.size) { "A batch must not change the same object twice." }
}

fun List<Mutation>.exceeds(limits: Limits): Boolean =
    size > limits.maxBatchOperations ||
        sumOf { if (it is Mutation.Put) it.resource.serializedSize.toLong() else 0L } > limits.maxBatchBytes
