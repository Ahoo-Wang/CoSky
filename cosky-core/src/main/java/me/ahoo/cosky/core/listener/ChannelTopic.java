
package me.ahoo.cosky.core.listener;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

/**
 * Channel topic for redis SUBSCRIBE
 */
public class ChannelTopic implements Topic {

    private final String channelName;

    public ChannelTopic(String channelName) {

        Preconditions.checkNotNull(channelName, "channelName must not be null!");

        this.channelName = channelName;
    }

    public static ChannelTopic of(String name) {
        return new ChannelTopic(name);
    }

    @Override
    public String getTopic() {
        return channelName;
    }

    @Override
    public String toString() {
        return "ChannelTopic{" +
                "channelName='" + channelName + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChannelTopic)) return false;
        ChannelTopic that = (ChannelTopic) o;
        return Objects.equal(channelName, that.channelName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(channelName);
    }
}
