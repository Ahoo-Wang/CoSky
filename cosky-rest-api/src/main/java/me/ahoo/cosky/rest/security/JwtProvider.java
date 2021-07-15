/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

package me.ahoo.cosky.rest.security;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import me.ahoo.cosid.snowflake.SnowflakeFriendlyId;
import me.ahoo.cosky.rest.dto.user.LoginResponse;
import me.ahoo.cosky.rest.security.user.User;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author ahoo wang
 */
public class JwtProvider {
    private final static String ROLE = "role";
    private final static String ROLE_SEPARATOR = ",";
    private final SecurityProperties.Jwt jwt;
    private final Key signingKey;
    private final SnowflakeFriendlyId idGenerator;
    private final JwtParser jwtParser;

    public JwtProvider(SecurityProperties.Jwt jwt, SnowflakeFriendlyId idGenerator) {
        this.jwt = jwt;
        this.idGenerator = idGenerator;
        final byte[] signingKeyBytes = jwt.getSigningKey().getBytes(Charsets.UTF_8);
        this.signingKey = new SecretKeySpec(signingKeyBytes, jwt.getAlgorithm());
        this.jwtParser = Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build();
    }

    public static String roleBindAsString(Set<String> roleBind) {
        return Joiner
                .on(ROLE_SEPARATOR)
                .skipNulls()
                .join(roleBind);
    }

    public static Set<String> stringAsRoleBind(String roleBindStr) {
        return Splitter
                .on(ROLE_SEPARATOR)
                .omitEmptyStrings()
                .splitToStream(roleBindStr)
                .collect(Collectors.toSet());
    }

    public LoginResponse generateToken(User user) {
        String accessTokenId = idGenerator.friendlyId().toString();
        Date now = new Date();
        Date accessTokenExp = new Date(now.getTime() + jwt.getAccessTokenValidity().toMillis());
        String accessToken = Jwts.builder()
                .setId(accessTokenId)
                .setSubject(user.getUsername())
                .claim(ROLE, roleBindAsString(user.getRoleBind()))
                .signWith(signingKey)
                .setIssuedAt(now)
                .setExpiration(accessTokenExp)
                .compact();

        String refreshTokenId = idGenerator.friendlyId().toString();
        Date refreshTokenExp = new Date(now.getTime() + jwt.getRefreshTokenValidity().toMillis());
        String refreshToken = Jwts.builder()
                .setId(refreshTokenId)
                .setSubject(accessTokenId)
                .signWith(signingKey)
                .setIssuedAt(now)
                .setExpiration(refreshTokenExp)
                .compact();
        LoginResponse authenticateResponse = new LoginResponse();
        authenticateResponse.setAccessToken(accessToken);
        authenticateResponse.setRefreshToken(refreshToken);
        return authenticateResponse;
    }

    public LoginResponse refresh(String accessToken, String refreshToken) {
        Claims refreshTokenClaims = decode(refreshToken);
        Claims accessTokenClaims;
        try {
            accessTokenClaims = decode(accessToken);
        } catch (ExpiredJwtException expiredJwtException) {
            accessTokenClaims = expiredJwtException.getClaims();
        }

        if (!refreshTokenClaims.getSubject().equals(accessTokenClaims.getId())) {
            throw new IllegalArgumentException("Illegal refreshToken ");
        }
        User user = parseUser(accessTokenClaims);
        return generateToken(user);
    }

    public User authorize(String accessToken) {
        Claims claims = decode(accessToken);
        return parseUser(claims);
    }

    private User parseUser(Claims claims) {
        User user = new User();
        user.setUsername(claims.getSubject());
        String roleBindStr = claims.get(ROLE, String.class);
        user.setRoleBind(stringAsRoleBind(roleBindStr));
        return user;
    }


    public Claims decode(String jwtToken) throws ExpiredJwtException {
        return jwtParser
                .parseClaimsJws(jwtToken)
                .getBody();
    }
}
