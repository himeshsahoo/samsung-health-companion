package com.example.samsunghealthcompanion.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.samsunghealthcompanion.HealthDataBundle
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DataExporter(private val context: Context) {

    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault())

    fun exportToZip(bundle: HealthDataBundle): Uri {
        val exportDir = File(context.cacheDir, "export")
        if (exportDir.exists()) exportDir.deleteRecursively()
        exportDir.mkdirs()

        val csvFiles = mutableListOf<File>()

        // 1. User Profile
        bundle.userProfile?.let { profile ->
            val file = File(exportDir, "01_profile.csv")
            file.writeText("Attribute,Value,Unit\n" +
                "Gender,${profile.biologicalSex ?: ""},\n" +
                "Height,${profile.heightCm ?: ""},cm\n" +
                "Weight,${profile.weightKg ?: ""},kg\n" +
                "Resting HR,${profile.restingHeartRate ?: ""},bpm\n" +
                "Max HR,${profile.maxHeartRate ?: ""},bpm\n" +
                "VO2 Max,${profile.vo2Max ?: ""},ml/kg/min")
            csvFiles.add(file)
        }

        // 2. Heart Rate
        if (bundle.heartRateRecords.isNotEmpty()) {
            val file = File(exportDir, "02_heart_rate.csv")
            file.bufferedWriter().use { writer ->
                writer.write("Timestamp,BPM,Type,Source\n")
                bundle.heartRateRecords.forEach { 
                    writer.write("${formatter.format(it.timestamp)},${it.bpm},${it.measurementType ?: ""},${it.source ?: ""}\n")
                }
            }
            csvFiles.add(file)
        }

        // 3. Sleep
        if (bundle.sleepRecords.isNotEmpty()) {
            val file = File(exportDir, "05_sleep.csv")
            file.bufferedWriter().use { writer ->
                writer.write("StartTime,EndTime,TotalDurationMin,SleepScore\n")
                bundle.sleepRecords.forEach {
                    writer.write("${formatter.format(it.startTime)},${formatter.format(it.endTime)},${it.totalDurationMinutes},${it.sleepScore ?: ""}\n")
                }
            }
            csvFiles.add(file)
        }

        // 4. Sleep Stages
        if (bundle.sleepStageRecords.isNotEmpty()) {
            val file = File(exportDir, "06_sleep_stages.csv")
            file.bufferedWriter().use { writer ->
                writer.write("StartTime,EndTime,Stage\n")
                bundle.sleepStageRecords.forEach {
                    writer.write("${formatter.format(it.startTime)},${formatter.format(it.endTime)},${it.stage}\n")
                }
            }
            csvFiles.add(file)
        }

        // 5. Workouts
        if (bundle.workoutRecords.isNotEmpty()) {
            val file = File(exportDir, "11_workouts.csv")
            file.bufferedWriter().use { writer ->
                writer.write("WorkoutID,Activity,StartTime,EndTime,DurationSec,DistanceM,Calories,AvgHR,MaxHR,AvgSpeedKPH,AvgPower,AvgCadence\n")
                bundle.workoutRecords.forEach {
                    writer.write("\"${it.workoutId}\",${it.activityType},${formatter.format(it.startTime)},${formatter.format(it.endTime)},${it.durationSeconds},${it.distanceMeters ?: ""},${it.calories ?: ""},${it.averageHr ?: ""},${it.maxHr ?: ""},${it.speedKph ?: ""},${it.powerAvg ?: ""},${it.cadence ?: ""}\n")
                }
            }
            csvFiles.add(file)
        }

        // 6. Workout HR Series
        if (bundle.workoutHrSeries.isNotEmpty()) {
            val file = File(exportDir, "12_workout_hr.csv")
            file.bufferedWriter().use { writer ->
                writer.write("WorkoutID,Timestamp,HR,Speed,Cadence,Power\n")
                bundle.workoutHrSeries.forEach {
                    writer.write("\"${it.workoutId}\",${formatter.format(it.timestamp)},${it.heartRate},${it.speed ?: ""},${it.cadence ?: ""},${it.power ?: ""}\n")
                }
            }
            csvFiles.add(file)
        }

        // 7. Workout GPS Route
        if (bundle.workoutRoute.isNotEmpty()) {
            val file = File(exportDir, "13_gps.csv")
            file.bufferedWriter().use { writer ->
                writer.write("WorkoutID,Timestamp,Latitude,Longitude,Altitude,Accuracy\n")
                bundle.workoutRoute.forEach {
                    writer.write("\"${it.workoutId}\",${formatter.format(it.timestamp)},${it.latitude},${it.longitude},${it.altitude ?: ""},${it.accuracy ?: ""}\n")
                }
            }
            csvFiles.add(file)
        }

        // 8. Blood Oxygen
        if (bundle.bloodOxygenRecords.isNotEmpty()) {
            val file = File(exportDir, "08_spo2.csv")
            file.bufferedWriter().use { writer ->
                writer.write("Timestamp,SpO2,Source\n")
                bundle.bloodOxygenRecords.forEach {
                    writer.write("${formatter.format(it.timestamp)},${it.spo2},${it.source ?: ""}\n")
                }
            }
            csvFiles.add(file)
        }

        // 9. Blood Pressure
        if (bundle.bloodPressureRecords.isNotEmpty()) {
            val file = File(exportDir, "20_blood_pressure.csv")
            file.bufferedWriter().use { writer ->
                writer.write("Timestamp,Systolic,Diastolic,Pulse,Source\n")
                bundle.bloodPressureRecords.forEach {
                    writer.write("${formatter.format(it.timestamp)},${it.systolic},${it.diastolic},${it.pulse ?: ""},${it.source ?: ""}\n")
                }
            }
            csvFiles.add(file)
        }

        // 10. Glucose
        if (bundle.glucoseRecords.isNotEmpty()) {
            val file = File(exportDir, "21_glucose.csv")
            file.bufferedWriter().use { writer ->
                writer.write("Timestamp,Glucose,Source\n")
                bundle.glucoseRecords.forEach {
                    writer.write("${formatter.format(it.timestamp)},${it.glucose},${it.source ?: ""}\n")
                }
            }
            csvFiles.add(file)
        }

        // 11. Temperature
        if (bundle.temperatureRecords.isNotEmpty()) {
            val file = File(exportDir, "22_temperature.csv")
            file.bufferedWriter().use { writer ->
                writer.write("Timestamp,Temperature,Type,Source\n")
                bundle.temperatureRecords.forEach {
                    writer.write("${formatter.format(it.timestamp)},${it.temperature},${it.type},${it.source ?: ""}\n")
                }
            }
            csvFiles.add(file)
        }

        // 12. Energy Score
        if (bundle.energyRecords.isNotEmpty()) {
            val file = File(exportDir, "19_energy.csv")
            file.bufferedWriter().use { writer ->
                writer.write("Timestamp,EnergyScore\n")
                bundle.energyRecords.forEach {
                    writer.write("${formatter.format(it.timestamp)},${it.score}\n")
                }
            }
            csvFiles.add(file)
        }

        // 13. Body Composition (Weight history)
        if (bundle.bodyCompositionRecords.isNotEmpty()) {
            val file = File(exportDir, "10_body_composition.csv")
            file.bufferedWriter().use { writer ->
                writer.write("Timestamp,WeightKg,BMI,BodyFatPercent,MuscleMassKg,BodyWaterPercent,BMR\n")
                bundle.bodyCompositionRecords.forEach {
                    writer.write("${formatter.format(it.timestamp)},${it.weight ?: ""},${it.bmi ?: ""},${it.bodyFat ?: ""},${it.muscleMass ?: ""},${it.bodyWater ?: ""},${it.bmr ?: ""}\n")
                }
            }
            csvFiles.add(file)
        }

        // README
        val readme = File(exportDir, "README.txt")
        val summaryText = """
            Samsung Health Raw Data Export
            Exported at: ${formatter.format(Instant.now())}
            
            Records Summary:
            - Profile: ${if (bundle.userProfile != null) "Yes" else "No"}
            - Heart Rate (Total Samples): ${bundle.heartRateRecords.size}
            - Sleep Sessions: ${bundle.sleepRecords.size}
            - Sleep Stage Transitions: ${bundle.sleepStageRecords.size}
            - Workouts: ${bundle.workoutRecords.size}
            - Workout HR Samples (Second-by-second): ${bundle.workoutHrSeries.size}
            - Workout GPS Points: ${bundle.workoutRoute.size}
            - Weight / Body Composition History: ${bundle.bodyCompositionRecords.size}
            - Blood Oxygen: ${bundle.bloodOxygenRecords.size}
            - Blood Pressure: ${bundle.bloodPressureRecords.size}
            - Glucose: ${bundle.glucoseRecords.size}
            - Temperature (Core & Skin): ${bundle.temperatureRecords.size}
            - Energy Score: ${bundle.energyRecords.size}
            
            MISSING DATA NOTES (Not exposed in SDK 1.1.0):
            - Raw HRV / RR Intervals: Samsung does not currently expose standalone RR intervals in the public Data SDK. 
            - Respiratory Rate: Not available in this SDK version.
            - Stress Level: Not available in this SDK version.
            - ECG Waveform: Not available in this SDK version.
            - Advanced Running Dynamics: Only Cadence and Power are exported; stride length/oscillation are missing.
        """.trimIndent()
        readme.writeText(summaryText)
        csvFiles.add(readme)

        // Create ZIP
        val zipFile = File(context.cacheDir, "SamsungHealth_RawData.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            csvFiles.forEach { file ->
                zos.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }
}
