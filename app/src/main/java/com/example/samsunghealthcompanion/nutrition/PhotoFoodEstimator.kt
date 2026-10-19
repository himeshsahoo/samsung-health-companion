package com.example.samsunghealthcompanion.nutrition

import android.net.Uri

/**
 * Boundary for opt-in AI nutrition estimation providers (multimodal vision + text).
 */
interface PhotoFoodEstimator {
    suspend fun estimate(photo: Uri): FoodEstimate
    suspend fun estimateText(textDescription: String): FoodEstimate
}

data class FoodEstimate(
    val suggestedTitle: String,
    val mealType: String? = null,
    val calories: Float,
    val proteinGrams: Float = 0f,
    val carbohydrateGrams: Float = 0f,
    val fatGrams: Float = 0f,
    val confidence: Float? = null,
)

class ReviewRequiredPhotoEstimator : PhotoFoodEstimator {
    override suspend fun estimate(photo: Uri) = FoodEstimate(
        suggestedTitle = "Review photo estimate",
        calories = 0f,
        confidence = null,
    )

    override suspend fun estimateText(textDescription: String) = FoodEstimate(
        suggestedTitle = textDescription,
        calories = 0f,
        confidence = null
    )
}
