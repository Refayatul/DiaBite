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

    private val _favorites = MutableStateFlow<List<HistoryEntity>>(emptyList()) // New StateFlow for favorites
    val favorites: StateFlow<List<HistoryEntity>> = _favorites.asStateFlow()    // New StateFlow for favorites

    init {
        Log.d(TAG, "ViewModel initialized")
        loadHistory()
        loadFavorites() // Load favorites on init
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
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, currentHistoryItemId = null, isCurrentItemFavorite = false)
            Log.d(TAG, "UI State set to loading")

            try {
                val result: FoodRepository.FoodResult = if (currentBarcode.isNotBlank()) {
                    Log.d(TAG, "Analyzing by barcode: $currentBarcode")
                    repository.getFoodByBarcode(
                        barcode = currentBarcode,
                        diabetesType = currentDiabetesType,
                        useStaging = _uiState.value.useStaging
                    )
                } else if (currentFoodName.isNotBlank()) {
                    Log.d(TAG, "Analyzing by name: $currentFoodName")
                    repository.searchFoodByName(
                        query = currentFoodName,
                        diabetesType = currentDiabetesType,
                        useStaging = _uiState.value.useStaging
                    )
                } else {
                    Log.w(TAG, "No food name or barcode provided")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Please enter a food name or barcode",
                        currentHistoryItemId = null,
                        isCurrentItemFavorite = false
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
                            error = null
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
                            isCurrentItemFavorite = false
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
                    isCurrentItemFavorite = false
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
                    Log.d(TAG, "Toggled favorite for item $itemId to $newFavoriteStatus")
                    // No need to explicitly reload all favorites here, as the Flow should update if items change.
                    // However, if getFavoriteHistoryItems() is not emitting on single item change, explicit reload might be needed.
                } catch (e: Exception) {
                    Log.e(TAG, "Error toggling favorite for item $itemId", e)
                }
            }
        } else {
            Log.w(TAG, "Attempted to toggle favorite, but no currentHistoryItemId is set.")
        }
    }

    fun clearResult() {
        Log.d(TAG, "Clearing result")
        _uiState.value = _uiState.value.copy(
            foodItem = null,
            decision = null,
            error = null,
            currentHistoryItemId = null,
            isCurrentItemFavorite = false
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

    // New function to load favorite items
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
            // Clearing all history also means clearing favorites from the UI perspective
            _favorites.value = emptyList() 
        }
    }

    fun reRunQuery(historyItem: HistoryEntity) {
        Log.d(TAG, "Re-running query: ${historyItem.queryText}")
        _uiState.value = _uiState.value.copy(
            foodName = historyItem.queryText,
            barcode = "", 
            diabetesType = historyItem.diabetesType,
            currentHistoryItemId = historyItem.id,
            isCurrentItemFavorite = historyItem.isFavorite,
            isLoading = true, 
            error = null
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
        val isCurrentItemFavorite: Boolean = false   
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