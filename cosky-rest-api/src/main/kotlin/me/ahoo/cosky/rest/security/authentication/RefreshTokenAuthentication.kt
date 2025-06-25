package me.ahoo.cosky.rest.security.authentication

import jakarta.validation.constraints.NotBlank
import me.ahoo.cosec.api.principal.CoSecPrincipal
import me.ahoo.cosec.api.token.TokenPrincipal
import me.ahoo.cosec.authentication.token.AbstractRefreshTokenAuthentication
import me.ahoo.cosec.authentication.token.RefreshTokenCredentials
import me.ahoo.cosec.token.TokenVerifier
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

@Service
class RefreshTokenAuthentication(
    private val tokenVerifier: TokenVerifier
) :
    AbstractRefreshTokenAuthentication<DefaultRefreshTokenCredentials, CoSecPrincipal>(
        DefaultRefreshTokenCredentials::class.java
    ) {
    override val supportCredentials: Class<DefaultRefreshTokenCredentials>
        get() = DefaultRefreshTokenCredentials::class.java

    override fun authenticate(credentials: DefaultRefreshTokenCredentials): Mono<out CoSecPrincipal> {
        return tokenVerifier.refresh<TokenPrincipal>(credentials).toMono()
    }
}

data class DefaultRefreshTokenCredentials(
    @field:NotBlank override val accessToken: String,
    @field:NotBlank override val refreshToken: String
) :
    RefreshTokenCredentials
