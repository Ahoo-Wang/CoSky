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

import com.google.protobuf.CodedOutputStream
import com.google.protobuf.Timestamp
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.store.withoutReadOnlyMeta

private const val MILLIS_PER_SECOND = 1000L
private const val NANOS_PER_MILLI = 1_000_000

/**
 * The read-only metadata of an object, stored beside its bytes because Lua cannot edit a protobuf message:
 * `<revision>:<create_revision>:<created_ms>:<updated_ms>:<actor>`. The actor comes last since it may contain `:`.
 */
internal data class StoredMeta(
    val revision: Long,
    val createRevision: Long,
    val createdMillis: Long,
    val updatedMillis: Long,
    val actor: String,
) {
    companion object {
        private const val FIELDS = 5

        fun parse(value: String): StoredMeta {
            val fields = value.split(":", limit = FIELDS)
            return StoredMeta(
                revision = fields[0].toLong(),
                createRevision = fields[1].toLong(),
                createdMillis = fields[2].toLong(),
                updatedMillis = fields[3].toLong(),
                actor = fields[4],
            )
        }
    }
}

/**
 * The bytes an object is stored and compared by. Serialization is deterministic, map keys sorted, so that the same
 * content always compares equal inside a script (design §5.1).
 */
internal fun Resource.canonicalBytes(): ByteArray {
    val stripped = withoutReadOnlyMeta()
    val bytes = ByteArray(stripped.serializedSize)
    val output = CodedOutputStream.newInstance(bytes)
    output.useDeterministicSerialization()
    stripped.writeTo(output)
    output.checkNoSpaceLeft()
    return bytes
}

/**
 * Puts together an object from its stored bytes and metadata.
 */
internal fun resourceOf(bytes: ByteArray, meta: StoredMeta): Resource = Resource.parseFrom(bytes).toBuilder().apply {
    metaBuilder
        .setRevision(meta.revision)
        .setCreateRevision(meta.createRevision)
        .setCreatedAt(timestampOf(meta.createdMillis))
        .setUpdatedAt(timestampOf(meta.updatedMillis))
        .setUpdatedBy(meta.actor)
}.build()

private fun timestampOf(millis: Long): Timestamp = Timestamp.newBuilder()
    .setSeconds(millis / MILLIS_PER_SECOND)
    .setNanos((millis % MILLIS_PER_SECOND).toInt() * NANOS_PER_MILLI)
    .build()
