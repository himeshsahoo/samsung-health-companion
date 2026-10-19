package com.example.samsunghealthcompanion.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.samsunghealthcompanion.data.db.dao.FoodDao
import com.example.samsunghealthcompanion.data.db.dao.HealthDao
import com.example.samsunghealthcompanion.data.db.entities.*

@Database(
    entities = [
        UserProfile::class,
        HeartRateRecord::class,
        HRVRecord::class,
        SleepRecord::class,
        StressRecord::class,
        WorkoutRecord::class,
        CalculatedMetric::class,
        FoodEntry::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao
    abstract fun foodDao(): FoodDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `food_entries` (
                        `id` TEXT NOT NULL, `title` TEXT NOT NULL, `mealType` TEXT NOT NULL,
                        `eatenAt` INTEGER NOT NULL, `calories` REAL NOT NULL,
                        `proteinGrams` REAL NOT NULL, `carbohydrateGrams` REAL NOT NULL,
                        `fatGrams` REAL NOT NULL, `fibreGrams` REAL NOT NULL, `sugarGrams` REAL NOT NULL,
                        `photoUri` TEXT, `source` TEXT NOT NULL, `confidence` REAL,
                        `samsungHealthUid` TEXT, `syncState` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "health_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
