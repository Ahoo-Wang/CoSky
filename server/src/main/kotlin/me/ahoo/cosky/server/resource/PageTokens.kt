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

internal object PageTokens {
    private const val SEPARATOR = '\n'
    private const val LIST_FIELDS = 3
    private const val FIELD = "page_token"

    fun encode(token: ListPageToken): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        listOf(token.epoch, token.revision, token.after).joinToString(SEPARATOR.toString()).encodeToByteArray(),
    )

    /**
     * @return null for the first page.
     */
    fun list(value: String): ListPageToken? {
        if (value.isEmpty()) {
            return null
        }
        val fields = runCatching { Base64.getUrlDecoder().decode(value).decodeToString() }
            .getOrElse { throw invalid("This is not a page token of a List.") }
            .split(SEPARATOR)
        val revision = fields.getOrNull(1)?.toLongOrNull()
        if (fields.size != LIST_FIELDS || revision == null) {
            throw invalid("This is not a page token of a List.")
        }
        return ListPageToken(fields[0], revision, fields[2])
    }

    /**
     * @return the revision to continue before; every revision is before the first page's.
     */
    fun history(value: String): Long {
        if (value.isEmpty()) {
            return Long.MAX_VALUE
        }
        return value.toLongOrNull() ?: throw invalid("This is not a page token of a History.")
    }

    fun invalid(description: String): CoSkyException = CoSkyException.invalidArgument(
        listOf(Violation(FIELD, ViolationCode.VIOLATION_CODE_INVALID_FORMAT, description)),
    )
}
