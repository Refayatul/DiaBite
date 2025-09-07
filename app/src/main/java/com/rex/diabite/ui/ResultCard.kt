package com.rex.diabite.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rex.diabite.domain.FoodDecisionLogic
import com.rex.diabite.model.FoodItem
import com.rex.diabite.ui.theme.*
import com.rex.diabite.util.toDaysAgo

@Composable
fun ResultCard(
    foodItem: FoodItem,
    decision: FoodDecisionLogic.FoodDecision,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Food Name and Brand
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = foodItem.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    foodItem.brand?.let {
                        brand ->
                        Text(
                            text = brand,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Suitability Chip
            val (chipColor, chipTextColor) = when (decision.category) {
                "SAFE" -> SafeGreen to Color.White
                "SMALL_PORTION" -> SmallPortionYellow to Color.Black
                "LIMIT" -> LimitOrange to Color.White
                "AVOID" -> AvoidRed to Color.White
                else -> UnknownGray to Color.White
            }

            Surface(
                color = chipColor,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = decision.category.replace("_", " "),
                    color = chipTextColor,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Nutrition Info
            foodItem.carbs100g?.let { carbs ->
                Text(
                    text = "Carbs: ${String.format("%.1f", carbs)}g per 100g",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            foodItem.sugars100g?.let { sugars ->
                Text(
                    text = "Sugars: ${String.format("%.1f", sugars)}g per 100g",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            foodItem.fiber100g?.let { fiber ->
                Text(
                    text = "Fiber: ${String.format("%.1f", fiber)}g per 100g",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Net Carbs
            Text(
                text = "Net Carbs: ${String.format("%.1f", foodItem.netCarbsPer100g)}g per 100g",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Portion Guidance
            Text(
                text = decision.portionText,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Alternatives
            Text(
                text = "Alternatives:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            decision.alternatives.forEach { alternative ->
                Text(
                    text = "• $alternative",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Source and Updated Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Source Badges
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = foodItem.source,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Updated Time
                Text(
                    text = "Updated ${foodItem.updatedAt.toDaysAgo()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Clear Button
            Button(
                onClick = onClear,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Clear")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ResultCardPreview() {
    DiaBiteTheme {
        ResultCard(
            foodItem = FoodItem(
                name = "White Rice",
                brand = "Basmati",
                carbs100g = 78.3f,
                sugars100g = 0.1f,
                fiber100g = 0.4f,
                source = "OFF",
                updatedAt = System.currentTimeMillis() - 86400000 // 1 day ago
            ),
            decision = FoodDecisionLogic.FoodDecision(
                category = "SMALL_PORTION",
                reason = "High net carbs (77.9g per 100g)",
                portionText = "45g portion. Keep portions small; pair with protein/fiber.",
                alternatives = listOf("brown rice", "cauliflower rice", "quinoa"),
                source = "OFF",
                diabetesType = "TYPE_2"
            ),
            isFavorite = true, // Added for preview
            onToggleFavorite = {}, // Added for preview
            onClear = {}
        )
    }
}