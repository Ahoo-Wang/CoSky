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
package me.ahoo.cosky.config.spring.cloud.refresh

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.ahoo.cosky.config.Config
import me.ahoo.cosky.config.ConfigWatcher
import me.ahoo.cosky.config.spring.cloud.CoSkyConfigProperties
import me.ahoo.cosky.core.cache.Snapshot
import me.ahoo.cosky.spring.cloud.CoSkyProperties
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.cloud.endpoint.event.RefreshEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEvent
import reactor.core.publisher.Sinks

class CoSkyConfigRefresherTest {
    private val coSkyProperties = CoSkyProperties()
    private val configProperties = CoSkyConfigProperties().apply { configId = "service.yaml" }
    private val snapshots = Sinks.many().multicast().directBestEffort<Snapshot<Config>>()
    private val configWatcher = mockk<ConfigWatcher> {
        every { watchConfig(coSkyProperties.namespace, "service.yaml") } returns snapshots.asFlux()
    }
    private val events = mutableListOf<ApplicationEvent>()
    private val applicationContext = mockk<ApplicationContext> {
        every { publishEvent(any<ApplicationEvent>()) } answers { events.add(firstArg()) }
    }
    private val refresher = CoSkyConfigRefresher(coSkyProperties, configProperties, configWatcher).apply {
        setApplicationContext(applicationContext)
    }

    @Test
    fun refreshOnlyOnChangesAfterTheInitialSnapshot() {
        refresher.onApplicationEvent(mockk())
        snapshots.tryEmitNext(Snapshot(1, null))
        events.assert().isEmpty()

        val changed = Snapshot<Config>(2, null)
        snapshots.tryEmitNext(changed)
        events.assert().hasSize(1)
        val event = events.single() as RefreshEvent
        event.event.assert().isSameAs(changed)
    }

    @Test
    fun watchOnceAndStopOnDestroy() {
        refresher.onApplicationEvent(mockk())
        refresher.onApplicationEvent(mockk())
        verify(exactly = 1) { configWatcher.watchConfig(coSkyProperties.namespace, "service.yaml") }

        refresher.destroy()
        snapshots.tryEmitNext(Snapshot(1, null))
        snapshots.tryEmitNext(Snapshot(2, null))
        events.assert().isEmpty()
    }

    @Test
    fun requireConfigId() {
        val refresher = CoSkyConfigRefresher(coSkyProperties, CoSkyConfigProperties(), configWatcher)
        assertThrows<IllegalArgumentException> { refresher.onApplicationEvent(mockk()) }
    }
}
