package com.rex.diabite.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rex.diabite.ui.theme.DiaBiteTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(
        factory = MainViewModel.Factory(LocalContext.current.applicationContext as Application)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val TAG = "HomeScreen"

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            val image = InputImage.fromBitmap(bitmap, 0)
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val scanner = BarcodeScanning.getClient(options)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val barcodeValue = barcodes[0].rawValue
                        if (barcodeValue != null) {
                            viewModel.updateBarcode(barcodeValue)
                            Log.d(TAG, "Barcode found: $barcodeValue")
                            viewModel.analyzeFood() // Automatically analyze after scan
                        } else {
                            Log.d(TAG, "Barcode value is null")
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Could not read barcode data.")
                            }
                        }
                    } else {
                        Log.d(TAG, "No barcode found in image")
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("No barcode found. Please try again.")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Barcode scanning failed", e)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Barcode scanning failed. Error: ${e.message}")
                    }
                }
        } else {
            Log.d(TAG, "Bitmap from camera is null")
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Could not capture image. Please try again.")
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                takePictureLauncher.launch(null)
            } else {
                Log.d(TAG, "Camera permission denied")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Camera permission is needed to scan barcodes.")
                }
            }
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background // Use theme background color
    ) { paddingValues -> 
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) 
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp), 
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "DiaBite",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                color = MaterialTheme.colorScheme.primary // Title with primary color
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface) // Ensure card uses surface color
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Select Your Profile",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp).align(Alignment.Start),
                        color = MaterialTheme.colorScheme.primary // Section title color
                    )
                    Row(
                        modifier = Modifier.padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.diabetesType == "TYPE_1",
                            onClick = { viewModel.updateDiabetesType("TYPE_1") },
                            label = { Text("Type 1 Diabetes") },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                        FilterChip(
                            selected = uiState.diabetesType == "TYPE_2",
                            onClick = { viewModel.updateDiabetesType("TYPE_2") },
                            label = { Text("Type 2 Diabetes") },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }

                    Text(
                        text = "Enter Food Details",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp).align(Alignment.Start),
                        color = MaterialTheme.colorScheme.primary // Section title color
                    )
                    OutlinedTextField(
                        value = uiState.foodName,
                        onValueChange = viewModel::updateFoodName,
                        label = { Text("Food Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.barcode,
                            onValueChange = viewModel::updateBarcode,
                            label = { Text("Barcode (Optional)") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
                                PackageManager.PERMISSION_GRANTED -> {
                                    takePictureLauncher.launch(null)
                                }
                                else -> {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Scan Barcode",
                                tint = MaterialTheme.colorScheme.secondary // Icon tint
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.analyzeFood() },
                enabled = !uiState.isLoading && (uiState.foodName.isNotBlank() || uiState.barcode.isNotBlank()),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Analyze Food", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.primary)
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
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).align(Alignment.Start),
                        color = MaterialTheme.colorScheme.primary // Section title color
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
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    DiaBiteTheme {
        HomeScreen()
    }
}