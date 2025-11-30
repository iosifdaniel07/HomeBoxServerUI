package org.example.project

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

@Composable
fun Pager(
    totalPages: Int,
    initialPage: Int = 1,
    windowSize: Int = 3,
    onPageChange: (Int) -> Unit
) {
    var currentPage by remember { mutableStateOf(initialPage) }

    // Calculate the range of visible pages
    val startPage = max(1, currentPage - windowSize / 2)
    val endPage = min(totalPages, startPage + windowSize - 1)

    // Handle page change
    fun onPageSelected(page: Int) {
        currentPage = page
        onPageChange(page)
    }

    // Render the pager
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(2.dp)
        ) {
            // Show "1..." if the first visible page is greater than 1 and make "1" clickable
            if (startPage > 1) {
                TextButton(
                    onClick = { onPageSelected(1) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (1 == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.padding(3.dp)//.shadow(elevation = 2.dp)
                ) {
                    Text("1", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                }
                Text("...", style = MaterialTheme.typography.bodyMedium, modifier = Modifier
                    .align(Alignment.Bottom)
                    .padding(bottom = 16.dp))
            }

            // Page numbers in the visible range
            for (page in startPage..endPage) {
                TextButton(
                    onClick = { onPageSelected(page) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent // No background for all buttons
                    ),
                    modifier = Modifier
                        .padding(4.dp)
                        .then(
                            if (page == currentPage) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(50)
                                )
                            } else Modifier
                        ) // Apply border only for the selected page
                ) {
                    Text(page.toString(), style = MaterialTheme.typography.bodyMedium, color = if (page == currentPage) Color.White else Color.Gray)
                }
            }

            // Show "... [last pages]" if there are more pages after the last visible page and make the last page clickable
            if (endPage < totalPages) {
                Text("...", style = MaterialTheme.typography.bodyMedium, modifier = Modifier
                    .align(Alignment.Bottom)
                    .padding(bottom = 16.dp))
                TextButton(
                    onClick = { onPageSelected(totalPages) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent // No background
                    ),
                    modifier = Modifier
                        .padding(3.dp)
                        .then(
                            if (totalPages == currentPage) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = Color.White,
                                    shape = RoundedCornerShape(50)
                                )
                            } else Modifier
                        )
                ) {
                    Text(totalPages.toString(), style = MaterialTheme.typography.bodyMedium, color = if (totalPages == currentPage) Color.White else Color.Gray)
                }
            }
        }
    }
}

