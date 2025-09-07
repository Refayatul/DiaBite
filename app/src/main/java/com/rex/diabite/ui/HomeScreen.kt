package com.rex.diabite.ui

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
// import androidx.compose.ui.text.style.TextAlign // No longer needed for slogan
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
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title - Made smaller and removed slogan
        Text(
            text = "DiaBite",
            style = MaterialTheme.typography.headlineLarge, // Changed from displaySmall
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp) // Adjusted bottom padding
        )
        // Removed Slogan Text Composable

        // Input Section Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp) // Material 3 recommends elevation on Card itself
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Select Your Profile",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp).align(Alignment.Start)
                )
                // Diabetes Type Toggle
                Row(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.diabetesType == "TYPE_1",
                        onClick = { viewModel.updateDiabetesType("TYPE_1") },
                        label = { Text("Type 1 Diabetes") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = uiState.diabetesType == "TYPE_2",
                        onClick = { viewModel.updateDiabetesType("TYPE_2") },
                        label = { Text("Type 2 Diabetes") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "Enter Food Details",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp, top = 8.dp).align(Alignment.Start)
                )
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Analyze Button
        Button(
            onClick = { viewModel.analyzeFood() },
            enabled = !uiState.isLoading && (uiState.foodName.isNotBlank() || uiState.barcode.isNotBlank()),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("Analyze Food", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(24.dp)) 

        // Output Section
        if (uiState.isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { 
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    text = if (uiState.isAnalysingWithAi) "Using advanced searching..." else "Searching...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        uiState.error?.let {
            error ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        uiState.foodItem?.let { foodItem ->
            uiState.decision?.let { decision ->
                Text(
                    text = "Analysis Result",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).align(Alignment.Start)
                )
                ResultCard(
                    foodItem = foodItem,
                    decision = decision,
                    isFavorite = uiState.isCurrentItemFavorite,
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    onClear = { viewModel.clearResult() },
                    onAlternativeClick = { alternativeName ->
                        viewModel.updateFoodName(alternativeName)
                        viewModel.updateBarcode("")
                        viewModel.analyzeFood()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp)) 
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    DiaBiteTheme {
        HomeScreen()
    }
}