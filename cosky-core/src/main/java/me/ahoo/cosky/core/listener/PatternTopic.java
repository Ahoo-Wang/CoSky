
package me.ahoo.cosky.core.listener;


import com.google.common.base.Preconditions;

import java.util.Objects;

/**
 * Pattern topic for redis PSUBSCRIBE
 *
 */
public class PatternTopic implements Topic {

    private final String channelPattern;

    public PatternTopic(String channelPattern) {

        Preconditions.checkNotNull(channelPattern, "channelPattern must not be null!");
        this.channelPattern = channelPattern;
    }

    public static PatternTopic of(String pattern) {
        return new PatternTopic(pattern);
    }

    @Override
    public String getTopic() {
        return channelPattern;
    }

    @Override
    public String toString() {
        return "PatternTopic{" +
                "channelPattern='" + channelPattern + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {

        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        PatternTopic that = (PatternTopic) o;
        return Objects.equals(channelPattern, that.channelPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelPattern);
    }
}
