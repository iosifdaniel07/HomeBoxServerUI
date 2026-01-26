package org.example.project

import androidx.compose.material3.*
import androidx.compose.runtime.*

enum class Screen(val text: String) {
    LOGIN(""),
    HOME("Search Page"),
    DISK_SPACE("Disk Management"),
    DOWNLOAD_MANAGEMENT("Download Management"),
    SERVER_SETTINGS("Server Settings")
}

@Composable
fun App() {
    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        var currentScreen by remember { mutableStateOf(Screen.LOGIN) }
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
                DiskSpaceScreen {
                    currentScreen = it
                }
            }

            Screen.DOWNLOAD_MANAGEMENT -> {
                DownloadManagementScreen {
                    currentScreen = it
                }
            }
            Screen.SERVER_SETTINGS -> {
               ServerSettingsScreen{
                   currentScreen = it
               }
            }
        }
    }
}