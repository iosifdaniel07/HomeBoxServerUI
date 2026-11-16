package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import homeboxserverui.composeapp.generated.resources.Res
import homeboxserverui.composeapp.generated.resources.myhomeBox
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
fun LoginScreen(onLogin: (String, String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }
    var pendingToken by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val client = Client

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(Res.drawable.myhomeBox),
                contentDescription = "My Home Box Logo",
                modifier = Modifier.size(240.dp)
            )

            if (pendingToken == null) {
                Text("Login", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            status = "Please fill in all fields"
                        } else {
                            status = "Logging in…"
                            scope.launch {
                                val response = client.login(username, password)
                                when {
                                    response.isSuccess -> onLogin(username, password)
                                    response.totpRequired -> {
                                        pendingToken = response.pendingToken
                                        status = null
                                    }
                                    else -> status = "Login failed"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Login") }
            } else {
                Text("Two-Factor Authentication", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Enter the 6-digit code from your authenticator app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = totpCode,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) totpCode = it },
                    label = { Text("6-digit code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (totpCode.length != 6) {
                            status = "Enter a 6-digit code"
                        } else {
                            status = "Verifying…"
                            scope.launch {
                                val response = client.verifyTotp(pendingToken!!, totpCode)
                                if (response.isSuccess) {
                                    onLogin(username, password)
                                } else {
                                    status = "Invalid code — try again"
                                    totpCode = ""
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Verify") }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { pendingToken = null; totpCode = ""; status = null }) {
                    Text("Back to login")
                }
            }

            status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFB00020))
            }
        }
    }
}
