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

internal object PageTokens {
    private const val SEPARATOR = '\n'
    private const val LIST_FIELDS = 3
    private const val HISTORY_FIELDS = 2
    private const val FIELD = "page_token"

    fun encode(token: ListPageToken): String = encoded(token.epoch, token.revision, token.after)

    fun encode(token: HistoryPageToken): String = encoded(token.before, token.remaining)

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

    private fun encoded(vararg fields: Any): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        fields.joinToString(SEPARATOR.toString()).encodeToByteArray(),
    )

    private fun decoded(value: String, of: String): List<String> =
        runCatching { Base64.getUrlDecoder().decode(value).decodeToString() }
            .getOrElse { throw invalid("This is not a page token of $of.") }
            .split(SEPARATOR)

    fun invalid(description: String): CoSkyException = CoSkyException.invalidArgument(
        listOf(Violation(FIELD, ViolationCode.VIOLATION_CODE_INVALID_FORMAT, description)),
    )
}
