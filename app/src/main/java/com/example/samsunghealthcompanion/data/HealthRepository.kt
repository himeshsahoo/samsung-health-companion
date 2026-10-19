package com.example.samsunghealthcompanion.data

import com.example.samsunghealthcompanion.data.db.dao.HealthDao
import com.example.samsunghealthcompanion.data.db.entities.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class HealthRepository(private val healthDao: HealthDao) {
    val userProfile: Flow<UserProfile?> = healthDao.getUserProfile()

    suspend fun saveUserProfile(profile: UserProfile) {
        healthDao.insertUserProfile(profile)
    }

    suspend fun saveHeartRateRecords(records: List<HeartRateRecord>) {
        healthDao.insertHeartRateRecords(records)
    }

    fun getHeartRateRecords(since: Instant): Flow<List<HeartRateRecord>> {
        return healthDao.getHeartRateRecords(since)
    }

    suspend fun saveHrvRecords(records: List<HRVRecord>) {
        healthDao.insertHrvRecords(records)
    }

    fun getHrvRecords(since: Instant): Flow<List<HRVRecord>> {
        return healthDao.getHrvRecords(since)
    }

    suspend fun saveSleepRecords(records: List<SleepRecord>) {
        healthDao.insertSleepRecords(records)
    }

    fun getSleepRecords(since: Instant): Flow<List<SleepRecord>> {
        return healthDao.getSleepRecords(since)
    }

    suspend fun saveStressRecords(records: List<StressRecord>) {
        healthDao.insertStressRecords(records)
    }

    fun getStressRecords(since: Instant): Flow<List<StressRecord>> {
        return healthDao.getStressRecords(since)
    }

    suspend fun saveWorkoutRecords(records: List<WorkoutRecord>) {
        healthDao.insertWorkoutRecords(records)
    }

    fun getWorkoutRecords(since: Instant): Flow<List<WorkoutRecord>> {
        return healthDao.getWorkoutRecords(since)
    }

    suspend fun saveCalculatedMetric(metric: CalculatedMetric) {
        healthDao.insertCalculatedMetric(metric)
    }

    fun getLatestMetric(name: String): Flow<CalculatedMetric?> {
        return healthDao.getLatestMetric(name)
    }
}
