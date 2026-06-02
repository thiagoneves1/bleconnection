package com.thiagoneves.bleconnection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thiagoneves.bleconnection.feature.pulseoximeter.PulseOximeterScreen
import com.thiagoneves.bleconnection.ui.theme.bleconnectionTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            bleconnectionTheme {
                PulseOximeterScreen()
            }
        }
    }
}

