package com.rex.diabite.network

import com.rex.diabite.BuildConfig
import com.rex.diabite.util.Constants
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitClient {

    private fun createOkHttpClient(isStaging: Boolean = false, isUsda: Boolean = false): OkHttpClient {
        val builder = OkHttpClient.Builder()

        // Add logging interceptor
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        builder.addInterceptor(logging)

        // Add User-Agent header
        val userAgentInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("User-Agent", Constants.OFF_USER_AGENT)
                .build()
            chain.proceed(request)
        }
        builder.addInterceptor(userAgentInterceptor)

        // Add authentication for staging
        if (isStaging) {
            val authInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", okhttp3.Credentials.basic("off", "off"))
                    .build()
                chain.proceed(request)
            }
            builder.addInterceptor(authInterceptor)
        }

        // Add API key for USDA
        if (isUsda) {
            val apiKeyInterceptor = Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-Api-Key", BuildConfig.FDC_API_KEY)
                    .build()
                chain.proceed(request)
            }
            builder.addInterceptor(apiKeyInterceptor)
        }

        return builder.build()
    }

    fun createOffClient(isStaging: Boolean = false): OpenFoodFactsApi {
        val baseUrl = if (isStaging) Constants.OFF_STAGING_BASE_URL else Constants.OFF_PROD_BASE_URL

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(createOkHttpClient(isStaging))
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(OpenFoodFactsApi::class.java)
    }

    fun createUsdaClient(): UsdaApi {
        return Retrofit.Builder()
            .baseUrl(Constants.USDA_BASE_URL)
            .client(createOkHttpClient(isUsda = true))
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(UsdaApi::class.java)
    }
}