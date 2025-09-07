package com.rex.diabite.repository

import com.rex.diabite.BuildConfig
import com.rex.diabite.data.FoodCacheEntity
import com.rex.diabite.data.HistoryEntity
import com.rex.diabite.db.AppDatabase
import com.rex.diabite.domain.FoodDecisionLogic
import com.rex.diabite.model.FoodItem
import com.rex.diabite.model.FoodSearchResult
import com.rex.diabite.network.RetrofitClient
import com.rex.diabite.util.Constants
import kotlinx.coroutines.flow.Flow

class FoodRepository private constructor(
    private val database: AppDatabase
) {
    private val offApi = RetrofitClient.createOffClient()
    private val usdaApi = RetrofitClient.createUsdaClient()
    private val decisionLogic = FoodDecisionLogic()

    suspend fun searchFoodByName(
        query: String,
        diabetesType: String,
        useStaging: Boolean = false
    ): FoodRepository.FoodResult {
        // Check cache first
        val cacheKey = "name:$query"
        val cached = getCachedFood(cacheKey)
        if (cached != null && isCacheValid(cached)) {
            val decision = decisionLogic.decideSuitability(cached, diabetesType)
            saveToHistory(query, diabetesType, cacheKey, decision)
            return FoodResult.Success(cached, decision)
        }

        // Try OFF search
        try {
            val response = offApi.searchProducts(query)
            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                if (!result.product_name.isNullOrBlank()) {
                    val foodItem = FoodItem(
                        name = result.product_name ?: query,
                        brand = result.brands,
                        carbs100g = result.carbohydrates_100g?.toFloat(),
                        sugars100g = result.sugars_100g?.toFloat(),
                        fiber100g = result.fiber_100g?.toFloat(),
                        energyKcal100g = result.`energy-kcal_100g`?.toFloat(),
                        countryTags = result.countries_tags_en?.joinToString(","),
                        source = "OFF"
                    )

                    cacheFood(cacheKey, foodItem)
                    val decision = decisionLogic.decideSuitability(foodItem, diabetesType)
                    saveToHistory(query, diabetesType, cacheKey, decision)
                    return FoodResult.Success(foodItem, decision)
                }
            }
        } catch (e: Exception) {
            // Continue to USDA if OFF fails
        }

        // Try USDA search
        try {
            val response = usdaApi.searchFoods(
                apiKey = BuildConfig.FDC_API_KEY,
                query = query
            )
            if (response.isSuccessful && response.body()?.foods?.isNotEmpty() == true) {
                val firstFood = response.body()!!.foods!![0]
                val detailsResponse = usdaApi.getFoodDetails(
                    apiKey = BuildConfig.FDC_API_KEY,
                    fdcId = firstFood.fdcId.toString()
                )

                if (detailsResponse.isSuccessful && detailsResponse.body() != null) {
                    val details = detailsResponse.body()!!
                    val nutrients = details.foodNutrients?.associate {
                        it.nutrientId.toString() to it.value?.toFloat()
                    } ?: emptyMap()

                    val foodItem = FoodItem(
                        name = details.description ?: query,
                        carbs100g = nutrients["1005"], // Carbohydrate
                        sugars100g = nutrients["2000"], // Sugars, total
                        fiber100g = nutrients["1079"], // Fiber, total dietary
                        source = "USDA"
                    )

                    cacheFood(cacheKey, foodItem)
                    val decision = decisionLogic.decideSuitability(foodItem, diabetesType)
                    saveToHistory(query, diabetesType, cacheKey, decision)
                    return FoodResult.Success(foodItem, decision)
                }
            }
        } catch (e: Exception) {
            // Continue to AI fallback if USDA fails
        }

        // AI fallback would go here
        return FoodResult.Error("No food data found")
    }

    suspend fun getFoodByBarcode(
        barcode: String,
        diabetesType: String,
        useStaging: Boolean = false
    ): FoodRepository.FoodResult {
        val cacheKey = "barcode:$barcode"
        val cached = getCachedFood(cacheKey)
        if (cached != null && isCacheValid(cached)) {
            val decision = decisionLogic.decideSuitability(cached, diabetesType)
            saveToHistory(barcode, diabetesType, cacheKey, decision)
            return FoodResult.Success(cached, decision)
        }

        try {
            val response = RetrofitClient.createOffClient(useStaging).getProductByBarcode(barcode)
            if (response.isSuccessful && response.body() != null) {
                val result: FoodSearchResult = response.body()!!
                if (!result.product_name.isNullOrBlank()) {
                    val foodItem = FoodItem(
                        name = result.product_name ?: "Unknown",
                        brand = result.brands,
                        carbs100g = result.carbohydrates_100g?.toFloat(),
                        sugars100g = result.sugars_100g?.toFloat(),
                        fiber100g = result.fiber_100g?.toFloat(),
                        energyKcal100g = result.`energy-kcal_100g`?.toFloat(),
                        countryTags = result.countries_tags_en?.joinToString(","),
                        source = "OFF"
                    )

                    cacheFood(cacheKey, foodItem)
                    val decision = decisionLogic.decideSuitability(foodItem, diabetesType)
                    saveToHistory(barcode, diabetesType, cacheKey, decision)
                    return FoodResult.Success(foodItem, decision)
                }
            }
        } catch (e: Exception) {
            return FoodResult.Error("Failed to fetch food data: ${e.message}")
        }

        return FoodResult.Error("Food not found")
    }

    fun getHistory(): Flow<List<HistoryEntity>> {
        return database.historyDao().getAllHistory()
    }

    suspend fun clearHistory() {
        database.historyDao().clearAll()
    }

    private suspend fun getCachedFood(key: String): FoodItem? {
        return database.foodCacheDao().getFoodByKey(key)?.let { entity ->
            FoodItem(
                name = entity.name,
                brand = entity.brand,
                carbs100g = entity.carbs100g,
                sugars100g = entity.sugars100g,
                fiber100g = entity.fiber100g,
                energyKcal100g = entity.energyKcal100g,
                countryTags = entity.countryTags,
                source = entity.source,
                updatedAt = entity.updatedAt
            )
        }
    }

    private fun isCacheValid(foodItem: FoodItem): Boolean {
        return System.currentTimeMillis() - foodItem.updatedAt < Constants.CACHE_TTL_MILLIS
    }

    private suspend fun cacheFood(key: String, foodItem: FoodItem) {
        val entity = FoodCacheEntity(
            key = key,
            name = foodItem.name,
            brand = foodItem.brand,
            source = foodItem.source,
            carbs100g = foodItem.carbs100g,
            sugars100g = foodItem.sugars100g,
            fiber100g = foodItem.fiber100g,
            energyKcal100g = foodItem.energyKcal100g,
            countryTags = foodItem.countryTags,
            updatedAt = System.currentTimeMillis()
        )

        // Clean up old cache entries if needed
        val count = database.foodCacheDao().getCount()
        if (count >= Constants.MAX_CACHE_SIZE) {
            database.foodCacheDao().deleteOldest(count - Constants.MAX_CACHE_SIZE + 10)
        }

        database.foodCacheDao().insertFood(entity)
    }

    private suspend fun saveToHistory(
        query: String,
        diabetesType: String,
        matchedKey: String?,
        decision: FoodDecisionLogic.FoodDecision
    ) {
        val history = HistoryEntity(
            queryText = query,
            diabetesType = diabetesType,
            matchedKey = matchedKey,
            suitability = decision.category,
            reason = decision.reason,
            portionText = decision.portionText,
            alternativesJson = decision.alternatives.joinToString(","),
            sourcesUsed = decision.source,
            createdAt = System.currentTimeMillis()
        )

        // Clean up old history entries if needed
        val count = database.historyDao().getCount()
        if (count >= Constants.MAX_HISTORY_SIZE) {
            database.historyDao().deleteOldest(count - Constants.MAX_HISTORY_SIZE + 10)
        }

        database.historyDao().insertHistory(history)
    }

    sealed class FoodResult {
        data class Success(val foodItem: FoodItem, val decision: FoodDecisionLogic.FoodDecision) : FoodResult()
        data class Error(val message: String) : FoodResult()
    }

    companion object {
        @Volatile
        private var INSTANCE: FoodRepository? = null

        fun getInstance(database: AppDatabase): FoodRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = FoodRepository(database)
                INSTANCE = instance
                instance
            }
        }
    }
}