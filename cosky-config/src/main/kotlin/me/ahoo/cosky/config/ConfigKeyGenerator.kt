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

import me.ahoo.cosky.core.CoSky

/**
 * Config Key Generator.
 *
 * @author ahoo wang
 */
object ConfigKeyGenerator {
    private const val CONFIG_IDX = "cfg_idx"
    private const val CONFIG_HISTORY_IDX = "cfg_htr_idx"
    private const val CONFIG_HISTORY = "cfg_htr"
    private const val CONFIG = "cfg"

    /**
     * [namespace][namespace]:[CONFIG_IDX][CONFIG_IDX].
     *
     * @param namespace namespace
     * @return the key of config idx
     */
    @JvmStatic
    fun getConfigIdxKey(namespace: String): String {
        return "$namespace:$CONFIG_IDX"
    }

    /**
     * [namespace][namespace]:[CONFIG_HISTORY_IDX][CONFIG_HISTORY_IDX]:[configId][configId].
     */
    @JvmStatic
    fun getConfigHistoryIdxKey(namespace: String, configId: String): String {
        return "$namespace:$CONFIG_HISTORY_IDX:$configId"
    }

    /**
     * [namespace][namespace]:[CONFIG_HISTORY]:[configId][configId]:[version][version].
     */
    @JvmStatic
    fun getConfigHistoryKey(namespace: String, configId: String, version: Int): String {
        return "$namespace:$CONFIG_HISTORY:$configId:$version"
    }

    /**
     * [namespace][namespace]:[CONFIG][CONFIG]:[configId][configId].
     */
    @JvmStatic
    fun getConfigKey(namespace: String, configId: String): String {
        return "$namespace:$CONFIG:$configId"
    }

    @JvmStatic
    fun getConfigIdOfKey(configKey: String): NamespacedConfigId {
        val firstSplitIdx: Int = configKey.indexOf(CoSky.KEY_SEPARATOR)
        val namespace = configKey.substring(0, firstSplitIdx)
        val configKeyPrefix = "$namespace:$CONFIG:"
        val configId = configKey.substring(configKeyPrefix.length)
        return NamespacedConfigId(namespace = namespace, configId = configId)
    }

    @JvmStatic
    fun getConfigVersionOfHistoryKey(namespace: String, configHistoryKey: String): ConfigVersion {
        val configHistoryKeyPrefix = "$namespace:$CONFIG_HISTORY:"
        val configIdWithVersion = configHistoryKey.substring(configHistoryKeyPrefix.length)
        val configIdWithVersionSplit: Array<String> =
            configIdWithVersion.split(CoSky.KEY_SEPARATOR.toRegex()).dropWhile { it.isEmpty() }
                .toTypedArray()
        require(configIdWithVersionSplit.size == 2) { "configHistoryKey:[$configHistoryKey] format error." }
        return ConfigVersionData(configIdWithVersionSplit[0], configIdWithVersionSplit[1].toInt())
    }
}
