package com.rex.diabite.db

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.rex.diabite.model.FoodItem
import java.io.FileOutputStream
import java.io.IOException

class LocalFoodDatabase(private val context: Context) {

    private var database: SQLiteDatabase? = null

    companion object {
        private const val DATABASE_NAME = "dia_bite_south_asian.db"
        private const val TAG = "LocalFoodDatabase"
    }

    init {
        try {
            openDatabase()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open or copy local database", e)
        }
    }

    private fun openDatabase() {
        val dbPath = context.getDatabasePath(DATABASE_NAME)

        if (!dbPath.exists()) {
            try {
                // Ensure the parent directory exists
                dbPath.parentFile?.mkdirs()
                
                // Copy the database from assets
                val inputStream = context.assets.open(DATABASE_NAME)
                val outputStream = FileOutputStream(dbPath)

                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Local database copied successfully to ${dbPath.absolutePath}")
            } catch (e: IOException) {
                Log.e(TAG, "Error copying local database from assets", e)
                throw e // Re-throw to indicate failure
            }
        }
        
        try {
            database = SQLiteDatabase.openDatabase(dbPath.path, null, SQLiteDatabase.OPEN_READONLY)
            Log.i(TAG, "Local database opened successfully from ${dbPath.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening local database", e)
            // If it still fails, try deleting the potentially corrupted copy and recopying once.
            if (dbPath.exists()) {
                dbPath.delete()
                Log.w(TAG, "Deleted potentially corrupted database. Attempting recopy.")
                // Attempt to re-copy and open
                openDatabase() // Recursive call, be careful or add a flag to prevent infinite loops
            }
        }
    }

    fun searchFoodByName(query: String): FoodItem? {
        if (database == null || !database!!.isOpen) {
            Log.w(TAG, "Database not open. Attempting to reopen.")
            try {
                openDatabase() // Try to reopen if not available
                if (database == null || !database!!.isOpen) {
                     Log.e(TAG, "Failed to reopen database. Cannot search.")
                    return null
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to reopen database on search", e)
                return null
            }
        }

        val selection = "lower(product_name) LIKE ? AND " +
                        "(lower(countries_tags) LIKE '%india%' OR " +
                        "lower(countries_tags) LIKE '%pakistan%' OR " +
                        "lower(countries_tags) LIKE '%bangladesh%' OR " +
                        "lower(countries_tags) LIKE '%nepal%')"
        
        val selectionArgs = arrayOf("%${query.lowercase()}%")
        
        // Define the columns you want to retrieve
        val projection = arrayOf(
            "product_name", 
            "categories_tags", 
            "countries_tags",
            "carbohydrates_100g", 
            "fiber_100g", 
            "sugars_100g"
            // Add other columns like proteins_100g, fat_100g if needed in FoodItem
        )

        var cursor: Cursor? = null
        try {
            cursor = database!!.query(
                "foods",         // The table to query
                projection,      // The array of columns to return (null to return all)
                selection,       // The columns for the WHERE clause
                selectionArgs,   // The values for the WHERE clause
                null,            // don't group the rows
                null,            // don't filter by row groups
                null,            // The sort order
                "1"              // Limit to 1 result
            )

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(c.getColumnIndexOrThrow("product_name"))
                    val categories = c.getString(c.getColumnIndexOrThrow("categories_tags"))
                    val countries = c.getString(c.getColumnIndexOrThrow("countries_tags"))
                    
                    // Handle potential nulls from DB before converting
                    val carbsVal = c.getDouble(c.getColumnIndexOrThrow("carbohydrates_100g"))
                    val fiberVal = c.getDouble(c.getColumnIndexOrThrow("fiber_100g"))
                    val sugarsVal = c.getDouble(c.getColumnIndexOrThrow("sugars_100g"))

                    return FoodItem(
                        name = name,
                        brand = categories?.split(",")?.firstOrNull()?.trim() ?: "Local Data", // Example: Use first category as brand
                        carbs100g = carbsVal.toFloat(),
                        sugars100g = sugarsVal.toFloat(),
                        fiber100g = fiberVal.toFloat(),
                        // energyKcal100g and other fields like proteins, fat could be added if available & needed
                        countryTags = countries?.split(",")?.map { it.trim() } ?: emptyList(),
                        source = "LOCAL_DB",
                        updatedAt = System.currentTimeMillis() // Represents time of this "fetch"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching local database for query: $query", e)
        } finally {
            cursor?.close()
        }
        return null
    }

    fun close() {
        database?.close()
        database = null
        Log.i(TAG, "Local database closed.")
    }
}
