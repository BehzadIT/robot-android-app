package com.bizi.roboapp.feature.drivecontrol.ui

import android.util.Log
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bizi.roboapp.core.transport.ReliabilityEvent
import com.bizi.roboapp.core.transport.WebSocketConnectionState
import com.bizi.roboapp.feature.drivecontrol.domain.DRIVE_STOP_ENTER_DEADBAND_PERCENT
import com.bizi.roboapp.feature.drivecontrol.domain.THROTTLE_SLIDER_MAX
import com.bizi.roboapp.feature.drivecontrol.domain.THROTTLE_SLIDER_MIN
import com.bizi.roboapp.feature.drivecontrol.domain.TURN_SLIDER_MAX
import com.bizi.roboapp.feature.drivecontrol.domain.TURN_SLIDER_MIN

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    driveSliderPosition: Float,
    turnSliderPosition: Float,
    status: String,
    connectionState: WebSocketConnectionState,
    latestEvent: ReliabilityEvent?,
    onDriveSliderPositionChange: (Int) -> Unit,
    onDriveSliderRelease: () -> Unit,
    onSteeringSliderPositionChange: (Int) -> Unit,
    onSteeringSliderRelease: () -> Unit,
    onRecoverRequested: () -> Unit,
) {
    Row(modifier = modifier.padding(16.dp)) {
        Column {
            val interactionSource = remember { MutableInteractionSource() }
            val interactions = remember { mutableStateListOf<Interaction>() }
            val steeringInteractionSource = remember { MutableInteractionSource() }

            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect { interaction ->
                    Log.d("HomeScreen", "Interaction: $interaction")
                    when (interaction) {
                        is PressInteraction.Press -> {
                            interactions.add(interaction)
                        }

                        is DragInteraction.Start -> {
                            interactions.add(interaction)
                        }

                        is PressInteraction.Release -> {
                            Log.d("HomeScreen", "Slider Released")
                        }
                    }
                }
            }

            LaunchedEffect(steeringInteractionSource) {
                steeringInteractionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) {
                        Log.d("HomeScreen", "Steering slider released")
                    }
                }
            }

            Slider(
                value = driveSliderPosition,
                interactionSource = interactionSource,
                onValueChange = { newValue -> onDriveSliderPositionChange(newValue.toInt()) },
                onValueChangeFinished = { onDriveSliderRelease() },
                valueRange = THROTTLE_SLIDER_MIN..THROTTLE_SLIDER_MAX
            )
            Slider(
                value = turnSliderPosition,
                interactionSource = steeringInteractionSource,
                onValueChange = { newValue -> onSteeringSliderPositionChange(newValue.toInt()) },
                onValueChangeFinished = { onSteeringSliderRelease() },
                valueRange = TURN_SLIDER_MIN..TURN_SLIDER_MAX
            )
            Text(text = "Transport: ${connectionState.label()}")
            Text(text = "Status: $status")
            if (kotlin.math.abs(driveSliderPosition) <= DRIVE_STOP_ENTER_DEADBAND_PERCENT) {
                Text(text = "Stopped")
            } else if (driveSliderPosition > 0f) {
                Text(text = "Throttle Forward ${driveSliderPosition.toInt()}%")
            } else if (driveSliderPosition < 0f) {
                Text(text = "Throttle Reverse ${-driveSliderPosition.toInt()}%")
            } else {
                Text(text = "Stopped")
            }
            Text(
                text = when {
                    turnSliderPosition > 0f -> "Turn Right ${turnSliderPosition.toInt()}%"
                    turnSliderPosition < 0f -> "Turn Left ${-turnSliderPosition.toInt()}%"
                    else -> "Turn Straight"
                }
            )
            Button(onClick = { onRecoverRequested() }) {
                Text(text = "Recover Drivetrain")
            }
            latestEvent?.let { event ->
                Text(text = "Last event: ${event.type} - ${event.message}")
            }
        }
    }
}

private fun WebSocketConnectionState.label(): String =
    when (this) {
        WebSocketConnectionState.Connected -> "Connected"
        WebSocketConnectionState.Connecting -> "Connecting"
        WebSocketConnectionState.Disconnected -> "Disconnected"
        is WebSocketConnectionState.Failed -> "Failed: $reason"
        is WebSocketConnectionState.Reconnecting -> "Reconnecting (#$attempt)"
    }
