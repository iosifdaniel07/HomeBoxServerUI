package org.example.project

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import org.example.project.serverData.FileEntry


@Composable
fun DiskSpaceScreen(onMenuSelected: (screen: Screen) -> Unit) {

    var data by remember { mutableStateOf<FilesystemUsage?>(null) }
    var items by remember { mutableStateOf<List<FileEntry>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val client = Client
    val scope = rememberCoroutineScope()
    var showDeleteDialog: Pair<Boolean, String?> by remember { mutableStateOf(Pair(false, null)) }
    var errorDelete by remember { mutableStateOf<String?>(null) }

    fun refresh() = scope.launch {
        loading = true; error = null
        try {
            data = client.diskSize()
            items = client.list().entries
        } catch (t: Throwable) {
            error = t.message ?: t.toString()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(errorDelete) {
        errorDelete?.let {
            delay(3000) // Wait for 5 seconds
            errorDelete = null // Hide the Toast after 5 seconds
        }
    }

    Scaffold { padding ->

        key(showDeleteDialog) {
            if (showDeleteDialog.first) {
                DeleteConfirmationDialog(
                    onConfirm = {
                        showDeleteDialog.second?.let { file ->
                            scope.launch {
                                val isSucceed = client.deleteFile(file)
                                if (isSucceed) {
                                    items = items?.filter { it.name != file }
                                } else {
                                    errorDelete = "Failed to delete ${file}. Please try again."
                                }
                            }
                        }
                        showDeleteDialog = false to null // Close the dialog
                    },
                    onDismiss = {
                        showDeleteDialog = false to null// Close the dialog
                    },
                    file = showDeleteDialog.second
                )
            }
        }

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

            item {
                HorizontalDivider(
                    color = Color.Gray, // Line color
                    thickness = 1.dp,    // Line thickness
                    modifier = Modifier.padding(vertical = 16.dp) // Padding around the divider
                )
            }

            items?.let { items ->
                item {
                    Text(
                        text = "Files",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 14.dp, bottom = 8.dp) // Adds space between the label and the card
                    )
                }
                items(items = items) { fileEntry ->
                    FileItem(fileEntry) {
                        scope.launch {
                            showDeleteDialog = true to fileEntry.name
                        }
                    }
                }
            }
        }

        errorDelete?.let {
            ToastNotification(message = it)
        }
    }
}

@Composable
private fun FilesystemCard(fs: FilesystemUsage) {
    Column {
        Text(
            text = "Disk Space",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp) // Adds space between the label and the card
        )

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
}

@Composable
fun ErrorBanner(message: String, onRetry: () -> Unit) {
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
fun FileItem(fileEntry: FileEntry, onClick: (name: String) -> Unit) {
    ElevatedCard(
        modifier = Modifier.padding(start = 14.dp, end = 14.dp),
        shape = MaterialTheme.shapes.medium,
        onClick = {
            onClick(fileEntry.name)
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

@Composable
fun DeleteConfirmationDialog(file: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Delete File")
        },
        text = {
            Text("Are you sure you want to delete ${file}?")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Yes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("No")
            }
        }
    )
}

@Composable
fun ToastNotification(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(16.dp)
            .background(Color.Red.copy(alpha = 0.7f), shape = RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(text = message, color = Color.White)
    }
}