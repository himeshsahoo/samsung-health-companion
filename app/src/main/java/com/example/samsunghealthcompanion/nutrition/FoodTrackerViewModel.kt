package com.example.samsunghealthcompanion.nutrition

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.samsunghealthcompanion.data.FoodRepository
import com.example.samsunghealthcompanion.data.db.entities.FoodEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class FoodTrackerViewModel(
    private val repository: FoodRepository,
    private val estimator: PhotoFoodEstimator = ReviewRequiredPhotoEstimator(),
    private val onGetGeminiKey: (() -> String?)? = null,
    private val onSaveGeminiKey: ((String) -> Unit)? = null,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()

    var selectedDate by mutableStateOf(LocalDate.now())
        private set

    private val selectedDateFlow = MutableStateFlow(selectedDate)

    val entries: StateFlow<List<FoodEntry>> = selectedDateFlow
        .flatMapLatest { date ->
            val dayStart = date.atStartOfDay(zone).toInstant()
            val nextDayStart = date.plusDays(1).atStartOfDay(zone).toInstant()
            repository.observeBetween(dayStart, nextDayStart)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var isAnalyzing by mutableStateOf(false)
        private set

    var geminiApiKey by mutableStateOf(onGetGeminiKey?.invoke() ?: "")
        private set

    fun selectDate(date: LocalDate) {
        selectedDate = date
        selectedDateFlow.value = date
    }

    fun selectPreviousDay() {
        selectDate(selectedDate.minusDays(1))
    }

    fun selectNextDay() {
        selectDate(selectedDate.plusDays(1))
    }

    fun selectToday() {
        selectDate(LocalDate.now())
    }

    fun updateGeminiApiKey(newKey: String) {
        geminiApiKey = newKey.trim()
        onSaveGeminiKey?.invoke(geminiApiKey)
    }

    fun save(entry: FoodEntry) = viewModelScope.launch { repository.save(entry) }
    fun delete(entry: FoodEntry) = viewModelScope.launch { repository.delete(entry) }

    fun createPhotoDraft(uri: Uri, onReady: (FoodEntry) -> Unit) = viewModelScope.launch {
        isAnalyzing = true
        try {
            val estimate = estimator.estimate(uri)
            val mealType = estimate.mealType ?: "SNACK"
            val eatenInstant = if (selectedDate == LocalDate.now()) Instant.now() else selectedDate.atStartOfDay(zone).toInstant()
            onReady(
                FoodEntry(
                    title = estimate.suggestedTitle,
                    mealType = mealType,
                    eatenAt = eatenInstant,
                    calories = estimate.calories,
                    proteinGrams = estimate.proteinGrams,
                    carbohydrateGrams = estimate.carbohydrateGrams,
                    fatGrams = estimate.fatGrams,
                    photoUri = uri.toString(),
                    source = FoodEntry.Source.PHOTO_ESTIMATE.name,
                    confidence = estimate.confidence,
                )
            )
        } finally {
            isAnalyzing = false
        }
    }

    fun createTextDraft(textDescription: String, onReady: (FoodEntry) -> Unit) = viewModelScope.launch {
        if (textDescription.isBlank()) return@launch
        isAnalyzing = true
        try {
            val estimate = estimator.estimateText(textDescription)
            val mealType = estimate.mealType ?: "SNACK"
            val eatenInstant = if (selectedDate == LocalDate.now()) Instant.now() else selectedDate.atStartOfDay(zone).toInstant()
            onReady(
                FoodEntry(
                    title = estimate.suggestedTitle,
                    mealType = mealType,
                    eatenAt = eatenInstant,
                    calories = estimate.calories,
                    proteinGrams = estimate.proteinGrams,
                    carbohydrateGrams = estimate.carbohydrateGrams,
                    fatGrams = estimate.fatGrams,
                    source = FoodEntry.Source.PHOTO_ESTIMATE.name,
                    confidence = estimate.confidence,
                )
            )
        } finally {
            isAnalyzing = false
        }
    }
}
