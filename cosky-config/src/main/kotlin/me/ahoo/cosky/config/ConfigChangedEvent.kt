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
 * Config Event .
 *
 * @author ahoo wang
 */
data class ConfigChangedEvent(val namespacedConfigId: NamespacedConfigId, val event: Event) {

    enum class Event(val op: String) {
        SET("set"), ROLLBACK("rollback"), REMOVE("remove");
    }

    companion object {
        fun String.asConfigChangedEvent(): Event {
            return when (this.lowercase()) {
                Event.SET.op -> Event.SET
                Event.ROLLBACK.op -> Event.ROLLBACK
                Event.REMOVE.op -> Event.REMOVE
                else -> throw IllegalStateException("Unexpected value: " + this.lowercase())
            }
        }
    }
}
