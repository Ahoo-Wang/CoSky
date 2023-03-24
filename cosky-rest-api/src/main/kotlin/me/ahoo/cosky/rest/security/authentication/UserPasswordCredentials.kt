package me.ahoo.cosky.rest.security.authentication

import me.ahoo.cosec.api.authentication.Credentials

data class UserPasswordCredentials(
    val username: String,
    val pwd: String,
) : Credentials
