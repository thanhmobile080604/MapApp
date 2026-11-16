package com.example.mapapp.util

import android.Manifest
import android.os.Build
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LocationHelperTest {

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun getRequiredPermissions_preTiramisu() {
        val permissions = LocationHelper.getRequiredPermissions()
        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertTrue(!permissions.contains("android.permission.POST_NOTIFICATIONS"))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun getRequiredPermissions_tiramisuPlus() {
        val permissions = LocationHelper.getRequiredPermissions()
        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.POST_NOTIFICATIONS))
    }
}
