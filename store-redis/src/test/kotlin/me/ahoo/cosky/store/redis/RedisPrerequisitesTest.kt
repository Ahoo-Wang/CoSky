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

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import kotlinx.coroutines.runBlocking
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer

class RedisPrerequisitesTest {
    private class RedisContainer(image: String) : GenericContainer<RedisContainer>(image)

    private fun check(image: String, vararg arguments: String): Result<Unit> {
        val container = RedisContainer(image).withExposedPorts(REDIS_PORT)
        if (arguments.isNotEmpty()) {
            container.withCommand("redis-server", *arguments)
        }
        return container.use {
            it.start()
            val client = RedisClient.create(RedisURI.create(it.host, it.getMappedPort(REDIS_PORT)))
            try {
                RedisStore.standalone(client).use { store ->
                    runBlocking { runCatching { store.checkPrerequisites() } }
                }
            } finally {
                client.shutdown()
            }
        }
    }

    @Test
    fun `a default redis is fine`() {
        check(REDIS_IMAGE).isSuccess.assert().isTrue()
    }

    @Test
    fun `an eviction policy is refused`() {
        val failure = check(REDIS_IMAGE, "--maxmemory-policy", "allkeys-lru").exceptionOrNull()
        (failure is IllegalStateException).assert().isTrue()
        failure?.message.assert().contains("allkeys-lru")
    }

    @Test
    fun `an old version is refused`() {
        val failure = check(OLD_IMAGE).exceptionOrNull()
        (failure is IllegalStateException).assert().isTrue()
        failure?.message.assert().contains("older than")
    }

    @Test
    fun `info being forbidden only warns`() {
        check(REDIS_IMAGE, "--rename-command", "INFO", "").isSuccess.assert().isTrue()
    }

    @Test
    fun `info without the fields only warns`() {
        val check = RedisPrerequisites.check("# Server\r\nos:Linux\r\n")
        check.problems.assert().isEmpty()
        check.unknown.assert().containsExactly("maxmemory_policy", "redis_version")
    }

    @Test
    fun `info is read per field`() {
        val info = "# Server\r\nredis_version:7.2.4\r\n# Memory\r\nmaxmemory_policy:noeviction\r\n"
        RedisPrerequisites.check(info).assert().isEqualTo(RedisCheck(emptyList(), emptyList()))
    }

    private companion object {
        const val REDIS_PORT = 6379
        const val OLD_IMAGE = "redis:6.2-alpine"
    }
}
