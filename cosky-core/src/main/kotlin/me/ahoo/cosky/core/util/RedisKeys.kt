/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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
package me.ahoo.cosky.core.util

import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands

/**
 * RedisKeys.
 *
 * @author ahoo wang
 */
object RedisKeys {
    private const val WRAP_LEFT = "{"
    private const val WRAP_RIGHT = "}"

    fun isCluster(asyncCommands: RedisClusterAsyncCommands<String, String>): Boolean {
        return asyncCommands is RedisAdvancedClusterAsyncCommands<*, *>
    }

    fun ofKey(asyncCommands: RedisClusterAsyncCommands<String, String>, key: String): String {
        return ofKey(isCluster(asyncCommands), key)
    }

    @JvmStatic
    fun ofKey(isCluster: Boolean, key: String): String {
        return if (!isCluster) {
            key
        } else hashTag(key)
    }

    /**
     * The first '{' index and the first '{' after '}' key.
     *
     * - {system} -> system
     * - {{system} -> {system
     * - {{system}} -> {system
     *
     * @param key redis key
     * @return If the key meets the hashtag specification, return true
     */
    @JvmStatic
    fun hasWrap(key: String): Boolean {
        val leftIndex = key.indexOf(WRAP_LEFT)
        if (leftIndex == -1) {
            return false
        }
        val rightIdx = key.substring(leftIndex).indexOf(WRAP_RIGHT)
        return rightIdx > -1
    }

    @JvmStatic
    fun wrap(key: String): String {
        return "$WRAP_LEFT$key$WRAP_RIGHT"
    }

    @JvmStatic
    fun unwrap(key: String): String {
        if (!hasWrap(key)) {
            return key
        }
        val leftIndex = key.indexOf(WRAP_LEFT)
        val rightIdx = key.substring(leftIndex).indexOf(WRAP_RIGHT)
        return key.substring(leftIndex + 1, leftIndex + rightIdx)
    }

    fun hashTag(key: String): String {
        return if (hasWrap(key)) {
            key
        } else wrap(key)
    }
}
