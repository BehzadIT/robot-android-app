package com.robost.blip.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robost.blip.data.NavigationApi
import com.robost.blip.data.NavigationWebSocketApi
import com.robost.blip.data.ReliabilityEvent
import com.robost.blip.data.WebSocketConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val TAG = "HomeViewModel"
private const val DRIVE_REFRESH_MS = 100L

class HomeViewModel(
    val navigationApi: NavigationApi,
    val navigationWebSocketApi: NavigationWebSocketApi
) : ViewModel() {
    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _navigateCommand = MutableStateFlow<NavigateCommand>(NavigateCommand.Stop)
    val navigateCommand = _navigateCommand.asStateFlow()

    val connectionState: StateFlow<WebSocketConnectionState> = navigationWebSocketApi.connectionState
    val recentEvents: StateFlow<List<ReliabilityEvent>> = navigationWebSocketApi.recentEvents

    private var driveRefreshJob: Job? = null
    private var latestDriveCommand: NavigateCommand.Drive? = null
    private var isDriveActive = false

    init {
        navigationWebSocketApi.connect(
            onMessage = { message ->
                Log.i(TAG, "Pico message: $message")
            },
            onClose = {
                Log.i(TAG, "WebSocket closed; waiting for reconnect manager")
            }
        )
    }

    fun onDriveSliderPositionChange(signedTargetRpm: Int) {
        Log.i(TAG, "New slider position: $signedTargetRpm")
        val command = NavigateCommand.Drive.fromSignedRpm(signedTargetRpm)
        latestDriveCommand = command
        _navigateCommand.value = command

        if (!isDriveActive) {
            isDriveActive = true
            startDriveRefreshLoop()
        }
    }

    fun onDriveSliderRelease() {
        Log.d(TAG, "Slider released")
        isDriveActive = false
        latestDriveCommand = null
        _navigateCommand.value = NavigateCommand.Stop
        stopDriveLoopAndSendStop()
    }

    override fun onCleared() {
        navigationWebSocketApi.disconnect()
        super.onCleared()
    }

    private fun startDriveRefreshLoop() {
        driveRefreshJob?.cancel()

        // Best practice for low-latency control here is latest-intent sampling,
        // not debounce: keep the freshest operator command and transmit it at a
        // fixed cadence while the drag remains active.
        driveRefreshJob = viewModelScope.launch {
            while (isDriveActive) {
                val command = latestDriveCommand ?: break
                val sent = navigationWebSocketApi.sendDriveCommand(
                    targetRpm = command.absTargetRpm,
                    targetDirection = command.targetDirection.value
                )
                _status.value = if (sent) {
                    "Driving ${command.targetDirection.name.lowercase()} at ${command.absTargetRpm} RPM"
                } else {
                    "Drive pending reconnect"
                }
                delay(DRIVE_REFRESH_MS)
            }
        }
    }

    private fun stopDriveLoopAndSendStop() {
        driveRefreshJob?.cancel()
        driveRefreshJob = null
        _status.value = "Stopping"

        viewModelScope.launch {
            val sent = navigationWebSocketApi.sendStopCommand()
            _status.value = if (sent) {
                "Stopped"
            } else {
                "Stop requested; waiting for Pico timeout"
            }
        }
    }
}

sealed interface NavigateCommand {
    val absTargetRpm: Int
    val signedTargetRpm: Int

    data class Drive(override val absTargetRpm: Int, val targetDirection: Direction) :
        NavigateCommand {
        override val signedTargetRpm: Int
            get() {
                return when (targetDirection) {
                    Direction.FORWARD -> absTargetRpm
                    Direction.REVERSE -> -1 * absTargetRpm
                }
            }

        companion object {
            fun fromSignedRpm(signedTargetRpm: Int): Drive {
                val direction = if (signedTargetRpm >= 0) Direction.FORWARD else Direction.REVERSE
                return Drive(signedTargetRpm.absoluteValue, direction)
            }
        }
    }

    data object Stop : NavigateCommand {
        override val absTargetRpm = 0
        override val signedTargetRpm = 0
    }
}

enum class Direction(val value: Int) {
    FORWARD(0),
    REVERSE(1)
}
