/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosky.core.redis;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.masterreplica.MasterReplica;
import io.lettuce.core.masterreplica.StatefulRedisMasterReplicaConnection;
import io.lettuce.core.resource.ClientResources;
import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosky.core.listener.DefaultMessageListenable;
import me.ahoo.cosky.core.listener.MessageListenable;

import java.util.Objects;

/**
 * @author ahoo wang
 */
@Slf4j
public class RedisConnectionFactory implements AutoCloseable {

    private final ClientResources clientResources;
    private final RedisConfig redisConfig;
    private final AbstractRedisClient client;
    private RedisConnection shareConnection;
    private boolean isCluster;

    public RedisConnectionFactory(ClientResources clientResources, RedisConfig redisConfig) {
        this.clientResources = clientResources;
        this.redisConfig = redisConfig;
        this.isCluster = RedisConfig.RedisMode.CLUSTER.equals(redisConfig.getMode());
        this.client = createClient();
    }

    public boolean isCluster() {
        return isCluster;
    }

    private AbstractRedisClient createClient() {
        if (RedisConfig.RedisMode.CLUSTER.equals(redisConfig.getMode())) {
            return RedisClusterClient.create(clientResources, redisConfig.getUrl());
        }
        return RedisClient.create(clientResources, redisConfig.getUrl());
    }

    public <T extends AbstractRedisClient> T getClient() {
        return (T) client;
    }

    public synchronized RedisConnection getShareConnection() {
        if (Objects.nonNull(shareConnection)) {
            return shareConnection;
        }

        shareConnection = getConnection();
        return shareConnection;
    }
    public RedisClusterReactiveCommands<String, String> getShareReactiveCommands() {
        return getShareConnection().getReactiveCommands();
    }
    public RedisClusterAsyncCommands<String, String> getShareAsyncCommands() {
        return getShareConnection().getAsyncCommands();
    }

    public RedisClusterCommands<String, String> getShareSyncCommands() {
        return getShareConnection().getSyncCommands();
    }


    public RedisConnection getConnection() {

        if (client instanceof RedisClusterClient) {
            StatefulRedisClusterConnection<String, String> clusterConnection = ((RedisClusterClient) client).connect();
            return new RedisConnection(clusterConnection, clusterConnection.sync(), clusterConnection.async(), clusterConnection.reactive());
        }

        RedisClient redisClient = (RedisClient) client;

        if (Objects.isNull(redisConfig.getReadFrom())) {
            StatefulRedisConnection<String, String> connection = redisClient.connect();
            return new RedisConnection(connection, connection.sync(), connection.async(), connection.reactive());
        }

        ReadFrom readFrom = ReadFrom.valueOf(redisConfig.getReadFrom().name());

        StatefulRedisMasterReplicaConnection<String, String> connection = MasterReplica.connect(redisClient, StringCodec.UTF8, RedisURI.create(redisConfig.getUrl()));
        connection.setReadFrom(readFrom);
        return new RedisConnection(connection, connection.sync(), connection.async(), connection.reactive());
    }

    public MessageListenable getMessageListenable() {
        if (client instanceof RedisClusterClient) {
            return new DefaultMessageListenable(((RedisClusterClient) client).connectPubSub().reactive());
        }
        return new DefaultMessageListenable(((RedisClient) client).connectPubSub().reactive());
    }

    @Override
    public void close() throws Exception {
        if (log.isInfoEnabled()) {
            log.info("close.");
        }
        shareConnection.close();
        client.shutdown();
    }
}
