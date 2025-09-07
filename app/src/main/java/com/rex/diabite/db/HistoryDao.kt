package com.rex.diabite.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rex.diabite.data.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY isFavorite DESC, createdAt DESC") // Favorites will be on top, then by time
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteHistory(): Flow<List<HistoryEntity>> // New method for favorites

    @Insert(onConflict = OnConflictStrategy.REPLACE) 
    suspend fun insertHistory(history: HistoryEntity)

    @Query("SELECT * FROM history WHERE lower(queryText) = :queryText AND diabetesType = :diabetesType LIMIT 1")
    suspend fun findHistoryItem(queryText: String, diabetesType: String): HistoryEntity?

    @Query("UPDATE history SET displayName = :displayName, createdAt = :timestamp WHERE id = :id AND diabetesType = :diabetesType")
    suspend fun updateHistoryItem(id: Long, displayName: String, timestamp: Long, diabetesType: String)
    
    @Query("UPDATE history SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavoriteStatus(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun getCount(): Int

    @Query("DELETE FROM history WHERE rowid IN (SELECT rowid FROM history ORDER BY createdAt ASC LIMIT :count)")
    suspend fun deleteOldest(count: Int)
}