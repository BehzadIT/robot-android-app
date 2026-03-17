package com.bizi.roboapp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bizi.roboapp.core.designsystem.RoboAppTheme
import com.bizi.roboapp.feature.drivecontrol.ui.HomeScreen
import com.bizi.roboapp.feature.drivecontrol.ui.HomeViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModel()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        enableEdgeToEdge()
        setContent {
            RoboAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val throttlePercent by viewModel.throttlePercent.collectAsStateWithLifecycle()
                    val status by viewModel.status.collectAsStateWithLifecycle()
                    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
                    val recentEvents by viewModel.recentEvents.collectAsStateWithLifecycle()
                    HomeScreen(
                        modifier = Modifier.padding(innerPadding),
                        throttlePercent = throttlePercent,
                        status = status,
                        connectionState = connectionState,
                        latestEvent = recentEvents.lastOrNull(),
                        onThrottleChanged = {
                            viewModel.onThrottleChanged(it)
                        },
                        onThrottleReleased = {
                            viewModel.onThrottleReleased()
                        },
                        onRecoverRequested = {
                            viewModel.onRecoverRequested()
                        }
                    )
                }
            }
        }
    }
}
