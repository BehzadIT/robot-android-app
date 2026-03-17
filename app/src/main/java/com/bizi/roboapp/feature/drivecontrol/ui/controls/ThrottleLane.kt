package com.bizi.roboapp.feature.drivecontrol.ui.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bizi.roboapp.feature.drivecontrol.domain.THROTTLE_LANE_DEAD_ZONE_RATIO
import com.bizi.roboapp.feature.drivecontrol.domain.THROTTLE_LANE_MAX_TRAVEL_RATIO
import com.bizi.roboapp.feature.drivecontrol.domain.ThrottleGuideSide
import com.bizi.roboapp.feature.drivecontrol.domain.chooseThrottleGuideSide
import com.bizi.roboapp.feature.drivecontrol.domain.clampThrottleCurrentY
import com.bizi.roboapp.feature.drivecontrol.domain.throttlePercentFromGesture

private val ThrottleLaneCorner = RoundedCornerShape(24.dp)
private val ThrottleLaneVerticalPadding = 12.dp
private val ActiveGuideRailOffset = 36.dp
private val ActiveGuideRailThickness = 38.dp
private val ActiveGuideRequiredWidth = 180.dp
private const val GuideCenterRingRadius = 30f
private const val GuideCenterCoreRadius = 15f
private const val ThumbRadius = 30f

@Composable
fun ThrottleLane(
    throttlePercent: Int,
    onThrottleChanged: (Int) -> Unit,
    onThrottleReleased: () -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var touchDownX by remember { mutableFloatStateOf(Float.NaN) }
    var originY by remember { mutableFloatStateOf(Float.NaN) }
    var currentY by remember { mutableFloatStateOf(Float.NaN) }
    var isActive by remember { mutableStateOf(false) }

    val onThrottleChangedState by rememberUpdatedState(onThrottleChanged)
    val onThrottleReleasedState by rememberUpdatedState(onThrottleReleased)
    val activeFillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
    val guideCenterRingColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val guideCenterCoreColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)
    val thumbColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
    val directionalHintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val paneFillColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val railColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    val guideSide = when {
        !isActive || touchDownX.isNaN() || size == IntSize.Zero -> ThrottleGuideSide.Right
        else -> chooseThrottleGuideSide(
            touchDownX = touchDownX,
            paneWidth = size.width.toFloat(),
            requiredWidthPx = ActiveGuideRequiredWidth.value * 1.2f
        )
    }

    Box(
        modifier = modifier
            .background(
                color = paneFillColor,
                shape = ThrottleLaneCorner
            )
            .onSizeChanged { size = it }
            .pointerInput(size) {
                if (size == IntSize.Zero) return@pointerInput

                val verticalPaddingPx = ThrottleLaneVerticalPadding.toPx()
                val laneTop = verticalPaddingPx
                val laneBottom = size.height.toFloat() - verticalPaddingPx
                val maxTravel = (laneBottom - laneTop) * THROTTLE_LANE_MAX_TRAVEL_RATIO
                val deadZone = maxTravel * THROTTLE_LANE_DEAD_ZONE_RATIO

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x !in 0f..size.width.toFloat() ||
                        down.position.y !in 0f..size.height.toFloat()
                    ) {
                        return@awaitEachGesture
                    }

                    val resolvedOriginY = down.position.y.coerceIn(laneTop, laneBottom)

                    isActive = true
                    touchDownX = down.position.x
                    originY = resolvedOriginY
                    currentY = resolvedOriginY

                    val postSlop = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ ->
                        val nextY = clampThrottleCurrentY(
                            rawY = change.position.y,
                            originY = resolvedOriginY,
                            maxTravelPx = maxTravel
                        )
                        currentY = nextY
                        onThrottleChangedState(
                            throttlePercentFromGesture(
                                originY = resolvedOriginY,
                                currentY = nextY,
                                maxTravelPx = maxTravel,
                                deadZonePx = deadZone
                            )
                        )
                        change.consume()
                    }

                    if (postSlop != null) {
                        drag(postSlop.id) { change ->
                            val nextY = clampThrottleCurrentY(
                                rawY = change.position.y,
                                originY = resolvedOriginY,
                                maxTravelPx = maxTravel
                            )
                            currentY = nextY
                            onThrottleChangedState(
                                throttlePercentFromGesture(
                                    originY = resolvedOriginY,
                                    currentY = nextY,
                                    maxTravelPx = maxTravel,
                                    deadZonePx = deadZone
                                )
                            )
                            change.consume()
                        }
                    }

                    isActive = false
                    touchDownX = Float.NaN
                    originY = Float.NaN
                    currentY = Float.NaN
                    onThrottleReleasedState()
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxTravel = (size.height - ThrottleLaneVerticalPadding.toPx() * 2f) * THROTTLE_LANE_MAX_TRAVEL_RATIO

            if (isActive && originY.isFinite() && currentY.isFinite() && touchDownX.isFinite()) {
                val guideTopY = originY - maxTravel
                val guideBottomY = originY + maxTravel
                val railX = when (guideSide) {
                    ThrottleGuideSide.Right -> (touchDownX + ActiveGuideRailOffset.toPx())
                        .coerceAtMost(size.width.toFloat() - 40f)
                    ThrottleGuideSide.Left -> (touchDownX - ActiveGuideRailOffset.toPx())
                        .coerceAtLeast(40f)
                }

                drawLine(
                    color = railColor,
                    start = Offset(railX, guideTopY),
                    end = Offset(railX, guideBottomY),
                    strokeWidth = ActiveGuideRailThickness.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = activeFillColor,
                    start = Offset(railX, originY),
                    end = Offset(railX, currentY),
                    strokeWidth = ActiveGuideRailThickness.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = guideCenterRingColor,
                    radius = GuideCenterRingRadius,
                    center = Offset(railX, originY)
                )
                drawCircle(
                    color = guideCenterCoreColor,
                    radius = GuideCenterCoreRadius,
                    center = Offset(railX, originY)
                )
                drawCircle(
                    color = thumbColor,
                    radius = ThumbRadius,
                    center = Offset(railX, currentY)
                )
            }
        }

        if (isActive && touchDownX.isFinite()) {
            val textModifier = when (guideSide) {
                ThrottleGuideSide.Right -> Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                ThrottleGuideSide.Left -> Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 18.dp)
            }

            Text(
                text = "${throttlePercent}%",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 52.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                textAlign = if (guideSide == ThrottleGuideSide.Right) TextAlign.End else TextAlign.Start,
                modifier = textModifier.heightIn(min = 72.dp)
            )
        }

        Text(
            text = "↑ Forward",
            style = MaterialTheme.typography.titleSmall,
            color = directionalHintColor,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 14.dp)
        )

        Text(
            text = "↓ Reverse",
            style = MaterialTheme.typography.titleSmall,
            color = directionalHintColor,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, bottom = 14.dp)
        )
    }
}
