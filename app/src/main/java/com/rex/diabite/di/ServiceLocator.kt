package com.rex.diabite.di

import android.content.Context
import com.rex.diabite.db.AppDatabase
import com.rex.diabite.repository.FoodRepository

object ServiceLocator {
    private var database: AppDatabase? = null
    private var foodRepository: FoodRepository? = null

    fun provideDatabase(context: Context): AppDatabase {
        synchronized(this) {
            return database ?: AppDatabase.getDatabase(context.applicationContext).also { database = it }
        }
    }

    fun provideFoodRepository(context: Context): FoodRepository {
        synchronized(this) {
            // Corrected to pass context as the first argument to FoodRepository.getInstance
            return foodRepository ?: FoodRepository.getInstance(
                context.applicationContext, // Pass the application context
                provideDatabase(context.applicationContext) // Pass the database instance
            ).also { foodRepository = it }
        }
    }

    // Call this when the application is shutting down to close resources
    fun onAppTerminate() {
        synchronized(this) {
            FoodRepository.closeLocalDatabase() // Close local SQLite DB
            database?.close() // Close Room DB
            database = null
            foodRepository = null
        }
    }

    // Optional: for testing or specific reset scenarios
    fun reset() {
        synchronized(this) {
            onAppTerminate() // Ensure resources are closed before resetting
        }
    }
}