package com.example.samsunghealthcompanion.data.db.dao

import androidx.room.*
import com.example.samsunghealthcompanion.data.db.entities.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface HealthDao {
    // User Profile
    @Query("SELECT * FROM user_profile WHERE id = 0")
    fun getUserProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    // Heart Rate
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHeartRateRecords(records: List<HeartRateRecord>)

    @Query("SELECT * FROM heart_rate_records WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getHeartRateRecords(since: Instant): Flow<List<HeartRateRecord>>

    // HRV
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHrvRecords(records: List<HRVRecord>)

    @Query("SELECT * FROM hrv_records WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getHrvRecords(since: Instant): Flow<List<HRVRecord>>

    // Sleep
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepRecords(records: List<SleepRecord>)

    @Query("SELECT * FROM sleep_records WHERE startTime >= :since ORDER BY startTime DESC")
    fun getSleepRecords(since: Instant): Flow<List<SleepRecord>>

    // Stress
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStressRecords(records: List<StressRecord>)

    @Query("SELECT * FROM stress_records WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getStressRecords(since: Instant): Flow<List<StressRecord>>

    // Workouts
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutRecords(records: List<WorkoutRecord>)

    @Query("SELECT * FROM workout_records WHERE startTime >= :since ORDER BY startTime DESC")
    fun getWorkoutRecords(since: Instant): Flow<List<WorkoutRecord>>

    // Calculated Metrics
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculatedMetric(metric: CalculatedMetric)

    @Query("SELECT * FROM calculated_metrics WHERE metricName = :name ORDER BY timestamp DESC LIMIT 1")
    fun getLatestMetric(name: String): Flow<CalculatedMetric?>
}
