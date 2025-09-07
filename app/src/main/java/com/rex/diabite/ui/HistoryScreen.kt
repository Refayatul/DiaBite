package com.rex.diabite.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rex.diabite.data.HistoryEntity
import com.rex.diabite.ui.theme.*
import com.rex.diabite.ui.theme.StarYellow // Added import for StarYellow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
    ),
    onReRunQuery: (HistoryEntity) -> Unit
) {
    val history by viewModel.history.collectAsState()
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Confirm Clear") },
            text = { Text("Are you sure you want to clear all history? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("History cleared")
                        }
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearHistoryDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply Scaffold padding
                .padding(16.dp) // Original padding
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.headlineSmall
                )

                if (history.isNotEmpty()) {
                    TextButton(onClick = { showClearHistoryDialog = true }) {
                        Text("Clear All")
                    }
                }
            }

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No history yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    items(history, key = { it.id }) { historyItem ->
                        HistoryItemCard(
                            historyItem = historyItem,
                            onClick = { onReRunQuery(historyItem) },
                            onToggleFavorite = { itemId, currentStatus ->
                                viewModel.toggleFavoriteStatusForItem(itemId, currentStatus)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (!currentStatus) "Added to favorites" else "Removed from favorites"
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    historyItem: HistoryEntity,
    onClick: () -> Unit,
    onToggleFavorite: (itemId: Long, currentIsFavorite: Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp) 
            ) {
                Text(
                    text = historyItem.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2 
                )

                Text(
                    text = "${historyItem.diabetesType.replace("_", " ")} • ${historyItem.suitability.replace("_", " ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { onToggleFavorite(historyItem.id, historyItem.isFavorite) }) {
                Crossfade(targetState = historyItem.isFavorite, animationSpec = tween(300), label = "FavoriteStarCrossfade") {
                    isFavorite ->
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Remove from Favorites",
                            tint = StarYellow 
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.StarBorder, 
                            contentDescription = "Add to Favorites",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            val badgeColor = when (historyItem.suitability) {
                "SAFE" -> SafeGreen
                "SMALL_PORTION" -> SmallPortionYellow
                "LIMIT" -> LimitOrange
                "AVOID" -> AvoidRed
                else -> UnknownGray
            }

            val textColor = when (historyItem.suitability) {
                "SMALL_PORTION" -> Color.Black 
                else -> Color.White
            }

            Surface(
                color = badgeColor,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(start = 8.dp) 
            ) {
                Text(
                    text = historyItem.suitability.replace("_", " ").take(3).uppercase(), 
                    color = textColor,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    DiaBiteTheme {
        HistoryScreen(
            onReRunQuery = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryItemCardPreview() {
    val sampleItem = HistoryEntity(
        id = 1,
        queryText = "White Rice",
        displayName = "White Rice (Processed)",
        diabetesType = "TYPE_2",
        suitability = "SMALL_PORTION",
        reason = "High net carbs",
        portionText = "45g portion",
        alternativesJson = "brown rice,cauliflower rice,quinoa",
        sourcesUsed = "OFF",
        createdAt = System.currentTimeMillis() - 3600000,
        isFavorite = true
    )
    DiaBiteTheme {
        HistoryItemCard(
            historyItem = sampleItem,
            onClick = {},
            onToggleFavorite = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryItemCardNotFavoritePreview() {
    val sampleItem = HistoryEntity(
        id = 2,
        queryText = "Apple Pie",
        displayName = "Homemade Apple Pie",
        diabetesType = "TYPE_1",
        suitability = "AVOID",
        reason = "Very high sugar",
        portionText = "Small sliver",
        alternativesJson = "baked apple,berries",
        sourcesUsed = "AI",
        createdAt = System.currentTimeMillis() - 86400000, 
        isFavorite = false
    )
    DiaBiteTheme {
        HistoryItemCard(
            historyItem = sampleItem,
            onClick = {},
            onToggleFavorite = { _, _ -> }
        )
    }
}