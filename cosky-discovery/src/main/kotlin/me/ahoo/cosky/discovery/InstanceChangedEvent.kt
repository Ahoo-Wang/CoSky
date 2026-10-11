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
 * @param ttlAt the new ttlAt carried by a renew event, when its publisher sends it.
 *
 * @author ahoo wang
 */
class InstanceChangedEvent(
    val namespacedServiceId: NamespacedServiceId,
    val event: Event,
    val instance: Instance,
    val ttlAt: Long? = null
) {

    enum class Event(val op: String) {
        REGISTER("register"),
        DEREGISTER("deregister"),
        EXPIRED("expired"),
        RENEW("renew"),
        SET_METADATA("set_metadata")
    }

    companion object {
        private const val PAYLOAD_SEPARATOR = ':'
        private val EVENTS_BY_OP = Event.entries.associateBy { it.op }

        @JvmStatic
        fun String.asServiceChangedEvent(): Event {
            return EVENTS_BY_OP[lowercase()] ?: error("Unexpected value: ${lowercase()}")
        }

        /**
         * Parses an event message: `op` or `op:<ttlAt>`. An unreadable payload is ignored.
         */
        @JvmStatic
        fun parseMessage(message: String): Pair<Event, Long?> {
            val separator = message.indexOf(PAYLOAD_SEPARATOR)
            if (separator < 0) {
                return message.asServiceChangedEvent() to null
            }
            val event = message.substring(0, separator).asServiceChangedEvent()
            return event to message.substring(separator + 1).toLongOrNull()
        }
    }
}
