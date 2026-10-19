package com.example.samsunghealthcompanion.data

import com.example.samsunghealthcompanion.data.db.dao.FoodDao
import com.example.samsunghealthcompanion.data.db.entities.FoodEntry
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class FoodRepository(private val foodDao: FoodDao) {
    fun observeBetween(start: Instant, end: Instant): Flow<List<FoodEntry>> =
        foodDao.observeBetween(start, end)

    fun observeSince(since: Instant): Flow<List<FoodEntry>> =
        foodDao.observeSince(since)

    suspend fun save(entry: FoodEntry) = foodDao.upsert(entry)
    suspend fun delete(entry: FoodEntry) = foodDao.delete(entry)
}
