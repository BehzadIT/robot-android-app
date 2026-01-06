package com.robost.blip.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robost.blip.data.NavigationApi
import com.robost.blip.data.NavigationWebSocketApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(FlowPreview::class)
class HomeViewModel(
    val navigationApi: NavigationApi,
    val navigationWebSocketApi: NavigationWebSocketApi
) : ViewModel() {
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    private val _navigateCommand = MutableStateFlow<NavigateCommand>(NavigateCommand.Stop)
    val navigateCommand = _navigateCommand.asStateFlow()

    private var driveCommandJob: Job? = null
    private var wasStopped = true  // Track if the last command was a stop
    private var lastSentSignedRpm: Int = 0  // Track the last sent RPM value
    private val rpmChangeThreshold = 50  // RPM change threshold to bypass debouncing

    init {
        navigationWebSocketApi.connect()

        viewModelScope.launch {
            _navigateCommand.collect { command ->
                when (command) {
                    is NavigateCommand.Drive -> {
                        // Cancel any existing drive command collection
                        driveCommandJob?.cancel()

                        // Start a new collection for drive commands
                        driveCommandJob = viewModelScope.launch {
                            val driveFlow = _navigateCommand
                                .filterIsInstance<NavigateCommand.Drive>()

                            driveFlow.collectLatest { driveCommand ->
                                val rpmChange = (driveCommand.signedTargetRpm - lastSentSignedRpm).absoluteValue
                                val isLargeChange = rpmChange >= rpmChangeThreshold

                                // Only apply debounce if:
                                // 1. Previous state wasn't stopped AND
                                // 2. RPM change is not a large change (>= threshold)
                                val shouldDebounce = (!wasStopped && !isLargeChange)

                                if (shouldDebounce) delay(500)

                                lastSentSignedRpm = driveCommand.signedTargetRpm
                                wasStopped = false
                                Log.d(
                                    "HomeViewModel",
                                    "Sending drive command: ${driveCommand.absTargetRpm} ${driveCommand.targetDirection.name}"
                                )
                                navigationWebSocketApi.sendDriveCommand(
                                    driveCommand.absTargetRpm,
                                    driveCommand.targetDirection.value
                                )
                            }
                        }
                    }

                    is NavigateCommand.Stop -> {
                        // Cancel any ongoing drive command collection first
                        driveCommandJob?.cancel()

                        // Execute stop command
                        wasStopped = true
                        lastSentSignedRpm = 0
                        _status.value = "Stopping!"
                        Log.d("HomeViewModel", "Sending stop command")
                        navigationWebSocketApi.sendStopCommand()
                        _status.value = "Stopped!"
                    }
                }
            }
        }
    }

    fun onDriveSliderPositionChange(signedTargetRpm: Int) {
        Log.i("HomeViewModel", "new slider position: $signedTargetRpm")
        _navigateCommand.value = NavigateCommand.Drive.fromSignedRpm(signedTargetRpm)
    }

    fun onDriveSliderRelease() {
        Log.d("HomeViewModel", "slider released")
        _navigateCommand.value = NavigateCommand.Stop
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
                val direction = if (signedTargetRpm >= 0f) Direction.FORWARD else Direction.REVERSE
                return Drive(signedTargetRpm.absoluteValue, direction)
            }
        }
    }

    object Stop : NavigateCommand {
        override val absTargetRpm = 0
        override val signedTargetRpm = 0
    }
}

enum class Direction(val value: Int) {
    FORWARD(0),
    REVERSE(1)
}