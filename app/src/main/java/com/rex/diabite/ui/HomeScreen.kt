package com.rex.diabite.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rex.diabite.ui.theme.DiaBiteTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "DiaBite — Sugar‑Smart Food Guide",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Diabetes Type Toggle
        Row(
            modifier = Modifier.padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FilterChip(
                selected = uiState.diabetesType == "TYPE_1",
                onClick = { viewModel.updateDiabetesType("TYPE_1") },
                label = { Text("Type 1") }
            )
            FilterChip(
                selected = uiState.diabetesType == "TYPE_2",
                onClick = { viewModel.updateDiabetesType("TYPE_2") },
                label = { Text("Type 2") }
            )
        }

        // Food Name Input
        OutlinedTextField(
            value = uiState.foodName,
            onValueChange = viewModel::updateFoodName,
            label = { Text("Food Name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        // Barcode Input
        OutlinedTextField(
            value = uiState.barcode,
            onValueChange = viewModel::updateBarcode,
            label = { Text("Barcode (Optional)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        // Analyze Button
        Button(
            onClick = { viewModel.analyzeFood() },
            enabled = !uiState.isLoading && (uiState.foodName.isNotBlank() || uiState.barcode.isNotBlank()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text("Analyze Food")
        }

        // Loading Indicator
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        // Error Message
        uiState.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Result
        uiState.foodItem?.let { foodItem ->
            uiState.decision?.let { decision ->
                ResultCard(
                    foodItem = foodItem,
                    decision = decision,
                    isFavorite = uiState.isCurrentItemFavorite,      // Pass isFavorite state
                    onToggleFavorite = { viewModel.toggleFavorite() }, // Pass toggle action
                    onClear = { viewModel.clearResult() }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    DiaBiteTheme {
        HomeScreen()
    }
}