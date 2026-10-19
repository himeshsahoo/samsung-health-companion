package com.example.samsunghealthcompanion.nutrition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiPhotoFoodEstimator(
    private val context: Context,
    private val apiKeyProvider: () -> String?
) : PhotoFoodEstimator {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    override suspend fun estimate(photo: Uri): FoodEstimate = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return@withContext FoodEstimate(
                suggestedTitle = "Gemini API key missing. Tap key icon to configure.",
                mealType = "SNACK",
                calories = 0f,
                confidence = null
            )
        }

        val base64Image = encodeImageToBase64(photo) ?: return@withContext FoodEstimate(
            suggestedTitle = "Could not read photo from gallery/camera",
            calories = 0f
        )

        val promptText = """
            Analyze this food image and estimate the nutritional content for the meal shown.
            Respond ONLY with a valid JSON object containing these exact fields:
            - "suggestedTitle": short descriptive name of the food/meal (string)
            - "mealType": one of "BREAKFAST", "LUNCH", "DINNER", "SNACK" (string)
            - "calories": total estimated energy in kcal (number)
            - "proteinGrams": total protein in grams (number)
            - "carbohydrateGrams": total carbohydrates in grams (number)
            - "fatGrams": total fat in grams (number)
            - "confidence": confidence level between 0.0 and 1.0 (number)
        """.trimIndent()

        val jsonPayload = JsonObject().apply {
            val contents = JsonArray().apply {
                add(JsonObject().apply {
                    val parts = JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", promptText) })
                        add(JsonObject().apply {
                            add("inlineData", JsonObject().apply {
                                addProperty("mimeType", "image/jpeg")
                                addProperty("data", base64Image)
                            })
                        })
                    }
                    add("parts", parts)
                })
            }
            add("contents", contents)
            add("generationConfig", JsonObject().apply {
                addProperty("responseMimeType", "application/json")
            })
        }

        val candidateModels = listOf(
            "gemini-3.8-flash",
            "gemini-3.5-flash",
            "gemini-flash-latest",
            "gemini-3.1-flash-lite",
            "gemma-4-26b-a4b-it"
        )

        var lastErrorMsg = "Gemini request failed"

        for (modelName in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(jsonPayload).toRequestBody("application/json".toMediaType()))
                .build()

            try {
                Log.d("GeminiEstimator", "Sending request to model: $modelName")
                val response = client.newCall(request).execute()
                val responseStr = response.body?.string() ?: ""
                Log.d("GeminiEstimator", "Response code for $modelName: ${response.code}, body: $responseStr")

                if (response.isSuccessful) {
                    val responseJson = gson.fromJson(responseStr, JsonObject::class.java)
                    var textResult = responseJson
                        ?.getAsJsonArray("candidates")?.get(0)?.asJsonObject
                        ?.getAsJsonObject("content")
                        ?.getAsJsonArray("parts")?.get(0)?.asJsonObject
                        ?.get("text")?.asString

                    if (!textResult.isNullOrBlank()) {
                        textResult = textResult.replace("```json", "").replace("```", "").trim()
                        val parsed = gson.fromJson(textResult, JsonObject::class.java)
                        return@withContext FoodEstimate(
                            suggestedTitle = parsed.get("suggestedTitle")?.asString ?: "Detected Meal",
                            mealType = parsed.get("mealType")?.asString ?: "SNACK",
                            calories = parsed.get("calories")?.asFloat ?: 0f,
                            proteinGrams = parsed.get("proteinGrams")?.asFloat ?: 0f,
                            carbohydrateGrams = parsed.get("carbohydrateGrams")?.asFloat ?: 0f,
                            fatGrams = parsed.get("fatGrams")?.asFloat ?: 0f,
                            confidence = parsed.get("confidence")?.asFloat ?: 0.85f
                        )
                    }
                } else {
                    lastErrorMsg = when (response.code) {
                        400 -> "Invalid API Key or Request format (400)"
                        403 -> "API Key Access Denied / Quota Issue (403)"
                        404 -> "Model $modelName not found (404)"
                        429 -> "Gemini API Rate Limit Exceeded (429)"
                        else -> "Gemini Error (${response.code})"
                    }
                    Log.e("GeminiEstimator", "Error for model $modelName: ${response.code} - $responseStr")
                }
            } catch (e: Exception) {
                Log.e("GeminiEstimator", "Network exception for $modelName", e)
                lastErrorMsg = "Analysis failed: ${e.localizedMessage ?: "Network error"}"
            }
        }

        return@withContext FoodEstimate(lastErrorMsg, calories = 0f)
    }

    override suspend fun estimateText(textDescription: String): FoodEstimate = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isNullOrBlank()) {
            return@withContext FoodEstimate(
                suggestedTitle = "Gemini API key missing. Tap key icon to configure.",
                mealType = "SNACK",
                calories = 0f,
                confidence = null
            )
        }

        val promptText = """
            Analyze this food description and estimate the nutritional content for the meal described: "$textDescription".
            Respond ONLY with a valid JSON object containing these exact fields:
            - "suggestedTitle": short descriptive name of the food/meal (string)
            - "mealType": one of "BREAKFAST", "LUNCH", "DINNER", "SNACK" (string)
            - "calories": total estimated energy in kcal (number)
            - "proteinGrams": total protein in grams (number)
            - "carbohydrateGrams": total carbohydrates in grams (number)
            - "fatGrams": total fat in grams (number)
            - "confidence": confidence level between 0.0 and 1.0 (number)
        """.trimIndent()

        val jsonPayload = JsonObject().apply {
            val contents = JsonArray().apply {
                add(JsonObject().apply {
                    val parts = JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", promptText) })
                    }
                    add("parts", parts)
                })
            }
            add("contents", contents)
            add("generationConfig", JsonObject().apply {
                addProperty("responseMimeType", "application/json")
            })
        }

        val candidateModels = listOf(
            "gemini-3.8-flash",
            "gemini-3.5-flash",
            "gemini-flash-latest",
            "gemini-3.1-flash-lite",
            "gemma-4-26b-a4b-it"
        )

        var lastErrorMsg = "Gemini request failed"

        for (modelName in candidateModels) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(gson.toJson(jsonPayload).toRequestBody("application/json".toMediaType()))
                .build()

            try {
                Log.d("GeminiEstimator", "Sending text request to model: $modelName")
                val response = client.newCall(request).execute()
                val responseStr = response.body?.string() ?: ""
                Log.d("GeminiEstimator", "Text response code for $modelName: ${response.code}")

                if (response.isSuccessful) {
                    val responseJson = gson.fromJson(responseStr, JsonObject::class.java)
                    var textResult = responseJson
                        ?.getAsJsonArray("candidates")?.get(0)?.asJsonObject
                        ?.getAsJsonObject("content")
                        ?.getAsJsonArray("parts")?.get(0)?.asJsonObject
                        ?.get("text")?.asString

                    if (!textResult.isNullOrBlank()) {
                        textResult = textResult.replace("```json", "").replace("```", "").trim()
                        val parsed = gson.fromJson(textResult, JsonObject::class.java)
                        return@withContext FoodEstimate(
                            suggestedTitle = parsed.get("suggestedTitle")?.asString ?: textDescription,
                            mealType = parsed.get("mealType")?.asString ?: "SNACK",
                            calories = parsed.get("calories")?.asFloat ?: 0f,
                            proteinGrams = parsed.get("proteinGrams")?.asFloat ?: 0f,
                            carbohydrateGrams = parsed.get("carbohydrateGrams")?.asFloat ?: 0f,
                            fatGrams = parsed.get("fatGrams")?.asFloat ?: 0f,
                            confidence = parsed.get("confidence")?.asFloat ?: 0.90f
                        )
                    }
                } else {
                    lastErrorMsg = "Gemini Error (${response.code})"
                }
            } catch (e: Exception) {
                Log.e("GeminiEstimator", "Text estimation exception for $modelName", e)
                lastErrorMsg = "Analysis failed: ${e.localizedMessage ?: "Network error"}"
            }
        }

        return@withContext FoodEstimate(lastErrorMsg, calories = 0f)
    }

    private fun encodeImageToBase64(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (bitmap == null) return null

            val maxDimension = 1024
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val scale = maxDimension.toFloat() / Math.max(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val bytes = outputStream.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("GeminiEstimator", "Failed to encode image to base64", e)
            null
        }
    }
}
