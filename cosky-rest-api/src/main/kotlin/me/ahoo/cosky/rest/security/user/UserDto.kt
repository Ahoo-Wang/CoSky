package me.ahoo.cosky.rest.security.user

data class AddUserRequest(
    val password: String,
)

data class ChangePwdRequest(
    val oldPassword: String,
    val newPassword: String,
)

class LoginRequest(
    var password: String,
)
