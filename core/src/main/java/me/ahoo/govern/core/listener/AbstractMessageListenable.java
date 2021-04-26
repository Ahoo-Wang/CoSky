package me.ahoo.govern.core.listener;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import lombok.var;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author ahoo wang
 */
@Slf4j
public abstract class AbstractMessageListenable implements MessageListenable {

    private final ConcurrentHashMap<Topic, MessageListener> topicMapListener;

    protected AbstractMessageListenable() {
        this.topicMapListener = new ConcurrentHashMap<>();
    }

    @Override
    public CompletableFuture<Void> addListener(Topic topic, MessageListener messageListener) {

        AtomicReference<CompletableFuture<Void>> futureRef = new AtomicReference<>();
        var currentValue = topicMapListener.computeIfAbsent(topic, _topic -> {
            futureRef.set(subscribe(topic));
            if (log.isInfoEnabled()) {
                log.info("addListener - topic[{}] : Success.", topic);
            }
            return messageListener;
        });
        if (!messageListener.equals(currentValue) && log.isInfoEnabled()) {
            log.info("addListener - topic[{}] : Failure,existed.", topic);
        }
        return Objects.nonNull(futureRef.get()) ? futureRef.get() : CompletableFuture.completedFuture(null);
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
    public CompletableFuture<Void> removeListener(Topic topic) {
        var preValue = topicMapListener.remove(topic);
        if (Objects.isNull(preValue) && log.isInfoEnabled()) {
            log.info("removeListener - topic[{}] : Failure,not existed.", topic);
        }
        if (log.isInfoEnabled()) {
            log.info("removeListener - topic[{}] : Success.", topic);
        }

        return unsubscribe(topic);
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
        var messageListener = topicMapListener.get(topic);
        if (Objects.nonNull(messageListener)) {
            messageListener.onMessage(topic, channel, message);
            if (log.isInfoEnabled()) {
                log.info("onMessage - topic[{}] : Success.", topic);
            }
        } else {
            if (log.isInfoEnabled()) {
                log.info("onMessage - topic[{}] : Failure,messageListener not existed.", topic);
            }
        }
    }
}
