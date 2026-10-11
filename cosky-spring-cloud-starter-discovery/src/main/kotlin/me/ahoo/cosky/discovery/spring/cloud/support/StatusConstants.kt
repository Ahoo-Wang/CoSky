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
package me.ahoo.cosky.discovery.spring.cloud.support

import me.ahoo.cosky.discovery.ServiceInstance

/**
 * Status Constants.
 *
 * @author ahoo wang
 */
object StatusConstants {
    const val INSTANCE_STATUS_KEY = "instance_status"
    const val STATUS_UP = "UP"
    const val STATUS_OUT_OF_SERVICE = "OUT_OF_SERVICE"

    /**
     * An instance without status, e.g. registered through the REST API, counts as up.
     */
    fun ServiceInstance.isUp(): Boolean {
        val status = metadata[INSTANCE_STATUS_KEY] ?: return true
        return status.equals(STATUS_UP, ignoreCase = true)
    }
}
