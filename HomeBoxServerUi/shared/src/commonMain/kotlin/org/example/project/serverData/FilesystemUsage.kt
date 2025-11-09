package org.example.project.serverData

import kotlinx.serialization.Serializable

@Serializable
data class FilesystemUsage(
    val filesystem: String,    // e.g. /dev/sda1 or tmpfs
    val mount: String,         // typically same as filesystem.name() or "/"
    val total: String,         // human-readable, like "935G"
    val used: String,
    val avail: String,
    val usePercent: Int
)