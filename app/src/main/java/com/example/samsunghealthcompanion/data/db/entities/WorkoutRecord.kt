package com.example.samsunghealthcompanion.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "workout_records")
data class WorkoutRecord(
    @PrimaryKey val workoutId: String,
    val activityType: String,
    val startTime: Instant,
    val endTime: Instant,
    val durationSeconds: Long,
    val distanceMeters: Float? = null,
    val calories: Float? = null,
    val averageHr: Int? = null,
    val maxHr: Int? = null,
    val trainingLoad: Float? = null,
    val aerobicTrainingEffect: Float? = null,
    val anaerobicTrainingEffect: Float? = null,
    val paceMinPerKm: Float? = null,
    val speedKph: Float? = null,
    val elevationGain: Float? = null,
    val elevationLoss: Float? = null,
    val cadence: Int? = null,
    val powerAvg: Float? = null
)
