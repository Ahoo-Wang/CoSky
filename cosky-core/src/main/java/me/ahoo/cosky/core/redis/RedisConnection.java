package me.ahoo.cosky.core.redis;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;


/**
 * @author ahoo wang
 */
public class RedisConnection implements AutoCloseable {
    private StatefulConnection<String, String> connection;
    private RedisClusterAsyncCommands<String, String> asyncCommands;

    public RedisConnection(StatefulConnection<String, String> connection, RedisClusterAsyncCommands<String, String> asyncCommands) {
        this.connection = connection;
        this.asyncCommands = asyncCommands;
    }

    public StatefulConnection<String, String> getConnection() {
        return connection;
    }

    public RedisClusterAsyncCommands<String, String> getAsyncCommands() {
        return asyncCommands;
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }
}
