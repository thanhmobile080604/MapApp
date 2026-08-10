package com.example.mapapp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.mapapp.service.LocationForegroundService
import com.example.mapapp.ui.MainScreen
import com.example.mapapp.ui.theme.ThemeTemplate
import com.example.mapapp.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationForegroundService.LocalBinder
            val locationService = binder.getService()
            locationService.setMessenger(messenger)
            viewModel.onServiceConnected(locationService)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            viewModel.onServiceDisconnected()
        }
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what == LocationForegroundService.MSG_HEARTBEAT) {
                Toast.makeText(this@MainActivity, "Service Heartbeat Received", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private val messenger = Messenger(handler)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ThemeTemplate {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, LocationForegroundService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
        viewModel.onServiceDisconnected()
    }
}