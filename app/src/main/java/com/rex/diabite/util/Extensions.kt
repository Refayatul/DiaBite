package com.rex.diabite.util

import java.util.concurrent.TimeUnit

fun Long.toDaysAgo(): String {
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - this)
    return when {
        days == 0L -> "Today"
        days == 1L -> "1 day ago"
        else -> "$days days ago"
    }
}