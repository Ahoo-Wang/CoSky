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

package me.ahoo.cosky.store.memory

import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.Mutation
import me.ahoo.test.asserts.assert
import me.ahoo.test.asserts.assertThrownBy
import org.junit.jupiter.api.Test

/**
 * What only the in-memory store has; everything shared is in [MemoryStoreConformanceTest].
 */
class MemoryStoreTest {
    private val config = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            name = "application.yaml"
        }
        config = configSpec { content = "a: 1" }
    }

    @Test
    fun `losing writes needs the revision to be retained`() = runBlocking {
        val store = MemoryStore()
        store.createNamespace(NAMESPACE)
        store.apply(NAMESPACE, listOf(Mutation.Put(config)), "test")
        assertThrownBy<IllegalArgumentException> { store.loseWritesAfter(NAMESPACE, 0) }
        Unit
    }

    @Test
    fun `losing writes needs the namespace to exist`() {
        assertThrownBy<IllegalArgumentException> { MemoryStore().loseWritesAfter(NAMESPACE, 0) }
    }

    @Test
    fun `losing nothing changes nothing`() = runBlocking {
        val store = MemoryStore()
        val position = store.createNamespace(NAMESPACE)
        store.loseWritesAfter(NAMESPACE, 0)
        store.position(NAMESPACE).assert().isEqualTo(position)
    }

    private companion object {
        const val NAMESPACE = "memory"
    }
}
