package org.example.project.serverData

import kotlinx.serialization.Serializable

@Serializable
data class ServerSettings(
    val downloadFolder: String,
    val filelistUsername: String,
    val filelistPassword: String,
    val qbUsername: String,
    val qbPassword: String
)