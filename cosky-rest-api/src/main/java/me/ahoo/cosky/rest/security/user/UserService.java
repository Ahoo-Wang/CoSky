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

package me.ahoo.cosky.rest.security.user;

import me.ahoo.cosky.core.Namespaced;
import me.ahoo.cosky.rest.dto.user.LoginResponse;
import me.ahoo.cosky.rest.security.CoSkySecurityException;
import me.ahoo.cosky.rest.security.JwtProvider;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.utility.RandomString;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User Service.
 *
 * @author ahoo wang
 */
@Slf4j
@Service
public class UserService {
    
    public static final String USER_IDX = Namespaced.SYSTEM + ":user_idx";
    public static final String USER_ROLE_BIND = Namespaced.SYSTEM + ":user_role_bind:%s";
    public static final String USER_LOGIN_LOCK = Namespaced.SYSTEM + ":login_lock:%s";
    private final JwtProvider jwtProvider;
    private final ReactiveStringRedisTemplate redisTemplate;
    
    public UserService(JwtProvider jwtProvider, ReactiveStringRedisTemplate redisTemplate) {
        this.jwtProvider = jwtProvider;
        this.redisTemplate = redisTemplate;
    }
    
    private static String getUserRoleBindKey(String username) {
        return Strings.lenientFormat(USER_ROLE_BIND, username);
    }
    
    public Mono<Boolean> initRoot(boolean enforce) {
        return Mono.from(enforce ? removeUser(User.SUPER_USER) : Mono.empty())
            .then(Mono.defer(() -> {
                final String coskyPwd = RandomString.make(10);
                return addUser(User.SUPER_USER, coskyPwd)
                    .map(result -> {
                        if (result) {
                            printSuperUserPwd(coskyPwd);
                        }
                        return result;
                    });
            }));
    }
    
    private void printSuperUserPwd(String coskyPwd) {
        System.out.println(Strings.lenientFormat("---------------- ****** CoSky -  init super user:[%s] password:[%s] ****** ----------------", User.SUPER_USER, coskyPwd));
    }
    
    public Mono<List<User>> query() {
        return redisTemplate.<String, String>opsForHash().keys(USER_IDX)
            .flatMap(username -> getRoleBind(username).collect(Collectors.toSet())
                .map(roleBind -> {
                    User user = new User();
                    user.setUsername(username);
                    user.setRoleBind(roleBind);
                    return user;
                })
            )
            .collect(Collectors.toList());
    }
    
    public Mono<Boolean> existsUser(String username) {
        return redisTemplate
            .opsForHash()
            .hasKey(USER_IDX, username);
    }
    
    public Mono<Boolean> addUser(String username, String pwd) {
        return redisTemplate
            .opsForHash()
            .putIfAbsent(USER_IDX, username, encodePwd(pwd));
    }
    
    public Mono<Boolean> removeUser(String username) {
        String userRoleBindKey = getUserRoleBindKey(username);
        return redisTemplate.delete(userRoleBindKey)
            .then(
                redisTemplate
                    .opsForHash()
                    .remove(USER_IDX, username)
            ).map(affected -> affected > 0);
    }
    
    public Mono<Void> bindRole(String username, Set<String> roleBind) {
        String userRoleBindKey = getUserRoleBindKey(username);
        return redisTemplate
            .opsForSet()
            .delete(userRoleBindKey)
            .thenMany(Flux.fromIterable(roleBind))
            .flatMap(role -> redisTemplate.opsForSet().add(userRoleBindKey, role))
            .then();
    }
    
    public Flux<String> getRoleBind(String username) {
        String userRoleBindKey = getUserRoleBindKey(username);
        return redisTemplate
            .opsForSet()
            .members(userRoleBindKey);
    }
    
    public Mono<Boolean> changePwd(String username, String oldPwd, String newPwd) {
        return redisTemplate
            .<String, String>opsForHash()
            .get(USER_IDX, username)
            .switchIfEmpty(Mono.error(new IllegalArgumentException(Strings.lenientFormat("username:[%s] not exists!", username))))
            .flatMap(preEncodePwd -> {
                final String oldEncodePwd = encodePwd(oldPwd);
                final String newEncodePwd = encodePwd(newPwd);
                if (!preEncodePwd.equals(oldEncodePwd)) {
                    return Mono.error(new CoSkySecurityException(Strings.lenientFormat("username:[%s] - old password is incorrect!", username)));
                }
                return redisTemplate.opsForHash().put(USER_IDX, username, newEncodePwd);
            });
    }
    
    public static final int MAX_LOGIN_ERROR_TIMES = 10;
    public static final long LOGIN_LOCK_EXPIRE = Duration.ofMinutes(15).toMillis();
    public static final long MAX_LOGIN_LOCK_EXPIRE = Duration.ofDays(3).toMillis();
    
    public Mono<LoginResponse> login(String username, String pwd) throws CoSkySecurityException {
        String loginLockKey = Strings.lenientFormat(USER_LOGIN_LOCK, username);
        return redisTemplate
            .opsForValue()
            .increment(loginLockKey)
            .flatMap(tryCount -> {
                long expansion = (int) Math.max(tryCount / MAX_LOGIN_ERROR_TIMES, 1);
                final long loginLockExpire = Math.min(LOGIN_LOCK_EXPIRE * expansion, MAX_LOGIN_LOCK_EXPIRE);
                if (tryCount > MAX_LOGIN_ERROR_TIMES) {
                    return Mono.error(new CoSkySecurityException(
                        Strings.lenientFormat("User:[%s] sign in freezes for [%s] minutes,Too many:[%s] sign in errors!", username, Duration.ofMillis(loginLockExpire).toMinutes(), tryCount)));
                }
                return redisTemplate.expire(loginLockKey, Duration.ofMillis(loginLockExpire));
            })
            .flatMap(nil ->
                redisTemplate
                    .<String, String>opsForHash()
                    .get(USER_IDX, username)
                    .switchIfEmpty(Mono.error(new CoSkySecurityException(Strings.lenientFormat("username:[%s] not exists!", username))))
                    .flatMap(realEncodedPwd -> {
                        String encodedPwd = encodePwd(pwd);
                        if (!realEncodedPwd.equals(encodedPwd)) {
                            return Mono.error(new CoSkySecurityException(Strings.lenientFormat("username:[%s] - password is incorrect.!", username)));
                        }
                        return redisTemplate.delete(loginLockKey);
                    })
            )
            .flatMap(nil -> getRoleBind(username).collect(Collectors.toSet()))
            .map(roleBind -> {
                User user = new User();
                user.setUsername(username);
                user.setRoleBind(roleBind);
                return jwtProvider.generateToken(user);
            });
    }
    
    private String encodePwd(String pwd) {
        return Hashing.sha256().hashString(pwd, Charsets.UTF_8).toString();
    }
    
    public Mono<Boolean> unlock(String username) {
        String loginLockKey = Strings.lenientFormat(USER_LOGIN_LOCK, username);
        return redisTemplate.delete(loginLockKey)
            .map(affected -> affected > 0);
    }
    
    public void logout() {
    
    }
}
