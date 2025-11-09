package org.example.project

import org.example.project.serverData.FilesystemUsage
import java.nio.file.FileSystems
import java.text.DecimalFormat
import kotlin.math.roundToInt

fun getAllDiskUsage(): List<FilesystemUsage> {
    val df = DecimalFormat("#.##")
    val list = mutableListOf<FilesystemUsage>()

    for (store in FileSystems.getDefault().fileStores) {
        val total = runCatching { store.totalSpace }.getOrDefault(0L)
        val usable = runCatching { store.usableSpace }.getOrDefault(0L)
        val unallocated = runCatching { store.unallocatedSpace }.getOrDefault(0L)
        val used = (total - unallocated).coerceAtLeast(0L)
        val percentUsed = if (total > 0) ((used.toDouble() / total) * 100).roundToInt() else 0

        list += FilesystemUsage(
            filesystem = store.name().ifBlank { "unknown" },
            mount = store.toString(),
            total = humanSize(total, df),
            used = humanSize(used, df),
            avail = humanSize(usable, df),
            usePercent = percentUsed
        )
    }
    return list.sortedBy { it.mount }
}

private fun humanSize(bytes: Long, df: DecimalFormat): String {
    if (bytes <= 0) return "0B"
    val units = arrayOf("B", "K", "M", "G", "T", "P")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return df.format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + units[digitGroups]
}