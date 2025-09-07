package com.rex.diabite.model

data class FoodSearchResult(
    val code: String? = null,
    val product_name: String? = null,
    val brands: String? = null,
    val countries_tags_en: List<String>? = null,
    val categories_tags: List<String>? = null,
    val carbohydrates_100g: Double? = null,
    val sugars_100g: Double? = null,
    val fiber_100g: Double? = null,
    val `energy-kcal_100g`: Double? = null,
    val nutriscore_grade: String? = null,
    val nutrition_grade_fr: String? = null,
    val last_modified_t: Long? = null
)