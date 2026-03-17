package com.bizi.roboapp.feature.drivecontrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bizi.roboapp.core.transport.ReliabilityEvent
import com.bizi.roboapp.core.transport.WebSocketConnectionState
import com.bizi.roboapp.feature.drivecontrol.ui.controls.ThrottleLane

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    throttlePercent: Int,
    status: String,
    connectionState: WebSocketConnectionState,
    latestEvent: ReliabilityEvent?,
    onThrottleChanged: (Int) -> Unit,
    onThrottleReleased: () -> Unit,
    onRecoverRequested: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TopStatusStrip(
            status = status,
            connectionState = connectionState
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ThrottleLane(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    throttlePercent = throttlePercent,
                    onThrottleChanged = onThrottleChanged,
                    onThrottleReleased = onThrottleReleased
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                )
            }
        }

        BottomStatusStrip(
            latestEvent = latestEvent,
            onRecoverRequested = onRecoverRequested
        )
    }
}

@Composable
private fun TopStatusStrip(
    status: String,
    connectionState: WebSocketConnectionState,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Transport: ${connectionState.label()}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Status: $status",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomStatusStrip(
    latestEvent: ReliabilityEvent?,
    onRecoverRequested: () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 36.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = latestEvent?.let { "Last event: ${it.type} - ${it.message}" } ?: "Last event: none",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onRecoverRequested) {
                Text(text = "Recover")
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
