package com.rex.diabite

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rex.diabite.network.RetrofitClient
import com.rex.diabite.ui.* // Keep existing wildcard for other UI components
import com.rex.diabite.ui.HomeScreen // Explicit import for HomeScreen
import com.rex.diabite.ui.theme.DiaBiteTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("BuildConfigTest", "FDC_API_KEY: ${com.rex.diabite.BuildConfig.FDC_API_KEY}")
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainApp() {
    val viewModel: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = MainViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
    )

    val items = listOf("Home", "Favorites", "History", "Settings")
    val pagerState = rememberPagerState { items.size }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Default.Home, contentDescription = item)
                                1 -> Icon(Icons.Default.Favorite, contentDescription = item)
                                2 -> Icon(Icons.Default.History, contentDescription = item)
                                3 -> Icon(Icons.Default.Settings, contentDescription = item)
                            }
                        },
                        label = { Text(item) },
                        selected = pagerState.currentPage == index,
                        onClick = {
                            if (pagerState.currentPage != index) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(innerPadding),
            // userScrollEnabled = false, // TODO: Consider if you want to disable direct swipe for now if other issues arise
        ) { page ->
            when (page) {
                0 -> HomeScreen(viewModel)
                1 -> FavoritesScreen(
                    viewModel = viewModel,
                    onReRunQuery = { historyItem ->
                        viewModel.reRunQuery(historyItem)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(0) // Navigate to Home (page 0)
                        }
                    }
                )
                2 -> HistoryScreen(
                    viewModel = viewModel,
                    onReRunQuery = { historyItem ->
                        viewModel.reRunQuery(historyItem)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(0) // Navigate to Home (page 0)
                        }
                    }
                )
                3 -> SettingsScreen(viewModel)
            }
        }
    }
}

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