package com.bizi.roboapp.feature.drivecontrol.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizi.roboapp.core.protocol.navigation.NavigationServerEvent
import com.bizi.roboapp.core.protocol.navigation.parseNavigationServerEvent
import com.bizi.roboapp.core.transport.NavigationWebSocketApi
import com.bizi.roboapp.core.transport.ReliabilityEvent
import com.bizi.roboapp.core.transport.WebSocketConnectionState
import com.bizi.roboapp.feature.drivecontrol.domain.DRIVE_REFRESH_MS
import com.bizi.roboapp.feature.drivecontrol.domain.DRIVE_START_EXIT_DEADBAND_PERCENT
import com.bizi.roboapp.feature.drivecontrol.domain.DRIVE_STOP_ENTER_DEADBAND_PERCENT
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val TAG = "HomeViewModel"
private const val STATUS_IDLE = "Idle"
private const val STATUS_DRIVING = "Driving"
private const val STATUS_STOPPING = "Stopping"
private const val STATUS_STOPPED = "Stopped"
private const val STATUS_STOP_NOT_CONFIRMED = "Stop not confirmed; waiting for Pico timeout/reconnect"
private const val STATUS_RECOVERING = "Recovering"
private const val STATUS_RECOVERY_REQUESTED = "Recovery requested"
private const val STATUS_RECOVERY_NOT_SENT = "Recovery not sent; reconnect and retry"

class HomeViewModel(
    val navigationWebSocketApi: NavigationWebSocketApi
) : ViewModel() {
    private val _status = MutableStateFlow(STATUS_IDLE)
    val status: StateFlow<String> = _status.asStateFlow()

    private val _navigateCommand = MutableStateFlow<NavigateCommand>(NavigateCommand.Stop)
    val navigateCommand = _navigateCommand.asStateFlow()

    private val _throttlePercent = MutableStateFlow(0)
    val throttlePercent: StateFlow<Int> = _throttlePercent.asStateFlow()

    val connectionState: StateFlow<WebSocketConnectionState> = navigationWebSocketApi.connectionState
    val recentEvents: StateFlow<List<ReliabilityEvent>> = navigationWebSocketApi.recentEvents

    private var driveRefreshJob: Job? = null
    private var latestDriveCommand: NavigateCommand.Drive? = null
    private var isDriveActive = false
    private var awaitingStopAck = false
    private var pendingDriveAfterStop: NavigateCommand.Drive? = null

    init {
        navigationWebSocketApi.connect(
            onMessage = { message ->
                Log.i(TAG, "Pico message: $message")
                handlePicoMessage(message)
            },
            onClose = {
                Log.i(TAG, "WebSocket closed; waiting for reconnect manager")
                awaitingStopAck = false
                pendingDriveAfterStop = null
                if (_status.value == STATUS_STOPPING) {
                    _status.value = STATUS_STOP_NOT_CONFIRMED
                }
            }
        )
    }

    fun onThrottleChanged(throttlePercent: Int) {
        Log.i(TAG, "New throttle position: $throttlePercent")
        _throttlePercent.value = throttlePercent

        val absThrottle = throttlePercent.absoluteValue
        val nextCommand = NavigateCommand.Drive.fromThrottlePercent(throttlePercent)

        if (awaitingStopAck) {
            if (absThrottle < DRIVE_START_EXIT_DEADBAND_PERCENT) {
                pendingDriveAfterStop = null
                latestDriveCommand = null
                _navigateCommand.value = NavigateCommand.Stop
                return
            }
            pendingDriveAfterStop = nextCommand
            latestDriveCommand = null
            _navigateCommand.value = NavigateCommand.Stop
            return
        }

        if (isDriveActive && absThrottle <= DRIVE_STOP_ENTER_DEADBAND_PERCENT) {
            isDriveActive = false
            latestDriveCommand = null
            _navigateCommand.value = NavigateCommand.Stop
            stopDriveLoopAndSendStop()
            return
        }

        val currentThrottleSign = latestDriveCommand?.throttlePercent?.compareTo(0) ?: 0
        val nextThrottleSign = nextCommand.throttlePercent.compareTo(0)
        if (isDriveActive && currentThrottleSign != 0 && nextThrottleSign != 0 && currentThrottleSign != nextThrottleSign) {
            isDriveActive = false
            latestDriveCommand = null
            pendingDriveAfterStop = nextCommand
            _navigateCommand.value = NavigateCommand.Stop
            stopDriveLoopAndSendStop()
            return
        }

        if (!isDriveActive && absThrottle < DRIVE_START_EXIT_DEADBAND_PERCENT) {
            latestDriveCommand = null
            _navigateCommand.value = NavigateCommand.Stop
            _status.value = STATUS_IDLE
            return
        }

        latestDriveCommand = nextCommand
        _navigateCommand.value = nextCommand

        if (!isDriveActive) {
            isDriveActive = true
            startDriveRefreshLoop()
        }
    }

    fun onThrottleReleased() {
        Log.d(TAG, "Throttle released")
        isDriveActive = false
        latestDriveCommand = null
        pendingDriveAfterStop = null
        _navigateCommand.value = NavigateCommand.Stop
        _throttlePercent.value = 0
        stopDriveLoopAndSendStop()
    }

    fun onRecoverRequested() {
        val wasDriveActive = isDriveActive
        isDriveActive = false
        awaitingStopAck = false
        latestDriveCommand = null
        pendingDriveAfterStop = null
        _navigateCommand.value = NavigateCommand.Stop
        _throttlePercent.value = 0
        driveRefreshJob?.cancel()
        driveRefreshJob = null
        _status.value = STATUS_RECOVERING

        viewModelScope.launch {
            if (wasDriveActive) {
                navigationWebSocketApi.sendStopCommand()
                delay(DRIVE_REFRESH_MS + 50L)
            }
            val sent = navigationWebSocketApi.sendRecoverCommand()
            _status.value = if (sent) {
                STATUS_RECOVERY_REQUESTED
            } else {
                STATUS_RECOVERY_NOT_SENT
            }
        }
    }

    override fun onCleared() {
        navigationWebSocketApi.disconnect()
        super.onCleared()
    }

    private fun startDriveRefreshLoop() {
        driveRefreshJob?.cancel()

        driveRefreshJob = viewModelScope.launch {
            while (isDriveActive) {
                val command = latestDriveCommand ?: break
                val sent = navigationWebSocketApi.sendDriveCommand(
                    throttlePercent = command.throttlePercent,
                    turnPercent = command.turnPercent
                )
                _status.value = if (sent) {
                    STATUS_DRIVING
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
        awaitingStopAck = true
        _status.value = STATUS_STOPPING

        viewModelScope.launch {
            val sent = navigationWebSocketApi.sendStopCommand()
            _status.value = if (sent) {
                STATUS_STOPPING
            } else {
                awaitingStopAck = false
                pendingDriveAfterStop = null
                STATUS_STOP_NOT_CONFIRMED
            }
        }
    }

    private fun handlePicoMessage(message: String) {
        when (val event = parseNavigationServerEvent(message) ?: return) {
            is NavigationServerEvent.Ack -> when (event.code) {
                "s" -> {
                    awaitingStopAck = false
                    _status.value = STATUS_STOPPED
                    val queuedCommand = pendingDriveAfterStop
                    if (queuedCommand != null) {
                        pendingDriveAfterStop = null
                        latestDriveCommand = queuedCommand
                        _navigateCommand.value = queuedCommand
                        isDriveActive = true
                        startDriveRefreshLoop()
                    }
                }
                "r" -> _status.value = "Recovered"
            }
            is NavigationServerEvent.Error -> {
                if (_status.value == STATUS_STOPPING) {
                    awaitingStopAck = false
                    pendingDriveAfterStop = null
                    _status.value = STATUS_STOP_NOT_CONFIRMED
                }
            }
        }
    }
}

sealed interface NavigateCommand {
    val throttlePercent: Int
    val turnPercent: Int

    data class Drive(
        override val throttlePercent: Int,
        override val turnPercent: Int,
    ) : NavigateCommand {
        companion object {
            fun fromThrottlePercent(throttlePercent: Int): Drive {
                return Drive(
                    throttlePercent = throttlePercent.coerceIn(-100, 100),
                    turnPercent = 0
                )
            }
        }
    }

    data object Stop : NavigateCommand {
        override val throttlePercent = 0
        override val turnPercent = 0
    }
}
