package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import homeboxserverui.composeapp.generated.resources.Res
import homeboxserverui.composeapp.generated.resources.search
import kotlinx.coroutines.launch
import org.example.project.searchData.CategoryOptions
import org.example.project.searchData.FirstSearchResponse
import org.example.project.searchData.SearchFilters
import org.example.project.searchData.SearchInOptions
import org.example.project.searchData.SearchResponse
import org.example.project.searchData.SortOptions
import org.jetbrains.compose.resources.painterResource

@Composable
fun HomeScreen(username: String, onLogout: () -> Unit) {
    // Sample data for demonstration
    val client = Client
    val scope = rememberCoroutineScope()                        // ← prefer this over MainScope()

    var firstSearch by remember { mutableStateOf<FirstSearchResponse?>(null) }
    var searchInOptions by remember { mutableStateOf<MutableList<SearchInOptions>>(mutableListOf()) }
    var categoryOptions by remember { mutableStateOf<MutableList<CategoryOptions>>(mutableListOf()) }
    var sortOptions by remember { mutableStateOf<MutableList<SortOptions>>(mutableListOf()) }


    // Call the API when the page opens
    LaunchedEffect(Unit) {
        try {
            firstSearch = client.firstSearch()
            firstSearch?.searchFiltersData?.searchInOptionsList?.let {
                searchInOptions = it.toMutableList()
            }
            firstSearch?.searchFiltersData?.categoryOptionsList?.let {
                categoryOptions = it.toMutableList()
            }
            firstSearch?.searchFiltersData?.sortOptionsList?.let {
                sortOptions = it.toMutableList()
            }
        } catch (e: Exception) {

        }
    }

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

                Row(
                    modifier = Modifier.wrapContentWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DropdownMenuView(
                        SearchFilters.SearchInOptions.name, searchInOptions.map { it.text },
                        onFilterSelected = { searchInOption ->
                            //TODO....
                        }
                    )
                    DropdownMenuView(
                        SearchFilters.CategoryOptions.name, categoryOptions.map { it.text },
                        onFilterSelected = { categoryOptions ->
                            //TODO....
                        }
                    )
                    DropdownMenuView(
                        SearchFilters.SortOptions.name, sortOptions.map { it.text },
                        onFilterSelected = { categoryOptions ->
                            //TODO....
                        }
                    )
                }

                // Search bar with simple button for search
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Search items...") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
                    )

                    // Simple search button
                    Button(
                        onClick = {
                            println("Search query: $searchQuery")
                            scope.launch {
                                val result = client.search(searchQuery)
                                println("Search result: $result")
                            }
                        },
                        modifier = Modifier.padding(start = 2.dp).size(70.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.search),
                            contentDescription = "Search",
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }
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