package com.example.samsunghealthcompanion.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.example.samsunghealthcompanion.data.db.entities.FoodEntry
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_entries WHERE eatenAt >= :start AND eatenAt < :end ORDER BY eatenAt DESC")
    fun observeBetween(start: Instant, end: Instant): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entries WHERE eatenAt >= :since ORDER BY eatenAt DESC")
    fun observeSince(since: Instant): Flow<List<FoodEntry>>

    @Upsert
    suspend fun upsert(entry: FoodEntry)

    @Delete
    suspend fun delete(entry: FoodEntry)
}
