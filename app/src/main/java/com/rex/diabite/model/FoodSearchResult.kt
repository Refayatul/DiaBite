package com.rex.diabite.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FoodSearchResult(
    val products: List<Product>?,
    val product: Product?
)

@JsonClass(generateAdapter = true)
data class Product(
    @Json(name = "product_name") val productName: String?,
    val brands: String?,
    @Json(name = "carbohydrates_100g") val carbohydrates100g: Float?,
    @Json(name = "sugars_100g") val sugars100g: Float?,
    @Json(name = "fiber_100g") val fiber100g: Float?,
    @Json(name = "energy-kcal_100g") val energyKcal100g: Float?,
    @Json(name = "countries_tags_en") val countriesTagsEn: String?
)