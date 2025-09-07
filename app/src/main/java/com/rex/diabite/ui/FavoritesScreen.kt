package com.rex.diabite.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rex.diabite.data.HistoryEntity
import com.rex.diabite.ui.theme.DiaBiteTheme

@Composable
fun FavoritesScreen(
    viewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(LocalContext.current.applicationContext as Application)
    ),
    onReRunQuery: (HistoryEntity) -> Unit
) {
    val favorites by viewModel.favorites.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Favorites",
            style = MaterialTheme.typography.headlineSmall,
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
            LazyColumn {
                items(favorites) { historyItem ->
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

@Preview(showBackground = true)
@Composable
fun FavoritesScreenPreview() {
    DiaBiteTheme {
        // You might want to create a mock ViewModel or pass mock data for a better preview
        val mockApplication = LocalContext.current.applicationContext as Application
        val factory = MainViewModel.Factory(mockApplication)
        val mockViewModel = viewModel<MainViewModel>(factory = factory)
        // For preview purposes, you could try to manually add some items to the favorites flow
        // in a debug version of the ViewModel or use a more complex Preview setup.
        FavoritesScreen(viewModel = mockViewModel, onReRunQuery = {})
    }
}