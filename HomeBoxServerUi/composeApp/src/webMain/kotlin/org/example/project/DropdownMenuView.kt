package org.example.project

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

const val NON_SELECTED = "None Selected"

@Composable
fun DropdownMenuView(
    filterName: String,
    options: List<String>,
    onFilterSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf(NON_SELECTED) }

    Row(
        modifier = Modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$filterName:")
        Column(modifier = Modifier.padding(16.dp)) {
            Button(onClick = { expanded = !expanded }) {
                Text(text = selectedOption)
            }
            if (expanded) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach {
                        DropdownMenuItem(
                            onClick = {
                                selectedOption = it
                                expanded = false
                                onFilterSelected(it)
                            },
                            text = { Text(it) }
                        )
                    }
                }
            }
        }
    }
}
