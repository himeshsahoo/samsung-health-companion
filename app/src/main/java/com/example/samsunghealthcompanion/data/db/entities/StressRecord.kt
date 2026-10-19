package com.example.samsunghealthcompanion.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "stress_records")
data class StressRecord(
    @PrimaryKey val timestamp: Instant,
    val stressScore: Int,
    val durationSeconds: Int? = null,
    val source: String? = null
)
