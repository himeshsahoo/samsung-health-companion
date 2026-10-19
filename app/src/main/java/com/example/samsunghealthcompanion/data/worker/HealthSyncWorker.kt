package com.example.samsunghealthcompanion.data.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.samsunghealthcompanion.SamsungHealthReader
import com.example.samsunghealthcompanion.data.cloud.CloudSyncManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class HealthSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
        val url = prefs.getString("url", "") ?: ""
        val key = prefs.getString("key", "") ?: ""

        if (url.isEmpty()) return@withContext Result.failure()

        Log.d("HealthSyncWorker", "Starting background sync...")

        val success = suspendCoroutine<Boolean> { continuation ->
            SamsungHealthReader.readData(
                context = applicationContext,
                isFullHistory = false,
                onComplete = { bundle ->
                    val cloudSyncManager = CloudSyncManager(Gson())
                    cloudSyncManager.pushToChatGPT(url, key, bundle) { pushSuccess ->
                        continuation.resume(pushSuccess)
                    }
                },
                onError = {
                    Log.e("HealthSyncWorker", "Read error", it)
                    continuation.resume(false)
                }
            )
        }

        if (success) {
            Log.d("HealthSyncWorker", "Background sync successful")
            Result.success()
        } else {
            Log.d("HealthSyncWorker", "Background sync failed")
            Result.retry()
        }
    }
}
