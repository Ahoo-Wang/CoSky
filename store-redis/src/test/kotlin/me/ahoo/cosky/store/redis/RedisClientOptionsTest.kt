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

import io.lettuce.core.ClientOptions.DisconnectedBehavior
import io.lettuce.core.cluster.models.partitions.RedisClusterNode
import io.lettuce.core.cluster.models.partitions.RedisClusterNode.NodeFlag
import io.lettuce.core.resource.EpollProvider
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.time.toJavaDuration

class RedisClientOptionsTest {
    @Test
    fun `a host that vanished is given up on`() {
        listOf(RedisStore.clientOptions().socketOptions, RedisStore.clusterClientOptions().socketOptions).forEach {
            it.keepAlive.isEnabled.assert().isTrue()
            it.tcpUserTimeout.isEnabled.assert().isEqualTo(EpollProvider.isAvailable())
        }
    }

    @Test
    fun `a cluster client reads the topology again when it changes, and every so often`() {
        val refresh = RedisStore.clusterClientOptions().topologyRefreshOptions
        refresh.adaptiveRefreshTriggers.assert().isNotEmpty()
        refresh.isPeriodicRefreshEnabled.assert().isTrue()
    }

    @Test
    fun `a command on a connection that is down fails at once, which on a cluster the store sees to`() {
        RedisStore.clientOptions().disconnectedBehavior.assert().isEqualTo(DisconnectedBehavior.REJECT_COMMANDS)
        // The driver would refuse a command on a connection still being made too.
        RedisStore.clusterClientOptions().disconnectedBehavior.assert().isEqualTo(DisconnectedBehavior.DEFAULT)
    }

    @Test
    fun `a cluster node taken as failed, or without an address, is left out of the topology`() {
        val filter = RedisStore.clusterClientOptions().nodeFilter
        fun node(vararg flags: NodeFlag) = RedisClusterNode().apply { setFlags(flags.toSet()) }

        filter.test(node(NodeFlag.UPSTREAM)).assert().isTrue()
        filter.test(node(NodeFlag.REPLICA)).assert().isTrue()
        // One node's suspicion, which the others may not share.
        filter.test(node(NodeFlag.UPSTREAM, NodeFlag.EVENTUAL_FAIL)).assert().isTrue()
        filter.test(node(NodeFlag.UPSTREAM, NodeFlag.FAIL)).assert().isFalse()
        filter.test(node(NodeFlag.REPLICA, NodeFlag.FAIL)).assert().isFalse()
        filter.test(node(NodeFlag.UPSTREAM, NodeFlag.NOADDR)).assert().isFalse()
    }

    @Test
    fun `a command waits for Redis no longer than the URI allows, or a few seconds`() {
        RedisStore.redisUri("redis://127.0.0.1:7000").timeout
            .assert().isEqualTo(RedisStore.COMMAND_TIMEOUT.toJavaDuration())
        RedisStore.redisUri("redis://127.0.0.1:7000?timeout=30s").timeout.assert().isEqualTo(Duration.ofSeconds(30))
        RedisStore.redisUri("redis://timeout=@127.0.0.1:7000").timeout
            .assert().isEqualTo(RedisStore.COMMAND_TIMEOUT.toJavaDuration())
        RedisStore.redisUri("redis-sentinel://127.0.0.1:26379#cosky").timeout
            .assert().isEqualTo(RedisStore.COMMAND_TIMEOUT.toJavaDuration())
    }
}
