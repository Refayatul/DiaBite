package com.rex.diabite.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rex.diabite.data.HistoryEntity
import com.rex.diabite.di.ServiceLocator
import com.rex.diabite.domain.FoodDecisionLogic
import com.rex.diabite.repository.FoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServiceLocator.provideFoodRepository(application)
    private val TAG = "MainViewModel"

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntity>>(emptyList())
    val history: StateFlow<List<HistoryEntity>> = _history.asStateFlow()

    private val _favorites = MutableStateFlow<List<HistoryEntity>>(emptyList())
    val favorites: StateFlow<List<HistoryEntity>> = _favorites.asStateFlow()

    init {
        Log.d(TAG, "ViewModel initialized")
        loadHistory()
        loadFavorites()
    }

    fun updateFoodName(name: String) {
        Log.d(TAG, "Updating food name: $name")
        _uiState.value = _uiState.value.copy(foodName = name)
    }

    fun updateBarcode(barcode: String) {
        Log.d(TAG, "Updating barcode: $barcode")
        _uiState.value = _uiState.value.copy(barcode = barcode)
    }

    fun updateDiabetesType(type: String) {
        Log.d(TAG, "Updating diabetes type: $type")
        _uiState.value = _uiState.value.copy(diabetesType = type)
    }

    fun updateUseStaging(useStaging: Boolean) {
        Log.d(TAG, "Updating use staging: $useStaging")
        _uiState.value = _uiState.value.copy(useStaging = useStaging)
    }

    private fun updateUiWithHistoryItemDetails(queryKey: String, diabetesType: String) {
        viewModelScope.launch {
            val historyItem = repository.getHistoryItem(queryKey, diabetesType)
            if (historyItem != null) {
                _uiState.value = _uiState.value.copy(
                    currentHistoryItemId = historyItem.id,
                    isCurrentItemFavorite = historyItem.isFavorite
                )
            } else {
                Log.w(TAG, "Could not find history item for $queryKey and $diabetesType after search.")
                _uiState.value = _uiState.value.copy(
                    currentHistoryItemId = null,
                    isCurrentItemFavorite = false
                )
            }
        }
    }

    fun analyzeFood() {
        Log.d(TAG, "Starting food analysis...")
        val currentFoodName = _uiState.value.foodName
        val currentBarcode = _uiState.value.barcode
        val currentDiabetesType = _uiState.value.diabetesType

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, 
                error = null, 
                currentHistoryItemId = null, 
                isCurrentItemFavorite = false,
                isAnalysingWithAi = false // Reset AI analysis flag
            )
            Log.d(TAG, "UI State set to loading")

            try {
                val result: FoodRepository.FoodResult = if (currentBarcode.isNotBlank()) {
                    Log.d(TAG, "Analyzing by barcode: $currentBarcode")
                    repository.getFoodByBarcode(
                        barcode = currentBarcode,
                        diabetesType = currentDiabetesType,
                        useStaging = _uiState.value.useStaging,
                        onAiAnalysisStart = { 
                            _uiState.value = _uiState.value.copy(isAnalysingWithAi = true)
                            Log.d(TAG, "AI analysis started for barcode, updating UI state.")
                        }
                    )
                } else if (currentFoodName.isNotBlank()) {
                    Log.d(TAG, "Analyzing by name: $currentFoodName")
                    repository.searchFoodByName(
                        query = currentFoodName,
                        diabetesType = currentDiabetesType,
                        useStaging = _uiState.value.useStaging,
                        onAiAnalysisStart = { 
                            _uiState.value = _uiState.value.copy(isAnalysingWithAi = true)
                            Log.d(TAG, "AI analysis started for name, updating UI state.")
                        }
                    )
                } else {
                    Log.w(TAG, "No food name or barcode provided")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Please enter a food name or barcode",
                        currentHistoryItemId = null,
                        isCurrentItemFavorite = false,
                        isAnalysingWithAi = false
                    )
                    return@launch
                }

                when (result) {
                    is FoodRepository.FoodResult.Success -> {
                        Log.d(TAG, "Search successful: ${result.foodItem.name}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            foodItem = result.foodItem,
                            decision = result.decision,
                            error = null,
                            isAnalysingWithAi = false 
                        )
                        val queryKeyForHistory = if (currentBarcode.isNotBlank()) currentBarcode else currentFoodName
                        updateUiWithHistoryItemDetails(queryKeyForHistory.lowercase().trim(), result.decision.diabetesType)

                    }
                    is FoodRepository.FoodResult.Error -> {
                        Log.e(TAG, "Search error: ${result.message}")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.message,
                            foodItem = null,
                            decision = null,
                            currentHistoryItemId = null,
                            isCurrentItemFavorite = false,
                            isAnalysingWithAi = false 
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during food analysis", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message ?: "Unknown error"}",
                    foodItem = null,
                    decision = null,
                    currentHistoryItemId = null,
                    isCurrentItemFavorite = false,
                    isAnalysingWithAi = false 
                )
            }
        }
    }

    fun toggleFavorite() {
        val itemId = _uiState.value.currentHistoryItemId
        if (itemId != null) {
            val currentFavoriteStatus = _uiState.value.isCurrentItemFavorite
            val newFavoriteStatus = !currentFavoriteStatus
            viewModelScope.launch {
                try {
                    repository.setFavoriteStatus(itemId, newFavoriteStatus)
                    _uiState.value = _uiState.value.copy(isCurrentItemFavorite = newFavoriteStatus)
                    Log.d(TAG, "Toggled favorite for item $itemId (via HomeScreen) to $newFavoriteStatus")
                    // No need to explicitly reload history/favorites here, Flows should update them.
                } catch (e: Exception) {
                    Log.e(TAG, "Error toggling favorite for item $itemId (via HomeScreen)", e)
                }
            }
        } else {
            Log.w(TAG, "Attempted to toggle favorite (via HomeScreen), but no currentHistoryItemId is set.")
        }
    }

    // New function to toggle favorite from lists
    fun toggleFavoriteStatusForItem(itemId: Long, currentIsFavorite: Boolean) {
        val newFavoriteStatus = !currentIsFavorite
        Log.d(TAG, "Toggling favorite for item $itemId (from list) to $newFavoriteStatus")
        viewModelScope.launch {
            try {
                repository.setFavoriteStatus(itemId, newFavoriteStatus)
                // The history and favorites Flows will automatically update the lists.
                // If _uiState.currentHistoryItemId matches itemId, update its favorite status too.
                if (_uiState.value.currentHistoryItemId == itemId) {
                    _uiState.value = _uiState.value.copy(isCurrentItemFavorite = newFavoriteStatus)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling favorite for item $itemId (from list)", e)
                // Potentially show an error to the user via a different state if needed
            }
        }
    }

    fun clearResult() {
        Log.d(TAG, "Clearing result")
        _uiState.value = _uiState.value.copy(
            foodItem = null,
            decision = null,
            error = null,
            currentHistoryItemId = null,
            isCurrentItemFavorite = false,
            isAnalysingWithAi = false 
        )
    }

    fun loadHistory() {
        Log.d(TAG, "Loading history")
        viewModelScope.launch {
            repository.getHistory().collectLatest { historyList ->
                Log.d(TAG, "History loaded with ${historyList.size} items")
                _history.value = historyList
            }
        }
    }

    private fun loadFavorites() {
        Log.d(TAG, "Loading favorites")
        viewModelScope.launch {
            repository.getFavoriteHistoryItems().collectLatest { favoriteList ->
                Log.d(TAG, "Favorites loaded with ${favoriteList.size} items")
                _favorites.value = favoriteList
            }
        }
    }

    fun clearHistory() {
        Log.d(TAG, "Clearing history")
        viewModelScope.launch {
            repository.clearHistory()
            // The history and favorites flows will emit empty lists
        }
    }

    fun reRunQuery(historyItem: HistoryEntity) {
        Log.d(TAG, "Re-running query: ${historyItem.queryText}")
        _uiState.value = _uiState.value.copy(
            foodName = if (historyItem.matchedKey?.startsWith("barcode:") == true || historyItem.matchedKey?.startsWith("ai:${historyItem.queryText.lowercase().trim()}") == true && historyItem.queryText.all { Character.isDigit(it) } ) "" else historyItem.queryText,
            barcode = if (historyItem.matchedKey?.startsWith("barcode:") == true || historyItem.matchedKey?.startsWith("ai:${historyItem.queryText.lowercase().trim()}") == true && historyItem.queryText.all { Character.isDigit(it) } ) historyItem.queryText else "",
            diabetesType = historyItem.diabetesType,
            currentHistoryItemId = historyItem.id, // Keep track of the original item id
            isCurrentItemFavorite = historyItem.isFavorite, // Preserve favorite status from history
            isLoading = true, 
            error = null,
            isAnalysingWithAi = false 
        )
        analyzeFood()
    }

    data class UiState(
        val foodName: String = "",
        val barcode: String = "",
        val diabetesType: String = "TYPE_2",
        val useStaging: Boolean = false,
        val isLoading: Boolean = false,
        val foodItem: com.rex.diabite.model.FoodItem? = null,
        val decision: FoodDecisionLogic.FoodDecision? = null,
        val error: String? = null,
        val currentHistoryItemId: Long? = null,      
        val isCurrentItemFavorite: Boolean = false,
        val isAnalysingWithAi: Boolean = false 
    )

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}