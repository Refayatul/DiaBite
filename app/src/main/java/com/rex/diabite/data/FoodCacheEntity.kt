package com.rex.diabite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_cache")
data class FoodCacheEntity(
    @PrimaryKey
    val key: String, // "name:{query}" or "barcode:{code}"
    val name: String,
    val brand: String? = null,
    val source: String, // "OFF"/"USDA"/"AI"
    val carbs100g: Float? = null,
    val sugars100g: Float? = null,
    val fiber100g: Float? = null,
    val energyKcal100g: Float? = null,
    val countryTags: String? = null, // comma-separated
    val updatedAt: Long
)