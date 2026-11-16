package com.example.mapapp.viewmodel

import android.app.Application
import android.os.Messenger
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import android.os.Bundle
import android.os.Message
import com.example.mapapp.service.LocationForegroundService
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel
    private val application: Application = mock()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MainViewModel(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun handleLocationUpdate_updatesState() {
        val serviceMessenger: Messenger = mock()
        viewModel.onServiceConnected(serviceMessenger)

        val captor = argumentCaptor<Message>()
        verify(serviceMessenger).send(captor.capture())
        val clientMessenger = captor.firstValue.replyTo

        val msg = Message.obtain(null, LocationForegroundService.MSG_LOCATION_UPDATE)
        msg.data = Bundle().apply {
            putDouble(LocationForegroundService.KEY_LAT, 10.0)
            putDouble(LocationForegroundService.KEY_LON, 20.0)
            putFloat(LocationForegroundService.KEY_SPEED, 30f)
            putBoolean(LocationForegroundService.KEY_FIX, true)
        }
        
        clientMessenger.send(msg)
        ShadowLooper.idleMainLooper()

        val location = viewModel.locationFlow.value
        assertEquals(10.0, location.latitude, 0.0)
        assertEquals(20.0, location.longitude, 0.0)
        assertEquals(30f, location.speedKmh, 0.0f)
        assertTrue(location.hasFix)
    }

    @Test
    fun handleConfigUpdate_updatesState() {
        val serviceMessenger: Messenger = mock()
        viewModel.onServiceConnected(serviceMessenger)

        val captor = argumentCaptor<Message>()
        verify(serviceMessenger).send(captor.capture())
        val clientMessenger = captor.firstValue.replyTo

        val msg = Message.obtain(null, LocationForegroundService.MSG_CONFIG_UPDATE)
        msg.data = Bundle().apply {
            putInt(LocationForegroundService.KEY_LIMIT, 120)
            putBoolean(LocationForegroundService.KEY_DROWSY, false)
        }
        
        clientMessenger.send(msg)
        ShadowLooper.idleMainLooper()

        val config = viewModel.configFlow.value
        assertEquals(120, config.speedLimitKmh)
        assertTrue(!config.drowsinessAlertEnabled)
    }

    @Test
    fun updateSearchQuery_updatesQuery() {
        viewModel.updateSearchQuery("Paris")
        assertEquals("Paris", viewModel.searchQuery.value)
    }

    @Test
    fun updateSearchQuery_emptyQuery_clearsResults() {
        viewModel.updateSearchQuery("")
        assertEquals("", viewModel.searchQuery.value)
        assertEquals(0, viewModel.searchResults.value.size)
    }

    @Test
    fun setSpeedLimit_doesNotCrash() {
        // Since serviceMessenger is null initially, sendCommand should handle it gracefully
        viewModel.setSpeedLimit(100f)
    }

    @Test
    fun selectDestination_updatesState() {
        val result = OsmSearchResult("Eiffel Tower", 48.8584, 2.2945)
        viewModel.selectDestination(result)
        
        assertEquals(result, viewModel.destination.value)
        assertEquals(0, viewModel.searchResults.value.size)
        assertEquals("Eiffel Tower", viewModel.searchQuery.value)
    }

    @Test
    fun onServiceConnected_registersClient() {
        val messenger: Messenger = mock()
        viewModel.onServiceConnected(messenger)
        // Verify it doesn't crash and potentially check interaction if we mock Message
    }
}
