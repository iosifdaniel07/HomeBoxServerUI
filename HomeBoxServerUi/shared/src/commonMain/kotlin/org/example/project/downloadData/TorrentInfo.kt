package org.example.project.downloadData

import kotlinx.serialization.Serializable

@Serializable
data class TorrentInfo(
    val added_on: Long,
    val amount_left: Long,
    val auto_tmm: Boolean,
    val availability: Double,
    val category: String,
    val completed: Long,
    val completion_on: Long,
    val content_path: String,
    val dl_limit: Int,
    val dlspeed: Int,
    val download_path: String,
    val downloaded: Long,
    val downloaded_session: Long,
    val eta: Long,
    val f_l_piece_prio: Boolean,
    val force_start: Boolean,
    val hash: String,
    val inactive_seeding_time_limit: Int,
    val infohash_v1: String,
    val infohash_v2: String,
    val last_activity: Long,
    val magnet_uri: String,
    val max_inactive_seeding_time: Int,
    val max_ratio: Int,
    val max_seeding_time: Int,
    val name: String,
    val num_complete: Int,
    val num_incomplete: Int,
    val num_leechs: Int,
    val num_seeds: Int,
    val priority: Int,
    val progress: Double,
    val ratio: Double,
    val ratio_limit: Int,
    val save_path: String,
    val seeding_time: Long,
    val seeding_time_limit: Int,
    val seen_complete: Long,
    val seq_dl: Boolean,
    val size: Long,
    val state: String,
    val super_seeding: Boolean,
    val tags: String,
    val time_active: Long,
    val total_size: Long,
    val tracker: String,
    val trackers_count: Int,
    val up_limit: Int,
    val uploaded: Long,
    val uploaded_session: Long,
    val upspeed: Int
)

fun TorrentInfo.getTorrentStatusDescription(): String {
    return when (this.state) {
        "error" -> "Some error occurred, applies to paused torrents."
        "missingFiles" -> "Torrent data files are missing."
        "uploading" -> "Torrent is being seeded and data is being transferred."
        "pausedUP" -> "Torrent is paused and has finished downloading."
        "queuedUP" -> "Queuing is enabled and torrent is queued for upload."
        "stalledUP" -> "Torrent is being seeded, but no connections were made."
        "checkingUP" -> "Torrent has finished downloading and is being checked."
        "forcedUP" -> "Torrent is forced to upload and ignore the queue limit."
        "allocating" -> "Torrent is allocating disk space for download."
        "downloading" -> "Torrent is being downloaded and data is being transferred."
        "metaDL" -> "Torrent has just started downloading and is fetching metadata."
        "pausedDL" -> "Torrent is paused and has not finished downloading."
        "queuedDL" -> "Queuing is enabled and torrent is queued for download."
        "stalledDL" -> "Torrent is being downloaded, but no connection was made."
        "checkingDL" -> "Same as checkingUP, but torrent has not finished downloading."
        "forcedDL" -> "Torrent is forced to download and ignores the queue limit."
        "checkingResumeData" -> "Checking resume data on qBt startup."
        "moving" -> "Torrent is moving to another location."
        "unknown" -> "Unknown status."
        else -> "Status not recognized."
    }
}
