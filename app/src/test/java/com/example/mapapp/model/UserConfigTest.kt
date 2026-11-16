package com.example.mapapp.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserConfigTest {
    @Test
    fun userConfig_defaultValues() {
        val config = UserConfig()
        assertEquals(80, config.speedLimitKmh)
        assertTrue(config.drowsinessAlertEnabled)
    }

    @Test
    fun userConfig_customValues() {
        val config = UserConfig(speedLimitKmh = 100, drowsinessAlertEnabled = false)
        assertEquals(100, config.speedLimitKmh)
        assertFalse(config.drowsinessAlertEnabled)
    }
}
