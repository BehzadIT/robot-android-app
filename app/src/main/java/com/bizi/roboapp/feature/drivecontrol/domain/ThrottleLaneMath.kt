package com.bizi.roboapp.feature.drivecontrol.domain

import kotlin.math.abs
import kotlin.math.roundToInt

enum class ThrottleGuideSide {
    Left,
    Right
}

fun chooseThrottleGuideSide(
    touchDownX: Float,
    paneWidth: Float,
    requiredWidthPx: Float
): ThrottleGuideSide {
    val rightSpace = paneWidth - touchDownX
    return if (rightSpace >= requiredWidthPx) {
        ThrottleGuideSide.Right
    } else {
        ThrottleGuideSide.Left
    }
}

fun clampThrottleCurrentY(
    rawY: Float,
    originY: Float,
    maxTravelPx: Float
): Float {
    return rawY.coerceIn(originY - maxTravelPx, originY + maxTravelPx)
}

fun throttlePercentFromGesture(
    originY: Float,
    currentY: Float,
    maxTravelPx: Float,
    deadZonePx: Float
): Int {
    require(maxTravelPx > 0f) { "maxTravelPx must be > 0" }
    require(deadZonePx >= 0f) { "deadZonePx must be >= 0" }

    val clampedCurrent = clampThrottleCurrentY(currentY, originY, maxTravelPx)
    val signedDisplacement = originY - clampedCurrent
    val magnitude = abs(signedDisplacement)

    if (magnitude <= deadZonePx || deadZonePx >= maxTravelPx) {
        return 0
    }

    val normalized = ((magnitude - deadZonePx) / (maxTravelPx - deadZonePx))
        .coerceIn(0f, 1f)
    val percent = (normalized * 100f).roundToInt()
    return if (signedDisplacement > 0f) percent else -percent
}
