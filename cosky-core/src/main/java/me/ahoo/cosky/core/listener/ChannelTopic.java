
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
