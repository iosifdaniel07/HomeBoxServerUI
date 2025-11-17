package org.example.project.serverData

@kotlinx.serialization.Serializable
data class FileEntry(
    val name: String,
    val pathRel: String,      // relative to base folder
    val isDir: Boolean,
    val sizeBytes: Long,      // file size; for directory it's 0 unless you ask to compute
    val modifiedEpochMs: Long,
    val readable: Boolean,
    val writable: Boolean,
    var children: List<FileEntry>
)

@kotlinx.serialization.Serializable
data class DirListing(
    val base: String,
    val at: String,           // the subpath you listed
    val entries: List<FileEntry>,
    val computedDirSizes: Boolean = false
)

@kotlinx.serialization.Serializable
data class OpResult(
    val ok: Boolean,
    val message: String
)

@kotlinx.serialization.Serializable
data class DeleteItem(
    val item: String
)

@kotlinx.serialization.Serializable
data class DeleteResponse(
    val result: Boolean
)