package com.rex.diabite.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val queryText: String,
    val diabetesType: String, // "TYPE_1" | "TYPE_2"
    val matchedKey: String? = null, // FoodCacheEntity key or null
    val suitability: String, // "SAFE"|"SMALL_PORTION"|"LIMIT"|"AVOID"|"UNKNOWN"
    val reason: String,
    val portionText: String,
    val alternativesJson: String, // JSON array of strings
    val sourcesUsed: String, // comma-separated e.g. "OFF,USDA"
    val createdAt: Long
)