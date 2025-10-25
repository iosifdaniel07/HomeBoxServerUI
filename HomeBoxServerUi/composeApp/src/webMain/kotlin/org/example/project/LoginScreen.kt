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
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }   // ← UI feedback
    val scope = rememberCoroutineScope()                        // ← prefer this over MainScope()
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

            Text("Login", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        error = "Please fill in all fields"
                        status = null
                    } else {
                        error = null
                        status = "Logging in…"
                        scope.launch {
                            print("start loggin")
                            val response = client.login(username, password).isSuccess//getServerStatus()//
                            print("end loggin ${response}")
                            if(response){
                                onLogin(username, password)
                            } else {
                                status = "Login failed"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Login") }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color.Red)
            }

            status?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = if (it.startsWith("Login success")) Color(0xFF2E7D32) else Color(0xFFB00020))
            }
        }
    }
}
