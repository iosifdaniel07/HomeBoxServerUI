package org.example.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun DownloadManagementScreen(onMenuSelected: (screen: Screen) -> Unit) {

    val scope = rememberCoroutineScope()
    val client = Client

    LaunchedEffect(Unit) {
        scope.launch {
            val isInstalled = client.qBittorentRunning()
            println(isInstalled)
        }
    }

    Scaffold { padding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            item {
                TopBar(Screen.DOWNLOAD_MANAGEMENT, onMenuSelected)
            }
        }
    }
}