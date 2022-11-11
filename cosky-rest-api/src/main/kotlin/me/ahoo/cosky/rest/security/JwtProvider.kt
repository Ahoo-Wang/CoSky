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
package me.ahoo.cosky.rest.security

import com.google.common.base.Charsets
import com.google.common.base.Joiner
import com.google.common.base.Splitter
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import me.ahoo.cosid.IdGenerator
import me.ahoo.cosky.rest.security.user.LoginResponse
import me.ahoo.cosky.rest.security.user.User
import java.security.Key
import java.util.*
import java.util.stream.Collectors
import javax.crypto.spec.SecretKeySpec

/**
 * Jwt Provider.
 *
 * @author ahoo wang
 */
class JwtProvider(private val jwt: SecurityProperties.Jwt, private val idGenerator: IdGenerator) {
    private val signingKey: Key
    private val jwtParser: JwtParser

    init {
        val signingKeyBytes = jwt.signingKey.toByteArray(Charsets.UTF_8)
        signingKey = SecretKeySpec(signingKeyBytes, jwt.algorithm)
        jwtParser = Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
    }

    fun generateToken(user: User): LoginResponse {
        val accessTokenId = idGenerator.generateAsString()
        val now = Date()
        val accessTokenExp = Date(now.time + jwt.accessTokenValidity.toMillis())
        val accessToken = Jwts.builder()
            .setId(accessTokenId)
            .setSubject(user.username)
            .claim(ROLE, roleBindAsString(user.roleBind))
            .signWith(signingKey)
            .setIssuedAt(now)
            .setExpiration(accessTokenExp)
            .compact()
        val refreshTokenId = idGenerator.generateAsString()
        val refreshTokenExp = Date(now.time + jwt.refreshTokenValidity.toMillis())
        val refreshToken = Jwts.builder()
            .setId(refreshTokenId)
            .setSubject(accessTokenId)
            .signWith(signingKey)
            .setIssuedAt(now)
            .setExpiration(refreshTokenExp)
            .compact()
        return LoginResponse(accessToken = accessToken, refreshToken = refreshToken)
    }

    fun refresh(accessToken: String, refreshToken: String): LoginResponse {
        val refreshTokenClaims = decode(refreshToken)
        val accessTokenClaims: Claims
        accessTokenClaims = try {
            decode(accessToken)
        } catch (expiredJwtException: ExpiredJwtException) {
            expiredJwtException.claims
        }
        require(refreshTokenClaims.subject == accessTokenClaims.id) { "Illegal refreshToken " }
        val user = parseUser(accessTokenClaims)
        return generateToken(user)
    }

    fun authorize(accessToken: String): User {
        val claims = decode(accessToken)
        return parseUser(claims)
    }

    private fun parseUser(claims: Claims): User {
        val roleBindStr = claims.get(ROLE, String::class.java)
        return User(claims.subject, stringAsRoleBind(roleBindStr))
    }

    @Throws(ExpiredJwtException::class)
    fun decode(jwtToken: String): Claims {
        return jwtParser
            .parseClaimsJws(jwtToken)
            .body
    }

    companion object {
        private const val ROLE = "role"
        private const val ROLE_SEPARATOR = ","
        fun roleBindAsString(roleBind: Set<String>): String {
            return Joiner
                .on(ROLE_SEPARATOR)
                .skipNulls()
                .join(roleBind)
        }

        fun stringAsRoleBind(roleBindStr: String): Set<String> {
            return Splitter
                .on(ROLE_SEPARATOR)
                .omitEmptyStrings()
                .splitToStream(roleBindStr)
                .collect(Collectors.toSet())
        }
    }
}
