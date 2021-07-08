
/*
 *
 *  * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

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
