package com.example.samsunghealthcompanion

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.samsunghealthcompanion.data.db.entities.*
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.UserDataPoint
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.InstantTimeFilter
import com.samsung.android.sdk.health.data.request.LocalDateFilter
import com.samsung.android.sdk.health.data.request.DataType
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors

data class HealthDataBundle(
    val userProfile: UserProfile?,
    val heartRateRecords: List<HeartRateRecord>,
    val sleepRecords: List<SleepRecord>,
    val sleepStageRecords: List<SleepStageRecord> = emptyList(),
    val workoutRecords: List<WorkoutRecord>,
    val workoutHrSeries: List<WorkoutHrRecord> = emptyList(),
    val workoutRoute: List<WorkoutRoutePoint> = emptyList(),
    val bloodOxygenRecords: List<BloodOxygenRecord> = emptyList(),
    val bloodPressureRecords: List<BloodPressureRecord> = emptyList(),
    val glucoseRecords: List<GlucoseRecord> = emptyList(),
    val temperatureRecords: List<TemperatureRecord> = emptyList(),
    val energyRecords: List<EnergyRecord> = emptyList(),
    val bodyCompositionRecords: List<BodyCompositionRecord> = emptyList()
)

data class SleepStageRecord(val startTime: Instant, val endTime: Instant, val stage: String)
data class WorkoutHrRecord(val workoutId: String, val timestamp: Instant, val heartRate: Float, val speed: Float?, val cadence: Float?, val power: Float?)
data class WorkoutRoutePoint(val workoutId: String, val timestamp: Instant, val latitude: Double, val longitude: Double, val altitude: Float?, val accuracy: Float?)
data class BloodOxygenRecord(val timestamp: Instant, val spo2: Float, val source: String?)
data class BloodPressureRecord(val timestamp: Instant, val systolic: Float, val diastolic: Float, val pulse: Int?, val source: String?)
data class GlucoseRecord(val timestamp: Instant, val glucose: Float, val source: String?)
data class TemperatureRecord(val timestamp: Instant, val temperature: Float, val type: String, val source: String?)
data class EnergyRecord(val timestamp: Instant, val score: Float)
data class BodyCompositionRecord(val timestamp: Instant, val weight: Float?, val bmi: Float?, val bodyFat: Float?, val muscleMass: Float?, val bodyWater: Float?, val bmr: Int?)

/** Reads each SDK data type and extracts actual values for storage or export. */
object SamsungHealthReader {
    fun readData(
        context: Context,
        isFullHistory: Boolean = false,
        onComplete: (HealthDataBundle) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        Executors.newSingleThreadExecutor().execute {
            try {
                val store = HealthDataService.getStore(context.applicationContext)
                val end = Instant.now()
                // Fetching up to 10 years for full history
                val start = if (isFullHistory) end.minus(3650, ChronoUnit.DAYS) else end.minus(30, ChronoUnit.DAYS)
                val instantFilter = InstantTimeFilter.of(start, end)
                val localDateFilter = LocalDateFilter.of(start.atZone(ZoneId.systemDefault()).toLocalDate(), end.atZone(ZoneId.systemDefault()).toLocalDate())
                
                // 1. User Profile
                val profileRequest = DataTypes.USER_PROFILE.readDataRequestBuilder.build()
                val profilePoints = store.readDataAsync(profileRequest).get().dataList
                val userProfile = profilePoints.firstOrNull()?.let { mapToUserProfile(it) }

                // 2. Heart Rate
                val hrRequest = DataTypes.HEART_RATE.readDataRequestBuilder
                    .setInstantTimeFilter(instantFilter)
                    .setLimit(if (isFullHistory) 100_000 else 5000)
                    .build()
                val hrPoints = store.readDataAsync(hrRequest).get().dataList
                val heartRateRecords = mutableListOf<HeartRateRecord>()
                hrPoints.forEach { dp ->
                    val series = dp.getValue(DataType.HeartRateType.SERIES_DATA)
                    if (series != null) {
                        series.forEach {
                            heartRateRecords.add(HeartRateRecord(
                                timestamp = it.startTime,
                                bpm = it.heartRate.toInt(),
                                measurementType = "SERIES",
                                source = dp.dataSource?.appId
                            ))
                        }
                    } else {
                        heartRateRecords.add(HeartRateRecord(
                            timestamp = dp.getStartLocalDateTime().atZone(ZoneId.systemDefault()).toInstant(),
                            bpm = dp.getValue(DataType.HeartRateType.HEART_RATE)?.toString()?.toIntOrNull() ?: 0,
                            measurementType = "SINGLE",
                            source = dp.dataSource?.appId
                        ))
                    }
                }

                // 3. Sleep & Stages
                val sleepRequest = DataTypes.SLEEP.readDataRequestBuilder
                    .setInstantTimeFilter(instantFilter)
                    .setLimit(if (isFullHistory) 5000 else 100)
                    .build()
                val sleepPoints = store.readDataAsync(sleepRequest).get().dataList
                val sleepRecords = mutableListOf<SleepRecord>()
                val sleepStageRecords = mutableListOf<SleepStageRecord>()
                sleepPoints.forEach { dp ->
                    sleepRecords.add(mapToSleepRecord(dp))
                    dp.getValue(DataType.SleepType.SESSIONS)?.forEach { session ->
                        session.stages?.forEach { stage ->
                            sleepStageRecords.add(SleepStageRecord(stage.startTime, stage.endTime, stage.stage.name))
                        }
                    }
                }

                // 4. Workouts & Series
                val workoutRequest = DataTypes.EXERCISE.readDataRequestBuilder
                    .setInstantTimeFilter(instantFilter)
                    .setLimit(if (isFullHistory) 5000 else 200)
                    .build()
                val workoutPoints = store.readDataAsync(workoutRequest).get().dataList
                val workoutHrSeries = mutableListOf<WorkoutHrRecord>()
                val workoutRoute = mutableListOf<WorkoutRoutePoint>()
                val workoutRecords = workoutPoints.map { dp ->
                    val workout = mapToWorkoutRecord(dp)
                    dp.getValue(DataType.ExerciseType.SESSIONS)?.firstOrNull()?.let { session ->
                        session.log?.forEach { log ->
                            workoutHrSeries.add(WorkoutHrRecord(workout.workoutId, log.timestamp, log.heartRate ?: 0f, log.speed, log.cadence, log.power))
                        }
                        session.route?.forEach { loc ->
                            workoutRoute.add(WorkoutRoutePoint(workout.workoutId, loc.timestamp, loc.latitude.toDouble(), loc.longitude.toDouble(), loc.altitude, loc.accuracy))
                        }
                    }
                    workout
                }

                // 5. Blood Oxygen
                val bloodOxygenRecords = store.readDataAsync(DataTypes.BLOOD_OXYGEN.readDataRequestBuilder.setInstantTimeFilter(instantFilter).setLimit(10_000).build()).get().dataList.map { dp ->
                    BloodOxygenRecord(dp.getStartLocalDateTime().atZone(ZoneId.systemDefault()).toInstant(), dp.getValue(DataType.BloodOxygenType.OXYGEN_SATURATION) ?: 0f, dp.dataSource?.appId)
                }

                // 6. Blood Pressure
                val bloodPressureRecords = store.readDataAsync(DataTypes.BLOOD_PRESSURE.readDataRequestBuilder.setInstantTimeFilter(instantFilter).setLimit(5000).build()).get().dataList.map { dp ->
                    BloodPressureRecord(dp.getStartLocalDateTime().atZone(ZoneId.systemDefault()).toInstant(), dp.getValue(DataType.BloodPressureType.SYSTOLIC) ?: 0f, dp.getValue(DataType.BloodPressureType.DIASTOLIC) ?: 0f, dp.getValue(DataType.BloodPressureType.PULSE_RATE), dp.dataSource?.appId)
                }

                // 7. Glucose
                val glucoseRecords = store.readDataAsync(DataTypes.BLOOD_GLUCOSE.readDataRequestBuilder.setInstantTimeFilter(instantFilter).setLimit(5000).build()).get().dataList.map { dp ->
                    GlucoseRecord(dp.getStartLocalDateTime().atZone(ZoneId.systemDefault()).toInstant(), dp.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL) ?: 0f, dp.dataSource?.appId)
                }

                // 8. Body Temperature
                val temperatureRecords = store.readDataAsync(DataTypes.BODY_TEMPERATURE.readDataRequestBuilder.setInstantTimeFilter(instantFilter).setLimit(5000).build()).get().dataList.map { dp ->
                    TemperatureRecord(dp.getStartLocalDateTime().atZone(ZoneId.systemDefault()).toInstant(), dp.getValue(DataType.BodyTemperatureType.BODY_TEMPERATURE) ?: 0f, "CORE", dp.dataSource?.appId)
                }
                val skinTempRecords = store.readDataAsync(DataTypes.SKIN_TEMPERATURE.readDataRequestBuilder.setInstantTimeFilter(instantFilter).setLimit(5000).build()).get().dataList.map { dp ->
                    TemperatureRecord(dp.getStartLocalDateTime().atZone(ZoneId.systemDefault()).toInstant(), dp.getValue(DataType.SkinTemperatureType.SKIN_TEMPERATURE) ?: 0f, "SKIN", dp.dataSource?.appId)
                }

                // 9. Energy Score
                val energyRecords = store.readDataAsync(DataTypes.ENERGY_SCORE.readDataRequestBuilder.setLocalDateFilter(localDateFilter).build()).get().dataList.map { dp ->
                    EnergyRecord(dp.getStartLocalDateTime().atZone(ZoneId.systemDefault()).toInstant(), dp.getValue(DataType.EnergyScoreType.ENERGY_SCORE) ?: 0f)
                }

                // 10. Body Composition
                val bodyCompRecords = store.readDataAsync(DataTypes.BODY_COMPOSITION.readDataRequestBuilder.setInstantTimeFilter(instantFilter).setLimit(5000).build()).get().dataList.map { dp ->
                    BodyCompositionRecord(
                        timestamp = dp.getStartLocalDateTime().atZone(ZoneId.systemDefault()).toInstant(),
                        weight = dp.getValue(DataType.BodyCompositionType.WEIGHT),
                        bmi = dp.getValue(DataType.BodyCompositionType.BODY_MASS_INDEX),
                        bodyFat = dp.getValue(DataType.BodyCompositionType.BODY_FAT),
                        muscleMass = dp.getValue(DataType.BodyCompositionType.MUSCLE_MASS),
                        bodyWater = dp.getValue(DataType.BodyCompositionType.TOTAL_BODY_WATER),
                        bmr = dp.getValue(DataType.BodyCompositionType.BASAL_METABOLIC_RATE)
                    )
                }

                val mainHandler = Handler(Looper.getMainLooper())
                mainHandler.post { onComplete(HealthDataBundle(userProfile, heartRateRecords, sleepRecords, sleepStageRecords, workoutRecords, workoutHrSeries, workoutRoute, bloodOxygenRecords, bloodPressureRecords, glucoseRecords, temperatureRecords + skinTempRecords, energyRecords, bodyCompRecords)) }
            } catch (error: Throwable) {
                val mainHandler = Handler(Looper.getMainLooper())
                mainHandler.post { onError(error) }
            }
        }
    }

    private fun mapToUserProfile(dp: UserDataPoint): UserProfile {
        return UserProfile(
            biologicalSex = dp.getValue(DataType.UserProfileDataType.GENDER)?.toString(),
            heightCm = dp.getValue(DataType.UserProfileDataType.HEIGHT),
            weightKg = dp.getValue(DataType.UserProfileDataType.WEIGHT)
        )
    }

    private fun mapToSleepRecord(dp: HealthDataPoint): SleepRecord {
        val startTime = dp.getStartLocalDateTime().atZone(ZoneId.systemDefault()).toInstant()
        val endTime = dp.getEndLocalDateTime()?.atZone(ZoneId.systemDefault())?.toInstant() ?: startTime
        return SleepRecord(startTime, endTime, dp.getValue(DataType.SleepType.DURATION)?.toMinutes() ?: 0L, sleepScore = dp.getValue(DataType.SleepType.SLEEP_SCORE))
    }

    private fun mapToWorkoutRecord(dp: HealthDataPoint): WorkoutRecord {
        val startTime = dp.getStartLocalDateTime().atZone(ZoneId.systemDefault()).toInstant()
        val endTime = dp.getEndLocalDateTime()?.atZone(ZoneId.systemDefault())?.toInstant() ?: startTime
        val session = dp.getValue(DataType.ExerciseType.SESSIONS)?.firstOrNull()
        return WorkoutRecord(
            workoutId = startTime.toString(),
            activityType = dp.getValue(DataType.ExerciseType.EXERCISE_TYPE)?.toString() ?: "UNKNOWN",
            startTime = startTime,
            endTime = endTime,
            durationSeconds = ChronoUnit.SECONDS.between(startTime, endTime),
            calories = session?.calories,
            distanceMeters = session?.distance,
            averageHr = session?.meanHeartRate?.toInt(),
            maxHr = session?.maxHeartRate?.toInt(),
            speedKph = session?.meanSpeed?.let { it * 3.6f },
            powerAvg = session?.meanPower,
            cadence = session?.meanCadence?.toInt()
        )
    }
}
