package com.robost.blip.feature.drivecontrol.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robost.blip.core.protocol.navigation.NavigationServerEvent
import com.robost.blip.core.protocol.navigation.parseNavigationServerEvent
import com.robost.blip.core.transport.NavigationApi
import com.robost.blip.core.transport.NavigationWebSocketApi
import com.robost.blip.core.transport.ReliabilityEvent
import com.robost.blip.core.transport.WebSocketConnectionState
import com.robost.blip.feature.drivecontrol.domain.DRIVE_REFRESH_MS
import com.robost.blip.feature.drivecontrol.domain.DRIVE_START_EXIT_DEADBAND_RPM
import com.robost.blip.feature.drivecontrol.domain.DRIVE_STOP_ENTER_DEADBAND_RPM
import com.robost.blip.feature.drivecontrol.domain.STEERING_ANGLE_DEFAULT
import com.robost.blip.feature.drivecontrol.domain.STEERING_ANGLE_MAX
import com.robost.blip.feature.drivecontrol.domain.STEERING_ANGLE_MIN
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val TAG = "HomeViewModel"
private const val STATUS_IDLE = "Idle"
private const val STATUS_STOPPING = "Stopping"
private const val STATUS_STOPPED = "Stopped"
private const val STATUS_STOP_NOT_CONFIRMED = "Stop not confirmed; waiting for Pico timeout/reconnect"
private const val STATUS_RECOVERING = "Recovering"
private const val STATUS_RECOVERY_REQUESTED = "Recovery requested"
private const val STATUS_RECOVERY_NOT_SENT = "Recovery not sent; reconnect and retry"

class HomeViewModel(
    val navigationApi: NavigationApi,
    val navigationWebSocketApi: NavigationWebSocketApi
) : ViewModel() {
    private val _status = MutableStateFlow(STATUS_IDLE)
    val status: StateFlow<String> = _status.asStateFlow()

    private val _navigateCommand = MutableStateFlow<NavigateCommand>(NavigateCommand.Stop)
    val navigateCommand = _navigateCommand.asStateFlow()
    private val _steeringAngle = MutableStateFlow(STEERING_ANGLE_DEFAULT)
    val steeringAngle = _steeringAngle.asStateFlow()

    val connectionState: StateFlow<WebSocketConnectionState> = navigationWebSocketApi.connectionState
    val recentEvents: StateFlow<List<ReliabilityEvent>> = navigationWebSocketApi.recentEvents

    private var driveRefreshJob: Job? = null
    private var latestDriveCommand: NavigateCommand.Drive? = null
    private var isDriveActive = false

    init {
        navigationWebSocketApi.connect(
            onMessage = { message ->
                Log.i(TAG, "Pico message: $message")
                handlePicoMessage(message)
            },
            onClose = {
                Log.i(TAG, "WebSocket closed; waiting for reconnect manager")
                if (_status.value == STATUS_STOPPING) {
                    _status.value = STATUS_STOP_NOT_CONFIRMED
                }
            }
        )
    }

    fun onDriveSliderPositionChange(signedTargetRpm: Int) {
        Log.i(TAG, "New slider position: $signedTargetRpm")
        val absRpm = signedTargetRpm.absoluteValue

        // The zero zone uses hysteresis so the UI does not chatter between
        // stop/start when the thumb hovers around zero. While inside the stop
        // band we send one explicit stop and suppress tiny ineffective drive
        // commands until the slider leaves the larger restart threshold.
        if (isDriveActive && absRpm <= DRIVE_STOP_ENTER_DEADBAND_RPM) {
            isDriveActive = false
            latestDriveCommand = null
            _navigateCommand.value = NavigateCommand.Stop
            stopDriveLoopAndSendStop()
            return
        }
        if (!isDriveActive && absRpm < DRIVE_START_EXIT_DEADBAND_RPM) {
            latestDriveCommand = null
            _navigateCommand.value = NavigateCommand.Stop
            return
        }

        val command = NavigateCommand.Drive.fromSignedRpm(
            signedTargetRpm = signedTargetRpm,
            targetAngle = _steeringAngle.value
        )
        latestDriveCommand = command
        _navigateCommand.value = command

        if (!isDriveActive) {
            isDriveActive = true
            startDriveRefreshLoop()
        }
    }

    fun onSteeringSliderPositionChange(targetAngle: Int) {
        val clampedAngle = targetAngle.coerceIn(STEERING_ANGLE_MIN.toInt(), STEERING_ANGLE_MAX.toInt())
        _steeringAngle.value = clampedAngle

        val currentDrive = latestDriveCommand ?: return
        val updatedCommand = currentDrive.copy(targetAngle = clampedAngle)
        latestDriveCommand = updatedCommand
        _navigateCommand.value = updatedCommand
    }

    fun onSteeringSliderRelease() {
        _steeringAngle.value = STEERING_ANGLE_DEFAULT
        val currentDrive = latestDriveCommand ?: return
        val updatedCommand = currentDrive.copy(targetAngle = STEERING_ANGLE_DEFAULT)
        latestDriveCommand = updatedCommand
        _navigateCommand.value = updatedCommand
    }

    fun onDriveSliderRelease() {
        Log.d(TAG, "Slider released")
        isDriveActive = false
        latestDriveCommand = null
        _navigateCommand.value = NavigateCommand.Stop
        _steeringAngle.value = STEERING_ANGLE_DEFAULT
        stopDriveLoopAndSendStop()
    }

    fun onRecoverRequested() {
        val wasDriveActive = isDriveActive
        isDriveActive = false
        latestDriveCommand = null
        _navigateCommand.value = NavigateCommand.Stop
        _steeringAngle.value = STEERING_ANGLE_DEFAULT
        driveRefreshJob?.cancel()
        driveRefreshJob = null
        _status.value = STATUS_RECOVERING

        viewModelScope.launch {
            if (wasDriveActive) {
                navigationWebSocketApi.sendStopCommand()
                delay(DRIVE_REFRESH_MS + 50L)
            }
            // Recover is an explicit one-shot operator action in the current
            // design. If the command cannot be sent now, the app must say so
            // plainly rather than imply a hidden queued retry.
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

        // Best practice for low-latency control here is latest-intent sampling,
        // not debounce: keep the freshest operator command and transmit it at a
        // fixed cadence while the drag remains active.
        driveRefreshJob = viewModelScope.launch {
            while (isDriveActive) {
                val command = latestDriveCommand ?: break
                val sent = navigationWebSocketApi.sendDriveCommand(
                    targetRpm = command.absTargetRpm,
                    targetDirection = command.targetDirection.value,
                    targetAngle = command.targetAngle
                )
                _status.value = if (sent) {
                    "Driving ${command.targetDirection.name.lowercase()} at ${command.absTargetRpm} RPM, steering ${command.targetAngle} deg"
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
        _status.value = STATUS_STOPPING

        viewModelScope.launch {
            val sent = navigationWebSocketApi.sendStopCommand()
            _status.value = if (sent) {
                STATUS_STOPPING
            } else {
                STATUS_STOP_NOT_CONFIRMED
            }
        }
    }

    private fun handlePicoMessage(message: String) {
        when (val event = parseNavigationServerEvent(message) ?: return) {
            is NavigationServerEvent.Ack -> when (event.code) {
                "s" -> _status.value = STATUS_STOPPED
                "r" -> _status.value = "Recovered"
            }
            is NavigationServerEvent.Error -> {
                if (_status.value == STATUS_STOPPING) {
                    _status.value = STATUS_STOP_NOT_CONFIRMED
                }
            }
        }
    }
}

sealed interface NavigateCommand {
    val absTargetRpm: Int
    val signedTargetRpm: Int
    val targetAngle: Int

    data class Drive(
        override val absTargetRpm: Int,
        val targetDirection: Direction,
        override val targetAngle: Int,
    ) :
        NavigateCommand {
        override val signedTargetRpm: Int
            get() {
                return when (targetDirection) {
                    Direction.FORWARD -> absTargetRpm
                    Direction.REVERSE -> -1 * absTargetRpm
                }
            }

        companion object {
            fun fromSignedRpm(signedTargetRpm: Int, targetAngle: Int): Drive {
                val direction = if (signedTargetRpm >= 0) Direction.FORWARD else Direction.REVERSE
                return Drive(signedTargetRpm.absoluteValue, direction, targetAngle)
            }
        }
    }

    data object Stop : NavigateCommand {
        override val absTargetRpm = 0
        override val signedTargetRpm = 0
        override val targetAngle = STEERING_ANGLE_DEFAULT
    }
}

enum class Direction(val value: Int) {
    FORWARD(0),
    REVERSE(1)
}
