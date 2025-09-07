package com.rex.diabite.domain

import com.rex.diabite.model.FoodItem
import com.rex.diabite.util.Constants

class FoodDecisionLogic {

    data class FoodDecision(
        val category: String, // "SAFE" | "SMALL_PORTION" | "LIMIT" | "AVOID" | "UNKNOWN"
        val reason: String,
        val portionText: String,
        val alternatives: List<String>,
        val source: String
    )

    fun decideSuitability(foodItem: FoodItem, diabetesType: String): FoodDecision {
        val carbs100g = foodItem.carbs100g ?: 0f
        val sugars100g = foodItem.sugars100g ?: 0f
        val fiber100g = foodItem.fiber100g ?: 0f
        val netCarbs = foodItem.netCarbsPer100g

        // Base category determination
        var category = when {
            sugars100g >= 20 -> "AVOID"
            sugars100g >= 15 || netCarbs >= 35 -> "LIMIT"
            netCarbs <= 5 -> "SAFE"
            else -> "SMALL_PORTION"
        }

        var reason = when (category) {
            "AVOID" -> "High sugar content (${sugars100g}g per 100g)"
            "LIMIT" -> "High sugar or net carb content"
            "SAFE" -> "Low net carbs (${netCarbs}g per 100g)"
            "SMALL_PORTION" -> "Moderate net carbs (${netCarbs}g per 100g)"
            else -> "Unknown"
        }

        // Fiber adjustment
        if (fiber100g >= 5 && (category == "SMALL_PORTION" || category == "LIMIT")) {
            when (category) {
                "LIMIT" -> {
                    category = "SMALL_PORTION"
                    reason += " (High fiber content helps)"
                }
                "SMALL_PORTION" -> {
                    category = "SAFE"
                    reason += " (High fiber content helps)"
                }
            }
        }

        // Diabetes type adjustments
        if (diabetesType == "TYPE_2") {
            if (category == "SMALL_PORTION" && sugars100g >= 12) {
                category = "LIMIT"
                reason = "High sugar content for Type 2 diabetes"
            }
            if (category == "SMALL_PORTION" && netCarbs > 30) {
                category = "LIMIT"
                reason = "High net carbs for Type 2 diabetes"
            }
        } else if (diabetesType == "TYPE_1") {
            if (category == "LIMIT" && sugars100g < 15 && fiber100g >= 5) {
                category = "SMALL_PORTION"
                reason = "Consider carb counting per your care plan"
            }
        }

        // Portion guidance
        val portionGrams = calculatePortionGrams(netCarbs)
        val portionNote = if (diabetesType == "TYPE_2") {
            "Keep portions small; pair with protein/fiber."
        } else {
            "Monitor carbs; follow your care plan."
        }
        val portionText = "${portionGrams}g portion. $portionNote"

        // Alternatives based on food name
        val alternatives = suggestAlternatives(foodItem.name.lowercase())

        return FoodDecision(
            category = category,
            reason = reason,
            portionText = portionText,
            alternatives = alternatives,
            source = foodItem.source
        )
    }

    private fun calculatePortionGrams(netCarbsPer100g: Float): Int {
        if (netCarbsPer100g <= 0) return 100
        val grams = 100 * Constants.TARGET_NET_CARBS_PER_SERVING / netCarbsPer100g
        return grams.coerceIn(20f, 300f).toInt()
    }

    private fun suggestAlternatives(foodName: String): List<String> {
        return when {
            foodName.contains("sugar") || foodName.contains("sweet") ||
                    foodName.contains("soda") || foodName.contains("cola") -> {
                listOf("unsweetened yogurt", "nuts", "fruit with peel", "water/unsweetened tea")
            }
            foodName.contains("rice") -> {
                listOf("brown rice", "cauliflower rice", "quinoa")
            }
            foodName.contains("roti") || foodName.contains("naan") -> {
                listOf("whole wheat roti", "multigrain roti")
            }
            foodName.contains("samosa") || foodName.contains("fries") ||
                    foodName.contains("chips") || foodName.contains("pakoda") -> {
                listOf("roasted chana", "baked options", "salad")
            }
            else -> {
                listOf("dal", "grilled fish/chicken", "non-starchy vegetables")
            }
        }
    }
}