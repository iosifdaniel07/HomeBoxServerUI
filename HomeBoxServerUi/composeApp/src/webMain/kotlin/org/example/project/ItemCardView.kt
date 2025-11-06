package org.example.project

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.example.project.searchData.SearchItem

@Composable
fun ItemCardView(item: SearchItem) {
    Card(
        modifier = Modifier
            .wrapContentWidth()
            .wrapContentHeight()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .width(400.dp)
                    .wrapContentHeight()
                    .padding(12.dp)
                    .height(IntrinsicSize.Min), // so the divider spans the row
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT: image + title
                Column(
                    modifier = Modifier
                        .wrapContentWidth()
                        .wrapContentHeight()
                        .weight(1f)
                        .padding(end = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (item.imageUrl.isBlank()) {
                        PosterPlaceholder(text = item.imageCategory ?: NO_IMAGE)
                    } else {
                        AsyncImage(
                            modifier = Modifier
                                .aspectRatio(185f / 278f),
                            model = item.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop

                        )
                    }
                }

                // Divider between columns
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // RIGHT: extra data (example fields)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight()
                        .width(200.dp)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.categories.isNotBlank()) {
                        Text(
                            text = "Categories",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = item.categories, // e.g. "[Action, Adventure, Indie, RPG]"
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    HorizontalDivider(
                        Modifier.padding(vertical = 2.dp),
                        DividerDefaults.Thickness,
                        DividerDefaults.color
                    )

                    Text(
                        text = "Uploaded date",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${item.uploadedDate}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    HorizontalDivider(
                        Modifier.padding(vertical = 2.dp),
                        DividerDefaults.Thickness,
                        DividerDefaults.color
                    )

                    Text(
                        text = "Size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Size: ${item.size}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    HorizontalDivider(
                        Modifier.padding(vertical = 2.dp),
                        DividerDefaults.Thickness,
                        DividerDefaults.color
                    )
                }
            }

            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(400.dp)
                    .wrapContentHeight(),
                textAlign = TextAlign.Center
            )

            HorizontalDivider(
                Modifier.padding(vertical = 2.dp).width(400.dp),
                DividerDefaults.Thickness,
                DividerDefaults.color
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                /* TextButton(onClick = { /* open details */ }) {
                     Text("Details")
                 }*/
                TextButton(onClick = { /* start download */ }) {
                    Text("Download")
                }
            }
        }

    }
}

@Composable
fun PosterPlaceholder(modifier: Modifier = Modifier, text: String) {
    Box(
        modifier = modifier
            .aspectRatio(185f / 278f) // same shape as 185x278
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

val NO_IMAGE = "NO IMAGE"