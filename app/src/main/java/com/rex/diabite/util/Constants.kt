package com.rex.diabite.util

object Constants {
    const val OFF_PROD_BASE_URL = "https://world.openfoodfacts.org/"
    const val OFF_STAGING_BASE_URL = "https://world.openfoodfacts.net/"
    const val USDA_BASE_URL = "https://api.nal.usda.gov/fdc/"

    const val OFF_USER_AGENT = "DiaBite/1.0 (student@example.com)"
    const val CACHE_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000 // 30 days
    const val MAX_CACHE_SIZE = 500
    const val MAX_HISTORY_SIZE = 300

    const val TARGET_NET_CARBS_PER_SERVING = 15f
}