package com.rex.diabite.model

data class FoodItem(
    val name: String,
    val brand: String? = null,
    val carbs100g: Float? = null,
    val sugars100g: Float? = null,
    val fiber100g: Float? = null,
    val energyKcal100g: Float? = null,
    val countryTags: List<String> = emptyList(), // Changed from String? to List<String>
    val source: String, // "OFF", "USDA", "AI", "LOCAL_DB", "AI_ESTIMATE"
    val updatedAt: Long = System.currentTimeMillis()
) {
    val netCarbsPer100g: Float
        get() = ((carbs100g ?: 0f) - (fiber100g ?: 0f)).coerceAtLeast(0f)
}