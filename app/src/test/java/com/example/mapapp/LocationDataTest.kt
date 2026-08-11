package com.example.mapapp

import com.example.mapapp.model.LocationData
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationDataTest {
    @Test
    fun locationData_defaultValues() {
        val location = LocationData()
        assertEquals(0.0, location.latitude, 0.0001)
        assertEquals(0.0, location.longitude, 0.0001)
        assertEquals(0f, location.speedKmh, 0.0001f)
    }

    @Test
    fun locationData_customValues() {
        val location = LocationData(21.0285, 105.8542, 45.5f)
        assertEquals(21.0285, location.latitude, 0.0001)
        assertEquals(105.8542, location.longitude, 0.0001)
        assertEquals(45.5f, location.speedKmh, 0.0001f)
    }
}
