package me.ahoo.govern.core.listener;

import io.lettuce.core.cluster.models.partitions.RedisClusterNode;
import io.lettuce.core.cluster.pubsub.RedisClusterPubSubListener;
import io.lettuce.core.cluster.pubsub.StatefulRedisClusterPubSubConnection;
import io.lettuce.core.cluster.pubsub.api.async.NodeSelectionPubSubAsyncCommands;
import lombok.extern.slf4j.Slf4j;
import lombok.var;
import me.ahoo.govern.core.util.RedisKeySpaces;

import java.util.concurrent.CompletableFuture;


/**
 * @author ahoo wang
 */
@Slf4j
public class RedisClusterMessageListenable extends AbstractMessageListenable {

    private final RedisClusterPubSubListenerAdapter listenerAdapter;
    private final StatefulRedisClusterPubSubConnection<String, String> pubSubConnection;
    private final NodeSelectionPubSubAsyncCommands<String, String> pubSubCommands;

    public RedisClusterMessageListenable(StatefulRedisClusterPubSubConnection<String, String> pubSubConnection) {
        RedisKeySpaces.ensureNotifyKeyspaceEvents(pubSubConnection.sync());
        this.pubSubConnection = pubSubConnection;
        this.pubSubConnection.setNodeMessagePropagation(true);
        this.pubSubCommands = pubSubConnection.async().upstream().commands();
        this.listenerAdapter = new RedisClusterPubSubListenerAdapter();
        this.pubSubConnection.addListener(listenerAdapter);
    }


    @Override
    protected CompletableFuture<Void> subscribe(ChannelTopic channelTopic) {
        var asyncExecutions = pubSubCommands.subscribe(channelTopic.getTopic());
        return CompletableFuture.allOf(asyncExecutions.futures());
    }

    @Override
    protected CompletableFuture<Void> subscribe(PatternTopic patternTopic) {
        var asyncExecutions = pubSubCommands.psubscribe(patternTopic.getTopic());
        return CompletableFuture.allOf(asyncExecutions.futures());
    }

    @Override
    protected CompletableFuture<Void> unsubscribe(ChannelTopic topic) {
        var asyncExecutions = pubSubCommands.unsubscribe(topic.getTopic());
        return CompletableFuture.allOf(asyncExecutions.futures());
    }

    @Override
    protected CompletableFuture<Void> unsubscribe(PatternTopic topic) {
        var asyncExecutions = pubSubCommands.punsubscribe(topic.getTopic());
        return CompletableFuture.allOf(asyncExecutions.futures());
    }

    @Override
    public void close() throws Exception {
        pubSubConnection.close();
    }

    private class RedisClusterPubSubListenerAdapter implements RedisClusterPubSubListener<String, String> {
        /**
         * Message received from a channel subscription.
         *
         * @param node    the {@link RedisClusterNode} where the {@literal message} originates.
         * @param channel Channel.
         * @param message Message.
         */
        @Override
        public void message(RedisClusterNode node, String channel, String message) {
            if (log.isDebugEnabled()){
                log.debug("Message received from a channel subscription - RedisNode[{}] | channel[{}] | message[{}].", node.getUri(), channel, message);
            }
            onMessage(channel, message, null);
        }

        /**
         * Message received from a pattern subscription.
         *
         * @param node    the {@link RedisClusterNode} where the {@literal message} originates.
         * @param pattern Pattern
         * @param channel Channel
         * @param message Message
         */
        @Override
        public void message(RedisClusterNode node, String pattern, String channel, String message) {
            if (log.isDebugEnabled()){
                log.debug("Message received from a pattern subscription - RedisNode[{}]  | pattern[{}] | channel[{}] | message[{}].", node.getUri(), pattern, channel, message);
            }
            onMessage(channel, message, pattern);
        }

        /**
         * Subscribed to a channel.
         *
         * @param node    the {@link RedisClusterNode} where the {@literal message} originates.
         * @param channel Channel
         * @param count   Subscription count.
         */
        @Override
        public void subscribed(RedisClusterNode node, String channel, long count) {
            if (log.isInfoEnabled()){
                log.debug("Subscribed to a channel - RedisNode[{}]  | channel[{}] | {}.", node.getUri(), channel, count);
            }
        }

        /**
         * Subscribed to a pattern.
         *
         * @param node
         * @param pattern Pattern.
         * @param count   Subscription count.
         */
        @Override
        public void psubscribed(RedisClusterNode node, String pattern, long count) {
            if (log.isInfoEnabled()){
                log.info("Subscribed to a pattern - RedisNode[{}]  | pattern[{}] | {}.", node.getUri(), pattern, count);
            }
        }

        /**
         * Unsubscribed from a channel.
         *
         * @param node    the {@link RedisClusterNode} where the {@literal message} originates.
         * @param channel Channel
         * @param count   Subscription count.
         */
        @Override
        public void unsubscribed(RedisClusterNode node, String channel, long count) {
            if (log.isInfoEnabled()){
                log.info("Unsubscribed from a channel - RedisNode[{}] | channel[{}] | {}.", node.getUri(), channel, count);
            }

        }

        /**
         * Unsubscribed from a pattern.
         *
         * @param node    the {@link RedisClusterNode} where the {@literal message} originates.
         * @param pattern Channel
         * @param count   Subscription count.
         */
        @Override
        public void punsubscribed(RedisClusterNode node, String pattern, long count) {
            if (log.isInfoEnabled()){
                log.info("Unsubscribed from a pattern - RedisNode[{}] | pattern[{}] | {}.", node.getUri(), pattern, count);
            }

        }
    }
}
