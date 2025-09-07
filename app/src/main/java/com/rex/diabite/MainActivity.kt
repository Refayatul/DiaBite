package com.rex.diabite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rex.diabite.ui.HomeScreen
import com.rex.diabite.ui.HistoryScreen
import com.rex.diabite.ui.SettingsScreen
import com.rex.diabite.ui.MainViewModel
import com.rex.diabite.ui.theme.DiaBiteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    var selectedItem by remember { mutableStateOf(0) }
    val items = listOf("Home", "History", "Settings")

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Default.Home, contentDescription = null)
                                1 -> Icon(Icons.Default.History, contentDescription = null)
                                2 -> Icon(Icons.Default.Settings, contentDescription = null)
                            }
                        },
                        label = { Text(item) },
                        selected = selectedItem == index,
                        onClick = {
                            selectedItem = index
                            when (index) {
                                0 -> navController.navigate("home") { popUpTo("home") { inclusive = true } }
                                1 -> navController.navigate("history") { popUpTo("home") }
                                2 -> navController.navigate("settings") { popUpTo("home") }
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
            composable("history") {
                HistoryScreen(
                    viewModel = viewModel,
                    onReRunQuery = { historyItem ->
                        viewModel.reRunQuery(historyItem)
                        selectedItem = 0
                        navController.navigate("home") { popUpTo("home") { inclusive = true } }
                    }
                )
            }
            composable("settings") { SettingsScreen(viewModel) }
        }
    }
}