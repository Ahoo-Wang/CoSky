package me.ahoo.govern.core.listener;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import lombok.var;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author ahoo wang
 */
@Slf4j
public abstract class AbstractMessageListenable implements MessageListenable {

    private final ConcurrentHashMap<Topic, CopyOnWriteArraySet<MessageListener>> topicMapListener;

    protected AbstractMessageListenable() {
        this.topicMapListener = new ConcurrentHashMap<>();
    }

    @Override
    public CompletableFuture<Void> addListener(Topic topic, MessageListener messageListener) {
        AtomicReference<CompletableFuture> resultFuture = new AtomicReference<>(CompletableFuture.completedFuture(null));
        topicMapListener.compute(topic, (key, val) -> {
            var messageListeners = val;
            if (Objects.isNull(messageListeners)) {
                messageListeners = new CopyOnWriteArraySet<>();
            }
            if (messageListeners.isEmpty()) {
                resultFuture.set(subscribe(topic));
            }
            boolean succeeded = messageListeners.add(messageListener);
            if (!succeeded) {
                if (log.isInfoEnabled()) {
                    log.info("addListener - topic[{}] | messageListener:[{}] existed - Failure.", topic, messageListener);
                }
            } else {
                if (log.isInfoEnabled()) {
                    log.info("addListener - topic[{}] | messageListener:[{}] - Success.", topic, messageListener);
                }
            }
            return messageListeners;
        });

        return resultFuture.get();
    }

    protected CompletableFuture<Void> subscribe(Topic topic) {
        if (topic instanceof ChannelTopic) {
            return subscribe((ChannelTopic) topic);
        }
        if (topic instanceof PatternTopic) {
            return subscribe((PatternTopic) topic);
        }
        throw new IllegalArgumentException("wrong topic : " + topic.getClass().getName());
    }

    protected abstract CompletableFuture<Void> subscribe(ChannelTopic channelTopic);

    protected abstract CompletableFuture<Void> subscribe(PatternTopic patternTopic);

    @Override
    public CompletableFuture<Void> removeListener(Topic topic, MessageListener messageListener) {
        AtomicReference<CompletableFuture> resultFuture = new AtomicReference<>(CompletableFuture.completedFuture(null));
        var messageListeners = topicMapListener.compute(topic, (key, val) -> {
            if (Objects.isNull(val)) {
                if (log.isInfoEnabled()) {
                    log.info("removeListener - topic[{}] not existed - Failure.", topic);
                }
                return null;
            }

            if (!val.remove(messageListener)) {
                if (log.isInfoEnabled()) {
                    log.info("removeListener - topic[{}] | messageListener:[{}] not existed - Failure.", topic, messageListener);
                }
                return val;
            }
            if (val.isEmpty()) {
                resultFuture.set(unsubscribe(topic));
            }

            if (log.isInfoEnabled()) {
                log.info("removeListener - topic[{}] | messageListener:[{}] - Success.", topic, messageListener);
            }

            return val;
        });

        return resultFuture.get();
    }

    private CompletableFuture<Void> unsubscribe(Topic topic) {
        Preconditions.checkNotNull(topic);
        if (topic instanceof ChannelTopic) {
            return unsubscribe((ChannelTopic) topic);
        }
        if (topic instanceof PatternTopic) {
            return unsubscribe((PatternTopic) topic);
        }
        throw new IllegalArgumentException("wrong topic : " + topic.getClass().getName());
    }

    protected abstract CompletableFuture<Void> unsubscribe(ChannelTopic topic);

    protected abstract CompletableFuture<Void> unsubscribe(PatternTopic topic);

    protected void onMessage(String channel, String message, @Nullable String pattern) {
        Topic topic;
        if (Objects.nonNull(pattern)) {
            topic = PatternTopic.of(pattern);
        } else {
            topic = ChannelTopic.of(channel);
        }
        var messageListeners = topicMapListener.get(topic);
        if (Objects.nonNull(messageListeners) && !messageListeners.isEmpty()) {
            messageListeners.forEach(messageListener -> messageListener.onMessage(topic, channel, message));
            if (log.isInfoEnabled()) {
                log.info("onMessage - topic[{}] - Success.", topic);
            }
        } else {
            if (log.isInfoEnabled()) {
                log.info("onMessage - topic[{}] messageListener not existed - Failure.", topic);
            }
        }
    }
}
