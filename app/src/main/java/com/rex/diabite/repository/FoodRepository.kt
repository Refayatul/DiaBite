package com.rex.diabite.repository

import android.content.Context
import android.util.Log
import com.rex.diabite.BuildConfig
import com.rex.diabite.data.FoodCacheEntity
import com.rex.diabite.data.HistoryEntity
import com.rex.diabite.db.AppDatabase
import com.rex.diabite.db.LocalFoodDatabase
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

@JsonClass(generateAdapter = true)
data class SimpleAiResponse(
    @Json(name = "category") val category: String,
    @Json(name = "reason") val reason: String,
    @Json(name = "safePortion") val safePortion: String,
    @Json(name = "alternatives") val alternatives: List<String>
)

class FoodRepository private constructor(
    private val context: Context,
    private val database: AppDatabase
) {
    private val usdaApi = RetrofitClient.createUsdaClient()
    private val decisionLogic = FoodDecisionLogic()
    private val localFoodDatabase = LocalFoodDatabase(context)
    private val TAG = "FoodRepository"

    suspend fun searchFoodByName(
        query: String,
        diabetesType: String,
        useStaging: Boolean = false
    ): FoodResult {
        val normalizedQuery = query.lowercase().trim()
        Log.d(TAG, "Searching for food: $normalizedQuery, Diabetes Type: $diabetesType")

        val existingHistoryItem = database.historyDao().findHistoryItem(normalizedQuery, diabetesType)
        if (existingHistoryItem != null) {
            Log.d(TAG, "Found in history: ${existingHistoryItem.displayName}. Checking Room cache via matchedKey.")
            existingHistoryItem.matchedKey?.let { key ->
                val cachedFromHistory = getCachedFood(key)
                if (cachedFromHistory != null && isCacheValid(cachedFromHistory)) {
                    Log.d(TAG, "Found valid cached food for history item: ${cachedFromHistory.name} from key: $key")
                    val decision = decisionLogic.decideSuitability(cachedFromHistory, diabetesType)
                    // Update timestamp, do not change favorite status here
                    saveToHistory(normalizedQuery, cachedFromHistory.name, key, decision)
                    return FoodResult.Success(cachedFromHistory, decision)
                }
            }
        }

        val cacheKey = "name:$normalizedQuery"
        val cachedFromDirectKey = getCachedFood(cacheKey)
        if (cachedFromDirectKey != null && isCacheValid(cachedFromDirectKey)) {
            Log.d(TAG, "Found valid cached food directly from Room cache: ${cachedFromDirectKey.name}")
            val decision = decisionLogic.decideSuitability(cachedFromDirectKey, diabetesType)
            saveToHistory(normalizedQuery, cachedFromDirectKey.name, cacheKey, decision) // New item, default not favorite
            return FoodResult.Success(cachedFromDirectKey, decision)
        }

        Log.d(TAG, "Not in history or Room cache. Trying local SQLite database for: $normalizedQuery")
        val localDbFoodItem = localFoodDatabase.searchFoodByName(normalizedQuery)
        if (localDbFoodItem != null) {
            Log.i(TAG, "Found in local SQLite database: ${localDbFoodItem.name}")
            val localDbCacheKey = "name:${localDbFoodItem.name.lowercase().trim()}"
            cacheFood(localDbCacheKey, localDbFoodItem)
            val decision = decisionLogic.decideSuitability(localDbFoodItem, diabetesType)
            saveToHistory(normalizedQuery, localDbFoodItem.name, localDbCacheKey, decision) // New item, default not favorite
            return FoodResult.Success(localDbFoodItem, decision)
        }

        // USDA API call
        Log.d(TAG, "Not in local DB. Trying USDA API for: $normalizedQuery")
        try {
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
                    val usdaCacheKey = "name:${actualFoodName.lowercase().trim()}"
                    cacheFood(usdaCacheKey, foodItem)
                    val decision = decisionLogic.decideSuitability(foodItem, diabetesType)
                    saveToHistory(normalizedQuery, actualFoodName, usdaCacheKey, decision) // New item, default not favorite
                    return FoodResult.Success(foodItem, decision)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "USDA search exception: ${e.message}", e)
        }

        Log.d(TAG, "Not found in USDA. Trying Gemini AI for query: $query")
        return getAiFoodAnalysis(query, diabetesType) // Removed third boolean argument
    }

    suspend fun getFoodByBarcode(
        barcode: String,
        diabetesType: String,
        useStaging: Boolean = false
    ): FoodResult {
        val normalizedBarcode = barcode.lowercase().trim()
        Log.d(TAG, "Searching for barcode: $normalizedBarcode, Diabetes Type: $diabetesType")

        val existingHistoryItem = database.historyDao().findHistoryItem(normalizedBarcode, diabetesType)
        if (existingHistoryItem != null) {
            Log.d(TAG, "Found barcode in history: ${existingHistoryItem.displayName}. Checking cache.")
            existingHistoryItem.matchedKey?.let { key ->
                val cached = getCachedFood(key)
                if (cached != null && isCacheValid(cached)) {
                    Log.d(TAG, "Found valid cached food for history item: ${cached.name}")
                    val decision = decisionLogic.decideSuitability(cached, diabetesType)
                    saveToHistory(normalizedBarcode, cached.name, key, decision) // Update timestamp, preserve favorite
                    return FoodResult.Success(cached, decision)
                }
            }
        }

        val cacheKey = "barcode:$normalizedBarcode"
        val cachedFromDirectKey = getCachedFood(cacheKey)
        if (cachedFromDirectKey != null && isCacheValid(cachedFromDirectKey)) {
            Log.d(TAG, "Found cached food for barcode: ${cachedFromDirectKey.name}")
            val decision = decisionLogic.decideSuitability(cachedFromDirectKey, diabetesType)
            saveToHistory(normalizedBarcode, cachedFromDirectKey.name, cacheKey, decision) // New item, default not favorite
            return FoodResult.Success(cachedFromDirectKey, decision)
        }
        
        Log.w(TAG, "Food not found for barcode '$barcode' after checking history, cache, and (commented out) OFF API.")
        return FoodResult.Error("Food not found for barcode '$barcode'")
    }

    // Removed isPotentiallyFavorite parameter
    private suspend fun getAiFoodAnalysis(
        queryText: String,
        diabetesType: String
    ): FoodResult {
        val normalizedQuery = queryText.lowercase().trim()
        val cacheKeyForAi = "ai:${normalizedQuery}:${diabetesType.lowercase()}"

        val existingHistoryItem = database.historyDao().findHistoryItem(normalizedQuery, diabetesType)
        if (existingHistoryItem != null && existingHistoryItem.sourcesUsed.contains("AI")) {
            Log.d(TAG, "Found in history AI analysis for: ${existingHistoryItem.displayName}. Checking cache.")
            existingHistoryItem.matchedKey?.let { key ->
                val cached = getCachedFood(key)
                if (cached != null && isCacheValid(cached)) {
                    Log.d(TAG, "Found valid cached food for AI history item: ${cached.name}")
                    val decision = decisionLogic.decideSuitability(cached, diabetesType)
                    saveToHistory(normalizedQuery, cached.name, key, decision) // Update timestamp, preserve favorite
                    return FoodResult.Success(cached, decision)
                }
            }
        }

        val cachedAiFoodItem = getCachedFood(cacheKeyForAi)
        if (cachedAiFoodItem != null && isCacheValid(cachedAiFoodItem) && cachedAiFoodItem.source.contains("AI")) {
            Log.d(TAG, "Found cached AI analysis (FoodItem): ${cachedAiFoodItem.name}")
            val decision = decisionLogic.decideSuitability(cachedAiFoodItem, diabetesType)
            saveToHistory(normalizedQuery, cachedAiFoodItem.name, cacheKeyForAi, decision) // New item, default not favorite
            return FoodResult.Success(cachedAiFoodItem, decision)
        }

        Log.d(TAG, "No valid cached AI result. Calling Gemini API for: $queryText")
        try {
            val prompt = createPrompt(queryText, diabetesType)
            val request = GeminiRequest(contents = listOf(Content(parts = listOf(Part(prompt)))))
            val apiKey = BuildConfig.GEMINI_API_KEY

            if (apiKey.isBlank() || apiKey == "YOUR_GEMINI_API_KEY_HERE") {
                Log.w(TAG, "Gemini API key not configured. Falling back to simple AI analysis.")
                val simpleResult = getSimpleAiAnalysis(queryText, diabetesType)
                if (simpleResult is FoodResult.Success) {
                    val simpleAiCacheKey = "ai_estimate:${normalizedQuery}:${diabetesType.lowercase()}"
                    cacheFood(simpleAiCacheKey, simpleResult.foodItem)
                    saveToHistory(normalizedQuery, simpleResult.foodItem.name, simpleAiCacheKey, simpleResult.decision)
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
                            diabetesType = diabetesType
                        )
                        cacheFood(cacheKeyForAi, foodItem)
                        saveToHistory(normalizedQuery, actualFoodName, cacheKeyForAi, decision)
                        return FoodResult.Success(foodItem, decision)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI call failed, falling back to simple estimation.", e)
        }

        Log.w(TAG, "Gemini AI failed or returned no usable data. Falling back to simple AI analysis for: $queryText")
        val simpleResultOnFailure = getSimpleAiAnalysis(queryText, diabetesType)
        if (simpleResultOnFailure is FoodResult.Success) {
            val simpleAiCacheKey = "ai_estimate:${normalizedQuery}:${diabetesType.lowercase()}"
            cacheFood(simpleAiCacheKey, simpleResultOnFailure.foodItem)
            saveToHistory(normalizedQuery, simpleResultOnFailure.foodItem.name, simpleAiCacheKey, simpleResultOnFailure.decision)
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

    fun getFavoriteHistoryItems(): Flow<List<HistoryEntity>> { // New method for favorites
        return database.historyDao().getFavoriteHistory()
    }

    suspend fun clearHistory() {
        database.historyDao().clearAll()
    }

    suspend fun getHistoryItem(queryText: String, diabetesType: String): HistoryEntity? {
        return database.historyDao().findHistoryItem(queryText.lowercase().trim(), diabetesType)
    }

    suspend fun setFavoriteStatus(id: Long, isFavorite: Boolean) {
        database.historyDao().setFavoriteStatus(id, isFavorite)
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
                countryTags = foodCacheEntity.countryTags?.split(",")?.map { it.trim() } ?: emptyList(),
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
            countryTags = foodItem.countryTags.joinToString(",").takeIf { it.isNotEmpty() },
            updatedAt = System.currentTimeMillis()
        )
        database.foodCacheDao().insertFood(entityToCache)
        Log.d(TAG, "Cached food item: ${foodItem.name} with key: $key")
    }

    private suspend fun saveToHistory(
        queryText: String,
        actualFoodName: String,
        matchedKey: String?,
        decision: FoodDecisionLogic.FoodDecision,
        isFavoriteForNewItem: Boolean = false // Default to false for new items
    ) {
        val normalizedQueryForHistory = queryText.lowercase().trim()
        val existingHistoryItem = database.historyDao().findHistoryItem(normalizedQueryForHistory, decision.diabetesType)

        if (existingHistoryItem != null) {
            // Update existing: only display name, timestamp. Favorite status is NOT changed here.
            database.historyDao().updateHistoryItem(existingHistoryItem.id, actualFoodName, System.currentTimeMillis(), decision.diabetesType)
            Log.d(TAG, "Updated history item: $actualFoodName for query: $normalizedQueryForHistory. Favorite status (${existingHistoryItem.isFavorite}) preserved.")
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
                createdAt = System.currentTimeMillis(),
                isFavorite = isFavoriteForNewItem // Use this only for new items
            )
            val count = database.historyDao().getCount()
            if (count >= Constants.MAX_HISTORY_SIZE) {
                database.historyDao().deleteOldest(count - Constants.MAX_HISTORY_SIZE + 1)
            }
            database.historyDao().insertHistory(history)
            Log.d(TAG, "Saved new history item: $actualFoodName for query: $normalizedQueryForHistory. Favorite: $isFavoriteForNewItem")
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
    ): FoodResult {
        Log.d(TAG, "Using simple AI estimation for: $foodNameQuery, Type: $diabetesType")
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
            reason = "Estimated values for $actualFoodName - AI analysis not available or API key missing.",
            portionText = "Approximate values (100g portion) - verify with healthcare provider.",
            alternatives = listOf("Consult a nutritionist", "Check detailed nutrition info"),
            source = "AI_ESTIMATE",
            diabetesType = diabetesType
        )
        return FoodResult.Success(foodItem, decision)
    }

    sealed class FoodResult {
        data class Success(val foodItem: FoodItem, val decision: FoodDecisionLogic.FoodDecision) : FoodResult()
        data class Error(val message: String) : FoodResult()
    }

    companion object {
        @Volatile
        private var INSTANCE: FoodRepository? = null

        fun getInstance(context: Context, database: AppDatabase): FoodRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = FoodRepository(context.applicationContext, database)
                INSTANCE = instance
                instance
            }
        }

        fun closeLocalDatabase() {
            INSTANCE?.localFoodDatabase?.close()
            Log.i("FoodRepositoryCompanion", "Requested to close local database through FoodRepository companion.")
        }
    }
}