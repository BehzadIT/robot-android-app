package com.robost.blip.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.robost.blip.data.NavigationApi
import com.robost.blip.data.NavigationWebSocketApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class HomeViewModel(val navigationApi: NavigationApi, val navigationWebSocketApi: NavigationWebSocketApi) : ViewModel() {
    private val _status = MutableStateFlow<String?>(null)
    val status = _status.asStateFlow()

    private val _targetRpm = MutableStateFlow(0)
    val targetRpm = _targetRpm.asStateFlow()

    init {
        navigationWebSocketApi.connect()
    }

    fun setTargetRpm(targetRpm: Int) {
        _status.value = "Setting target RPM to $targetRpm"
        _targetRpm.value = targetRpm
    }

    fun drive() {
        viewModelScope.launch {
            // Call the API to drive
            runCatching {
                navigationWebSocketApi.sendDriveCommand(targetRpm.value, 0) // Assuming 0 direction for forward
//                navigationApi.drive(targetRpm.value, 0) // Assuming 0 direction for forward
            }.onFailure {
                Log.e("HomeViewModel", "Error driving: ${it.message}")
            }
        }
        _status.value = "Driving at $targetRpm RPM"
    }

    fun stop() {
        _status.value = "Stopping!"
        viewModelScope.launch {
            // Call the API to stop
            runCatching {
                navigationWebSocketApi.sendStopCommand()
//                navigationApi.stop()
            }.onFailure {
                Log.e("HomeViewModel", "Error stopping: ${it.message}")
            }
        }
        _status.value = "Stopped!"
    }

    fun brake() {
        _status.value = "Braking!"
        viewModelScope.launch {
            // Call the API to brake
            navigationApi.drive(0, 0) // Assuming 0 RPM means braking
        }
        _status.value = "Braked!"
    }
}