package org.example.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.downloadData.TorrentInfo
import org.example.project.downloadData.getTorrentStatusDescription

@Composable
fun TorrentCard(torrentInfo: TorrentInfo, onClick: (name: String) -> Unit) {
    ElevatedCard(
        modifier = Modifier.padding(start = 14.dp, end = 14.dp),
        shape = MaterialTheme.shapes.medium,
        onClick = {
            onClick(torrentInfo.name)
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Name and progress bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = torrentInfo.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(torrentInfo.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            LinearProgressIndicator(
            progress = { torrentInfo.progress.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = ProgressIndicatorDefaults.linearTrackColor,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )

            // Torrent details (seeds, peers, download speed, etc.)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Seeds: ${torrentInfo.num_seeds}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Peers: ${torrentInfo.num_leechs}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Down Speed: ${formatSpeed(torrentInfo.dlspeed)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // ETA and ratio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = torrentInfo.getTorrentStatusDescription(),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "ETA: ${formatTime(torrentInfo.eta)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Ratio: ${torrentInfo.ratio}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// Helper function to format download speed (e.g., converting bytes to MiB/s)
fun formatSpeed(speed: Int): String {
    val speedInMiB = speed / (1024.0 * 1024.0)  // Convert speed to MiB/s
    return "$speedInMiB MiB/s"  // Using Kotlin string template
}

// Helper function to format ETA time (converting seconds into a readable format)
fun formatTime(eta: Long): String {
    val hours = eta / 3600  // 1 hour = 3600 seconds
    val minutes = (eta % 3600) / 60  // Get remaining minutes after hours
    return "$hours h $minutes m"
}
