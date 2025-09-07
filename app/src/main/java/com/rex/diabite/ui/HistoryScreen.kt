package com.rex.diabite.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.rex.diabite.util.toDaysAgo

@Composable
fun HistoryScreen(
    viewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
    ),
    onReRunQuery: (HistoryEntity) -> Unit
) {
    val history by viewModel.history.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
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
                TextButton(onClick = { viewModel.clearHistory() }) {
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
                items(history) { historyItem ->
                    HistoryItemCard(
                        historyItem = historyItem,
                        onClick = { onReRunQuery(historyItem) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(
    historyItem: HistoryEntity,
    onClick: () -> Unit
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
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = historyItem.displayName, // Changed from queryText to displayName
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${historyItem.diabetesType.replace("_", " ")} • ${historyItem.suitability.replace("_", " ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Suitability Badge
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
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = historyItem.suitability.replace("_", " ").take(3),
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
    DiaBiteTheme {
        HistoryItemCard(
            historyItem = HistoryEntity(
                id = 1,
                queryText = "White Rice",
                displayName = "White Rice (Processed)", // Added displayName
                diabetesType = "TYPE_2",
                suitability = "SMALL_PORTION",
                reason = "High net carbs",
                portionText = "45g portion",
                alternativesJson = "brown rice,cauliflower rice,quinoa",
                sourcesUsed = "OFF",
                createdAt = System.currentTimeMillis() - 3600000 // 1 hour ago
            ),
            onClick = {}
        )
    }
}