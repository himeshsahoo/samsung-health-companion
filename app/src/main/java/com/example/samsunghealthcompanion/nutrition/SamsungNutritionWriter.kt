package com.example.samsunghealthcompanion.nutrition

import android.content.Context
import com.example.samsunghealthcompanion.data.db.entities.FoodEntry
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import java.time.ZoneId

/**
 * Performs the actual SDK write after Samsung has approved the app's write access and the user
 * granted NUTRITION WRITE permission. It deliberately does not retry authorization failures.
 */
class SamsungNutritionWriter(private val context: Context) {
    suspend fun insert(entry: FoodEntry) {
        val localTime = entry.eatenAt.atZone(ZoneId.systemDefault()).toLocalDateTime()
        val mealType = when (entry.mealType.uppercase()) {
            "BREAKFAST" -> DataType.NutritionType.MealType.BREAKFAST
            "LUNCH" -> DataType.NutritionType.MealType.LUNCH
            "DINNER" -> DataType.NutritionType.MealType.DINNER
            "SNACK" -> DataType.NutritionType.MealType.AFTERNOON_SNACK
            "MORNING_SNACK" -> DataType.NutritionType.MealType.MORNING_SNACK
            "AFTERNOON_SNACK" -> DataType.NutritionType.MealType.AFTERNOON_SNACK
            "EVENING_SNACK" -> DataType.NutritionType.MealType.EVENING_SNACK
            else -> runCatching { DataType.NutritionType.MealType.valueOf(entry.mealType) }
                .getOrDefault(DataType.NutritionType.MealType.UNDEFINED)
        }
        val data = HealthDataPoint.builder()
            .setLocalStartTime(localTime)
            .addFieldData(DataType.NutritionType.MEAL_TYPE, mealType)
            .addFieldData(DataType.NutritionType.TITLE, entry.title)
            .addFieldData(DataType.NutritionType.CALORIES, entry.calories)
            .addFieldData(DataType.NutritionType.PROTEIN, entry.proteinGrams)
            .addFieldData(DataType.NutritionType.CARBOHYDRATE, entry.carbohydrateGrams)
            .addFieldData(DataType.NutritionType.TOTAL_FAT, entry.fatGrams)
            .addFieldData(DataType.NutritionType.DIETARY_FIBER, entry.fibreGrams)
            .addFieldData(DataType.NutritionType.SUGAR, entry.sugarGrams)
            .build()
        val request = DataTypes.NUTRITION.insertDataRequestBuilder.addData(data).build()
        HealthDataService.getStore(context.applicationContext).insertData(request)
    }
}
