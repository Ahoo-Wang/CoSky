package me.ahoo.cosky.rest.security.user

data class AddUserRequest(
    val password: String
)

data class ChangePwdRequest(
    val oldPassword: String,
    val newPassword: String,
)

class LoginRequest(
    var password: String
)

interface TokenResponse {
    val accessToken: String
    val refreshToken: String
}

data class LoginResponse(
    override val accessToken: String,
    override val refreshToken: String,
) : TokenResponse


data class RefreshRequest(
    override val accessToken: String,
    override val refreshToken: String,
) : TokenResponse
