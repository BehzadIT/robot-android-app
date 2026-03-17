package com.bizi.roboapp

import com.bizi.roboapp.feature.drivecontrol.domain.ThrottleGuideSide
import com.bizi.roboapp.feature.drivecontrol.domain.chooseThrottleGuideSide
import com.bizi.roboapp.feature.drivecontrol.domain.clampThrottleCurrentY
import com.bizi.roboapp.feature.drivecontrol.domain.throttlePercentFromGesture
import org.junit.Assert.assertEquals
import org.junit.Test

class ThrottleLaneMathTest {
    @Test
    fun chooseGuideSide_prefersRightWhenSpaceIsAvailable() {
        assertEquals(
            ThrottleGuideSide.Right,
            chooseThrottleGuideSide(
                touchDownX = 120f,
                paneWidth = 400f,
                requiredWidthPx = 180f
            )
        )
    }

    @Test
    fun chooseGuideSide_flipsLeftWhenRightSpaceIsTooSmall() {
        assertEquals(
            ThrottleGuideSide.Left,
            chooseThrottleGuideSide(
                touchDownX = 280f,
                paneWidth = 400f,
                requiredWidthPx = 180f
            )
        )
    }

    @Test
    fun clampCurrent_limitsTravelAroundOrigin() {
        assertEquals(40f, clampThrottleCurrentY(10f, 60f, 20f))
        assertEquals(80f, clampThrottleCurrentY(90f, 60f, 20f))
        assertEquals(55f, clampThrottleCurrentY(55f, 60f, 20f))
    }

    @Test
    fun deadZone_returnsZero() {
        assertEquals(0, throttlePercentFromGesture(50f, 48f, 40f, 5f))
        assertEquals(0, throttlePercentFromGesture(50f, 52f, 40f, 5f))
    }

    @Test
    fun upwardTravel_isPositiveAndDownwardTravel_isNegative() {
        assertEquals(14, throttlePercentFromGesture(50f, 40f, 40f, 5f))
        assertEquals(-14, throttlePercentFromGesture(50f, 60f, 40f, 5f))
    }

    @Test
    fun fullTravel_saturatesAtSignedHundred() {
        assertEquals(100, throttlePercentFromGesture(50f, 0f, 40f, 5f))
        assertEquals(-100, throttlePercentFromGesture(50f, 100f, 40f, 5f))
    }
}
