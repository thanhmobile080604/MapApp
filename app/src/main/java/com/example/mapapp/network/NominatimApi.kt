package com.example.mapapp.network

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 5,
        @Header("User-Agent") userAgent: String = "NaviMapViewer/2.0"
    ): List<NominatimResponse>
}
