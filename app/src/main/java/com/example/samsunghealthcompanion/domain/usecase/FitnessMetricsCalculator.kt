package com.example.samsunghealthcompanion.domain.usecase

import com.example.samsunghealthcompanion.data.db.entities.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.ln

class FitnessMetricsCalculator {

    fun calculateTrainingLoad(workout: WorkoutRecord, maxHr: Int, restingHr: Int): Float {
        val avgHr = workout.averageHr?.toFloat() ?: return 0f
        val durationMin = workout.durationSeconds / 60f
        
        val hrReserve = (maxHr - restingHr).toFloat()
        if (hrReserve <= 0) return 0f
        
        val intensity = (avgHr - restingHr) / hrReserve
        if (intensity <= 0) return 0f
        
        // TRIMP model: duration * intensity * weighting
        // Generic weighting: 0.75 * exp(1.8 * x)
        val weightingFactor = 0.75f * exp(1.8f * intensity)
        
        return durationMin * intensity * weightingFactor
    }

    fun calculateTrainingEffect(load: Float): Pair<Float, Float> {
        // Aerobic (0.0 - 5.0)
        val aerobic = (ln(load + 1) / ln(500.0) * 5.0).coerceIn(0.0, 5.0).toFloat()
        // Anaerobic estimation
        val anaerobic = (aerobic * 0.3f).coerceIn(0.0f, 5.0f)
        return Pair(aerobic, anaerobic)
    }

    fun calculateTrainingStatus(
        acuteLoad: Float,
        chronicLoad: Float,
        vo2MaxTrend: Float
    ): String {
        val ratio = if (chronicLoad > 0) acuteLoad / chronicLoad else 0f
        
        return when {
            ratio > 1.5f -> "Overreaching"
            ratio > 1.2f && vo2MaxTrend >= 0 -> "Productive"
            ratio > 0.8f && ratio <= 1.2f && vo2MaxTrend >= 0 -> "Maintaining"
            ratio < 0.8f && vo2MaxTrend < 0 -> "Detraining"
            ratio < 0.5f -> "Recovery"
            vo2MaxTrend < 0 -> "Unproductive"
            else -> "Steady"
        }
    }

    fun estimateRecoveryTime(load: Float, currentFatigue: Float): Int {
        return (load * 0.5f + currentFatigue * 2).toInt().coerceIn(0, 96)
    }

    fun estimateVo2Max(workout: WorkoutRecord, user: UserProfile): Float? {
        if (workout.activityType != "RUNNING" || (workout.distanceMeters ?: 0f) < 2000f) return null
        
        val avgHr = workout.averageHr?.toFloat() ?: return null
        val maxHr = user.maxHeartRate?.toFloat() ?: (220f - (user.age ?: 30))
        val restingHr = user.restingHeartRate?.toFloat() ?: 60f
        
        val durationSec = workout.durationSeconds
        if (durationSec <= 0) return null
        
        val speedMps = (workout.distanceMeters ?: 0f) / durationSec
        val intensity = (avgHr - restingHr) / (maxHr - restingHr)
        if (intensity < 0.6) return null
        
        val vo2 = (speedMps * 60f) * 0.2f + 3.5f
        return vo2 / intensity
    }
}
