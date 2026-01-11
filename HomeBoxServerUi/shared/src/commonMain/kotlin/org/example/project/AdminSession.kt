package org.example.project

import kotlinx.serialization.Serializable

@Serializable
data class AdminSession(val user: String, val issuedAtMillis: Long)
