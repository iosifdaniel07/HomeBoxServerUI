package org.example.project.serverData

import kotlinx.serialization.Serializable

@Serializable
data class DownloadStatus(
    val downloadingStarted: Boolean,
    val error: String? = null
)

@Serializable
data class DownloadItem(
    val itemId: String
)