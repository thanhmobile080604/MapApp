package com.example.mapapp.network

import com.google.gson.annotations.SerializedName

data class NominatimResponse(
    @SerializedName("display_name") val displayName: String,
    val lat: String,
    val lon: String
)
