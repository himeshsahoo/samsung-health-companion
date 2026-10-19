package com.example.samsunghealthcompanion.domain.usecase

import com.example.samsunghealthcompanion.data.db.entities.HRVRecord

class HRVStatusCalculator {
    fun calculateHRVStatus(
        sevenDayAvg: Float,
        baselineAvg: Float,
        baselineStdDev: Float
    ): String {
        if (baselineStdDev == 0f) return "Balanced"
        val diff = sevenDayAvg - baselineAvg
        return when {
            diff > baselineStdDev -> "High"
            diff < -baselineStdDev -> "Low"
            else -> "Balanced"
        }
    }
}
