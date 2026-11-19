# Android App Documentation

This directory contains Android-specific implementation documentation for the
robot operator app.

Use the parent `../docs` directory only when this repo is checked out inside the
`ai-robot` workspace. The Android repo remains usable as an independent Git
repository.

## Documents
- `operator-control-ui.md`: Android operator UI behavior, app-side command
  semantics, and future UX direction.
- `floating-touch-lanes.md`: detailed implementation plan for the split
  floating touch-lane control surface.
- `code-documentation.md`: how to document Kotlin transport and state-holder
  behavior with robotics-specific ownership and safety context.

## Agent Verification Adapter

`python3 tools/robot_adapter.py doctor --json` performs read-only Android
toolchain/device discovery. `verify android-default --json` owns the repository
Gradle compile, unit-test, and lint workflow. Device installation and
instrumentation remain explicit Gate 1 scenarios selected by the parent CLI.

## Parent Context
When available, read:
- `../../docs/overview/current-architecture.md`
- `../../docs/interfaces/command-protocol.md`
- `../../docs/control-and-apps/operator-control-model.md`
