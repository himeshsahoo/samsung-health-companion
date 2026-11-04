package com.example.samsunghealthcompanion.data.cloud

import android.util.Log
import com.example.samsunghealthcompanion.HealthDataBundle
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import java.util.UUID

/**
 * Uploads a privacy-scoped, versioned health snapshot to the user's cloud API.
 * The bearer token is sent only in the Authorization header, never in the JSON body.
 */
class CloudSyncManager(private val gson: Gson) {
    private val client = OkHttpClient()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    fun pushToChatGPT(
        url: String,
        apiKey: String,
        bundle: HealthDataBundle,
        onResult: (Boolean) -> Unit
    ) {
        val latestHeartRate = bundle.heartRateRecords.maxByOrNull { it.timestamp }
        val latestSleep = bundle.sleepRecords.maxByOrNull { it.endTime }
        val latestEnergy = bundle.energyRecords.maxByOrNull { it.timestamp }
        val latestBody = bundle.bodyCompositionRecords.maxByOrNull { it.timestamp }

        val payload = mapOf(
            "schema_version" to 1,
            "sync_id" to UUID.randomUUID().toString(),
            "timestamp" to Instant.now().toString(),
            "summary" to mapOf(
                "hr_latest_bpm" to latestHeartRate?.bpm,
                "hr_latest_at" to latestHeartRate?.timestamp?.toString(),
                "energy_score" to latestEnergy?.score,
                "sleep_score" to latestSleep?.sleepScore,
                "sleep_duration_minutes" to latestSleep?.totalDurationMinutes,
                "workout_count" to bundle.workoutRecords.size,
                "latest_weight_kg" to latestBody?.weight,
                "latest_body_fat_percent" to latestBody?.bodyFat
            ),
            "user_profile" to bundle.userProfile?.let { profile ->
                mapOf(
                    "age" to profile.age,
                    "date_of_birth" to profile.dateOfBirth?.toString(),
                    "biological_sex" to profile.biologicalSex,
                    "height_cm" to profile.heightCm,
                    "weight_kg" to profile.weightKg,
                    "body_fat_percent" to profile.bodyFatPercentage,
                    "resting_heart_rate_bpm" to profile.restingHeartRate,
                    "max_heart_rate_bpm" to profile.maxHeartRate,
                    "vo2_max" to profile.vo2Max
                )
            },
            "heart_rate_records" to bundle.heartRateRecords.map {
                mapOf(
                    "timestamp" to it.timestamp.toString(),
                    "bpm" to it.bpm,
                    "source" to it.source,
                    "measurement_type" to it.measurementType,
                    "during_sleep" to it.isDuringSleep,
                    "during_exercise" to it.isDuringExercise
                )
            },
            "sleep_records" to bundle.sleepRecords.map {
                mapOf(
                    "start_time" to it.startTime.toString(),
                    "end_time" to it.endTime.toString(),
                    "duration_minutes" to it.totalDurationMinutes,
                    "awake_minutes" to it.awakeDurationMinutes,
                    "light_minutes" to it.lightDurationMinutes,
                    "deep_minutes" to it.deepDurationMinutes,
                    "rem_minutes" to it.remDurationMinutes,
                    "sleep_score" to it.sleepScore,
                    "average_overnight_hr" to it.averageOvernightHr,
                    "average_overnight_hrv" to it.averageOvernightHrv,
                    "spo2_min" to it.spo2Min,
                    "respiration_rate_avg" to it.respirationRateAvg
                )
            },
            "sleep_stage_records" to bundle.sleepStageRecords.map {
                mapOf(
                    "start_time" to it.startTime.toString(),
                    "end_time" to it.endTime.toString(),
                    "stage" to it.stage
                )
            },
            "workout_records" to bundle.workoutRecords.map {
                mapOf(
                    "workout_id" to it.workoutId,
                    "activity_type" to it.activityType,
                    "start_time" to it.startTime.toString(),
                    "end_time" to it.endTime.toString(),
                    "duration_seconds" to it.durationSeconds,
                    "distance_meters" to it.distanceMeters,
                    "calories" to it.calories,
                    "average_hr" to it.averageHr,
                    "max_hr" to it.maxHr,
                    "training_load" to it.trainingLoad,
                    "aerobic_training_effect" to it.aerobicTrainingEffect,
                    "anaerobic_training_effect" to it.anaerobicTrainingEffect,
                    "speed_kph" to it.speedKph,
                    "cadence" to it.cadence,
                    "power_avg" to it.powerAvg
                )
            },
            "workout_hr_series" to bundle.workoutHrSeries.map {
                mapOf(
                    "workout_id" to it.workoutId,
                    "timestamp" to it.timestamp.toString(),
                    "heart_rate_bpm" to it.heartRate,
                    "speed" to it.speed,
                    "cadence" to it.cadence,
                    "power" to it.power
                )
            },
            "energy_records" to bundle.energyRecords.map {
                mapOf("timestamp" to it.timestamp.toString(), "score" to it.score)
            },
            "body_composition_records" to bundle.bodyCompositionRecords.map {
                mapOf(
                    "timestamp" to it.timestamp.toString(),
                    "weight_kg" to it.weight,
                    "bmi" to it.bmi,
                    "body_fat_percent" to it.bodyFat,
                    "muscle_mass_kg" to it.muscleMass,
                    "body_water" to it.bodyWater,
                    "basal_metabolic_rate_kcal" to it.bmr
                )
            }
            // GPS workout routes are intentionally excluded from cloud sync by default.
        )

        val body = gson.toJson(payload).toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        Thread {
            try {
                Log.d("CloudSync", "Uploading health snapshot to configured endpoint")
                client.newCall(request).execute().use { response ->
                    Log.d("CloudSync", "Cloud sync response: ${response.code}")
                    onResult(response.isSuccessful)
                }
            } catch (e: Exception) {
                Log.e("CloudSync", "Sync failed", e)
                onResult(false)
            }
        }.start()
    }
}
