package com.rex.diabite.repository

import android.util.Log
import com.rex.diabite.BuildConfig
import com.rex.diabite.data.FoodCacheEntity
import com.rex.diabite.data.HistoryEntity
import com.rex.diabite.db.AppDatabase
import com.rex.diabite.domain.FoodDecisionLogic
import com.rex.diabite.model.FoodItem
import com.rex.diabite.network.RetrofitClient
import com.rex.diabite.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

class FoodRepository private constructor(
    private val database: AppDatabase
) {
    private val offApi = RetrofitClient.createOffClient()
    private val usdaApi = RetrofitClient.createUsdaClient()
    private val decisionLogic = FoodDecisionLogic()

    private val TAG = "FoodRepository"

    suspend fun searchFoodByName(
        query: String,
        diabetesType: String,
        useStaging: Boolean = false
    ): FoodRepository.FoodResult {
        Log.d(TAG, "Searching for food: $query")

        // Check cache first
        val cacheKey = "name:$query"
        val cached = getCachedFood(cacheKey)
        if (cached != null && isCacheValid(cached)) {
            Log.d(TAG, "Found cached food for: $query")
            val decision = decisionLogic.decideSuitability(cached, diabetesType)
            saveToHistory(query, diabetesType, cacheKey, decision)
            return FoodResult.Success(cached, decision)
        }

        // Try OFF search
        try {
            Log.d(TAG, "Trying OFF search for: $query")
            val response = offApi.searchProducts(query)
            Log.d(TAG, "OFF response successful: ${response.isSuccessful}")

            if (response.isSuccessful && response.body() != null) {
                val offResponse = response.body()!!
                Log.d(TAG, "OFF raw response: $offResponse")

                val firstProduct = offResponse.products?.firstOrNull()

                if (firstProduct?.productName?.isNotBlank() == true) {
                    val foodItem = FoodItem(
                        name = firstProduct.productName,
                        brand = firstProduct.brands,
                        carbs100g = firstProduct.carbohydrates100g,
                        sugars100g = firstProduct.sugars100g,
                        fiber100g = firstProduct.fiber100g,
                        energyKcal100g = firstProduct.energyKcal100g,
                        countryTags = firstProduct.countriesTagsEn,
                        source = "OFF"
                    )

                    Log.d(TAG, "OFF food item created: ${foodItem.name}")
                    Log.d(TAG, "OFF food item nutrients - carbs: ${foodItem.carbs100g}, sugars: ${foodItem.sugars100g}, fiber: ${foodItem.fiber100g}")

                    cacheFood(cacheKey, foodItem)
                    val decision = decisionLogic.decideSuitability(foodItem, diabetesType)
                    saveToHistory(query, diabetesType, cacheKey, decision)
                    return FoodResult.Success(foodItem, decision)
                } else {
                    Log.d(TAG, "OFF no products found or product name is blank")
                }
            } else {
                Log.e(TAG, "OFF search failed. Response: ${response.code()} - ${response.message()}")
                if (response.errorBody() != null) {
                    try {
                        Log.e(TAG, "OFF error body: ${response.errorBody()?.string()}")
                    } catch (e: Exception) {
                        Log.e(TAG, "OFF error reading error body", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OFF search exception: ${e.message}", e)
        }

        // Try USDA search with retry logic for rate limits
        try {
            Log.d(TAG, "Trying USDA search for: $query")
            Log.d(TAG, "USDA API Key length: ${BuildConfig.FDC_API_KEY.length}")

            var retryCount = 0
            val maxRetries = 3

            while (retryCount <= maxRetries) {
                val response = usdaApi.searchFoods(
                    apiKey = BuildConfig.FDC_API_KEY,
                    query = query
                )

                Log.d(TAG, "USDA response code: ${response.code()}")

                // Check for rate limit
                if (response.code() == 429) {
                    retryCount++
                    if (retryCount <= maxRetries) {
                        val delayMs = (1000 * retryCount * retryCount).toLong() // Exponential backoff
                        Log.d(TAG, "USDA rate limit hit, retrying in ${delayMs}ms (attempt $retryCount)")
                        delay(delayMs)
                        continue
                    } else {
                        Log.e(TAG, "USDA max retries exceeded for rate limit")
                        break
                    }
                }

                Log.d(TAG, "USDA response successful: ${response.isSuccessful}")

                if (response.isSuccessful && response.body()?.foods?.isNotEmpty() == true) {
                    val foods = response.body()!!.foods!!
                    Log.d(TAG, "USDA found ${foods.size} foods")

                    val firstFood = foods[0]
                    Log.d(TAG, "USDA first food: ${firstFood.description}, fdcId: ${firstFood.fdcId}")

                    // Get detailed nutrition info
                    var detailsRetryCount = 0
                    while (detailsRetryCount <= maxRetries) {
                        val detailsResponse = usdaApi.getFoodDetails(
                            apiKey = BuildConfig.FDC_API_KEY,
                            fdcId = firstFood.fdcId.toString()
                        )

                        if (detailsResponse.code() == 429) {
                            detailsRetryCount++
                            if (detailsRetryCount <= maxRetries) {
                                val delayMs = (1000 * detailsRetryCount * detailsRetryCount).toLong()
                                Log.d(TAG, "USDA details rate limit hit, retrying in ${delayMs}ms (attempt $detailsRetryCount)")
                                delay(delayMs)
                                continue
                            } else {
                                Log.e(TAG, "USDA details max retries exceeded for rate limit")
                                break
                            }
                        }

                        if (detailsResponse.isSuccessful && detailsResponse.body() != null) {
                            val details = detailsResponse.body()!!
                            Log.d(TAG, "USDA details: ${details.description}")

                            val nutrients = details.foodNutrients?.associate {
                                it.nutrientId.toString() to it.value?.toFloat()
                            } ?: emptyMap()

                            Log.d(TAG, "USDA nutrients: $nutrients")

                            val foodItem = FoodItem(
                                name = details.description ?: query,
                                carbs100g = nutrients["1005"], // Carbohydrate
                                sugars100g = nutrients["2000"], // Sugars, total
                                fiber100g = nutrients["1079"], // Fiber, total dietary
                                source = "USDA"
                            )

                            Log.d(TAG, "USDA food item created: ${foodItem.name}")
                            cacheFood(cacheKey, foodItem)
                            val decision = decisionLogic.decideSuitability(foodItem, diabetesType)
                            saveToHistory(query, diabetesType, cacheKey, decision)
                            return FoodResult.Success(foodItem, decision)
                        } else {
                            Log.e(TAG, "USDA details failed. Response: ${detailsResponse.code()} - ${detailsResponse.message()}")
                            break
                        }
                    }
                } else {
                    Log.e(TAG, "USDA search failed. Response: ${response.code()} - ${response.message()}")
                    if (response.errorBody() != null) {
                        try {
                            val errorBody = response.errorBody()?.string()
                            Log.e(TAG, "USDA error body: $errorBody")
                        } catch (e: Exception) {
                            Log.e(TAG, "USDA error reading error body", e)
                        }
                    }
                }
                break
            }
        } catch (e: Exception) {
            Log.e(TAG, "USDA search exception: ${e.message}", e)
        }

        Log.d(TAG, "No food data found for: $query")
        return FoodResult.Error("No food data found for '$query'")
    }

    suspend fun getFoodByBarcode(
        barcode: String,
        diabetesType: String,
        useStaging: Boolean = false
    ): FoodRepository.FoodResult {
        Log.d(TAG, "Searching for barcode: $barcode")

        val cacheKey = "barcode:$barcode"
        val cached = getCachedFood(cacheKey)
        if (cached != null && isCacheValid(cached)) {
            Log.d(TAG, "Found cached food for barcode: $barcode")
            val decision = decisionLogic.decideSuitability(cached, diabetesType)
            saveToHistory(barcode, diabetesType, cacheKey, decision)
            return FoodResult.Success(cached, decision)
        }

        try {
            Log.d(TAG, "Trying OFF barcode search for: $barcode")
            val response = RetrofitClient.createOffClient(useStaging).getProductByBarcode(barcode)
            Log.d(TAG, "OFF barcode response successful: ${response.isSuccessful}")

            if (response.isSuccessful && response.body() != null) {
                val offResponse = response.body()!!
                Log.d(TAG, "OFF barcode raw response: $offResponse")

                val product = offResponse.product

                if (product?.productName?.isNotBlank() == true) {
                    val foodItem = FoodItem(
                        name = product.productName,
                        brand = product.brands,
                        carbs100g = product.carbohydrates100g,
                        sugars100g = product.sugars100g,
                        fiber100g = product.fiber100g,
                        energyKcal100g = product.energyKcal100g,
                        countryTags = product.countriesTagsEn,
                        source = "OFF"
                    )

                    Log.d(TAG, "OFF barcode food item created: ${foodItem.name}")
                    Log.d(TAG, "OFF barcode food item nutrients - carbs: ${foodItem.carbs100g}, sugars: ${foodItem.sugars100g}, fiber: ${foodItem.fiber100g}")

                    cacheFood(cacheKey, foodItem)
                    val decision = decisionLogic.decideSuitability(foodItem, diabetesType)
                    saveToHistory(barcode, diabetesType, cacheKey, decision)
                    return FoodResult.Success(foodItem, decision)
                } else {
                    Log.d(TAG, "OFF barcode product name is blank")
                }
            } else {
                Log.e(TAG, "OFF barcode search failed. Response: ${response.code()} - ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "OFF barcode search exception: ${e.message}", e)
            return FoodResult.Error("Failed to fetch food data: ${e.message}")
        }

        Log.d(TAG, "Food not found for barcode: $barcode")
        return FoodResult.Error("Food not found for barcode '$barcode'")
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