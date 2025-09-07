package com.rex.diabite.ui

import android.app.Application
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

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntity>>(emptyList())
    val history: StateFlow<List<HistoryEntity>> = _history.asStateFlow()

    init {
        loadHistory()
    }

    fun updateFoodName(name: String) {
        _uiState.value = _uiState.value.copy(foodName = name)
    }

    fun updateBarcode(barcode: String) {
        _uiState.value = _uiState.value.copy(barcode = barcode)
    }

    fun updateDiabetesType(type: String) {
        _uiState.value = _uiState.value.copy(diabetesType = type)
    }

    fun updateUseStaging(useStaging: Boolean) {
        _uiState.value = _uiState.value.copy(useStaging = useStaging)
    }

    fun analyzeFood() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val result = if (_uiState.value.barcode.isNotBlank()) {
                    repository.getFoodByBarcode(
                        barcode = _uiState.value.barcode,
                        diabetesType = _uiState.value.diabetesType,
                        useStaging = _uiState.value.useStaging
                    )
                } else {
                    repository.searchFoodByName(
                        query = _uiState.value.foodName,
                        diabetesType = _uiState.value.diabetesType,
                        useStaging = _uiState.value.useStaging
                    )
                }

                when (result) {
                    is FoodRepository.FoodResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            foodItem = result.foodItem,
                            decision = result.decision
                        )
                    }
                    is FoodRepository.FoodResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun clearResult() {
        _uiState.value = _uiState.value.copy(
            foodItem = null,
            decision = null,
            error = null
        )
    }

    fun loadHistory() {
        viewModelScope.launch {
            repository.getHistory().collectLatest { historyList ->
                _history.value = historyList
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun reRunQuery(historyItem: HistoryEntity) {
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