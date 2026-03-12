package com.robost.blip.home

import android.util.Log
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.robost.blip.data.ReliabilityEvent
import com.robost.blip.data.WebSocketConnectionState

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    driveSliderPosition: Float,
    status: String,
    connectionState: WebSocketConnectionState,
    latestEvent: ReliabilityEvent?,
    onDriveSliderPositionChange: (Int) -> Unit,
    onDriveSliderRelease: () -> Unit,
) {
    Row(modifier = modifier.padding(16.dp)) {
        Column {
            val interactionSource = remember { MutableInteractionSource() }
            val interactions = remember { mutableStateListOf<Interaction>() }

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

            Slider(
                value = driveSliderPosition,
                interactionSource = interactionSource,
                onValueChange = { newValue -> onDriveSliderPositionChange(newValue.toInt()) },
                onValueChangeFinished = { onDriveSliderRelease() },
                valueRange = -300f..300f
            )
            Text(text = "Transport: ${connectionState.label()}")
            Text(text = "Status: $status")
            if (driveSliderPosition > 0f) {
                Text(text = "Driving Forward at ${driveSliderPosition.toInt()} RPM")
            } else if (driveSliderPosition < 0f) {
                Text(text = "Driving Backward at ${-driveSliderPosition.toInt()} RPM")
            } else {
                Text(text = "Stopped")
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
