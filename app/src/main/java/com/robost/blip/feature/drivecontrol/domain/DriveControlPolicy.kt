package com.robost.blip.feature.drivecontrol.domain

/**
 * Shared Android-side control constants for the drivetrain UI and transport.
 *
 * Positive steering angle means steer right.
 * Negative steering angle means steer left.
 */
const val DRIVE_REFRESH_MS = 50L
const val DRIVE_STOP_ENTER_DEADBAND_RPM = 5
const val DRIVE_START_EXIT_DEADBAND_RPM = 8
const val STEERING_ANGLE_MIN = -90f
const val STEERING_ANGLE_MAX = 90f
const val STEERING_ANGLE_DEFAULT = 0
