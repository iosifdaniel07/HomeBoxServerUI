package org.example.project

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val isSuccess: Boolean,
    val csrfToken: String? = null,
)

@Serializable
data class MeResponse(
    val user: String,
    val issuedAtMillis: Long,
    val csrfToken: String,
)