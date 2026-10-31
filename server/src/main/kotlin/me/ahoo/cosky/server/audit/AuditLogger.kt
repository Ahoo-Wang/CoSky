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

package me.ahoo.cosky.server.audit

import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Verb
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * One JSON line per write and per authentication event, for the operator's log system to collect (design §6.3, §7.8).
 * The server stores nothing.
 */
class AuditLogger(private val clock: Clock = Clock.systemUTC()) {
    /**
     * @param result `OK`, or the gRPC code of the failure.
     */
    @Suppress("LongParameterList") // The fields of the audit line, design 7.8.
    fun record(
        subject: String,
        verb: Verb?,
        kind: Kind?,
        namespace: String?,
        name: String?,
        revision: Long?,
        result: String,
        remote: String?,
    ) {
        if (!LOG.isInfoEnabled) {
            return
        }
        val fields = listOf(
            "time" to clock.instant().toString(),
            "subject" to subject,
            "verb" to verb?.name?.removePrefix("VERB_"),
            "kind" to kind?.name?.removePrefix("KIND_"),
            "namespace" to namespace,
            "name" to name,
            "revision" to revision,
            "result" to result,
            "remote" to remote,
        )
        LOG.info(fields.filter { it.second != null }.joinToString(",", "{", "}") { (key, value) -> json(key, value) })
    }

    private fun json(key: String, value: Any?): String = when (value) {
        is Long -> "\"$key\":$value"
        else -> "\"$key\":\"${escape(value.toString())}\""
    }

    private fun escape(value: String): String = buildString(value.length) {
        value.forEach { char ->
            when {
                char == '"' -> append("\\\"")
                char == '\\' -> append("\\\\")
                char < ' ' -> append("\\u%04x".format(char.code))
                else -> append(char)
            }
        }
    }

    private companion object {
        val LOG = LoggerFactory.getLogger("cosky.audit")
    }
}
