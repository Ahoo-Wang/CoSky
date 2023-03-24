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
package me.ahoo.cosky.discovery

/**
 * Service Changed Event.
 *
 * @author ahoo wang
 */
class InstanceChangedEvent(
    val namespacedServiceId: NamespacedServiceId,
    val event: Event,
    val instance: Instance,
) {

    enum class Event(val op: String) {
        REGISTER("register"),
        DEREGISTER("deregister"),
        EXPIRED("expired"),
        RENEW("renew"),
        SET_METADATA("set_metadata"),
    }

    companion object {
        @JvmStatic
        fun String.asServiceChangedEvent(): Event {
            return when (this.lowercase()) {
                Event.REGISTER.op -> Event.REGISTER
                Event.DEREGISTER.op -> Event.DEREGISTER
                Event.EXPIRED.op -> Event.EXPIRED
                Event.RENEW.op -> Event.RENEW
                Event.SET_METADATA.op -> Event.SET_METADATA
                else -> throw IllegalStateException("Unexpected value: " + this.lowercase())
            }
        }
    }
}
