package org.example.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.example.project.serverData.FilesystemUsage
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.example.project.serverData.DirListing
import org.example.project.serverData.FileEntry


@Composable
fun DiskSpaceScreen(onMenuSelected: (screen: Screen) -> Unit) {

    var data by remember { mutableStateOf<FilesystemUsage?>(null) }
    var items by remember { mutableStateOf<DirListing?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val client = Client
    val scope = rememberCoroutineScope()

    fun refresh() = scope.launch {
        loading = true; error = null
        try {
            data = client.diskSize()
            items = client.list()
        } catch (t: Throwable) {
            error = t.message ?: t.toString()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(

    ) { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            item {
                TopBar(Screen.DISK_SPACE, onMenuSelected)
            }
            item {
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    when {
                        loading && data == null -> CircularProgressIndicator(
                            Modifier.align(
                                Alignment.Center
                            )
                        )

                        error != null -> ErrorBanner(message = error!!, onRetry = { refresh() })
                        else -> {
                            data?.let {
                                FilesystemCard(it)
                            }
                        }
                    }
                }
            }

            items?.entries?.let {
                items(it) { fileEntry ->
                    FileItem(fileEntry) {
                        scope.launch {
                            client.deleteFile(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesystemCard(fs: FilesystemUsage) {
    ElevatedCard {
        Column(Modifier.padding(14.dp)) {
            Text(
                fs.filesystem,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                fs.mount,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { fs.usePercent.coerceIn(0, 100) / 100f },
                color = usageColor(fs.usePercent),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            )

            Spacer(Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LabelValue("Total", fs.total, bold = true)
                Dot()
                LabelValue("Used", fs.used)
                Dot()
                LabelValue("Avail", fs.avail)
                Spacer(Modifier.weight(1f))
                Text(
                    "${fs.usePercent}%",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = usageColor(fs.usePercent)
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String, bold: Boolean = false) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = if (bold) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            else MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun Dot() {
    Box(Modifier.size(6.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxSize()
        ) {}
    }
}

@Composable
private fun usageColor(percent: Int): Color = when {
    percent >= 90 -> MaterialTheme.colorScheme.error
    percent >= 75 -> MaterialTheme.colorScheme.tertiary
    percent >= 60 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
fun FileItem(fileEntry: FileEntry, onDelete: (name: String) -> Unit) {
    ElevatedCard(
        modifier = Modifier.padding(14.dp),
        shape = MaterialTheme.shapes.medium,
        onClick = {
            onDelete(fileEntry.name)
        }
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = fileEntry.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatSize(fileEntry.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// Helper function to format size in a readable way (e.g., bytes, KB, MB)
fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}