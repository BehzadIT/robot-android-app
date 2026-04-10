# Temporary Implementation Note: Floating Touch Lanes for Android Robot Control

## Purpose
Capture the implementation approach for replacing the current throttle and turn sliders with a `landscape`, `two-thumb`, `axis-locked`, `floating-origin` touch control surface in the Android app.

This note is temporary and implementation-oriented. It also records what is useful and not useful from the two joystick repos reviewed:
- `/home/behzad/Projects/compose-virtualjoystick-multiplatform`
- `/home/behzad/Projects/virtual-joystick-compose`

## Current app context
- The current screen uses two Compose sliders in [HomeScreen.kt](../app/src/main/java/com/bizi/roboapp/feature/drivecontrol/ui/HomeScreen.kt).
- The current ViewModel already works in the correct operator-intent vocabulary:
  - `throttlePercent`
  - `turnPercent`
  - `stop`
- The current drive loop and stop-ack handoff already exist in [HomeViewModel.kt](../app/src/main/java/com/bizi/roboapp/feature/drivecontrol/ui/HomeViewModel.kt).

That means the main change is the input surface and local touch mapping, not the transport model.

## High-level conclusion
Do not adopt either joystick repo directly.

Instead, implement a custom Compose control surface with:
- one left `Throttle lane`
- one right `Turn lane`
- one active pointer per lane
- floating origin captured from touch-down
- immediate axis lock per lane
- dead zone around origin
- normalized signed output in `-100..100`
- release-to-neutral behavior per lane

This matches the existing robot control docs better than a circular joystick.

## Research synthesis
Recent Compose-era Android guidance and the repo review point to the same conclusion: this should be a custom touch surface, not a generic joystick dependency.

What to carry forward from the external research:
- Official Compose gesture guidance supports `pointerInput` for custom gesture handling and `awaitEachGesture` for touch-down-to-release ownership.
- Axis-specific touch-slop APIs fit this UI better than a generic joystick detector because throttle and turn are separate one-axis controls.
- The 2024 Droidcon / Android Makers joystick talk reinforces the need for `pointer ownership` when two controls can be active at the same time.
- Android game-controller guidance uses a center `flat` region. That maps cleanly to this app's throttle neutral band and turn straight-driving band.
- Android haptics guidance favors sparse, event-based feedback rather than continuous buzzing.

Why not a library:
- None of the reviewed implementations matched `floating origin + split halves + one pointer per lane + protected origin margins + axis lock + displacement dead zone`.
- The robot app already has the correct ViewModel-level drive semantics, so a library joystick would mainly add the wrong abstraction rather than solving the hard part.

## What is useful from the reviewed repos

### From `compose-virtualjoystick-multiplatform`
Useful ideas:
- Keep gesture handling separate from render code.
- Keep a dedicated state object that owns:
  - measured size
  - current pointer-derived position
  - derived normalized output
  - active/inactive state
- Expose clear gesture lifecycle events:
  - start
  - move
  - held
  - end
- Reset UI state immediately on gesture end.
- Keep drawing customizable and independent from input math.

Files worth learning from:
- [VirtualJoystick.kt](/home/behzad/Projects/compose-virtualjoystick-multiplatform/library/src/commonMain/kotlin/io/github/yoimerdr/compose/virtualjoystick/ui/view/VirtualJoystick.kt)
- [JoystickState.kt](/home/behzad/Projects/compose-virtualjoystick-multiplatform/library/src/commonMain/kotlin/io/github/yoimerdr/compose/virtualjoystick/ui/state/JoystickState.kt)
- [JoystickGestures.kt](/home/behzad/Projects/compose-virtualjoystick-multiplatform/library/src/commonMain/kotlin/io/github/yoimerdr/compose/virtualjoystick/extensions/modifier/gestures/JoystickGestures.kt)

Not suitable as-is:
- It is built around a fixed-center circular joystick.
- It models `direction`, `angle`, and circular `strength`, which is not the right abstraction for split throttle/turn lanes.
- Its default gesture model is single-control oriented, not left-lane plus right-lane ownership.

Important caution:
- In [MovementGestures.kt](/home/behzad/Projects/compose-virtualjoystick-multiplatform/library/src/commonMain/kotlin/io/github/yoimerdr/compose/virtualjoystick/extensions/modifier/gestures/MovementGestures.kt), the optional threshold is checked against per-event delta, not total displacement from touch-down. That is not the right dead-zone or activation model for this robot UI.

### From `virtual-joystick-compose`
Useful ideas:
- It explicitly supports a floating center set from touch-down.
- It explicitly supports axis restriction.
- It normalizes control output to percentages.
- It clamps displacement to a maximum travel boundary.

File worth learning from:
- [JoystickView.java](/home/behzad/Projects/virtual-joystick-compose/virtualjoystick/src/main/java/io/github/controlwear/virtual/joystick/android/JoystickView.java)

Not suitable as-is:
- It is a legacy View widget, not Compose.
- It uses angle/strength as the primary API.
- It is still fundamentally a circular joystick, not a lane-based control surface.
- It uses a periodic callback thread inside the widget, while the robot app already has a ViewModel refresh loop.
- It has no proper center dead-zone semantics for robot straight-hold / neutral-hold behavior.
- It does not implement protected origin margins for usable travel on both sides of the floating origin.

## Required behavior for the robot app

### Layout
- Orientation is `landscape` by default for driving.
- Screen is split into two halves:
  - left half = `Throttle`
  - right half = `Turn`
- A compact top overlay shows:
  - connection state
  - latest telemetry summary if available
  - live throttle percent
  - live turn percent
  - emergency stop

### Pointer ownership
- The first pointer that starts in the left half owns the throttle lane.
- The first pointer that starts in the right half owns the turn lane.
- Additional pointers in the same half are ignored while that lane is active.
- Losing the throttle pointer returns throttle to zero immediately.
- Losing the turn pointer returns turn to center immediately.
- Losing both returns the operator command to neutral and sends stop according to existing ViewModel semantics.

### Floating origin
- Each lane captures a floating origin at touch-down.
- The origin is not rendered until touch-down.
- The origin is clamped into a valid usable region before it is stored.
- The displayed guide origin may differ slightly from the raw finger-down point if clamping is needed.

### Axis lock
- Throttle lane only reacts to `vertical` displacement.
- Turn lane only reacts to `horizontal` displacement.
- Cross-axis motion is ignored after capture.

### Dead zone
- Each lane has a dead zone centered on the floating origin.
- Inside the dead zone, output is exactly zero.
- Dead zone must be applied against displacement from the lane origin, not event-to-event movement.

### Output mapping
- Throttle:
  - up = positive forward
  - down = negative reverse
- Turn:
  - left = negative turn
  - right = positive turn
- Both outputs are normalized to signed percentages in `-100..100`.
- Mapping should allow:
  - a neutral band around zero
  - fine control near center
  - faster ramp farther from center

### Release behavior
- Throttle pointer release:
  - throttle goes to `0`
  - existing stop / drive loop semantics continue to be owned by `HomeViewModel`
- Turn pointer release:
  - turn goes to `0`
- All lanes released:
  - no input remains active
  - UI resets to neutral visuals

## Geometry and mapping rules

### Lane geometry
- Use rectangular lane regions, not circles.
- Track one scalar displacement per lane:
  - throttle uses `deltaY`
  - turn uses `deltaX`

### Origin clamp margins
- Each lane origin must leave usable travel on both sides.
- Do not allow the stored origin too close to the lane edge.

Recommended rule:
- Choose a `laneTravelPx` for each lane.
- Clamp origin so there is at least `laneTravelPx / 2` available in both directions along the active axis.

This gives:
- throttle origin enough room above and below
- turn origin enough room left and right

### Value mapping pipeline
For both lanes, use this order:
1. Compute signed scalar displacement from origin on the active axis.
2. Clamp displacement to `[-maxTravel, +maxTravel]`.
3. Apply dead zone:
   - if `abs(displacement) <= deadZonePx`, output = `0`
4. Renormalize the remaining travel outside dead zone.
5. Apply non-linear curve.
6. Convert to signed percent in `-100..100`.

Suggested curve for v1:
- `curved = sign(x) * abs(x)^1.5`

Where:
- `x` is normalized scalar displacement after dead-zone removal in `[-1, 1]`

This gives smoother control near center without hiding full-scale authority.

## Compose implementation approach

### Recommendation
Implement this as a custom `pointerInput` control, not as a library joystick.

Primary path:
- `awaitEachGesture`
- `awaitFirstDown`
- `awaitVerticalTouchSlopOrCancellation` for `Throttle`
- `awaitHorizontalTouchSlopOrCancellation` for `Turn`
- pointer-id ownership per lane
- explicit drag loop per active pointer

Fallback only:
- `detectDragGestures(orientationLock = ...)` if a simpler but less controllable implementation is temporarily needed

Do not use:
- `Slider`
- circular joystick math
- angle/direction enums as the main control contract
- per-event delta thresholds as the dead-zone rule

Implementation note:
- Capture raw down position first.
- Clamp the lane origin immediately after capture.
- Compute the dead zone from `currentPosition - origin`, not from `currentPosition - previousPosition`.
- Axis lock should apply to command generation from the start of the gesture lifecycle for that lane.

### Suggested UI structure
- `feature/drivecontrol/ui/DriveControlScreen.kt`
- `feature/drivecontrol/ui/controls/TouchLaneSurface.kt`
- `feature/drivecontrol/ui/controls/TouchLaneState.kt`
- `feature/drivecontrol/ui/controls/TouchLaneModels.kt`

Suggested responsibilities:
- `DriveControlScreen`
  - overall layout
  - top overlay
  - emergency stop placement
  - wiring lane outputs to ViewModel callbacks
- `TouchLaneSurface`
  - pointer capture
  - lane ownership
  - rendering temporary guides and fill
- `TouchLaneState`
  - origin
  - current pointer position
  - active flag
  - normalized signed output
- `TouchLaneModels`
  - geometry config
  - dead-zone config
  - response-curve helpers

### State model
Each lane should track:
- `isActive`
- `pointerId`
- `originPx`
- `currentPx`
- `clampedCurrentPx`
- `signedPercent`
- `displayFillFraction`

App-level drive screen should derive:
- `throttlePercent`
- `turnPercent`
- current active/inactive visual state

## Integration with the current ViewModel
- Keep `HomeViewModel` as the owner of:
  - drive loop timing
  - stop handoff
  - reconnect behavior
  - recover command
- Replace slider callbacks with lane callbacks that emit the same semantics:
  - `onThrottleChanged(percent: Int)`
  - `onThrottleReleased()`
  - `onTurnChanged(percent: Int)`
  - `onTurnReleased()`

The touch surface should not send WebSocket messages directly.

## Visual behavior
- Quiet background when inactive.
- On touch-down, show:
  - lane origin marker
  - one-axis guide line
  - center detent marker
- While active, show:
  - fill from origin to current value
  - stronger thumb indicator
  - current signed percent in overlay
- Use shape, length, and motion as the primary cue.
- Do not rely on color only.

## Haptics
Recommended events:
- light tick on capture
- light tick when crossing zero
- optional sparse ticks at `25%`, `50%`, `75%`, `100%`
- stronger confirmation on emergency stop

Do not simulate motor power with constant vibration.

Implementation preference:
- Use `LocalView.current.performHapticFeedback(...)` when an exact Android haptic constant is required.
- Use `LocalHapticFeedback` only for generic feedback cases where exact Android constant control is not necessary.

## Android API status
Checked against current Android docs as of `March 17, 2026`.

Current and acceptable for this implementation:
- `Modifier.pointerInput`
- `awaitEachGesture`
- `awaitFirstDown`
- `awaitTouchSlopOrCancellation`
- `awaitHorizontalTouchSlopOrCancellation`
- `awaitVerticalTouchSlopOrCancellation`
- `detectDragGestures(orientationLock = ...)`
- `View.performHapticFeedback(...)`
- `HapticFeedbackConstants.CONFIRM`
- `HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE`
- `HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE`
- `HapticFeedbackConstants.SEGMENT_TICK`
- `HapticFeedbackConstants.SEGMENT_FREQUENT_TICK`

Deprecated or avoided:
- `forEachGesture`
  - replacement: `awaitEachGesture`
- `HapticFeedbackConstants.KEYBOARD_PRESS`
  - replacement: `HapticFeedbackConstants.KEYBOARD_TAP`

Guidance:
- Prefer the low-level `await*TouchSlopOrCancellation` APIs for the lane implementation because they align with floating-origin capture and strict per-lane axis ownership.
- Keep `detectDragGestures(orientationLock = ...)` as a secondary option, not the main design choice.
- Guard newer haptic constants by Android API level if needed and fall back to lighter generic feedback when unavailable.

## Testing checklist

### Gesture behavior
- Touch in left half only moves throttle.
- Touch in right half only moves turn.
- Cross-axis thumb drift does not affect output.
- Releasing one lane resets only that lane.
- Two simultaneous thumbs can control both lanes independently.
- A second pointer in an already-owned lane does not steal control.

### Geometry behavior
- Touching near an edge still creates a usable clamped origin.
- Full command can be reached without the finger leaving the display.
- Dead zone cleanly returns zero without jitter.
- Sign mapping is correct:
  - up = forward
  - down = reverse
  - left = left turn
  - right = right turn

### Integration behavior
- Throttle release triggers the same stop behavior as slider release today.
- Turn release recenters steering without stopping active throttle.
- Reconnect still does not auto-resume motion.
- Forward-to-reverse crossover still performs stop-ack handoff.

## Implementation defaults for the first pass
- `landscape` only for the drive screen
- one active pointer per lane
- rectangular lanes
- axis-locked from touch-down
- dead zone enabled for both lanes
- cubic-like non-linear response using exponent `1.5`
- no heading-hold logic in v1
- no assist mode in v1
- no library dependency for joystick input

## Phased rollout
Implement this feature in phases rather than as one large control-surface rewrite.

### Phase 1: Throttle-only lane
Replace the current drive slider with a left-side floating `Throttle` lane and remove the turn slider UI from the screen.

Phase 1 goals:
- keep the screen in `landscape`
- dedicate most of the screen to the left-side throttle lane
- keep a persistent bottom status strip for:
  - WebSocket connection state
  - current throttle value
  - stop / idle / driving status
  - latest transport event
  - recover action
- force `turnPercent = 0` from the UI while turn control is not yet implemented
- use a floating origin, vertical-only movement, origin clamping, a small dead zone, and `linear` output mapping
- keep release-to-stop behavior fully owned by the existing `HomeViewModel`

Phase 1 explicitly does not include:
- turn lane
- dual-pointer ownership
- haptic feedback
- non-linear response curves
- visual polish beyond what is needed for clarity

Why start here:
- it validates the core floating-origin lane model
- it keeps command semantics simple
- it allows practical testing without the harder simultaneous-control problem

### Phase 2: Turn lane
Add the right-side floating `Turn` lane after throttle-only behavior is stable.

Phase 2 goals:
- reuse the same lane model as throttle
- use horizontal-only movement
- use clamped origin and center dead zone
- keep output mapping `linear`
- release turn back to `0` without stopping active throttle

Phase 2 still does not include:
- haptics
- non-linear tuning
- heading assist

### Phase 3: Dual-lane behavior hardening
Once both lanes exist, focus on simultaneous use and correctness.

Phase 3 goals:
- one active pointer per lane
- simultaneous throttle + turn control
- no control stealing inside an already-owned lane
- correct release semantics per lane
- correct reconnect and stop behavior when a lane is lost
- verify forward/reverse stop-ack handoff still behaves correctly with touch lanes

### Phase 4: Tuning and visual improvement
Only after the control model is operationally correct:
- tune dead-zone sizes
- add non-linear response curves
- improve guide lines, fills, and active-state visuals
- refine status strip layout and readability

### Phase 5: Optional assistive feedback
Only after control and tuning are stable:
- add haptic feedback
- add threshold ticks
- evaluate straight-driving assist or later operator aids

Current implementation recommendation:
- build and verify `Phase 1` first
- do not include haptics in the first implementation
- do not include non-linear curves in the first implementation
- keep the bottom status area visible from the start so the operator retains transport awareness

## Bottom line
The repos were useful as references for:
- floating center
- state separation
- clamping
- lifecycle events

But the robot app should implement its own Compose touch lanes because the required model is:
- split by function
- axis locked
- origin clamped inside usable margins
- dead-zone driven
- percentage output based
- integrated with an existing ViewModel command loop

That is materially different from a reusable circular joystick widget.

## References
- Android Developers, Pointer input overview: https://developer.android.com/develop/ui/compose/touch-input/pointer-input
- Android Developers, Understand gestures: https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures
- AndroidX gestures package summary: https://developer.android.com/reference/kotlin/androidx/compose/foundation/gestures/package-summary
- Android game controller input guide: https://developer.android.com/games/sdk/game-controller/controller-input
- Android Developers, Haptics design principles: https://developer.android.com/develop/ui/views/haptics/haptics-principles
- Android Developers, Haptic feedback guide: https://developer.android.com/develop/ui/views/haptics/haptic-feedback
- Android Developers, `HapticFeedbackConstants`: https://developer.android.com/reference/android/view/HapticFeedbackConstants
- Android Developers, `View.performHapticFeedback(...)`: https://developer.android.com/reference/android/view/View#performHapticFeedback(int)
- Android Developers, `LocalHapticFeedback`: https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/LocalHapticFeedback
- Android Makers / Droidcon 2024 joystick talk page: https://www.droidcon.com/2024/06/11/building-a-joystick-controller-using-compose-multiplatform/
- Android Makers / Droidcon 2024 joystick slides: https://speakerdeck.com/renaudmathieu/building-a-joystick-controller-using-compose-multiplatform
- Local repo reviewed: `/home/behzad/Projects/compose-virtualjoystick-multiplatform`
- Local repo reviewed: `/home/behzad/Projects/virtual-joystick-compose`
