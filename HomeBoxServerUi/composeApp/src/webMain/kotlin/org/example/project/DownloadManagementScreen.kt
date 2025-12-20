package org.example.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.example.project.downloadData.TorrentInfo

@Composable
fun DownloadManagementScreen(onMenuSelected: (screen: Screen) -> Unit) {

    val scope = rememberCoroutineScope()
    val client = Client
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var torrentsInfo by remember { mutableStateOf<List<TorrentInfo>>(emptyList()) }
    var showDeleteDialog: Pair<Boolean, TorrentInfo?> by remember {
        mutableStateOf(
            Pair(
                false,
                null
            )
        )
    }
    var deleteError: Pair<String?, String?> by remember { mutableStateOf(Pair(null, null)) }

    val torrentsFlow = remember { getTorrentsStatusFlow(client) }
    var job: Job? = null

    LaunchedEffect(deleteError) {
        deleteError.first?.let {
            delay(3000) // Wait for 5 seconds
            deleteError = null to null// Hide the Toast after 5 seconds
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                error = null
                val isRunning = client.qBittorentRunning()
                if (!isRunning) {
                    error = "QBittorrent is not running"
                }
                println(isRunning)
                job = launch {
                    torrentsFlow.collect { newTorrents ->
                        torrentsInfo = newTorrents
                        loading = false
                        println(torrentsInfo)
                    }
                }
            } catch (e: Exception) {
                loading = false
                error = e.message ?: e.toString()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            job?.cancel()  // Cancel the job when the composable is disposed
        }
    }

    Scaffold { padding ->

        key(showDeleteDialog) {
            if (showDeleteDialog.first && showDeleteDialog.second != null) {
                DeleteConfirmationDialog(
                    onConfirm = {
                        showDeleteDialog.second?.hash?.let { hash ->
                            scope.launch {
                                println("delete torent ${hash}")
                                val isSucceed = client.deleteTorrent(hash)
                                if (isSucceed) {
                                    println("deleted")
                                    torrentsInfo = torrentsInfo.filter { it.hash != hash }
                                } else {
                                    println("failed to delete")
                                    deleteError = "Failed to delete the item" to hash
                                }
                                showDeleteDialog = false to null // Close the dialog
                            }
                        }
                    },
                    onDismiss = {
                        showDeleteDialog = false to null// Close the dialog
                    },
                    file = showDeleteDialog.second?.name
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            item {
                TopBar(Screen.DOWNLOAD_MANAGEMENT, onMenuSelected)
            }

            item {
                Box(
                    Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    when {
                        loading -> CircularProgressIndicator(
                            Modifier.align(
                                Alignment.Center
                            )
                        )

                        error != null -> ErrorBanner(message = error!!, onRetry = { })
                    }
                }
            }

            items(items = torrentsInfo) { torrent ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    TorrentCard(torrent) {
                        showDeleteDialog = true to torrent
                    }
                    key(deleteError) {
                        if (deleteError.first != null && deleteError.second == torrent.hash) {
                            deleteError.first?.let {
                                Notification(message = it)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getTorrentsStatusFlow(client: Client): Flow<List<TorrentInfo>> = flow {
    while (true) {
        // Simulate API call
        val torrents = client.getTorrentsStatus()
        emit(torrents)

        // Delay before fetching again (e.g., every 3 seconds)
        delay(3000)
    }
}