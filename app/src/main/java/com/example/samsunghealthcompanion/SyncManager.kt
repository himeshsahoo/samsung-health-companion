package com.example.samsunghealthcompanion

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.samsunghealthcompanion.data.HealthRepository
import com.example.samsunghealthcompanion.data.db.entities.CalculatedMetric
import com.example.samsunghealthcompanion.domain.usecase.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SyncManager(
    private val repository: HealthRepository,
    private val fitnessCalculator: FitnessMetricsCalculator,
    private val batteryCalculator: BodyBatteryCalculator,
    private val hrvCalculator: HRVStatusCalculator
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun sync(activity: Context, onComplete: () -> Unit, onError: (Throwable) -> Unit) {
        SamsungHealthReader.readData(
            context = activity,
            onComplete = { bundle ->
                scope.launch {
                    try {
                        saveData(bundle)
                        calculateMetrics()
                        val mainHandler = Handler(Looper.getMainLooper())
                        mainHandler.post { onComplete() }
                    } catch (e: Exception) {
                        val mainHandler = Handler(Looper.getMainLooper())
                        mainHandler.post { onError(e) }
                    }
                }
            },
            onError = { error ->
                val mainHandler = Handler(Looper.getMainLooper())
                mainHandler.post { onError(error) }
            }
        )
    }

    private suspend fun saveData(bundle: HealthDataBundle) {
        bundle.userProfile?.let { 
            val existing = repository.userProfile.first()
            if (existing == null) {
                repository.saveUserProfile(it)
            } else {
                repository.saveUserProfile(existing.copy(
                    biologicalSex = it.biologicalSex ?: existing.biologicalSex,
                    heightCm = it.heightCm ?: existing.heightCm,
                    weightKg = it.weightKg ?: existing.weightKg
                ))
            }
        }
        repository.saveHeartRateRecords(bundle.heartRateRecords)
        repository.saveSleepRecords(bundle.sleepRecords)
        repository.saveWorkoutRecords(bundle.workoutRecords)
        // Stress is not currently available in the SDK bundle
    }

    private suspend fun calculateMetrics() {
        val now = Instant.now()
        val user = repository.userProfile.first() ?: return
        val workouts = repository.getWorkoutRecords(now.minus(28, ChronoUnit.DAYS)).first()
        val sleep = repository.getSleepRecords(now.minus(7, ChronoUnit.DAYS)).first()
        val stress = repository.getStressRecords(now.minus(1, ChronoUnit.DAYS)).first()
        
        val maxHr = user.maxHeartRate ?: (220 - (user.age ?: 30))
        val restingHr = user.restingHeartRate ?: 60

        // 1. Training load for individual workouts
        workouts.forEach { workout ->
            if (workout.trainingLoad == null) {
                val load = fitnessCalculator.calculateTrainingLoad(workout, maxHr, restingHr)
                val (aerobic, anaerobic) = fitnessCalculator.calculateTrainingEffect(load)
                repository.saveWorkoutRecords(listOf(workout.copy(
                    trainingLoad = load,
                    aerobicTrainingEffect = aerobic,
                    anaerobicTrainingEffect = anaerobic
                )))
            }
        }

        // 2. Training Status
        val acuteLoad = workouts.filter { it.startTime.isAfter(now.minus(7, ChronoUnit.DAYS)) }
            .sumOf { (it.trainingLoad ?: 0f).toDouble() }.toFloat()
        val chronicLoad = workouts.sumOf { (it.trainingLoad ?: 0f).toDouble() }.toFloat() / 4f
        val status = fitnessCalculator.calculateTrainingStatus(acuteLoad, chronicLoad, 0f)
        
        repository.saveCalculatedMetric(CalculatedMetric(
            metricName = "TRAINING_STATUS",
            value = status,
            timestamp = now,
            algorithmVersion = "1.0",
            confidence = if (workouts.size > 3) "HIGH" else "MODERATE"
        ))

        // 3. Body Battery
        val latestBattery = repository.getLatestMetric("BODY_BATTERY").first()
        val prevValue = latestBattery?.value?.toIntOrNull() ?: 50
        val batteryValue = batteryCalculator.calculateCurrentBattery(
            prevValue,
            sleep.firstOrNull(),
            stress,
            workouts.filter { it.startTime.isAfter(now.minus(1, ChronoUnit.DAYS)) }
        )
        
        repository.saveCalculatedMetric(CalculatedMetric(
            metricName = "BODY_BATTERY",
            value = batteryValue.toString(),
            timestamp = now,
            algorithmVersion = "1.0",
            confidence = "MODERATE"
        ))

        // 4. Acute Load Ratio
        val ratio = if (chronicLoad > 0) acuteLoad / chronicLoad else 0f
        repository.saveCalculatedMetric(CalculatedMetric(
            metricName = "LOAD_RATIO",
            value = "%.2f".format(ratio),
            timestamp = now,
            algorithmVersion = "1.0",
            confidence = "HIGH"
        ))

        // 5. Recovery Time (simplified estimate from last workout)
        val lastWorkout = workouts.maxByOrNull { it.endTime }
        if (lastWorkout != null) {
            val recoveryHours = fitnessCalculator.estimateRecoveryTime(lastWorkout.trainingLoad ?: 0f, 0f)
            repository.saveCalculatedMetric(CalculatedMetric(
                metricName = "RECOVERY_TIME",
                value = "$recoveryHours",
                timestamp = now,
                algorithmVersion = "1.0",
                confidence = "MODERATE"
            ))
        }

        // 6. VO2 Max
        val lastRun = workouts.filter { it.activityType == "RUNNING" }.maxByOrNull { it.endTime }
        if (lastRun != null) {
            val vo2Max = fitnessCalculator.estimateVo2Max(lastRun, user)
            if (vo2Max != null) {
                repository.saveCalculatedMetric(CalculatedMetric(
                    metricName = "VO2_MAX",
                    value = "%.1f".format(vo2Max),
                    timestamp = now,
                    algorithmVersion = "1.0",
                    confidence = "MODERATE"
                ))
            }
        }
    }
}
