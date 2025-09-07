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

    init {
        Log.d(TAG, "ViewModel initialized")
        loadHistory()
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

    fun analyzeFood() {
        Log.d(TAG, "Starting food analysis...")
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            Log.d(TAG, "UI State set to loading")

            try {
                if (_uiState.value.barcode.isNotBlank()) {
                    Log.d(TAG, "Analyzing by barcode: ${_uiState.value.barcode}")
                    val result = repository.getFoodByBarcode(
                        barcode = _uiState.value.barcode,
                        diabetesType = _uiState.value.diabetesType,
                        useStaging = _uiState.value.useStaging
                    )

                    when (result) {
                        is FoodRepository.FoodResult.Success -> {
                            Log.d(TAG, "Barcode search successful: ${result.foodItem.name}")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                foodItem = result.foodItem,
                                decision = result.decision
                            )
                        }
                        is FoodRepository.FoodResult.Error -> {
                            Log.e(TAG, "Barcode search error: ${result.message}")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = result.message
                            )
                        }
                    }
                } else if (_uiState.value.foodName.isNotBlank()) {
                    Log.d(TAG, "Analyzing by name: ${_uiState.value.foodName}")
                    val result = repository.searchFoodByName(
                        query = _uiState.value.foodName,
                        diabetesType = _uiState.value.diabetesType,
                        useStaging = _uiState.value.useStaging
                    )

                    when (result) {
                        is FoodRepository.FoodResult.Success -> {
                            Log.d(TAG, "Name search successful: ${result.foodItem.name}")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                foodItem = result.foodItem,
                                decision = result.decision
                            )
                        }
                        is FoodRepository.FoodResult.Error -> {
                            Log.e(TAG, "Name search error: ${result.message}")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = result.message
                            )
                        }
                    }
                } else {
                    Log.w(TAG, "No food name or barcode provided")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Please enter a food name or barcode"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during food analysis", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun clearResult() {
        Log.d(TAG, "Clearing result")
        _uiState.value = _uiState.value.copy(
            foodItem = null,
            decision = null,
            error = null
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

    fun clearHistory() {
        Log.d(TAG, "Clearing history")
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun reRunQuery(historyItem: HistoryEntity) {
        Log.d(TAG, "Re-running query: ${historyItem.queryText}")
        _uiState.value = _uiState.value.copy(
            foodName = historyItem.queryText,
            barcode = "",
            diabetesType = historyItem.diabetesType
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
        val error: String? = null
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