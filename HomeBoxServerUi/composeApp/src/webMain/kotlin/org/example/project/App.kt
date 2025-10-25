package org.example.project

import androidx.compose.material3.*
import androidx.compose.runtime.*

enum class Screen {
    LOGIN,
    HOME
}

data class ItemCard(
    val id: Int,
    val title: String,
    val imageRes: String = "myhomeBox"
)

@Composable
fun App() {
    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        var currentScreen by remember { mutableStateOf(Screen.HOME) }
        var loggedInUser by remember { mutableStateOf("") }

        when (currentScreen) {
            Screen.LOGIN -> {
                LoginScreen { username, password ->
                    println("Current scren: Home")
                    loggedInUser = username
                    currentScreen = Screen.HOME
                }
            }

            Screen.HOME -> {
                HomeScreen(username = loggedInUser) {
                    currentScreen = Screen.LOGIN
                    loggedInUser = ""
                }
            }
        }
    }
}