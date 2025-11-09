package org.example.project

import androidx.compose.material3.*
import androidx.compose.runtime.*

enum class Screen {
    LOGIN,
    HOME,
    DISK_SPACE
}

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
                HomeScreen(username = loggedInUser) { screen ->
                    currentScreen = screen
                    if (screen == Screen.LOGIN) {
                        loggedInUser = ""
                    }
                }
            }

            Screen.DISK_SPACE -> {
                DiskSpaceScreen{
                    currentScreen = it
                }
            }
        }
    }
}