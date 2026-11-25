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

package me.ahoo.cosky.server.resource

import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.validation.Violation
import java.util.Base64

/**
 * Where a List continues (design §4.6): the server keeps no state, so the token carries it all.
 *
 * @param revision of the first page, which every page reports.
 * @param after the last name of the previous page.
 */
internal data class ListPageToken(val epoch: String, val revision: Long, val after: String)

/**
 * Where a History continues (design §4.6). The limit is for all pages together, so the token carries what is left of
 * it: versions written since the first page must not use it up.
 *
 * @param before the revision of the last version on the previous page.
 * @param remaining versions still to come at most.
 */
internal data class HistoryPageToken(val before: Long, val remaining: Int)

/**
 * Where the services view continues, as a List does.
 *
 * @param after the last service of the previous page.
 */
internal data class ServicesPageToken(val epoch: String, val revision: Long, val after: String)

/**
 * Where the topology view continues: after the last edge of the previous page, in the order of consumer, then producer.
 */
internal data class TopologyPageToken(val consumer: String, val producer: String)

internal object PageTokens {
    private const val SEPARATOR = '\n'
    private const val LIST_FIELDS = 3
    private const val HISTORY_FIELDS = 2
    private const val VIEW_FIELDS = 4
    private const val TOPOLOGY_FIELDS = 3

    // A view's token starts with what it is a token of, so that none passes for another's, nor for a List's.
    private const val SERVICES = "services"
    private const val TOPOLOGY = "topology"
    private const val FIELD = "page_token"

    fun encode(token: ListPageToken): String = encoded(token.epoch, token.revision, token.after)

    fun encode(token: HistoryPageToken): String = encoded(token.before, token.remaining)

    fun encode(token: ServicesPageToken): String = encoded(SERVICES, token.epoch, token.revision, token.after)

    /**
     * The consumer goes last: it is the name a client gave itself, or its subject, and may hold anything.
     */
    fun encode(token: TopologyPageToken): String = encoded(TOPOLOGY, token.producer, token.consumer)

    /**
     * @return null for the first page.
     */
    fun list(value: String): ListPageToken? {
        if (value.isEmpty()) {
            return null
        }
        val fields = decoded(value, "a List")
        val revision = fields.getOrNull(1)?.toLongOrNull()
        if (fields.size != LIST_FIELDS || revision == null) {
            throw invalid("This is not a page token of a List.")
        }
        return ListPageToken(fields[0], revision, fields[2])
    }

    /**
     * @return null for the first page.
     */
    fun history(value: String): HistoryPageToken? {
        if (value.isEmpty()) {
            return null
        }
        val fields = decoded(value, "a History")
        val before = fields.first().toLongOrNull()
        val remaining = fields.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
        if (fields.size != HISTORY_FIELDS || before == null || remaining == null) {
            throw invalid("This is not a page token of a History.")
        }
        return HistoryPageToken(before, remaining)
    }

    /**
     * @return null for the first page.
     */
    fun services(value: String): ServicesPageToken? {
        if (value.isEmpty()) {
            return null
        }
        val fields = decoded(value, "the services view")
        // What it is a token of, then what a List's token holds.
        val list = fields.takeIf { it.size == VIEW_FIELDS && it.first() == SERVICES }?.drop(1)
        val revision = list?.get(1)?.toLongOrNull()
        if (list == null || revision == null) {
            throw invalid("This is not a page token of the services view.")
        }
        return ServicesPageToken(list[0], revision, list[2])
    }

    /**
     * @return null for the first page.
     */
    fun topology(value: String): TopologyPageToken? {
        if (value.isEmpty()) {
            return null
        }
        val fields = decoded(value, "the topology view", TOPOLOGY_FIELDS)
        if (fields.size != TOPOLOGY_FIELDS || fields[0] != TOPOLOGY) {
            throw invalid("This is not a page token of the topology view.")
        }
        return TopologyPageToken(consumer = fields[2], producer = fields[1])
    }

    private fun encoded(vararg fields: Any): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        fields.joinToString(SEPARATOR.toString()).encodeToByteArray(),
    )

    /**
     * @param limit fields at most, the last taking the rest; 0 for any number.
     */
    private fun decoded(value: String, of: String, limit: Int = 0): List<String> =
        runCatching { Base64.getUrlDecoder().decode(value).decodeToString() }
            .getOrElse { throw invalid("This is not a page token of $of.") }
            .split(SEPARATOR, limit = limit)

    fun invalid(description: String): CoSkyException = CoSkyException.invalidArgument(
        listOf(Violation(FIELD, ViolationCode.VIOLATION_CODE_INVALID_FORMAT, description)),
    )
}
