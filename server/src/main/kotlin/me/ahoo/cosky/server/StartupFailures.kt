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

package me.ahoo.cosky.server

import io.lettuce.core.RedisConnectionException
import me.ahoo.cosky.store.redis.RedisPrerequisitesException
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer
import org.springframework.boot.diagnostics.FailureAnalysis
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order

/**
 * A start that cannot go on, with what an operator can do about it.
 */
class StartupFailure(message: String, val action: String) : IllegalStateException(message)

/**
 * Shows a [StartupFailure] as what went wrong and what to do, instead of a stack trace. Ahead of Spring's own, which
 * would show one thrown while binding a setting as a failure to bind the settings around it.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class StartupFailureAnalyzer : AbstractFailureAnalyzer<StartupFailure>() {
    override fun analyze(rootFailure: Throwable, cause: StartupFailure): FailureAnalysis =
        FailureAnalysis(cause.message, cause.action, cause)
}

/**
 * A Redis the server cannot reach at start, the first thing most people meet: what to set, or how to do without.
 */
class RedisUnreachableFailureAnalyzer : AbstractFailureAnalyzer<RedisConnectionException>() {
    override fun analyze(rootFailure: Throwable, cause: RedisConnectionException): FailureAnalysis = FailureAnalysis(
        "CoSky cannot reach Redis: ${cause.message}",
        "Start Redis, or set cosky.store.redis.uri (COSKY_STORE_REDIS_URI) to where it is, with its password or " +
            "rediss:// for TLS. To try CoSky without Redis, start it with --dev.",
        cause,
    )
}

/**
 * A Redis that would lose data without anyone noticing, or that is older than the scripts were tested on: what to
 * change on it, as the server can do nothing about either.
 */
class RedisPrerequisitesFailureAnalyzer : AbstractFailureAnalyzer<RedisPrerequisitesException>() {
    override fun analyze(rootFailure: Throwable, cause: RedisPrerequisitesException): FailureAnalysis =
        FailureAnalysis(
            cause.message,
            "Change Redis, then start again: it must be Redis 7.0 or later, or Valkey 8, with maxmemory-policy " +
                "noeviction (CONFIG SET maxmemory-policy noeviction, and in redis.conf so that a restart keeps it). " +
                "To try CoSky without Redis, start it with --dev.",
            cause,
        )
}

/** What to set a bootstrap token to, for those who have none yet. */
internal const val BOOTSTRAP_TOKEN_EXAMPLE = "cosky_\$(openssl rand -hex 8)_\$(openssl rand -hex 16)"
