package me.ahoo.cosky.rest.security.authentication

import me.ahoo.cosec.api.authentication.Authentication
import me.ahoo.cosec.api.principal.CoSecPrincipal
import me.ahoo.cosky.rest.security.user.UserService
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty

@Service
class UserPasswordAuthentication(
    private val userService: UserService
) : Authentication<UserPasswordCredentials, CoSecPrincipal> {
    override val supportCredentials: Class<UserPasswordCredentials>
        get() = UserPasswordCredentials::class.java

    override fun authenticate(credentials: UserPasswordCredentials): Mono<out CoSecPrincipal> {
        return userService.login(username = credentials.username, pwd = credentials.pwd)
            .switchIfEmpty {
                Mono.error { IllegalArgumentException("用户名或密码错误!") }
            }
    }
}
