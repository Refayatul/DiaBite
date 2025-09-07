package com.rex.diabite.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun getFoodAnalysis(
        @Path("model") model: String,
        @Header("X-goog-api-key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}
@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: ContentResponse?
)

@JsonClass(generateAdapter = true)
data class ContentResponse(
    val parts: List<PartResponse>?
)

@JsonClass(generateAdapter = true)
data class PartResponse(
    val text: String?
)

// AI Response Model
@JsonClass(generateAdapter = true)
data class AiFoodResponse(
    @Json(name = "isSuitable") val isSuitable: Boolean,
    @Json(name = "category") val category: String, // "SAFE" | "SMALL_PORTION" | "LIMIT" | "AVOID" | "UNKNOWN"
    @Json(name = "reason") val reason: String,
    @Json(name = "safePortion") val safePortion: SafePortion,
    @Json(name = "alternatives") val alternatives: List<Alternative>,
    @Json(name = "confidence") val confidence: Double
)

@JsonClass(generateAdapter = true)
data class SafePortion(
    @Json(name = "grams") val grams: Double,
    @Json(name = "note") val note: String
)

@JsonClass(generateAdapter = true)
data class Alternative(
    @Json(name = "name") val name: String,
    @Json(name = "why") val why: String
)