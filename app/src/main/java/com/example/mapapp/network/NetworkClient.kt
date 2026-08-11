package com.example.mapapp.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    private const val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/"
    private const val OSRM_BASE_URL = "https://router.project-osrm.org/"

    val nominatimApi: NominatimApi by lazy {
        Retrofit.Builder()
            .baseUrl(NOMINATIM_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NominatimApi::class.java)
    }

    val osrmApi: OsrmApi by lazy {
        Retrofit.Builder()
            .baseUrl(OSRM_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OsrmApi::class.java)
    }
}
