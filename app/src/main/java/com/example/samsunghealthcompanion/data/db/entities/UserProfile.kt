package com.example.samsunghealthcompanion.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 0, // Single user profile
    val age: Int? = null,
    val dateOfBirth: LocalDate? = null,
    val biologicalSex: String? = null,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val bodyFatPercentage: Float? = null,
    val restingHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val isMaxHrEstimated: Boolean = false,
    val vo2Max: Float? = null,
    val fitnessLevel: Int? = null, // 1-10
    val preferredActivities: String? = null // Comma separated
)
