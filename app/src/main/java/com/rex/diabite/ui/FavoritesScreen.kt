package com.rex.diabite.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rex.diabite.data.HistoryEntity
import com.rex.diabite.ui.theme.DiaBiteTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class) // Added for Scaffold
@Composable
fun FavoritesScreen(
    viewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(LocalContext.current.applicationContext as Application)
    ),
    onReRunQuery: (HistoryEntity) -> Unit
) {
    val favorites by viewModel.favorites.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background // Ensure background consistency
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply Scaffold padding
                .padding(16.dp) // Original padding
        ) {
            Text(
                text = "Favorites",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary, // Themed title
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (favorites.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No favorites yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(favorites, key = { it.id }) { historyItem ->
                        HistoryItemCard(
                            historyItem = historyItem,
                            onClick = { onReRunQuery(historyItem) },
                            onToggleFavorite = { itemId, currentIsFavorite ->
                                viewModel.toggleFavoriteStatusForItem(itemId, currentIsFavorite)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Removed from favorites")
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

@Preview(showBackground = true)
@Composable
fun FavoritesScreenPreview() {
    DiaBiteTheme {
        val mockApplication = LocalContext.current.applicationContext as Application
        val factory = MainViewModel.Factory(mockApplication)
        val mockViewModel = viewModel<MainViewModel>(factory = factory)
        FavoritesScreen(viewModel = mockViewModel, onReRunQuery = {})
    }
}