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

package me.ahoo.cosky.server.auth

import me.ahoo.cosky.api.v1.Subject
import java.time.Duration
import java.time.Instant

/**
 * Who is calling (design §6.3).
 *
 * @param groups what an OIDC token claims; a ServiceAccount has none.
 * @param tokenId the ServiceAccount token it signed in with, for noticing that it was revoked.
 * @param expiresAt when the OIDC token it signed in with expires: nothing else ends what it opened with that token,
 * since the server keeps no record of a person.
 */
data class Principal(
    val type: Subject.Type,
    val name: String,
    val groups: Set<String> = emptySet(),
    val tokenId: String? = null,
    val expiresAt: Instant? = null,
) {
    /**
     * What it is recorded as: `updated_by`, the owner of a lease, the audit log.
     */
    val id: String
        get() = when (type) {
            Subject.Type.TYPE_SERVICE_ACCOUNT -> "serviceaccount:$name"
            Subject.Type.TYPE_USER -> "user:$name"
            Subject.Type.TYPE_GROUP -> "group:$name"
            Subject.Type.TYPE_UNSPECIFIED, Subject.Type.UNRECOGNIZED -> name
        }

    /**
     * Until when what it opened may go on: [expiresAt] and the clock skew the token was accepted with, so that a token
     * the server takes keeps its stream for as long as the server takes it. Null where that is not up to the token.
     */
    val validUntil: Instant?
        get() = expiresAt?.plus(CLOCK_SKEW)

    companion object {
        /**
         * The caller where authentication is off, as in development mode.
         */
        val ANONYMOUS = Principal(Subject.Type.TYPE_UNSPECIFIED, "anonymous")

        /** What Spring Security's `JwtTimestampValidator` allows past `exp` by default. */
        val CLOCK_SKEW: Duration = Duration.ofSeconds(60)
    }
}

/**
 * A principal and where it calls from.
 */
data class Caller(val principal: Principal, val remote: String? = null) {
    companion object {
        val ANONYMOUS = Caller(Principal.ANONYMOUS)
    }
}
