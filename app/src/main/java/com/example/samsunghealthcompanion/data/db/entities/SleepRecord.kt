package com.example.samsunghealthcompanion.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "sleep_records")
data class SleepRecord(
    @PrimaryKey val startTime: Instant,
    val endTime: Instant,
    val totalDurationMinutes: Long,
    val awakeDurationMinutes: Long? = null,
    val lightDurationMinutes: Long? = null,
    val deepDurationMinutes: Long? = null,
    val remDurationMinutes: Long? = null,
    val sleepScore: Int? = null,
    val averageOvernightHr: Int? = null,
    val averageOvernightHrv: Float? = null,
    val spo2Min: Float? = null,
    val respirationRateAvg: Float? = null
)
