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
    val totpRequired: Boolean = false,
    val pendingToken: String? = null,
)

@Serializable
data class MeResponse(
    val user: String,
    val issuedAtMillis: Long,
    val csrfToken: String,
)

@Serializable
data class TotpVerifyRequest(val pendingToken: String, val code: String)

@Serializable
data class TotpSetupResponse(val otpauthUri: String, val secret: String)

@Serializable
data class TotpEnableRequest(val secret: String, val code: String, val deviceName: String = "Device")

@Serializable
data class TotpDisableRequest(val code: String, val deviceName: String? = null)