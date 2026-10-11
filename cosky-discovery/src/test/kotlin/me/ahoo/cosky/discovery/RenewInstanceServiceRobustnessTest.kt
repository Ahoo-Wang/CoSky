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

import io.mockk.every
import io.mockk.mockk
import me.ahoo.cosky.discovery.ServiceInstance.Companion.asServiceInstance
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class RenewInstanceServiceRobustnessTest {
    private val renewProperties = RenewProperties(initialDelay = Duration.ZERO, period = Duration.ofMillis(100))
    private val failing = Instance.asInstance("failing", "http", "127.0.0.1", 8080).asServiceInstance()
    private val healthy = Instance.asInstance("healthy", "http", "127.0.0.1", 8080).asServiceInstance()

    @Test
    fun failedInstanceShouldNotStopRenewingOthers() {
        val serviceRegistry = mockk<ServiceRegistry> {
            every { registeredEphemeralInstances } returns linkedMapOf(
                NamespacedInstanceId("ns", failing.instanceId) to failing,
                NamespacedInstanceId("ns", healthy.instanceId) to healthy,
            )
            every { renew("ns", failing) } returns Mono.error(IllegalStateException("renew failed"))
            every { renew("ns", healthy) } returns Mono.just(true)
            every { reapExpiredInstances(any(), any()) } returns Mono.just(0)
        }
        val renewed = Semaphore(0)
        val renewService = RenewInstanceService(renewProperties, serviceRegistry, hookOnRenew = {
            if (it == healthy) {
                renewed.release()
            }
        })
        renewService.start()
        try {
            renewed.tryAcquire(2, TimeUnit.SECONDS).assert().isTrue()
        } finally {
            renewService.stop()
        }
    }

    @Test
    fun restartAfterStop() {
        val serviceRegistry = mockk<ServiceRegistry> {
            every {
                registeredEphemeralInstances
            } returns mapOf(NamespacedInstanceId("ns", healthy.instanceId) to healthy)
            every { renew("ns", healthy) } returns Mono.just(true)
            every { reapExpiredInstances(any(), any()) } returns Mono.just(0)
        }
        val renewed = Semaphore(0)
        val renewService = RenewInstanceService(renewProperties, serviceRegistry, hookOnRenew = { renewed.release() })
        renewService.start()
        renewed.tryAcquire(2, TimeUnit.SECONDS).assert().isTrue()
        renewService.stop()

        renewed.drainPermits()
        renewService.start()
        try {
            renewed.tryAcquire(2, TimeUnit.SECONDS).assert().isTrue()
        } finally {
            renewService.stop()
        }
    }

    @Test
    fun reapRenewedNamespacesAfterEachRound() {
        val reaped = Semaphore(0)
        val serviceRegistry = mockk<ServiceRegistry> {
            every {
                registeredEphemeralInstances
            } returns mapOf(NamespacedInstanceId("ns", healthy.instanceId) to healthy)
            every { renew("ns", healthy) } returns Mono.just(true)
            every { reapExpiredInstances("ns", any()) } answers {
                reaped.release()
                Mono.just(1)
            }
        }
        val renewService = RenewInstanceService(renewProperties, serviceRegistry)
        renewService.start()
        try {
            reaped.tryAcquire(2, TimeUnit.SECONDS).assert().isTrue()
        } finally {
            renewService.stop()
        }
    }
}
