package com.rex.diabite.network

import com.rex.diabite.model.FoodSearchResult
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenFoodFactsApi {

    @GET("api/v2/product/{barcode}.json")
    suspend fun getProductByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "code,product_name,brands,countries_tags_en,categories_tags,carbohydrates_100g,sugars_100g,fiber_100g,energy-kcal_100g,nutriscore_grade,nutrition_grade_fr,last_modified_t"
    ): Response<FoodSearchResult>

    @GET("api/v2/search")
    suspend fun searchProducts(
        @Query("search_terms") query: String,
        @Query("page_size") pageSize: Int = 10,
        @Query("fields") fields: String = "code,product_name,brands,countries_tags_en,categories_tags,carbohydrates_100g,sugars_100g,fiber_100g,energy-kcal_100g,nutriscore_grade,nutrition_grade_fr,last_modified_t"
    ): Response<FoodSearchResult>
}