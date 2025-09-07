package com.rex.diabite.util

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromCountryTagsList(value: List<String>?): String? {
        return value?.joinToString(",")
    }

    @TypeConverter
    fun toCountryTagsList(value: String?): List<String>? {
        return value?.split(",")?.filter { it.isNotEmpty() }
    }
}