package com.example.mapapp.network

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface OsrmApi {
    @GET("route/v1/driving/{coords}")
    suspend fun getRoute(
        @Path("coords", encoded = true) coords: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
        @Header("User-Agent") userAgent: String = "NaviMapViewer/2.0"
    ): OsrmResponse
}
