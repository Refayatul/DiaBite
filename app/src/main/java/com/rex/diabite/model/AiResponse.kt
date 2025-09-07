package com.rex.diabite.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AiResponse(
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