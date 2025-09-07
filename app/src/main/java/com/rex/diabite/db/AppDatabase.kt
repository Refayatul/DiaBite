package com.rex.diabite.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rex.diabite.data.FoodCacheEntity
import com.rex.diabite.data.HistoryEntity
import com.rex.diabite.util.Converters

@Database(
    entities = [FoodCacheEntity::class, HistoryEntity::class],
    version = 2, // Incremented version from 1 to 2
    exportSchema = true // Recommended to set to true for schema validation
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodCacheDao(): FoodCacheDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2: Add isFavorite column to history table
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "diabite_database"
                )
                .addMigrations(MIGRATION_1_2) // Added migration
                // .fallbackToDestructiveMigration() // Use this only if you don't want to write migrations during dev
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}