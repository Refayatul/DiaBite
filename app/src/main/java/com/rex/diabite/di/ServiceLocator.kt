package com.rex.diabite.di

import android.content.Context
import com.rex.diabite.db.AppDatabase
import com.rex.diabite.repository.FoodRepository

object ServiceLocator {
    private var database: AppDatabase? = null
    private var foodRepository: FoodRepository? = null

    fun provideDatabase(context: Context): AppDatabase {
        synchronized(this) {
            return database ?: AppDatabase.getDatabase(context).also { database = it }
        }
    }

    fun provideFoodRepository(context: Context): FoodRepository {
        synchronized(this) {
            return foodRepository ?: FoodRepository.getInstance(
                provideDatabase(context)
            ).also { foodRepository = it }
        }
    }

    fun reset() {
        synchronized(this) {
            database = null
            foodRepository = null
        }
    }
}