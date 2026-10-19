package com.example.samsunghealthcompanion.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "heart_rate_records")
data class HeartRateRecord(
    @PrimaryKey val timestamp: Instant,
    val bpm: Int,
    val source: String? = null,
    val measurementType: String? = null,
    val isDuringSleep: Boolean = false,
    val isDuringExercise: Boolean = false,
    val workoutId: String? = null
)
