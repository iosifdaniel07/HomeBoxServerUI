package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource

import homeboxserverui.composeapp.generated.resources.Res
import homeboxserverui.composeapp.generated.resources.compose_multiplatform
import homeboxserverui.composeapp.generated.resources.myhomeBox

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
        var currentScreen by remember { mutableStateOf(Screen.LOGIN) }
        var loggedInUser by remember { mutableStateOf("") }
        
        when (currentScreen) {
            Screen.LOGIN -> {
                LoginScreen { username, password ->
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

@Composable
fun HomeScreen(username: String, onLogout: () -> Unit) {
    // Sample data for demonstration
    val allItems = remember {
        listOf(
            ItemCard(1, "Smart Bulb"),
            ItemCard(2, "Temperature Sensor"),
            ItemCard(3, "Door Lock"),
            ItemCard(4, "Security Camera"),
            ItemCard(5, "Motion Detector"),
            ItemCard(6, "Smart Thermostat"),
            ItemCard(7, "Light Switch"),
            ItemCard(8, "Smoke Detector"),
            ItemCard(9, "Water Leak Sensor"),
            ItemCard(10, "Smart Plug")
        )
    }
    
    var searchQuery by remember { mutableStateOf("") }
    
    // Filter items based on search query
    val filteredItems = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allItems
        } else {
            allItems.filter { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Welcome, $username!",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Button(onClick = onLogout) {
                        Text("Logout")
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search items...") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
        }
    ) { paddingValues ->
        // Vertical grid of cards
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 200.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(filteredItems) { item ->
                ItemCardView(item)
            }
        }
    }
}

@Composable
fun ItemCardView(item: ItemCard) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Image
            Image(
                painter = painterResource(Res.drawable.myhomeBox),
                contentDescription = item.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
                contentScale = ContentScale.Fit
            )
            
            // Title
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}