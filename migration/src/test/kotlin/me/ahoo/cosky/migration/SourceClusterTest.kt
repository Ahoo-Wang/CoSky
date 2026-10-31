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

package me.ahoo.cosky.migration

import io.lettuce.core.cluster.SlotHash
import io.lettuce.core.internal.HostAndPort
import io.lettuce.core.resource.ClientResources
import io.lettuce.core.resource.DnsResolvers
import io.lettuce.core.resource.MappingSocketAddressResolver
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

/**
 * 6.x on a Redis Cluster keeps each namespace in the slot of its hash tag, and they land on different nodes.
 */
class SourceClusterTest {
    @Test
    fun `reads every kind of key, whichever node holds it`() {
        val namespace = "cosky-{default}"
        val other = "cosky-{orders}"
        SlotHash.getSlot(namespace).assert().isNotEqualTo(SlotHash.getSlot(other))
        val options = Options(source = "redis://127.0.0.1:${PORTS.first()}", cluster = true, target = "http://unused")

        SourceConnection.open(options, resources()).use { connection ->
            val six = SixRedis(connection.commands)
            val source = Source(connection.commands)
            six.listNamespace(namespace)
            six.listNamespace(other)
            six.setConfig(namespace, "app.txt", "1")
            six.setConfig(namespace, "app.txt", "2")
            six.setConfig(other, "app.txt", "3")
            six.register(other, "database", "db-1", ephemeral = false)
            six.saveRole("developer", "", mapOf(namespace to "r"))
            six.addUser("ada", "hash", "developer")

            source.namespaces().assert().containsExactlyInAnyOrder(namespace, other)
            source.versions(namespace, "app.txt").assert().containsExactly("1", "2")
            source.versions(other, "app.txt").assert().containsExactly("3")
            source.fixedInstances(other, "database").single().id.assert().isEqualTo("db-1")
            source.roles().single().grants.assert().isEqualTo(mapOf(namespace to "r"))
            source.users().single().roles.assert().containsExactly("developer")
        }
    }

    /** Nodes announce their ports inside the container, which Docker maps to others on the host. */
    private fun resources(): ClientResources = ClientResources.builder()
        .socketAddressResolver(
            MappingSocketAddressResolver.create(DnsResolvers.UNRESOLVED) { address ->
                HostAndPort.of(CLUSTER.host, CLUSTER.getMappedPort(address.port))
            },
        )
        .build()

    private class ClusterContainer : GenericContainer<ClusterContainer>("redis:7.4-alpine")

    private companion object {
        val PORTS = listOf(7000, 7001, 7002)

        val CLUSTER: ClusterContainer = ClusterContainer()
            .withExposedPorts(*PORTS.toTypedArray())
            .withCommand(
                "sh",
                "-c",
                PORTS.joinToString(" ") { port ->
                    "redis-server --port $port --cluster-enabled yes --cluster-config-file n$port.conf " +
                        "--daemonize yes --save '' --appendonly no &&"
                } + " sleep 1 && redis-cli --cluster create " + PORTS.joinToString(" ") { "127.0.0.1:$it" } +
                    " --cluster-yes && echo CLUSTER_READY && tail -f /dev/null",
            )
            .waitingFor(Wait.forLogMessage(".*CLUSTER_READY.*", 1))
            .also { it.start() }
    }
}
