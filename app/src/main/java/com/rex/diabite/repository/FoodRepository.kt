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
import kotlinx.coroutines.flow.Flow
import com.rex.diabite.network.GeminiApi
import com.rex.diabite.network.GeminiRequest
import com.rex.diabite.network.Content
import com.rex.diabite.network.Part
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

// Simple AI response model
@JsonClass(generateAdapter = true)
data class SimpleAiResponse(
    @Json(name = "category") val category: String,
    @Json(name = "reason") val reason: String,
    @Json(name = "safePortion") val safePortion: String,
    @Json(name = "alternatives") val alternatives: List<String>
)

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
        val normalizedQuery = query.lowercase().trim()
        Log.d(TAG, "Searching for food: $normalizedQuery")

        // Check history first
        val existingHistoryItem = database.historyDao().findHistoryItem(normalizedQuery, diabetesType)
        if (existingHistoryItem != null) {
            Log.d(TAG, "Found in history: ${existingHistoryItem.displayName}. Checking cache.")
            existingHistoryItem.matchedKey?.let { key ->
                val cached = getCachedFood(key)
                if (cached != null && isCacheValid(cached)) {
                    Log.d(TAG, "Found valid cached food for history item: ${existingHistoryItem.displayName}")
                    val decision = decisionLogic.decideSuitability(cached, diabetesType)
                    saveToHistory(normalizedQuery, cached.name, key, decision)
                    return FoodResult.Success(cached, decision)
                }
            }
        }

        val cacheKey = "name:$normalizedQuery"
        val cachedFromDirectKey = getCachedFood(cacheKey) 
        if (cachedFromDirectKey != null && isCacheValid(cachedFromDirectKey)) {
            Log.d(TAG, "Found cached food for: ${cachedFromDirectKey.name}")
            val decision = decisionLogic.decideSuitability(cachedFromDirectKey, diabetesType)
            saveToHistory(normalizedQuery, cachedFromDirectKey.name, cacheKey, decision)
            return FoodResult.Success(cachedFromDirectKey, decision)
        }

        // Try OFF search
        try {
            Log.d(TAG, "Trying OFF search for: $normalizedQuery")
            val response = offApi.searchProducts(normalizedQuery)
            if (response.isSuccessful && response.body() != null) {
                val offResponse = response.body()!!
                val firstProduct = offResponse.products?.firstOrNull()
                if (firstProduct?.productName?.isNotBlank() == true) {
                    val actualFoodName = firstProduct.productName
                    val foodItem = FoodItem(
                        name = actualFoodName,
                        brand = firstProduct.brands,
                        carbs100g = firstProduct.carbohydrates100g,
                        sugars100g = firstProduct.sugars100g,
                        fiber100g = firstProduct.fiber100g,
                        energyKcal100g = firstProduct.energyKcal100g,
                        countryTags = firstProduct.countriesTagsEn,
                        source = "OFF"
                    )
                    cacheFood(cacheKey, foodItem) 
                    val decision = decisionLogic.decideSuitability(foodItem, diabetesType)
                    saveToHistory(normalizedQuery, actualFoodName, cacheKey, decision)
                    return FoodResult.Success(foodItem, decision)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OFF search exception: ${e.message}", e)
        }

        // Try USDA search
        try {
            Log.d(TAG, "Trying USDA search for: $normalizedQuery")
            val response = usdaApi.searchFoods(apiKey = BuildConfig.FDC_API_KEY, query = normalizedQuery)
            if (response.isSuccessful && response.body()?.foods?.isNotEmpty() == true) {
                val firstFood = response.body()!!.foods!![0]
                val detailsResponse = usdaApi.getFoodDetails(apiKey = BuildConfig.FDC_API_KEY, fdcId = firstFood.fdcId.toString())
                if (detailsResponse.isSuccessful && detailsResponse.body() != null) {
                    val details = detailsResponse.body()!!
                    val nutrients = details.foodNutrients?.associate { it.nutrientId.toString() to it.value?.toFloat() } ?: emptyMap()
                    val actualFoodName = details.description ?: query
                    val foodItem = FoodItem(
                        name = actualFoodName,
                        carbs100g = nutrients["1005"],
                        sugars100g = nutrients["2000"],
                        fiber100g = nutrients["1079"],
                        source = "USDA"
                    )
                    cacheFood(cacheKey, foodItem) 
                    val decision = decisionLogic.decideSuitability(foodItem, diabetesType)
                    saveToHistory(normalizedQuery, actualFoodName, cacheKey, decision)
                    return FoodResult.Success(foodItem, decision)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "USDA search exception: ${e.message}", e)
        }

        return getAiFoodAnalysis(query, diabetesType) 
    }

    suspend fun getFoodByBarcode(
        barcode: String,
        diabetesType: String,
        useStaging: Boolean = false
    ): FoodRepository.FoodResult {
        val normalizedBarcode = barcode.lowercase().trim()
        Log.d(TAG, "Searching for barcode: $normalizedBarcode")

        val existingHistoryItem = database.historyDao().findHistoryItem(normalizedBarcode, diabetesType)
        if (existingHistoryItem != null) {
            Log.d(TAG, "Found in history: ${existingHistoryItem.displayName}. Checking cache.")
            existingHistoryItem.matchedKey?.let { key ->
                val cached = getCachedFood(key)
                if (cached != null && isCacheValid(cached)) {
                    Log.d(TAG, "Found valid cached food for history item: ${existingHistoryItem.displayName}")
                    val decision = decisionLogic.decideSuitability(cached, diabetesType)
                    saveToHistory(normalizedBarcode, cached.name, key, decision)
                    return FoodResult.Success(cached, decision)
                }
            }
        }

        val cacheKey = "barcode:$normalizedBarcode"
        val cachedFromDirectKey = getCachedFood(cacheKey) 
        if (cachedFromDirectKey != null && isCacheValid(cachedFromDirectKey)) {
            Log.d(TAG, "Found cached food for barcode: ${cachedFromDirectKey.name}")
            val decision = decisionLogic.decideSuitability(cachedFromDirectKey, diabetesType)
            saveToHistory(normalizedBarcode, cachedFromDirectKey.name, cacheKey, decision)
            return FoodResult.Success(cachedFromDirectKey, decision)
        }

        try {
            val response = RetrofitClient.createOffClient(useStaging).getProductByBarcode(barcode)
            if (response.isSuccessful && response.body() != null) {
                val product = response.body()!!.product
                if (product?.productName?.isNotBlank() == true) {
                    val actualFoodName = product.productName
                    val foodItem = FoodItem(
                        name = actualFoodName,
                        brand = product.brands,
                        carbs100g = product.carbohydrates100g,
                        sugars100g = product.sugars100g,
                        fiber100g = product.fiber100g,
                        source = "OFF"
                    )
                    cacheFood(cacheKey, foodItem) 
                    val decision = decisionLogic.decideSuitability(foodItem, diabetesType)
                    saveToHistory(normalizedBarcode, actualFoodName, cacheKey, decision)
                    return FoodResult.Success(foodItem, decision)
                }
            }
        } catch (e: Exception) {
             Log.e(TAG, "Barcode search exception: ${e.message}", e) 
            return FoodResult.Error("Failed to fetch food data by barcode: ${e.message}")
        }

        return FoodResult.Error("Food not found for barcode '$barcode'")
    }

    private suspend fun getAiFoodAnalysis(
        queryText: String, 
        diabetesType: String
    ): FoodRepository.FoodResult {
        val normalizedQuery = queryText.lowercase().trim()
        val cacheKeyForAi = "ai:${normalizedQuery}:${diabetesType.lowercase()}" 

        val existingHistoryItem = database.historyDao().findHistoryItem(normalizedQuery, diabetesType)
        if (existingHistoryItem != null) {
            Log.d(TAG, "Found in history AI analysis for: ${existingHistoryItem.displayName}. Checking cache.")
            existingHistoryItem.matchedKey?.let { key ->
                val cached = getCachedFood(key)
                if (cached != null && isCacheValid(cached)) {
                    Log.d(TAG, "Found valid cached food for AI history item: ${existingHistoryItem.displayName}")
                    val decision = decisionLogic.decideSuitability(cached, diabetesType)
                    saveToHistory(normalizedQuery, cached.name, key, decision)
                    return FoodResult.Success(cached, decision)
                }
            }
        }
        
        val cachedAi = getCachedFood(cacheKeyForAi)
        if (cachedAi != null && isCacheValid(cachedAi)) {
            Log.d(TAG, "Found cached AI analysis for: ${cachedAi.name}")
            val decision = decisionLogic.decideSuitability(cachedAi, diabetesType)
            saveToHistory(normalizedQuery, cachedAi.name, cacheKeyForAi, decision)
            return FoodResult.Success(cachedAi, decision)
        }

        try {
            val prompt = createPrompt(queryText, diabetesType)
            val request = GeminiRequest(contents = listOf(Content(parts = listOf(Part(prompt)))))
            val apiKey = BuildConfig.GEMINI_API_KEY

            if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
                val simpleResult = getSimpleAiAnalysis(queryText, diabetesType) 
                if (simpleResult is FoodResult.Success) {
                    saveToHistory(normalizedQuery, simpleResult.foodItem.name, null, simpleResult.decision)
                }
                return simpleResult
            }

            val response = createGeminiClient().getFoodAnalysis("gemini-2.5-flash-lite", apiKey, request)

            if (response.isSuccessful && response.body()?.candidates?.isNotEmpty() == true) {
                val textResponse = response.body()?.candidates?.get(0)?.content?.parts?.get(0)?.text
                if (textResponse != null) {
                    val cleanedJson = textResponse.substringAfter("```json").substringBeforeLast("```").trim()
                    val moshi = Moshi.Builder().build()
                    val jsonAdapter = moshi.adapter(SimpleAiResponse::class.java)
                    val aiResponse = jsonAdapter.fromJson(cleanedJson)

                    if (aiResponse != null) {
                        val actualFoodName = queryText.capitalize() 
                        val foodItem = FoodItem(name = actualFoodName, source = "AI")
                        val decision = FoodDecisionLogic.FoodDecision(
                            category = aiResponse.category,
                            reason = aiResponse.reason,
                            portionText = aiResponse.safePortion,
                            alternatives = aiResponse.alternatives,
                            source = "AI",
                            diabetesType = diabetesType // Added diabetesType
                        )
                        cacheFood(cacheKeyForAi, foodItem)
                        saveToHistory(normalizedQuery, actualFoodName, cacheKeyForAi, decision)
                        return FoodResult.Success(foodItem, decision)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI fallback failed", e)
        }
        
        val simpleResultOnFailure = getSimpleAiAnalysis(queryText, diabetesType)
        if (simpleResultOnFailure is FoodResult.Success) {
            saveToHistory(normalizedQuery, simpleResultOnFailure.foodItem.name, null, simpleResultOnFailure.decision)
        }
        return simpleResultOnFailure
    }

    private fun createPrompt(foodName: String, diabetesType: String): String {
        return """
            You are a nutrition assistant focusing on diabetes-friendly guidance.
            Food: $foodName, DiabetesType: $diabetesType.
            Return JSON ONLY in this format:
            {
              "category": "SAFE" | "SMALL_PORTION" | "LIMIT" | "AVOID" | "UNKNOWN",
              "reason": "...",
              "safePortion": "...",
              "alternatives": ["...", "..."]
            }
        """.trimIndent()
    }
    
    fun getHistory(): Flow<List<HistoryEntity>> {
        return database.historyDao().getAllHistory()
    }

    suspend fun clearHistory() {
        database.historyDao().clearAll()
    }

    private suspend fun getCachedFood(key: String): FoodItem? {
        return database.foodCacheDao().getFoodByKey(key)?.let { foodCacheEntity -> 
            FoodItem(
                name = foodCacheEntity.name,
                brand = foodCacheEntity.brand,
                carbs100g = foodCacheEntity.carbs100g,
                sugars100g = foodCacheEntity.sugars100g,
                fiber100g = foodCacheEntity.fiber100g,
                energyKcal100g = foodCacheEntity.energyKcal100g,
                countryTags = foodCacheEntity.countryTags,
                source = foodCacheEntity.source,
                updatedAt = foodCacheEntity.updatedAt
            )
        }
    }

    private fun isCacheValid(foodItem: FoodItem): Boolean {
        return System.currentTimeMillis() - foodItem.updatedAt < Constants.CACHE_TTL_MILLIS
    }

    private suspend fun cacheFood(key: String, foodItem: FoodItem) {
        val entityToCache = FoodCacheEntity( 
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
        database.foodCacheDao().insertFood(entityToCache)
    }

    private suspend fun saveToHistory(
        queryText: String, 
        actualFoodName: String, 
        matchedKey: String?,
        decision: FoodDecisionLogic.FoodDecision
    ) {
        val normalizedQueryForHistory = queryText.lowercase().trim()
        val existingHistoryItem = database.historyDao().findHistoryItem(normalizedQueryForHistory, decision.diabetesType)

        if (existingHistoryItem != null) {
            database.historyDao().updateHistoryItem(existingHistoryItem.id, actualFoodName, System.currentTimeMillis(), decision.diabetesType)
        } else {
            val history = HistoryEntity(
                queryText = normalizedQueryForHistory,
                displayName = actualFoodName, 
                diabetesType = decision.diabetesType,
                matchedKey = matchedKey,
                suitability = decision.category,
                reason = decision.reason,
                portionText = decision.portionText,
                alternativesJson = decision.alternatives.joinToString(","),
                sourcesUsed = decision.source,
                createdAt = System.currentTimeMillis()
            )
            val count = database.historyDao().getCount()
            if (count >= Constants.MAX_HISTORY_SIZE) {
                database.historyDao().deleteOldest(count - Constants.MAX_HISTORY_SIZE + 10) 
            }
            database.historyDao().insertHistory(history)
        }
    }
    
     private fun createGeminiClient(): GeminiApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        val moshi = Moshi.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(GeminiApi::class.java)
    }

    private suspend fun getSimpleAiAnalysis(
        foodNameQuery: String, 
        diabetesType: String
    ): FoodRepository.FoodResult {
        return try {
            Log.d(TAG, "Using simple AI estimation for: $foodNameQuery")
            val actualFoodName = foodNameQuery.capitalize()
            val foodItem = FoodItem(
                name = actualFoodName, 
                carbs100g = 15f,
                sugars100g = 5f,
                fiber100g = 2f,
                source = "AI_ESTIMATE"
            )
            val decision = FoodDecisionLogic.FoodDecision(
                category = "SMALL_PORTION",
                reason = "Estimated values for $actualFoodName - AI analysis not available",
                portionText = "Approximate values (100g portion) - verify with healthcare provider",
                alternatives = listOf("Consult a nutritionist", "Check detailed nutrition info"),
                source = "AI_ESTIMATE",
                diabetesType = diabetesType // Added diabetesType
            )
            FoodResult.Success(foodItem, decision)
        } catch (e: Exception) {
            Log.e(TAG, "Simple AI analysis failed", e)
            FoodResult.Error("Unable to analyze $foodNameQuery")
        }
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