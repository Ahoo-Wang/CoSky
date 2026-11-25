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

package me.ahoo.cosky.client

import io.grpc.inprocess.InProcessChannelBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.store.Mutation
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A client given one endpoint, such as a Kubernetes Service in front of several replicas, where only a new connection
 * can reach another replica.
 */
class OneEndpointTest {
    private val cold = TestServer().apply {
        cacheReachesStore = false
        start()
    }
    private val healthy = TestServer().apply { start() }

    @AfterEach
    fun stop() {
        cold.close()
        healthy.close()
    }

    @Test
    fun `a client of one endpoint leaves a replica that cannot read its store, over a new connection`() =
        runBlocking<Unit>(Dispatchers.Default) {
            healthy.store.createNamespace(NAMESPACE)
            healthy.store.apply(NAMESPACE, listOf(Mutation.Put(config("healthy"))), "test")
            // The first connection reaches the replica that cannot serve, which ends the stream for the client to go
            // elsewhere; any later one reaches the other.
            val connections = AtomicInteger()
            val options = CoSkyClientOptions(listOf("cosky")) {
                snapshotDir = null
                reconnectMin = 10.milliseconds
                reconnectMax = 100.milliseconds
                channelFactory = {
                    val replica = if (connections.getAndIncrement() == 0) cold else healthy
                    InProcessChannelBuilder.forName(replica.name).build()
                }
            }
            CoSkyClient(options).use { client ->
                withTimeout(10.seconds) {
                    client.namespace(NAMESPACE).configs.load(CONFIG)?.config?.content.assert().isEqualTo("healthy")
                }
            }
            connections.get().assert().isGreaterThan(1)
        }

    private fun config(content: String) = resource {
        meta = objectMeta {
            namespace = NAMESPACE
            name = CONFIG
        }
        config = configSpec {
            format = ConfigFormat.CONFIG_FORMAT_YAML
            this.content = content
        }
    }

    private companion object {
        const val NAMESPACE = "prod"
        const val CONFIG = "application.yaml"
    }
}
