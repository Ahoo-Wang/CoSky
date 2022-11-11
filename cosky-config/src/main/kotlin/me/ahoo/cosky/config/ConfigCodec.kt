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
package me.ahoo.cosky.config

/**
 * Config Codec.
 *
 * @author ahoo wang
 */
object ConfigCodec {
    private const val CONFIG_ID = "configId"
    private const val DATA = "data"
    private const val HASH = "hash"
    private const val VERSION = "version"
    private const val CREATE_TIME = "createTime"
    private const val OP = "op"
    private const val OP_TIME = "opTime"

    @JvmStatic
    fun Map<String, String>.decodeAsConfig(): Config {
        return ConfigData(
            configId = requireNotNull(this[CONFIG_ID]),
            data = requireNotNull(this[DATA]),
            hash = requireNotNull(this[HASH]),
            createTime = requireNotNull(this[CREATE_TIME]).toLong(),
            version = requireNotNull(this[VERSION]).toInt()
        )
    }

    @JvmStatic
    fun Map<String, String>.decodeAsHistory(): ConfigHistory {
        return ConfigHistory(
            configId = requireNotNull(this[CONFIG_ID]),
            data = requireNotNull(this[DATA]),
            hash = requireNotNull(this[HASH]),
            createTime = requireNotNull(this[CREATE_TIME]).toLong(),
            version = requireNotNull(this[VERSION]).toInt(),
            op = requireNotNull(this[OP]),
            opTime = requireNotNull(this[OP_TIME]).toLong()
        )
    }
}
