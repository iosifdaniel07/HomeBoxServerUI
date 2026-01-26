package org.example.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch
import org.example.project.serverData.ServerSettings

@Composable
fun ServerSettingsScreen(
    onMenuSelected: (screen: Screen) -> Unit
) {
    var settingsData by remember { mutableStateOf<ServerSettings?>(null) }

    var downloadFolder by rememberSaveable { mutableStateOf(settingsData?.downloadFolder) }

    var filelistUser by rememberSaveable { mutableStateOf(settingsData?.filelistUsername) }
    var filelistPass by rememberSaveable { mutableStateOf(settingsData?.filelistPassword) }

    var qbUser by rememberSaveable { mutableStateOf(settingsData?.qbUsername) }
    var qbPass by rememberSaveable { mutableStateOf(settingsData?.qbPassword) }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val client = Client
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        settingsData = client.getServerSettins()
        downloadFolder = settingsData?.downloadFolder
        filelistUser = settingsData?.filelistUsername
        filelistPass = settingsData?.filelistPassword
        qbUser = settingsData?.qbUsername
        qbPass = settingsData?.qbPassword
    }

    Scaffold { padding ->
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = 16.dp + padding.calculateTopPadding(),
                    bottom = 16.dp + padding.calculateBottomPadding()
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                item { TopBar(Screen.SERVER_SETTINGS, onMenuSelected) }

                item {
                    Text("Paths", style = MaterialTheme.typography.titleMedium)
                }

                item(key = downloadFolder) {
                    OutlinedTextField(
                        value = downloadFolder ?: "",
                        onValueChange = { downloadFolder = it },
                        label = { Text("Download folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Filelist", style = MaterialTheme.typography.titleMedium)
                }

                item {
                    OutlinedTextField(
                        value = filelistUser ?: "",
                        onValueChange = { filelistUser = it },
                        label = { Text("Filelist username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    PasswordField(
                        value = filelistPass ?: "",
                        onValueChange = { filelistPass = it },
                        label = "Filelist password",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text("qBittorrent", style = MaterialTheme.typography.titleMedium)
                }

                item {
                    OutlinedTextField(
                        value = qbUser ?: "",
                        onValueChange = { qbUser = it },
                        label = { Text("qBittorrent username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    PasswordField(
                        value = qbPass ?: "",
                        onValueChange = { qbPass = it },
                        label = "qBittorrent password",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            enabled = !saving,
                            onClick = {
                                error = null

                                // Validări minimale (le ajustezi cum vrei)
                                if (downloadFolder?.isBlank() ?: true) {
                                    error = "Download folder is required."; return@Button
                                }

                                saving = true
                                try {
                                    scope.launch {
                                        client.saveServerSettings(
                                            ServerSettings(
                                                downloadFolder = downloadFolder?.trim().orEmpty(),
                                                filelistUsername = filelistUser?.trim().orEmpty(),
                                                filelistPassword = filelistPass.orEmpty(),
                                                qbUsername = qbUser?.trim().orEmpty(),
                                                qbPassword = qbPass.orEmpty()
                                            )
                                        )
                                    }
                                } finally {
                                    saving = false
                                }
                            }
                        ) {
                            if (saving) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Save")
                        }
                    }
                }
            }
        }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    var show by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { show = !show }) {
                Text(if (show) "Hide" else "Show")
            }
        },
        modifier = modifier
    )
}
