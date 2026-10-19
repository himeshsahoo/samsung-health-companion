package com.example.samsunghealthcompanion

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.samsunghealthcompanion.data.FoodRepository
import com.example.samsunghealthcompanion.data.HealthRepository
import com.example.samsunghealthcompanion.data.cloud.CloudSyncManager
import com.example.samsunghealthcompanion.data.db.AppDatabase
import com.example.samsunghealthcompanion.data.db.entities.FoodEntry
import com.example.samsunghealthcompanion.domain.usecase.BodyBatteryCalculator
import com.example.samsunghealthcompanion.domain.usecase.FitnessMetricsCalculator
import com.example.samsunghealthcompanion.domain.usecase.HRVStatusCalculator
import com.example.samsunghealthcompanion.export.DataExporter
import com.example.samsunghealthcompanion.nutrition.FoodTrackerScreen
import com.example.samsunghealthcompanion.nutrition.FoodTrackerViewModel
import com.example.samsunghealthcompanion.nutrition.GeminiPhotoFoodEstimator
import com.example.samsunghealthcompanion.nutrition.SamsungNutritionWriter
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    private lateinit var syncManager: SyncManager
    private lateinit var repository: HealthRepository
    private lateinit var foodRepository: FoodRepository
    private lateinit var exporter: DataExporter
    private lateinit var cloudSyncManager: CloudSyncManager
    private lateinit var foodTrackerViewModel: FoodTrackerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val db = AppDatabase.getDatabase(this)
        repository = HealthRepository(db.healthDao())
        foodRepository = FoodRepository(db.foodDao())
        
        val prefs = getSharedPreferences("health_companion_prefs", MODE_PRIVATE)
        val geminiEstimator = GeminiPhotoFoodEstimator(
            context = this,
            apiKeyProvider = { prefs.getString("gemini_api_key", null) }
        )
        
        foodTrackerViewModel = FoodTrackerViewModel(
            repository = foodRepository,
            estimator = geminiEstimator,
            onGetGeminiKey = { prefs.getString("gemini_api_key", "") },
            onSaveGeminiKey = { key -> prefs.edit().putString("gemini_api_key", key).apply() }
        )
        exporter = DataExporter(this)
        cloudSyncManager = CloudSyncManager(Gson())
        syncManager = SyncManager(
            repository,
            FitnessMetricsCalculator(),
            BodyBatteryCalculator(),
            HRVStatusCalculator()
        )

        setContent {
            HealthCompanionTheme {
                var showFoodDiary by remember { mutableStateOf(false) }
                if (showFoodDiary) {
                    FoodTrackerScreen(
                        foodTrackerViewModel,
                        onClose = { showFoodDiary = false },
                        onSyncToSamsung = ::syncFoodEntryToSamsung,
                    )
                } else {
                    MainDashboard(onOpenFoodDiary = { showFoodDiary = true })
                }
            }
        }
    }

    private fun syncFoodEntryToSamsung(entry: FoodEntry) {
        SamsungHealthPermissions.requestNutritionWrite(this, onGranted = { granted ->
            if (!granted) {
                foodTrackerViewModel.save(entry.copy(syncState = FoodEntry.SyncState.NOT_AUTHORIZED.name))
                Toast.makeText(this, "Samsung Health nutrition permission was not granted.", Toast.LENGTH_LONG).show()
                return@requestNutritionWrite
            }
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { SamsungNutritionWriter(this@MainActivity).insert(entry) }
                    .onSuccess {
                        foodTrackerViewModel.save(entry.copy(syncState = FoodEntry.SyncState.SYNCED.name))
                        runOnUiThread { Toast.makeText(this@MainActivity, "Meal saved to Samsung Health.", Toast.LENGTH_SHORT).show() }
                    }
                    .onFailure { error ->
                        val is2003 = error.message?.contains("2003") == true
                        foodTrackerViewModel.save(entry.copy(syncState = if (is2003) "LOCAL_SAVED" else FoodEntry.SyncState.FAILED.name))
                        val msg = if (is2003) "Meal saved in Food Diary!" else "Could not sync meal: ${error.message ?: "unknown error"}"
                        runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
                    }
            }
        }, onError = { error ->
            val is2003 = error.message?.contains("2003") == true
            foodTrackerViewModel.save(entry.copy(syncState = if (is2003) "LOCAL_SAVED" else FoodEntry.SyncState.FAILED.name))
            val msg = if (is2003) "Meal saved in Food Diary!" else "Samsung Health permission error: ${error.message ?: "unknown error"}"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        })
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainDashboard(onOpenFoodDiary: () -> Unit) {
        var statusMsg by remember { mutableStateOf("Ready to sync") }
        val weeklyCalorieData = remember { mutableStateListOf<DayCalorieData>() }
        
        // Observe food & workout records for last 7 days
        LaunchedEffect(Unit) {
            val now = Instant.now()
            val sevenDaysAgo = now.minus(7, ChronoUnit.DAYS)
            
            combine(
                foodRepository.observeSince(sevenDaysAgo),
                repository.getWorkoutRecords(sevenDaysAgo),
                repository.userProfile
            ) { foodEntries, workoutRecords, userProfile ->
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now()
                val daysList = (0..6).map { today.minusDays((6 - it).toLong()) }
                
                val bmr = userProfile?.weightKg?.let { w ->
                    val h = userProfile.heightCm ?: 170f
                    (10 * w + 6.25f * h - 5 * 30 + 5).toFloat().coerceIn(1200f, 2500f)
                } ?: 1800f

                daysList.map { date ->
                    val dayStart = date.atStartOfDay(zone).toInstant()
                    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()

                    val daysFood = foodEntries.filter { it.eatenAt >= dayStart && it.eatenAt < dayEnd }
                    val daysWorkouts = workoutRecords.filter { it.startTime >= dayStart && it.startTime < dayEnd }

                    val intake = daysFood.sumOf { it.calories.toDouble() }.toFloat()
                    val protein = daysFood.sumOf { it.proteinGrams.toDouble() }.toFloat()
                    val carbs = daysFood.sumOf { it.carbohydrateGrams.toDouble() }.toFloat()
                    val fat = daysFood.sumOf { it.fatGrams.toDouble() }.toFloat()

                    val workoutBurn = daysWorkouts.sumOf { (it.calories ?: 0f).toDouble() }.toFloat()
                    val totalBurned = bmr + workoutBurn

                    DayCalorieData(date, intake, totalBurned, protein, carbs, fat)
                }
            }.collectLatest { dataList ->
                weeklyCalorieData.clear()
                weeklyCalorieData.addAll(dataList)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Calorie & Nutrition Companion", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Info
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Status", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(statusMsg, fontSize = 16.sp)
                        }
                    }
                }

                // Action Buttons
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { performSync { statusMsg = it } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync")
                        }
                        OutlinedButton(
                            onClick = { exportRawData { statusMsg = it } },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export")
                        }
                    }
                }

                item {
                    Button(
                        onClick = onOpenFoodDiary,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Restaurant, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Food Diary & Camera")
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { liveSyncToChatGPT { statusMsg = it } },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Live ChatGPT / MCP Sync")
                    }
                }

                // 7-Day Calorie Balance Graph
                item {
                    CalorieBalanceChart(weeklyCalorieData)
                }

                // 7-Day Summary Stats
                item {
                    WeeklySummaryCard(weeklyCalorieData)
                }

                // 7-Day Macros Breakdown
                item {
                    WeeklyMacrosCard(weeklyCalorieData)
                }
            }
        }
    }

    @Composable
    fun CalorieBalanceChart(data: List<DayCalorieData>) {
        val maxVal = remember(data) {
            (data.flatMap { listOf(it.intakeKcal, it.burnedKcal) }.maxOrNull() ?: 2000f).coerceAtLeast(1000f)
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Last 7 Days Calorie Balance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                // Legend
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFF2E7D32), RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Intake (kcal)", style = MaterialTheme.typography.labelSmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFFE65100), RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Burned (kcal)", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bars Row
                Row(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    data.forEach { day ->
                        val intakeHeightPct = (day.intakeKcal / maxVal).coerceIn(0.05f, 1f)
                        val burnedHeightPct = (day.burnedKcal / maxVal).coerceIn(0.05f, 1f)
                        val dayLabel = if (day.date == LocalDate.now()) "Today" else day.date.format(DateTimeFormatter.ofPattern("EEE"))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(intakeHeightPct)
                                        .background(Color(0xFF2E7D32), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(burnedHeightPct)
                                        .background(Color(0xFFE65100), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(dayLabel, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun WeeklySummaryCard(data: List<DayCalorieData>) {
        val avgIntake = if (data.isNotEmpty()) data.map { it.intakeKcal }.average().toInt() else 0
        val avgBurned = if (data.isNotEmpty()) data.map { it.burnedKcal }.average().toInt() else 0
        val netDiff = avgIntake - avgBurned
        val statusText = if (netDiff >= 0) "Net Surplus: +$netDiff kcal/day" else "Net Deficit: $netDiff kcal/day"
        val statusColor = if (netDiff <= 0) Color(0xFF2E7D32) else Color(0xFFC62828)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("7-Day Daily Averages", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Avg Intake", style = MaterialTheme.typography.bodySmall)
                        Text("$avgIntake kcal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    }
                    Column {
                        Text("Avg Burned", style = MaterialTheme.typography.bodySmall)
                        Text("$avgBurned kcal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }

    @Composable
    fun WeeklyMacrosCard(data: List<DayCalorieData>) {
        val avgProtein = if (data.isNotEmpty()) data.map { it.proteinGrams }.average().toInt() else 0
        val avgCarbs = if (data.isNotEmpty()) data.map { it.carbGrams }.average().toInt() else 0
        val avgFat = if (data.isNotEmpty()) data.map { it.fatGrams }.average().toInt() else 0

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Average Daily Macronutrients", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MacroStatBox("Protein", "${avgProtein}g", Color(0xFF1E88E5))
                    MacroStatBox("Carbs", "${avgCarbs}g", Color(0xFF43A047))
                    MacroStatBox("Fat", "${avgFat}g", Color(0xFFFB8C00))
                }
            }
        }
    }

    @Composable
    private fun MacroStatBox(label: String, value: String, color: Color) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    private fun performSync(updateStatus: (String) -> Unit) {
        updateStatus("Syncing...")
        SamsungHealthPermissions.request(this, { granted ->
            if (granted) {
                syncManager.sync(this, {
                    updateStatus("Sync complete!")
                }, { error ->
                    updateStatus("Sync failed: ${error.message}")
                })
            } else {
                updateStatus("Access denied")
            }
        }, { error ->
            updateStatus("Error: ${error.message}")
        })
    }

    private fun exportRawData(updateStatus: (String) -> Unit) {
        updateStatus("Preparing ZIP...")
        SamsungHealthPermissions.request(this, { granted ->
            if (granted) {
                SamsungHealthReader.readData(this, isFullHistory = true, onComplete = { bundle ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val uri = exporter.exportToZip(bundle)
                            launch(Dispatchers.Main) {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Health Raw Data")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(Intent.createChooser(shareIntent, "Export Data"))
                                updateStatus("Export ready!")
                            }
                        } catch (e: Exception) {
                            launch(Dispatchers.Main) { updateStatus("Export failed") }
                        }
                    }
                }, onError = { updateStatus("Read error") })
            } else {
                updateStatus("Access denied")
            }
        }, { updateStatus("Error") })
    }

    private fun liveSyncToChatGPT(updateStatus: (String) -> Unit) {
        val prefs = getSharedPreferences("cloud_sync", MODE_PRIVATE)
        val url = prefs.getString("url", "") ?: ""
        val key = prefs.getString("key", "") ?: ""
        
        if (url.isEmpty()) {
            updateStatus("Live Sync not configured")
            return
        }

        updateStatus("Pushing live update...")
        SamsungHealthReader.readData(this, isFullHistory = false, onComplete = { bundle ->
            cloudSyncManager.pushToChatGPT(url, key, bundle) { success ->
                runOnUiThread {
                    updateStatus(if (success) "Live sync success!" else "Live sync failed")
                }
            }
        }, onError = { updateStatus("Sync error") })
    }
}

data class DayCalorieData(
    val date: LocalDate,
    val intakeKcal: Float,
    val burnedKcal: Float,
    val proteinGrams: Float,
    val carbGrams: Float,
    val fatGrams: Float
)

@Composable
fun HealthCompanionTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = Color(0xFF006D32), // Deep Forest Green
        onPrimary = Color.White,
        primaryContainer = Color(0xFF96F7B1),
        onPrimaryContainer = Color(0xFF00210A),
        secondary = Color(0xFF4F6352),
        onSecondary = Color.White,
        background = Color(0xFFFBFDF8),
        surface = Color(0xFFFBFDF8),
        onSurface = Color(0xFF191C19),
        tertiary = Color(0xFF3B6470) // Slate Blue for cloud
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
