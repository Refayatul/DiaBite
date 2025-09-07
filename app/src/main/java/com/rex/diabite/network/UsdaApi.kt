package com.rex.diabite.network

import com.rex.diabite.model.FoodSearchResult
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface UsdaApi {

    @POST("v1/foods/search")
    suspend fun searchFoods(
        @Header("X-Api-Key") apiKey: String,
        @Query("query") query: String,
        @Query("pageSize") pageSize: Int = 10
    ): Response<UsdaSearchResponse>

    @GET("v1/food/{fdcId}")
    suspend fun getFoodDetails(
        @Header("X-Api-Key") apiKey: String,
        @Path("fdcId") fdcId: String
    ): Response<UsdaFoodDetailsResponse>
}

@JsonClass(generateAdapter = true)
data class UsdaSearchResponse(
    @Json(name = "foods") val foods: List<UsdaFood>?
)

@JsonClass(generateAdapter = true)
data class UsdaFood(
    @Json(name = "fdcId") val fdcId: Int?,
    @Json(name = "description") val description: String?,
    @Json(name = "dataType") val dataType: String?,
    @Json(name = "publishedDate") val publishedDate: String?
)

@JsonClass(generateAdapter = true)
data class UsdaFoodDetailsResponse(
    @Json(name = "fdcId") val fdcId: Int?,
    @Json(name = "description") val description: String?,
    @Json(name = "foodNutrients") val foodNutrients: List<FoodNutrient>?
)

@JsonClass(generateAdapter = true)
data class FoodNutrient(
    @Json(name = "nutrientId") val nutrientId: Int?,
    @Json(name = "nutrientName") val nutrientName: String?,
    @Json(name = "unitName") val unitName: String?,
    @Json(name = "value") val value: Double?
)