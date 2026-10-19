package com.example.samsunghealthcompanion.nutrition

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.samsunghealthcompanion.data.db.entities.FoodEntry
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodTrackerScreen(
    viewModel: FoodTrackerViewModel,
    onClose: () -> Unit,
    onSyncToSamsung: (FoodEntry) -> Unit
) {
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsState()
    var draft by remember { mutableStateOf<FoodEntry?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempCameraUri?.let { uri -> viewModel.createPhotoDraft(uri) { draft = it } }
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.createPhotoDraft(it) { draft = it } }
    }

    val totals = remember(entries) {
        MacroTotals(
            entries.sumOf { it.calories.toDouble() }.toFloat(),
            entries.sumOf { it.proteinGrams.toDouble() }.toFloat(),
            entries.sumOf { it.carbohydrateGrams.toDouble() }.toFloat(),
            entries.sumOf { it.fatGrams.toDouble() }.toFloat()
        )
    }

    fun launchCamera() {
        val tempFile = File.createTempFile("meal_photo_", ".jpg", context.cacheDir).apply {
            deleteOnExit()
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        tempCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Food Diary & Calorie Tracker") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, "Back to dashboard")
                    }
                },
                actions = {
                    IconButton(onClick = { showApiKeyDialog = true }) {
                        Icon(Icons.Default.Key, contentDescription = "Gemini API Key")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = { launchCamera() },
                    icon = { Icon(Icons.Default.PhotoCamera, "Snap meal photo") },
                    text = { Text("Snap Food Photo") },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
                SmallFloatingActionButton(
                    onClick = { galleryPickerLauncher.launch("image/*") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.PhotoLibrary, "Choose meal photo from gallery")
                }
                FloatingActionButton(onClick = {
                    draft = FoodEntry(title = "", mealType = "SNACK", eatenAt = Instant.now(), calories = 0f)
                }) {
                    Icon(Icons.Default.Add, "Add manual food entry")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    DateNavigator(
                        selectedDate = viewModel.selectedDate,
                        onPrevDay = { viewModel.selectPreviousDay() },
                        onNextDay = { viewModel.selectNextDay() },
                        onToday = { viewModel.selectToday() }
                    )
                }

                item { DailyTotals(totals) }

                item {
                    QuickTextInputCard(
                        onAnalyzeText = { text ->
                            viewModel.createTextDraft(text) { draft = it }
                        }
                    )
                }

                if (viewModel.geminiApiKey.isBlank()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            onClick = { showApiKeyDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Enable Gemini AI Food Detection", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text("Tap to enter your Gemini API key from Google AI Studio to detect meals from photos automatically.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                item {
                    val dateLabel = if (viewModel.selectedDate == LocalDate.now()) "Today's Meals"
                    else viewModel.selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
                    Text(dateLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                if (entries.isEmpty()) item { Text("No food logged for this date.") }

                items(entries, key = { it.id }) { entry ->
                    FoodEntryCard(
                        entry,
                        onDelete = { viewModel.delete(entry) },
                        onSync = { onSyncToSamsung(entry) }
                    )
                }
            }

            if (viewModel.isAnalyzing) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Analyzing meal with Gemini AI...",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Detecting meal type, calories, and macronutrients",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showApiKeyDialog) {
        GeminiApiKeyDialog(
            currentKey = viewModel.geminiApiKey,
            onDismiss = { showApiKeyDialog = false },
            onSave = { key ->
                viewModel.updateGeminiApiKey(key)
                showApiKeyDialog = false
            }
        )
    }

    draft?.let { entry ->
        FoodEntryDialog(
            entry,
            onDismiss = { draft = null },
            onSave = { saved ->
                viewModel.save(saved)
                onSyncToSamsung(saved)
                draft = null
            }
        )
    }
}

@Composable
private fun QuickTextInputCard(
    onAnalyzeText: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "✍️ Describe Food by Text",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("e.g. 2 slices of pepperoni pizza and a coke") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onAnalyzeText(textInput)
                        textInput = ""
                    }
                },
                enabled = textInput.isNotBlank(),
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Analyze with Gemini")
            }
        }
    }
}

@Composable
private fun DateNavigator(
    selectedDate: LocalDate,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
    val isToday = selectedDate == LocalDate.now()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevDay) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onToday() }
            ) {
                Text(
                    text = if (isToday) "Today" else selectedDate.format(DateTimeFormatter.ofPattern("EEEE")),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = selectedDate.format(formatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onNextDay) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
            }
        }
    }
}

@Composable
private fun GeminiApiKeyDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var keyText by remember { mutableStateOf(currentKey) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini AI Configuration") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter your Google Gemini API key to enable instant photo meal recognition.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "You can get a free API key at aistudio.google.com.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(keyText) }) {
                Text("Save Key")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private data class MacroTotals(val calories: Float, val protein: Float, val carbs: Float, val fat: Float)

@Composable
private fun DailyTotals(totals: MacroTotals) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
    Column(Modifier.padding(16.dp)) {
        Text("Daily total", style = MaterialTheme.typography.labelLarge)
        Text("${totals.calories.toInt()} kcal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Protein ${totals.protein.toInt()}g  •  Carbs ${totals.carbs.toInt()}g  •  Fat ${totals.fat.toInt()}g")
    }
}

@Composable
private fun FoodEntryCard(entry: FoodEntry, onDelete: () -> Unit, onSync: () -> Unit) = Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(entry.title, fontWeight = FontWeight.SemiBold)
            Text("${entry.mealType.lowercase().replaceFirstChar { it.titlecase() } } • ${entry.calories.toInt()} kcal")
            Text("P ${entry.proteinGrams.toInt()}g · C ${entry.carbohydrateGrams.toInt()}g · F ${entry.fatGrams.toInt()}g", style = MaterialTheme.typography.bodySmall)
            if (entry.source == FoodEntry.Source.PHOTO_ESTIMATE.name) {
                val confText = entry.confidence?.let { " (${(it * 100).toInt()}% confidence)" } ?: ""
                Text("✨ Gemini AI estimate$confText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (entry.syncState != FoodEntry.SyncState.SYNCED.name) TextButton(onClick = onSync) { Text("Sync") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete ${entry.title}") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodEntryDialog(initial: FoodEntry, onDismiss: () -> Unit, onSave: (FoodEntry) -> Unit) {
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var calories by remember(initial.id) { mutableStateOf(initial.calories.toString()) }
    var protein by remember(initial.id) { mutableStateOf(initial.proteinGrams.toString()) }
    var carbs by remember(initial.id) { mutableStateOf(initial.carbohydrateGrams.toString()) }
    var fat by remember(initial.id) { mutableStateOf(initial.fatGrams.toString()) }
    var meal by remember(initial.id) { mutableStateOf(initial.mealType) }
    var mealExpanded by remember { mutableStateOf(false) }
    val meals = listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.source == FoodEntry.Source.PHOTO_ESTIMATE.name) "Review Gemini AI Estimate" else "Add Food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initial.source == FoodEntry.Source.PHOTO_ESTIMATE.name) {
                    Text("Gemini analyzed your entry. Review or edit the values before saving.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(title, { title = it }, label = { Text("Food name") }, modifier = Modifier.fillMaxWidth())
                ExposedDropdownMenuBox(expanded = mealExpanded, onExpandedChange = { mealExpanded = it }) {
                    OutlinedTextField(
                        value = meal,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Meal") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mealExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = mealExpanded, onDismissRequest = { mealExpanded = false }) {
                        meals.forEach { choice ->
                            DropdownMenuItem(
                                text = { Text(choice.lowercase().replaceFirstChar { it.titlecase() }) },
                                onClick = { meal = choice; mealExpanded = false }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Calories", calories, { calories = it }, Modifier.weight(1f))
                    NumberField("Protein g", protein, { protein = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Carbs g", carbs, { carbs = it }, Modifier.weight(1f))
                    NumberField("Fat g", fat, { fat = it }, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            title = title.trim(),
                            mealType = meal,
                            calories = calories.toFloatOrNull() ?: 0f,
                            proteinGrams = protein.toFloatOrNull() ?: 0f,
                            carbohydrateGrams = carbs.toFloatOrNull() ?: 0f,
                            fatGrams = fat.toFloatOrNull() ?: 0f,
                            updatedAt = Instant.now()
                        )
                    )
                },
                enabled = title.isNotBlank() && (calories.toFloatOrNull() ?: -1f) >= 0f
            ) { Text("Save") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) =
    OutlinedTextField(value, { onValueChange(it.filter { char -> char.isDigit() || char == '.' }) }, label = { Text(label) }, modifier = modifier)
