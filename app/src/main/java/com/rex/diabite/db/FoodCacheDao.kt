package com.rex.diabite.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rex.diabite.data.FoodCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodCacheDao {
    @Query("SELECT * FROM food_cache WHERE key = :key")
    suspend fun getFoodByKey(key: String): FoodCacheEntity?

    @Query("SELECT * FROM food_cache WHERE key = :key")
    fun getFoodByKeyFlow(key: String): Flow<FoodCacheEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFood(food: FoodCacheEntity)

    @Query("DELETE FROM food_cache WHERE updatedAt < :timestamp")
    suspend fun deleteExpired(timestamp: Long)

    @Query("SELECT COUNT(*) FROM food_cache")
    suspend fun getCount(): Int

    @Query("DELETE FROM food_cache WHERE rowid IN (SELECT rowid FROM food_cache ORDER BY updatedAt ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}