package org.example.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import org.example.project.searchData.SearchCompleteItem
import org.example.project.searchData.SearchFilters
import org.example.project.searchData.SearchInOptions
import org.example.project.searchData.SearchItem
import org.example.project.searchData.SortOptions
import org.jetbrains.compose.resources.painterResource

@Composable
fun HomeScreen(username: String, onMenuSelected: (screen: Screen) -> Unit) {
    val client = Client
    val scope = rememberCoroutineScope()

    var searchInOptions by remember { mutableStateOf<MutableList<SearchInOptions>>(mutableListOf()) }
    var categoryOptions by remember { mutableStateOf<MutableList<CategoryOptions>>(mutableListOf()) }
    var sortOptions by remember { mutableStateOf<MutableList<SortOptions>>(mutableListOf()) }
    var searchItems by remember { mutableStateOf<List<SearchItem>>(mutableListOf()) }
    var selectedCategory by remember { mutableStateOf<CategoryOptions?>(null) }
    var selectedSearchIn by remember { mutableStateOf<SearchInOptions?>(null) }
    var selectedSort by remember { mutableStateOf<SortOptions?>(null) }
    var selectedPage by remember { mutableStateOf(1) }
    var pagesPair by remember { mutableStateOf(Pair(1, 1)) }
    var currentSearchItem by remember { mutableStateOf(CurrentSearchItem("", 0, 0, 0)) }
    var isInitialLoad by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf<Boolean>(true) }

    suspend fun loadInitialData() {
        try {
            error = null
            isLoading = true
            val firstSearch = client.firstSearch()
            if (firstSearch.searchItems.isEmpty()) {
                error = "Something went wrong, 0 items fetched..."
            }
            firstSearch.searchFiltersData.let {
                searchInOptions = it.searchInOptionsList.toMutableList()
                selectedSearchIn = it.selectedSearchIn ?: searchInOptions.firstOrNull()
                pagesPair = it.firstLastPage
            }
            firstSearch.searchFiltersData.let {
                categoryOptions = it.categoryOptionsList.toMutableList()
                selectedCategory = it.selectedCategory ?: categoryOptions.firstOrNull()
            }
            firstSearch.searchFiltersData.let {
                sortOptions = it.sortOptionsList.toMutableList()
                selectedSort = it.selectedSort ?: sortOptions.firstOrNull()
            }
            firstSearch.searchItems.let {
                searchItems = it.toMutableList()
            }
            currentSearchItem = CurrentSearchItem(
                "",
                selectedCategory?.value ?: 0,
                selectedSearchIn?.value ?: 0,
                selectedSort?.value ?: 0
            )
            isLoading = false
        } catch (e: Exception) {
            isLoading = false
            error = e.message
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
                TopBar(Screen.HOME, onMenuSelected)
                HorizontalDivider(
                    Modifier.padding(vertical = 0.dp),
                    DividerDefaults.Thickness,
                    DividerDefaults.color
                )
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
                            onFilterSelected = { newSelected ->
                                selectedCategory =
                                    categoryOptions.find { it.text == newSelected }
                            }
                        )
                    }
                    key(selectedSearchIn?.text) {
                        DropdownMenuView(
                            SearchFilters.SearchInOptions.name,
                            searchInOptions.map { it.text },
                            selectedSearchIn?.text,
                            onFilterSelected = { newSelected ->
                                selectedSearchIn =
                                    searchInOptions.find { it.text == newSelected }
                            }
                        )
                    }
                    key(selectedSort?.text) {
                        DropdownMenuView(
                            SearchFilters.SortOptions.name,
                            sortOptions.map { it.text },
                            selectedSort?.text,
                            onFilterSelected = { newSelected ->
                                selectedSort = sortOptions.find { it.text == newSelected }
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
                                client.search(
                                    SearchCompleteItem(
                                        pageNumber = null,
                                        selectedSort = selectedSort?.value,
                                        selectedSearchIn = selectedSearchIn?.value,
                                        selectedCategory = selectedCategory?.value,
                                        searchTerm = searchQuery
                                    )
                                ).also {
                                    searchItems = it.searchItems
                                    pagesPair = it.firstLastPage
                                    selectedPage = 1
                                }
                                isInitialLoad = false
                                currentSearchItem = CurrentSearchItem(
                                    searchQuery,
                                    selectedCategory?.value ?: 0,
                                    selectedSearchIn?.value ?: 0,
                                    selectedSort?.value ?: 0
                                )
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
                key(pagesPair, selectedPage) {
                    Pager(
                        totalPages = pagesPair.second,
                        initialPage = selectedPage,
                        onPageChange = { newPage ->
                            selectedPage = newPage
                            scope.launch {
                                if (isInitialLoad) {
                                    searchItems = client.search(
                                        SearchCompleteItem(
                                            pageNumber = newPage - 1,
                                            selectedSort = null,
                                            selectedSearchIn = null,
                                            selectedCategory = null,
                                            searchTerm = null
                                        )
                                    ).searchItems
                                } else {
                                    searchItems = client.search(
                                        SearchCompleteItem(
                                            pageNumber = newPage - 1,
                                            selectedSort = currentSearchItem.sort,
                                            selectedSearchIn = currentSearchItem.searchIn,
                                            selectedCategory = currentSearchItem.category,
                                            searchTerm = currentSearchItem.term
                                        )
                                    ).searchItems
                                }
                            }
                        }
                    )
                }
            }

            //loading + error
            item {
                key(error, isLoading, searchItems) {
                    if (error != null) {
                        ErrorBanner(message = error!!, onRetry = {
                            scope.launch {
                                loadInitialData()
                            }
                        })
                    } else if (searchItems.isEmpty() && isLoading) {
                        CircularProgressIndicator()
                    }
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
                key(pagesPair, selectedPage) {
                    Pager(
                        totalPages = pagesPair.second,
                        initialPage = selectedPage,
                        onPageChange = { newPage ->
                            selectedPage = newPage
                            scope.launch {
                                if (isInitialLoad) {
                                    searchItems = client.search(
                                        SearchCompleteItem(
                                            pageNumber = newPage - 1,
                                            selectedSort = null,
                                            selectedSearchIn = null,
                                            selectedCategory = null,
                                            searchTerm = null
                                        )
                                    ).searchItems
                                } else {
                                    searchItems = client.search(
                                        SearchCompleteItem(
                                            pageNumber = newPage - 1,
                                            selectedSort = currentSearchItem.sort,
                                            selectedSearchIn = currentSearchItem.searchIn,
                                            selectedCategory = currentSearchItem.category,
                                            searchTerm = currentSearchItem.term
                                        )
                                    ).searchItems
                                }
                            }
                        }
                    )
                }
            }
        }
    }

}