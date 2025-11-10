package org.example.project

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import homeboxserverui.composeapp.generated.resources.Res
import homeboxserverui.composeapp.generated.resources.menus
import org.jetbrains.compose.resources.painterResource

@Composable
fun TopBar(currentScreen: Screen, onMenuSelected: (screen: Screen) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    val allActions = remember {
        listOf(
            ActionItem("diskDetails", Screen.DISK_SPACE.text, screen = Screen.DISK_SPACE) {
                onMenuSelected(Screen.DISK_SPACE)
            },
            ActionItem("searchPage", Screen.HOME.text, screen = Screen.HOME) {
                onMenuSelected(Screen.HOME)
            },
            ActionItem("logout", "Logout", screen = Screen.LOGIN) {
                onMenuSelected(Screen.LOGIN)
            }
        )
    }

    val visibleActions = remember(currentScreen) {
        allActions.filter { it.screen != currentScreen }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = currentScreen.text,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 8.dp)
        )
        Button(
            onClick = { showSheet = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = LocalContentColor.current,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = LocalContentColor.current.copy(alpha = 0.38f)
            ),
        ) {
            Image(
                painter = painterResource(Res.drawable.menus),
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                colorFilter = ColorFilter.tint(
                    color = MaterialTheme.colorScheme.primary,   // pick your color
                    blendMode = BlendMode.SrcIn                  // standard for icon tinting
                )
            )
        }
        key(showSheet) {
            ActionSheet(
                visible = showSheet,
                title = "Actions",
                items = visibleActions,
                onDismiss = { showSheet = false },
                onClick = { item ->
                    item.onClick.invoke()
                }
            )
        }
    }
}