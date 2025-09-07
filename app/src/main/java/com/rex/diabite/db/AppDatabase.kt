package com.rex.diabite.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rex.diabite.data.FoodCacheEntity
import com.rex.diabite.data.HistoryEntity
import com.rex.diabite.util.Converters

@Database(
    entities = [FoodCacheEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodCacheDao(): FoodCacheDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "diabite_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}