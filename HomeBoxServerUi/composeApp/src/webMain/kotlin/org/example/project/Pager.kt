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
fun Pager(
    totalPages: Int,
    initialPage: Int = 1,
    windowSize: Int = 5,
    onPageChange: (Int) -> Unit
) {
    var currentPage by remember { mutableStateOf(initialPage) }
    var startPage by remember { mutableStateOf(1) }
    var endPage by remember { mutableStateOf(min(windowSize, totalPages)) }

    // Adjust the start and end pages when the current page changes
    fun updatePageRange() {
        startPage = max(1, currentPage - windowSize / 2)
        endPage = min(totalPages, startPage + windowSize - 1)

        if (endPage < currentPage) {
            startPage = max(1, endPage - windowSize + 1)
        }
    }

    // Handle page change
    fun onPageSelected(page: Int) {
        if (page in startPage..endPage) {
            currentPage = page
            onPageChange(page)
            updatePageRange() // Update the visible page range
        }
    }

    // Arrows
    val canGoBack = currentPage > 1
    val canGoForward = currentPage < totalPages

    // Render the pager
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Previous arrow
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
            IconButton(
                onClick = {
                    if (canGoBack) {
                        currentPage -= 1
                        onPageChange(currentPage)
                        updatePageRange()
                    }
                }
            ) {
                Text("Previous Page")
                //Icon(Icons.Filled.ArrowBack, contentDescription = "Previous Page")
            }

            // Page numbers
            for (page in startPage..endPage) {
                Button(
                    onClick = { onPageSelected(page) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (page == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(page.toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Next arrow
            IconButton(
                onClick = {
                    if (canGoForward) {
                        currentPage += 1
                        onPageChange(currentPage)
                        updatePageRange()
                    }
                }
            ) {
                //Icon(Icons.Filled.ArrowForward, contentDescription = "Next Page")
                Text("Next Page")
            }
        }
    }
}

