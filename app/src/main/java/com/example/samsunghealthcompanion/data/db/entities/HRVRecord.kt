package com.example.samsunghealthcompanion.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "hrv_records")
data class HRVRecord(
    @PrimaryKey val timestamp: Instant,
    val valueMs: Float,
    val metricType: String? = null, // RMSSD, SDNN, etc.
    val durationSeconds: Int? = null,
    val source: String? = null,
    val isDuringSleep: Boolean = false,
    val workoutId: String? = null
)
