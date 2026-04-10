# Android Control App

## Purpose
Describe both:
- the current Android operator client as implemented
- the recommended next control UX for touchscreen robot driving

This document keeps a strict distinction between live behavior and the proposed operator model for future phases.

## Current status
- `Implemented`: Compose UI with a signed throttle slider and a signed turn slider.
- `Implemented`: WebSocket client sending drive and stop commands.
- `Implemented`: connection-state and reconnect-aware transport handling in the WebSocket client.
- `Implemented`: WebSocket protocol now carries signed throttle / turn intent.
- `Not yet implemented`: touch-lane driving UI, haptic guidance, and dedicated emergency-brake UI.

## Implemented behavior
- App connects to `ws://10.0.0.186/ws`.
- Temporary throttle slider range is `-100..100`.
- Temporary turn slider range is `-100..100`.
- On movement, ViewModel treats both slider values as direct signed throttle / turn percentages and refreshes drive commands over WebSocket while the operator is actively driving.
- The current sliders are a temporary adapter over the intent protocol, not the long-term control surface.
- If the operator crosses directly from forward to reverse or reverse to forward, the app now sends an explicit stop and waits for the Pico stop acknowledgement before resuming in the new direction.
- On release, ViewModel sends a stop command.
- On turn slider release, ViewModel resets turn intent to straight.
- The app keeps explicit transport state and reconnects automatically with backoff.
- Reconnect does not auto-resume robot motion; a fresh operator drive command is required after reconnect.
- Recent transport events are retained in an in-memory buffer for diagnostics.

## Design conclusion
The recommended touchscreen driving UX is not a single unrestricted virtual joystick.

For this robot, the preferred direction is a `landscape`, `two-thumb`, `axis-locked` control surface:
- left half controls `Throttle` on a vertical lane
- right half controls `Turn` on a horizontal lane
- each control is floating from the operator's touch-down point
- each control is constrained to one axis after touch-down

This keeps the main strength of touch input, direct manipulation, while removing the biggest weakness of a free 2D stick: accidental cross-axis drift when the operator is trying to drive straight.

## Why this direction
The recommendation is based on three observations:
- Touch-only controls are generally less precise than physical controls, so the UI should reduce degrees of freedom instead of increasing them.
- Existing professional and commercial robot systems still use joystick-like metaphors, but add guidance or heading-hold behaviors rather than expecting raw finger precision alone.
- Android haptics guidance supports subtle, event-driven feedback, which is useful for center detents, threshold ticks, and confirmation events, but should not become a constant buzzing control layer.

## Recommended operator UX

### Layout
- Default driving orientation should be `landscape`.
- Screen is split into a `left Throttle zone` and `right Turn zone`.
- A compact top overlay should show:
  - connection state
  - battery / telemetry summary
  - current throttle percentage
  - current turn percentage
  - emergency stop button
- The driving zones should remain visually quiet until touched.

### Touch model
- First finger on the left half captures the Throttle lane.
- First finger on the right half captures the Turn lane.
- Each lane anchors at the initial touch-down point, not at a fixed on-screen knob.
- On touch-down, show a temporary guide:
  - left side: vertical reference line with center marker
  - right side: horizontal reference line with center marker
- Motion is measured relative to the touch-down origin.
- The control remains axis-locked for the duration of that touch.
- The touch-down origin must be clamped to a usable region so the operator always has meaningful travel on both sides of the origin.
- Releasing the Throttle finger returns throttle intent to zero.
- Releasing the Turn finger returns turn intent to center.
- Releasing all driving touches sends stop or neutral-drive intent immediately.

### Touch origin constraints
- The lane origin should not be allowed too close to the physical edge of its usable travel.
- For the left Throttle zone, touch-down should resolve to an origin that leaves enough travel both upward and downward.
- For the right Turn zone, touch-down should resolve to an origin that leaves enough travel both leftward and rightward within the right-half turn area.
- In practice, this means the visible lane length should define a protected margin:
  - throttle origin stays at least `lane_length / 2` away from the top and bottom limits of the Throttle zone
  - turn origin stays at least `lane_length / 2` away from the left limit of the Turn zone divider and the outer right edge of the screen
- If the initial finger-down is outside that usable origin region, the app should still accept the touch, but the displayed origin should snap or clamp to the nearest valid point.
- This avoids the failure mode where a user begins too close to an edge and immediately loses authority in one direction.

### Throttle behavior
- Operator-facing Throttle should be expressed as a signed percentage, not RPM.
- Up from the origin means forward.
- Down from the origin means reverse.
- Displacement magnitude maps to throttle intent magnitude.
- A small center dead zone prevents creep from finger jitter.
- Android should treat this as `throttle_percent`.
- The Pico-side controller can translate throttle intent into closed-loop motor control, minimum startup output, signed PWM, target RPM, and PID behavior.

### Turn behavior
- Right-lane control should be expressed as a normalized `Turn` command, not an absolute steering angle.
- Left from the origin means turn left.
- Right from the origin means turn right.
- Displacement magnitude maps to turn authority from center to full left or full right.
- A center dead zone should create an easy straight-driving band.
- For the current robot, which is driven by two motor outputs rather than a dedicated steering axle, the operator-facing value should be a signed turn intent such as `turn_percent` in `-100..100`.
- The Pico-side controller can translate turn intent into left/right track imbalance, differential RPM targets, PWM differences, or another drivetrain-specific internal representation.
- If the platform later adopts true steering geometry, the UI may additionally expose a derived angle readout, but that should be treated as a platform-specific mode rather than the default abstraction.

### Recommended mapping
- Use non-linear response curves.
- Near center, response should be gentler for fine control.
- Far from center, response should ramp faster to full authority.
- Turn should saturate before the thumb reaches the physical edge of the display so the operator does not need extreme thumb travel.
- Throttle should support a wider precision band around low-to-mid speed than around full speed.
- The center is not a single pixel target. It should be a small neutral band that is easy to re-enter while driving.
- The neutral band should be large enough to absorb small thumb jitter, but small enough that intentional low-speed commands still feel responsive.

### Dead zones and thresholds
- The center dead zone is a `zone`, not a point.
- Throttle dead zone:
  - prevents unwanted creep when the thumb is near the origin
  - should return a clean neutral command inside the band
- Turn dead zone:
  - creates a practical straight-driving corridor
  - should be easier to hold than a mathematically exact zero
- The app may also use additional thresholds beyond the center dead zone:
  - a small movement threshold before the lane becomes "actively driving"
  - threshold markers for haptic ticks or visual scale steps
- These thresholds should be tuned on-device with actual thumb use, not chosen only from theory.

### Visual feedback
- Show the touch origin and current thumb offset while pressed.
- Fill the active lane proportionally from origin to current command.
- Show a center detent marker on both lanes.
- Show explicit `F`, `R`, `L`, `R` labels only when needed for first-use guidance, not as always-on clutter.
- Use shape and motion, not color alone, to indicate active state and command magnitude.

### Haptic feedback
- Haptics should be subtle and event-based.
- Recommended haptic events:
  - light confirmation on touch capture
  - light tick when crossing center
  - sparse ticks at meaningful thresholds such as `25%`, `50%`, `75%`, `100%`
  - stronger confirmation for emergency stop
- Do not use continuous vibration to represent motor power.
- If richer haptics are used, provide safe fallback behavior for devices that only support basic haptic constants.
- Current Android APIs suggest using predefined gesture and tick semantics where possible rather than inventing bespoke constant buzzing patterns.
- Candidate mappings for implementation:
  - touch capture: `GESTURE_START` or `DRAG_START`
  - threshold crossing: `GESTURE_THRESHOLD_ACTIVATE` and `GESTURE_THRESHOLD_DEACTIVATE`
  - discrete command bands: `SEGMENT_TICK` or `SEGMENT_FREQUENT_TICK`
  - emergency stop confirmation: `CONFIRM`
- Final haptic tuning should be validated on the target phone hardware because Android actuator quality varies substantially by device.

## Safety and reliability requirements
- Driving remains a foreground, screen-on workflow.
- Reconnect must never auto-resume motion.
- Transport timeout or disconnect must result in safe neutral / stop behavior on the robot.
- Crossing directly from forward to reverse should continue to require a stop handoff or a controlled neutral pass-through.
- Emergency stop must be visible without covering the main touch lanes.
- The UI must remain usable with one thumb temporarily lifted; loss of Turn input should recenter turn, loss of Throttle input should remove drive intent.

## Control semantics
- The app should send `operator intent`, not UI-specific widget state.
- The preferred operator vocabulary is:
  - `throttle_percent`
  - `turn_percent`
  - `drive_state`
  - `stop`
- The app should not expose motor RPM as the main user-facing control language.
- Robot firmware may still use RPM, PWM, startup boost, track-mixing logic, and PID internals behind the protocol boundary.

## Rejected alternatives

### Single unrestricted 2D joystick
Not preferred because:
- it couples throttle and steering on the same finger
- it makes straight-line driving fragile on glass
- it increases accidental turning while the operator is trying to hold forward or reverse

### Tilt as the primary driving method
Not preferred because:
- it is harder to keep stable while holding a phone for live teleoperation
- it conflicts with camera viewing and body posture
- it is better treated as an experiment or secondary assist mode, not the default drive control

### Fixed on-screen sticks only
Not preferred because:
- fixed controls waste space
- they force the operator to reacquire exact target locations
- floating touch origins better fit large phones and different hand positions

## Phase outline

### Phase 1: Replace slider with split touch lanes
Change the active WebSocket protocol to signed `throttle_percent` / `turn_percent` intent while keeping the current percentage sliders as the temporary input widgets.

### Phase 2: Improve precision and feedback
Replace the temporary sliders with the planned split touch-lane UI and tune non-linear response curves, dead zones, clamping, and visual feedback.

### Phase 3: Improve control semantics
- Keep firmware authority on closed-loop control and startup thresholds.
- Keep Android percentages clearly separated from Pico internal RPM / PWM variables.
- Add stale-command timeout and neutral-hold semantics explicitly to protocol and robot behavior.

### Phase 4: Add assistive driving features
- Add straight-driving assist around zero turn.
- Consider heading-hold when turn remains near center.
- Consider mode switching for different robot platforms if drivetrain geometry changes later.

## Research notes
- Professional robot UX still uses joystick-like control metaphors, but often adds guidance. iRobot's `uPoint` controller described a virtual joystick on the video feed, predictive drive lines, and a `vector drive` mode to hold heading.
- DJI's RoboMaster product line supports touchscreen driving and a physical gamepad, which is a useful reminder that touch must compete with more precise physical control. The RoboMaster programming guide also documents virtual joystick overlay concepts and normalized joystick sensitivity in the `-1..1` range.
- Mobile input studies repeatedly show that touch-only controls are less precise than physical controls for demanding control tasks. That supports reducing accidental coupling and adding tactile / visual guidance.
- Android's current haptics guidance recommends restrained, meaningful haptic events and explicitly warns against overusing vibration.
- Android now exposes predefined haptic constants for gesture start, drag start, threshold crossing, and segmented ticks. That makes threshold-based feedback a better fit than custom continuous power-simulation vibration.

## Open questions
- Exact lane length and origin-clamp margins should be tuned against the target phone size and operator hand posture.
- Dead-zone size should be tested on hardware with gloves off and with realistic thumb jitter, not guessed up front.
- Haptic intensity and frequency need bench testing on the actual Android device before they should be treated as part of the default control contract.
- If the robot later exposes heading hold or steering geometry more explicitly, the right lane may need a mode distinction between `manual turn command` and a platform-specific held-angle or heading mode.

## Notes
The current app is still a thin operator client. It now speaks intent-based WebSocket control, but it still exposes transitional percentage sliders instead of the richer touch-lane control model described here.

This proposal assumes:
- Android owns operator interaction and connection lifecycle
- Pico owns real-time motor safety and final authority over motion
- The current drivetrain is differential / skid-style motor control, not a dedicated front-wheel steering axle

If future firmware introduces ackermann steering, differential mixing, heading hold, or velocity hold, the app should still preserve the same operator-facing abstraction unless there is a clear usability reason to surface a different one.

## Sources
- Code: [HomeScreen.kt](../app/src/main/java/com/bizi/roboapp/feature/drivecontrol/ui/HomeScreen.kt)
- Code: [HomeViewModel.kt](../app/src/main/java/com/bizi/roboapp/feature/drivecontrol/ui/HomeViewModel.kt)
- Code: [NavigationProtocol.kt](../app/src/main/java/com/bizi/roboapp/core/protocol/navigation/NavigationProtocol.kt)
- Code: [NavigationWebSocketApi.kt](../app/src/main/java/com/bizi/roboapp/core/transport/NavigationWebSocketApi.kt)
- Shared doc: [operator-control-model.md](../../docs/control-and-apps/operator-control-model.md)
- External reference: iRobot uPoint controller announcement, October 9, 2014, https://media.irobot.com/2014-10-09-iRobot-Unveils-Its-First-Multi-Robot-Tablet-Controller-for-First-Responders-Defense-Forces-and-Industrial-Customers
- External reference: DJI RoboMaster S1 support FAQ, https://www.dji.com/mobile/support/product/robomaster-s1
- External reference: DJI RoboMaster S1 programming guide, https://www.dji.com/robomaster-s1/programming-guide
- External reference: Android Developers, Haptics design principles, https://developer.android.com/develop/ui/views/haptics/haptics-principles
- External reference: Android Developers, Haptics API reference, https://developer.android.com/develop/ui/views/haptics/haptics-apis
- External reference: Android Developers, HapticFeedbackConstants, https://developer.android.com/reference/android/view/HapticFeedbackConstants
- External reference: Seo and Kang, "A Comparison Study of the Smartphone Gaming Control", Journal of Usability Studies, 2019, https://uxpajournal.org/comparison-study-smartphone-gaming-control/
- External reference: Zaman and MacKenzie, "Evaluation of Nano-stick, Foam Buttons, and Other Input Methods for Gameplay on Touchscreen Phones", 2013, https://www.yorku.ca/mack/mhci2013a.html
