package com.rex.diabite

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite // Added Favorite icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rex.diabite.network.RetrofitClient
import com.rex.diabite.ui.*
import com.rex.diabite.ui.theme.DiaBiteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Debug: Check if BuildConfig is working
        Log.d("BuildConfigTest", "FDC_API_KEY: ${com.rex.diabite.BuildConfig.FDC_API_KEY}")

        // Test API connectivity
        testApiConnectivity()

        setContent {
            DiaBiteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = MainViewModel.Factory(androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application)
    )

    var selectedItem by remember { mutableStateOf(0) } // Home is 0, Favorites 1, History 2, Settings 3
    val items = listOf("Home", "Favorites", "History", "Settings") // Added "Favorites"

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Default.Home, contentDescription = null)
                                1 -> Icon(Icons.Default.Favorite, contentDescription = null) // Favorites Icon
                                2 -> Icon(Icons.Default.History, contentDescription = null)
                                3 -> Icon(Icons.Default.Settings, contentDescription = null)
                            }
                        },
                        label = { Text(item) },
                        selected = selectedItem == index,
                        onClick = {
                            selectedItem = index
                            when (index) {
                                0 -> navController.navigate("home") { popUpTo("home") { inclusive = true } }
                                1 -> navController.navigate("favorites") { popUpTo("home") } // Navigate to Favorites
                                2 -> navController.navigate("history") { popUpTo("home") }
                                3 -> navController.navigate("settings") { popUpTo("home") }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") { HomeScreen(viewModel) }
            composable("favorites") { // Added FavoritesScreen route
                FavoritesScreen(
                    viewModel = viewModel,
                    onReRunQuery = { historyItem ->
                        viewModel.reRunQuery(historyItem)
                        selectedItem = 0 // Select Home tab
                        navController.navigate("home") { popUpTo("home") { inclusive = true } }
                    }
                )
            }
            composable("history") {
                HistoryScreen(
                    viewModel = viewModel,
                    onReRunQuery = { historyItem ->
                        viewModel.reRunQuery(historyItem)
                        selectedItem = 0 // Select Home tab
                        navController.navigate("home") { popUpTo("home") { inclusive = true } }
                    }
                )
            }
            composable("settings") { SettingsScreen(viewModel) }
        }
    }
}

// Test function to verify API connectivity
fun testApiConnectivity() {
    try {
        val offClient = RetrofitClient.createOffClient()
        Log.d("APITest", "OFF Client created successfully")

        val usdaClient = RetrofitClient.createUsdaClient()
        Log.d("APITest", "USDA Client created successfully")

        Log.d("APITest", "API clients initialized successfully")
    } catch (e: Exception) {
        Log.e("APITest", "Error initializing API clients", e)
    }
}