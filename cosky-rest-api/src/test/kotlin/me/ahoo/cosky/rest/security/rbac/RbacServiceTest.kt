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
package me.ahoo.cosky.rest.security.rbac

import me.ahoo.cosid.test.MockIdGenerator
import me.ahoo.cosky.test.AbstractReactiveRedisTest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import reactor.core.publisher.Mono
import reactor.kotlin.test.test
import java.time.Duration

class RbacServiceTest : AbstractReactiveRedisTest() {
    private lateinit var listenerContainer: ReactiveRedisMessageListenerContainer
    private lateinit var rbacService: RbacService
    private val roleName = "role-" + MockIdGenerator.INSTANCE.generateAsString()

    override fun afterInitializedRedisClient() {
        listenerContainer = ReactiveRedisMessageListenerContainer(connectionFactory)
        rbacService = RbacService(redisTemplate, listenerContainer)
    }

    override fun beforeDestroyRedisClient() {
        rbacService.removeRole(roleName).block()
        rbacService.destroy()
        listenerContainer.destroy()
    }

    private fun save(vararg binds: Pair<String, String>): Mono<Void> {
        val request = SaveRoleRequest("desc", binds.map { ResourceActionDto(it.first, it.second) }.toSet())
        return rbacService.saveRole(roleName, request)
    }

    private fun awaitRole(predicate: (Role) -> Boolean): Role {
        return Mono.defer { rbacService.getRole(roleName) }
            .onErrorResume { Mono.empty() }
            .filter(predicate)
            .repeatWhenEmpty { it.delayElements(Duration.ofMillis(20)) }
            .block(Duration.ofSeconds(5))!!
    }

    @Test
    fun roleChangesReachTheCache() {
        save("ns1" to "r").test().verifyComplete()
        rbacService.getRole(roleName)
            .test()
            .expectNextMatches { it.resourceActionBind.keys == setOf("ns1") }
            .verifyComplete()

        save("ns2" to "rw").test().verifyComplete()
        awaitRole { it.resourceActionBind.keys == setOf("ns2") }
            .resourceActionBind.getValue("ns2").action.assert().isEqualTo(Action.READ_WRITE)
    }

    @Test
    fun removedRoleIsNotFound() {
        save("ns1" to "r").test().verifyComplete()
        rbacService.getRole(roleName).test().expectNextCount(1).verifyComplete()

        rbacService.removeRole(roleName).test().expectNext(true).verifyComplete()

        Mono.defer {
            rbacService.getRole(
                roleName
            ).map { false }.onErrorResume(NotFoundRoleException::class.java) { Mono.just(true) }
        }
            .filter { it }
            .repeatWhenEmpty { it.delayElements(Duration.ofMillis(20)) }
            .block(Duration.ofSeconds(5))
    }

    @Test
    fun roleWithoutBindings() {
        save().test().verifyComplete()

        rbacService.getRole(roleName)
            .test()
            .expectNextMatches { it.desc == "desc" && it.resourceActionBind.isEmpty() }
            .verifyComplete()
    }
}
