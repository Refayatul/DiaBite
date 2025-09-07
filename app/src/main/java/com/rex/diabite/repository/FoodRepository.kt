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
            Log.d(TAG, "Found in history: $normalizedQuery. Checking cache.")
            existingHistoryItem.matchedKey?.let { key ->
                val cached = getCachedFood(key)
                if (cached != null && isCacheValid(cached)) {
                    Log.d(TAG, "Found valid cached food for history item: $normalizedQuery")
                    val decision = decisionLogic.decideSuitability(cached, diabetesType)
                    saveToHistory(normalizedQuery, diabetesType, key, decision) // Update timestamp
                    return FoodResult.Success(cached, decision)
                }
            }
        }

        // Check cache first (for direct cache hits not necessarily from history)
        val cacheKey = "name:$normalizedQuery"
        val cached = getCachedFood(cacheKey)
        if (cached != null && isCacheValid(cached)) {
            Log.d(TAG, "Found cached food for: $normalizedQuery")
            val decision = decisionLogic.decideSuitability(cached, diabetesType)
            saveToHistory(normalizedQuery, diabetesType, cacheKey, decision)
            return FoodResult.Success(cached, decision)
        }

        // Try OFF search
        try {
            Log.d(TAG, "Trying OFF search for: $normalizedQuery")
            val response = offApi.searchProducts(normalizedQuery)
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
                    cacheFood(cacheKey, foodItem)
                    val decision = decisionLogic.decideSuitability(foodItem, diabetesType)
                    saveToHistory(normalizedQuery, diabetesType, cacheKey, decision)
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
                    val foodItem = FoodItem(
                        name = details.description ?: normalizedQuery,
                        carbs100g = nutrients["1005"],
                        sugars100g = nutrients["2000"],
                        fiber100g = nutrients["1079"],
                        source = "USDA"
                    )
                    cacheFood(cacheKey, foodItem)
                    val decision = decisionLogic.decideSuitability(foodItem, diabetesType)
                    saveToHistory(normalizedQuery, diabetesType, cacheKey, decision)
                    return FoodResult.Success(foodItem, decision)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "USDA search exception: ${e.message}", e)
        }

        // AI Fallback
        return getAiFoodAnalysis(normalizedQuery, diabetesType)
    }

    suspend fun getFoodByBarcode(
        barcode: String,
        diabetesType: String,
        useStaging: Boolean = false
    ): FoodRepository.FoodResult {
        Log.d(TAG, "Searching for barcode: $barcode")

        // Check history first
        val existingHistoryItem = database.historyDao().findHistoryItem(barcode.lowercase().trim(), diabetesType)
        if (existingHistoryItem != null) {
            Log.d(TAG, "Found in history: $barcode. Checking cache.")
            existingHistoryItem.matchedKey?.let { key ->
                val cached = getCachedFood(key)
                if (cached != null && isCacheValid(cached)) {
                    Log.d(TAG, "Found valid cached food for history item: $barcode")
                    val decision = decisionLogic.decideSuitability(cached, diabetesType)
                    saveToHistory(barcode, diabetesType, key, decision) // Update timestamp
                    return FoodResult.Success(cached, decision)
                }
            }
        }

        val cacheKey = "barcode:$barcode"
        val cached = getCachedFood(cacheKey)
        if (cached != null && isCacheValid(cached)) {
            Log.d(TAG, "Found cached food for barcode: $barcode")
            val decision = decisionLogic.decideSuitability(cached, diabetesType)
            saveToHistory(barcode, diabetesType, cacheKey, decision)
            return FoodResult.Success(cached, decision)
        }

        try {
            val response = RetrofitClient.createOffClient(useStaging).getProductByBarcode(barcode)
            if (response.isSuccessful && response.body() != null) {
                val product = response.body()!!.product
                if (product?.productName?.isNotBlank() == true) {
                    val foodItem = FoodItem(
                        name = product.productName,
                        brand = product.brands,
                        carbs100g = product.carbohydrates100g,
                        sugars100g = product.sugars100g,
                        fiber100g = product.fiber100g,
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

        return FoodResult.Error("Food not found for barcode '$barcode'")
    }

    private suspend fun getAiFoodAnalysis(
        foodName: String,
        diabetesType: String
    ): FoodRepository.FoodResult {
        val cacheKey = "ai:${foodName.lowercase()}:${diabetesType.lowercase()}"
        val cached = getCachedFood(cacheKey)
        if (cached != null && isCacheValid(cached)) {
            Log.d(TAG, "Found cached AI analysis for: $foodName")
            val decision = decisionLogic.decideSuitability(cached, diabetesType)
            saveToHistory(foodName, diabetesType, cacheKey, decision)
            return FoodResult.Success(cached, decision)
        }

        try {
            val prompt = createPrompt(foodName, diabetesType)
            val request = GeminiRequest(contents = listOf(Content(parts = listOf(Part(prompt)))))
            val apiKey = BuildConfig.GEMINI_API_KEY

            if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
                return getSimpleAiAnalysis(foodName, diabetesType)
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
                        val foodItem = FoodItem(name = foodName, source = "AI")
                        val decision = FoodDecisionLogic.FoodDecision(
                            category = aiResponse.category,
                            reason = aiResponse.reason,
                            portionText = aiResponse.safePortion,
                            alternatives = aiResponse.alternatives,
                            source = "AI"
                        )
                        cacheFood(cacheKey, foodItem)
                        saveToHistory(foodName, diabetesType, cacheKey, decision)
                        return FoodResult.Success(foodItem, decision)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI fallback failed", e)
        }
        return getSimpleAiAnalysis(foodName, diabetesType)
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
        database.foodCacheDao().insertFood(entity)
    }

    private suspend fun saveToHistory(
        query: String,
        diabetesType: String,
        matchedKey: String?,
        decision: FoodDecisionLogic.FoodDecision
    ) {
        val normalizedQuery = query.lowercase().trim()
        val existingHistoryItem = database.historyDao().findHistoryItem(normalizedQuery, diabetesType)

        if (existingHistoryItem != null) {
            // Update existing item's timestamp to bring it to the top of the history
            database.historyDao().updateHistoryTimestamp(existingHistoryItem.id, System.currentTimeMillis())
        } else {
            // Insert a new history item
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

            // Clean up old history entries if needed (before inserting new one)
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
        foodName: String,
        diabetesType: String
    ): FoodRepository.FoodResult {
        return try {
            Log.d(TAG, "Using simple AI estimation for: $foodName")

            val foodItem = FoodItem(
                name = foodName,
                carbs100g = 15f,
                sugars100g = 5f,
                fiber100g = 2f,
                source = "AI_ESTIMATE"
            )

            val decision = FoodDecisionLogic.FoodDecision(
                category = "SMALL_PORTION",
                reason = "Estimated values for $foodName - AI analysis not available",
                portionText = "Approximate values (100g portion) - verify with healthcare provider",
                alternatives = listOf("Consult a nutritionist", "Check detailed nutrition info"),
                source = "AI_ESTIMATE"
            )

            FoodResult.Success(foodItem, decision)
        } catch (e: Exception) {
            Log.e(TAG, "Simple AI analysis failed", e)
            FoodResult.Error("Unable to analyze $foodName")
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