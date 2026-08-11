package com.example.mapapp.network

data class OsrmResponse(
    val routes: List<OsrmRoute>
)

data class OsrmRoute(
    val geometry: OsrmGeometry
)

data class OsrmGeometry(
    val coordinates: List<List<Double>>
)
