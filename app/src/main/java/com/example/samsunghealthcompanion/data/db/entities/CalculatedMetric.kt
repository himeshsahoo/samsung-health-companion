package com.example.samsunghealthcompanion.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "calculated_metrics")
data class CalculatedMetric(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metricName: String, // e.g., "TRAINING_STATUS", "BODY_BATTERY"
    val value: String, // JSON or simple string
    val timestamp: Instant,
    val algorithmVersion: String,
    val confidence: String, // "HIGH", "MODERATE", "LOW", "INSUFFICIENT_DATA"
    val metadata: String? = null // Extra context
)
