package com.example.samsunghealthcompanion.domain.usecase

import com.example.samsunghealthcompanion.data.db.entities.SleepRecord
import com.example.samsunghealthcompanion.data.db.entities.StressRecord
import com.example.samsunghealthcompanion.data.db.entities.WorkoutRecord

class BodyBatteryCalculator {
    fun calculateCurrentBattery(
        previousBattery: Int,
        lastSleep: SleepRecord?,
        recentStress: List<StressRecord>,
        recentWorkouts: List<WorkoutRecord>
    ): Int {
        var battery = previousBattery.toFloat()
        
        lastSleep?.let {
            val sleepQuality = (it.sleepScore ?: 50) / 100f
            val recharge = (it.totalDurationMinutes / 60f) * 10f * sleepQuality
            battery += recharge
        }
        
        if (recentStress.isNotEmpty()) {
            val avgStress = recentStress.map { it.stressScore }.average().toFloat()
            if (avgStress > 30) {
                val drain = (avgStress - 30) / 70f * 5f
                battery -= drain
            }
        }
        
        recentWorkouts.forEach {
            battery -= (it.trainingLoad ?: 0f) / 5f
        }
        
        return battery.toInt().coerceIn(0, 100)
    }
}
