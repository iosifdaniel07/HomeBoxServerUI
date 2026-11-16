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
import androidx.compose.foundation.text.selection.SelectionContainer
import kotlinx.coroutines.launch
import org.example.project.serverData.ServerSettingsClient

@Composable
fun ServerSettingsScreen(
    onMenuSelected: (screen: Screen) -> Unit
) {
    var settingsData by remember { mutableStateOf<ServerSettingsClient?>(null) }

    var filelistUser by rememberSaveable { mutableStateOf(settingsData?.filelistUsername) }
    var filelistPass by rememberSaveable { mutableStateOf(settingsData?.filelistPassword) }

    var qbUser by rememberSaveable { mutableStateOf(settingsData?.qbUsername) }
    var qbPass by rememberSaveable { mutableStateOf(settingsData?.qbPassword) }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    // 2FA state
    var totpDeviceNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var totpSetupUri by remember { mutableStateOf<String?>(null) }
    var totpSetupSecret by remember { mutableStateOf<String?>(null) }
    var totpDeviceName by remember { mutableStateOf("My Phone") }
    var totpCode by remember { mutableStateOf("") }
    var totpStatus by remember { mutableStateOf<String?>(null) }
    var removingDevice by remember { mutableStateOf<String?>(null) }
    var removeCode by remember { mutableStateOf("") }
    var removeStatus by remember { mutableStateOf<String?>(null) }

    val client = Client
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            settingsData = client.getServerSettins()
            filelistUser = settingsData?.filelistUsername
            filelistPass = settingsData?.filelistPassword
            qbUser = settingsData?.qbUsername
            qbPass = settingsData?.qbPassword
            totpDeviceNames = settingsData?.totpDeviceNames ?: emptyList()
        } catch (e: Exception) {
            loadError = "Failed to load settings: ${e.message}"
        }
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

                loadError?.let {
                    item { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                item {
                    Text(
                        "Download folder is configured by the server operator via the HOMEBOX_DOWNLOAD_FOLDER environment variable.",
                        style = MaterialTheme.typography.bodySmall
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

                // ---- 2FA section ----
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Two-Factor Authentication (2FA)", style = MaterialTheme.typography.titleMedium)
                }

                item {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(if (totpDeviceNames.isEmpty()) "Status: Disabled" else "Registered devices: ${totpDeviceNames.size}")
                        Spacer(Modifier.width(12.dp))
                        Button(onClick = {
                            totpStatus = null; totpCode = ""; totpDeviceName = "My Phone"
                            scope.launch {
                                try {
                                    val setup = client.setupTotp()
                                    totpSetupSecret = setup.secret
                                    totpSetupUri = setup.otpauthUri
                                } catch (e: Exception) {
                                    totpStatus = "Error: ${e.message}"
                                }
                            }
                        }) { Text("Add device") }
                    }
                }

                for (name in totpDeviceNames) {
                    item {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(name, modifier = Modifier.weight(1f))
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                onClick = { removingDevice = name; removeCode = ""; removeStatus = null }
                            ) { Text("Remove") }
                        }
                    }
                }

                if (totpSetupSecret != null) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Device name:", style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = totpDeviceName,
                                    onValueChange = { totpDeviceName = it },
                                    label = { Text("Device name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("1. Open your authenticator app (Google Authenticator, Authy…) and scan the QR code or enter the key manually:", style = MaterialTheme.typography.bodySmall)
                                SelectionContainer { Text(totpSetupSecret!!, style = MaterialTheme.typography.bodyMedium) }
                                Text("2. Enter the 6-digit code shown in your app to confirm:", style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = totpCode,
                                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) totpCode = it },
                                    label = { Text("6-digit code") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                totpStatus?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = totpCode.length == 6 && totpDeviceName.isNotBlank(),
                                        onClick = {
                                            scope.launch {
                                                val ok = client.enableTotp(totpSetupSecret!!, totpCode, totpDeviceName.trim())
                                                if (ok) {
                                                    totpDeviceNames = totpDeviceNames + totpDeviceName.trim()
                                                    totpSetupSecret = null
                                                    totpSetupUri = null
                                                    totpCode = ""
                                                    totpStatus = null
                                                } else {
                                                    totpStatus = "Invalid code — try again"
                                                    totpCode = ""
                                                }
                                            }
                                        }
                                    ) { Text("Add device") }
                                    TextButton(onClick = { totpSetupSecret = null; totpSetupUri = null; totpCode = ""; totpStatus = null }) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    }
                }

                if (removingDevice != null) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Enter the 6-digit code from \"${removingDevice}\" to remove it:", style = MaterialTheme.typography.bodySmall)
                                OutlinedTextField(
                                    value = removeCode,
                                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) removeCode = it },
                                    label = { Text("6-digit code") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                removeStatus?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        enabled = removeCode.length == 6,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        onClick = {
                                            val deviceToRemove = removingDevice ?: return@Button
                                            scope.launch {
                                                val ok = client.disableTotp(removeCode, deviceToRemove)
                                                if (ok) {
                                                    totpDeviceNames = totpDeviceNames.filter { it != deviceToRemove }
                                                    removingDevice = null
                                                    removeCode = ""
                                                    removeStatus = null
                                                } else {
                                                    removeStatus = "Invalid code — try again"
                                                    removeCode = ""
                                                }
                                            }
                                        }
                                    ) { Text("Confirm Remove") }
                                    TextButton(onClick = { removingDevice = null; removeCode = ""; removeStatus = null }) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
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
                                saving = true
                                scope.launch {
                                    try {
                                        client.saveServerSettings(
                                            ServerSettingsClient(
                                                filelistUsername = filelistUser?.trim().orEmpty(),
                                                filelistPassword = filelistPass.orEmpty(),
                                                qbUsername = qbUser?.trim().orEmpty(),
                                                qbPassword = qbPass.orEmpty()
                                            )
                                        )
                                    } catch (e: Exception) {
                                        error = "Save failed: ${e.message}"
                                    } finally {
                                        saving = false
                                    }
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
