package org.example.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

data class ActionItem(
    val id: String,
    val label: String,
    val screen: Screen,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    visible: Boolean,
    title: String? = null,
    items: List<ActionItem>,
    onDismiss: () -> Unit,
    onClick: (ActionItem) -> Unit
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (!title.isNullOrBlank()) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(8.dp)
                )
                Spacer(Modifier.height(4.dp))
            }

            items.forEach { item ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = item.label,
                            color = when {
                                !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                item.destructive -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    leadingContent = {
                       // item.icon?.let { Icon(it, null) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .let { m ->
                            if (item.enabled) m.clickable {
                                onClick(item)
                                onDismiss()
                            } else m
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            // separăm un buton "Cancel"
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp)
            ) {
                Text("Cancel")
            }
        }
    }
}
