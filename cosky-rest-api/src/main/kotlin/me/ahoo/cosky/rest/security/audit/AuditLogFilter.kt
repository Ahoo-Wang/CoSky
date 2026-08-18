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
package me.ahoo.cosky.rest.security.audit

import java.util.Locale

data class AuditLogFilter(
    val query: String? = null,
    val from: Long? = null,
    val to: Long? = null,
    val successful: Boolean? = null
) {
    private val normalizedQuery = query?.trim()?.lowercase(Locale.ROOT).orEmpty()

    val isEmpty: Boolean
        get() = normalizedQuery.isEmpty() && from == null && to == null && successful == null

    fun matches(log: AuditLog): Boolean {
        if (from != null && log.opTime < from) return false
        if (to != null && log.opTime > to) return false
        if (successful != null && (log.status < 400) != successful) return false
        if (normalizedQuery.isEmpty()) return true
        return sequenceOf(log.operator, log.ip, log.resource, log.action, log.status.toString(), log.msg)
            .any { it.lowercase(Locale.ROOT).contains(normalizedQuery) }
    }
}
