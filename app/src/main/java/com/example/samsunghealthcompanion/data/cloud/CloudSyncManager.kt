package com.example.samsunghealthcompanion.data.cloud

import android.util.Log
import com.example.samsunghealthcompanion.HealthDataBundle
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

class CloudSyncManager(private val gson: Gson) {
    private val client = OkHttpClient()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    fun pushToChatGPT(url: String, apiKey: String, bundle: HealthDataBundle, onResult: (Boolean) -> Unit) {
        val payload = mapOf(
            "timestamp" to Instant.now().toString(),
            "api_key" to apiKey,
            "summary" to mapOf(
                "hr_latest" to bundle.heartRateRecords.maxByOrNull { it.timestamp }?.bpm,
                "energy_score" to bundle.energyRecords.maxByOrNull { it.timestamp }?.score,
                "sleep_score" to bundle.sleepRecords.firstOrNull()?.sleepScore,
                "workout_count" to bundle.workoutRecords.size
            )
        )

        val body = gson.toJson(payload).toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body)
            .build()

        Thread {
            try {
                Log.d("CloudSync", "Sending payload to $url")
                client.newCall(request).execute().use { response ->
                    Log.d("CloudSync", "Response: ${response.code} - ${response.message}")
                    onResult(response.isSuccessful)
                }
            } catch (e: Exception) {
                Log.e("CloudSync", "Sync failed", e)
                onResult(false)
            }
        }.start()
    }
}
