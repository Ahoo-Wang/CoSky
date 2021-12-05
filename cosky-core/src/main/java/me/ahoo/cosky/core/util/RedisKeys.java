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

package me.ahoo.cosky.core.util;

import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;

/**
 * @author ahoo wang
 */
public final class RedisKeys {

    public static final String WRAP_LEFT = "{";
    public static final String WRAP_RIGHT = "}";

    private RedisKeys() {

    }

    public static boolean isCluster(RedisClusterAsyncCommands<String, String> asyncCommands) {
        return asyncCommands instanceof RedisAdvancedClusterAsyncCommands;
    }

    public static String ofKey(RedisClusterAsyncCommands<String, String> asyncCommands, String key) {
        return ofKey(isCluster(asyncCommands), key);
    }

    public static String ofKey(boolean isCluster, String key) {
        if (!isCluster) {
            return key;
        }
        return hashTag(key);
    }

    /**
     * The first '{' index and the first '{' after '}' key
     * <hr>
     * {system} -&gt; system
     * <hr>
     * {{system} -&gt; {system
     * <hr>
     * {{system}} -&gt; {system
     *
     * @param key redis key
     * @return If the key meets the hashtag specification, return true
     */
    public static boolean hasWrap(String key) {
        int leftIndex = key.indexOf(WRAP_LEFT);
        if (leftIndex == -1) {
            return false;
        }
        int rightIdx = key.substring(leftIndex).indexOf(WRAP_RIGHT);
        return rightIdx > -1;
    }

    public static String wrap(String key) {
        return "{" + key + "}";
    }

    public static String unwrap(String key) {
        if (!hasWrap(key)) {
            return key;
        }
        int leftIndex = key.indexOf(WRAP_LEFT);
        int rightIdx = key.substring(leftIndex).indexOf(WRAP_RIGHT);

        return key.substring(leftIndex + 1, leftIndex + rightIdx);
    }

    public static String hashTag(String key) {
        if (hasWrap(key)) {
            return key;
        }
        return wrap(key);
    }
}
