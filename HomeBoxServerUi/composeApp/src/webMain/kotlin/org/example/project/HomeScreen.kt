package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import org.example.project.searchData.SearchItem
import org.example.project.searchData.SortOptions
import org.jetbrains.compose.resources.painterResource
import kotlin.math.max
import kotlin.math.min

@Composable
fun HomeScreen(username: String, onLogout: () -> Unit) {
    // Sample data for demonstration
    val client = Client
    val scope = rememberCoroutineScope()                        // ← prefer this over MainScope()

    var firstSearch by remember { mutableStateOf<FirstSearchResponse?>(null) }
    var searchInOptions by remember { mutableStateOf<MutableList<SearchInOptions>>(mutableListOf()) }
    var categoryOptions by remember { mutableStateOf<MutableList<CategoryOptions>>(mutableListOf()) }
    var sortOptions by remember { mutableStateOf<MutableList<SortOptions>>(mutableListOf()) }
    var searchItems by remember { mutableStateOf<List<SearchItem>>(mutableListOf()) }
    var selectedCategory by remember { mutableStateOf<CategoryOptions?>(null) }
    var selectedSearchIn by remember { mutableStateOf<SearchInOptions?>(null) }
    var selectedSort by remember { mutableStateOf<SortOptions?>(null) }
    var selectedPage = 1
    var pagesPair by remember { mutableStateOf(Pair(1, 1)) }

    suspend fun loadInitialData() {
        try {
            firstSearch = client.firstSearch()
            firstSearch?.searchFiltersData?.let {
                searchInOptions = it.searchInOptionsList.toMutableList()
                selectedSearchIn = it.selectedSearchIn ?: searchInOptions.firstOrNull()
                pagesPair = it.firstLastPage
                println(pagesPair)
            }
            firstSearch?.searchFiltersData?.let {
                categoryOptions = it.categoryOptionsList.toMutableList()
                selectedCategory = it.selectedCategory ?: categoryOptions.firstOrNull()
            }
            firstSearch?.searchFiltersData?.let {
                sortOptions = it.sortOptionsList.toMutableList()
                selectedSort = it.selectedSort ?: sortOptions.firstOrNull()
            }
            firstSearch?.searchItems?.let {
                searchItems = it.toMutableList()
            }
        } catch (e: Exception) {

        }
    }

    // Call the API when the page opens
    LaunchedEffect(Unit) {
        loadInitialData()
    }

    var searchQuery by remember { mutableStateOf("") }

    @OptIn(ExperimentalLayoutApi::class)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Header (welcome + logout)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Welcome, $username!",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Button(onClick = onLogout) { Text("Logout") }
                }
            }

            // Filters (use FlowRow instead of a lazy grid)
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 1,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    key(selectedCategory?.text) {
                        DropdownMenuView(
                            SearchFilters.CategoryOptions.name,
                            categoryOptions.map { it.text },
                            selectedCategory?.text,
                            onFilterSelected = {
                                selectedCategory =
                                    categoryOptions.find { it.text == selectedCategory?.text }
                            }
                        )
                    }
                    key(selectedSearchIn?.text) {
                        DropdownMenuView(
                            SearchFilters.SearchInOptions.name,
                            searchInOptions.map { it.text },
                            selectedSearchIn?.text,
                            onFilterSelected = {
                                selectedSearchIn =
                                    searchInOptions.find { it.text == selectedSearchIn?.text }
                            }
                        )
                    }
                    key(selectedSort?.text) {
                        DropdownMenuView(
                            SearchFilters.SortOptions.name,
                            sortOptions.map { it.text },
                            selectedSort?.text,
                            onFilterSelected = {
                                selectedSort = sortOptions.find { it.text == selectedSort?.text }
                            }
                        )
                    }
                }
            }

            // Search bar
            item {
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
                    Button(
                        onClick = {
                            println("Search query: $searchQuery")
                            scope.launch {
                                // val result = client.search(searchQuery)
                                // println("Search result: $result")
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp).size(70.dp)
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.search),
                            contentDescription = "Search",
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }
            }

            item {
                key(pagesPair) {
                    Pager(
                        totalPages = pagesPair.second,
                        initialPage = selectedPage,
                        onPageChange = { newPage ->

                        }
                    )
                }
            }

            // Results list
            items(
                items = searchItems,
                key = { it.id } // if you have a stable id
            ) { item ->
                // Avoid huge horizontal padding—use something responsive
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    ItemCardView(item)
                }
            }

            item {
                key(pagesPair) {
                    Pager(
                        totalPages = pagesPair.second,
                        initialPage = selectedPage,
                        onPageChange = { newPage ->

                        }
                    )
                }
            }
        }
    }

}