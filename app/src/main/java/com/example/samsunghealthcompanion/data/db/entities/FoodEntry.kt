package com.example.samsunghealthcompanion.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/** A locally owned food log. Samsung Health identifiers are stored only after a successful write. */
@Entity(tableName = "food_entries")
data class FoodEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val mealType: String,
    val eatenAt: Instant,
    val calories: Float,
    val proteinGrams: Float = 0f,
    val carbohydrateGrams: Float = 0f,
    val fatGrams: Float = 0f,
    val fibreGrams: Float = 0f,
    val sugarGrams: Float = 0f,
    val photoUri: String? = null,
    val source: String = Source.MANUAL.name,
    val confidence: Float? = null,
    val samsungHealthUid: String? = null,
    val syncState: String = SyncState.LOCAL_ONLY.name,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    enum class Source { MANUAL, PHOTO_ESTIMATE }
    enum class SyncState { LOCAL_ONLY, PENDING, SYNCED, NOT_AUTHORIZED, FAILED }
}
