package com.bizi.roboapp.feature.drivecontrol.domain

/**
 * Shared Android-side control constants for the drivetrain UI and transport.
 *
 * Positive turn means right.
 * Negative turn means left.
 */
const val DRIVE_REFRESH_MS = 50L
const val DRIVE_STOP_ENTER_DEADBAND_PERCENT = 2
const val DRIVE_START_EXIT_DEADBAND_PERCENT = 3
const val THROTTLE_SLIDER_MIN = -100f
const val THROTTLE_SLIDER_MAX = 100f
const val TURN_SLIDER_MIN = -100f
const val TURN_SLIDER_MAX = 100f
const val TURN_SLIDER_DEFAULT = 0
