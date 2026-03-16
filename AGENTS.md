# AGENTS.md

## Purpose
This repo contains the Android operator app for the robot project.

## Instructions
- Before changing code here, read the shared docs root at `robot-project-docs`.
- At minimum, review:
  - `robot-project-docs/overview/current-architecture.md`
  - `robot-project-docs/control-and-apps/android-control-app.md`
  - `robot-project-docs/control-and-apps/operator-control-model.md`
  - `robot-project-docs/interfaces/command-protocol.md`
  - `robot-project-docs/verification/known-issues-and-open-questions.md`
- Keep a clear distinction between current implemented behavior and intended future operator model.
- Do not assume the ESP32 is the command endpoint; verify command routing against the shared docs and current code.
- When making non-trivial decisions, state the relevant best practice, design pattern, technology choice, and architecture tradeoff.
- Prefer reliability-oriented networking patterns for mobile clients: explicit connection state, bounded reconnect/backoff, keepalive configured for the actual engine in use, and transport observability.
- Log meaningful transport and protocol events, but classify expected reconnects as normal operational signals rather than fatal failures.
- Code documentation matters in this project. Add clear comments or docstrings for non-obvious behavior, especially around connection lifecycle, retry/backoff policy, protocol semantics, state ownership, and failure handling.
- Avoid commenting obvious UI or Kotlin mechanics; document the parts that encode system behavior or architectural assumptions.
- If app behavior or protocol changes, update the shared docs alongside the code.

## Structure Pattern
- Use feature-first structure inside the Android app, with a small number of technical support roots.
- Prefer parent directories with established Android terminology:
  - `feature/` for operator capabilities and screen flows.
  - `core/` for cross-feature code that is not itself a user feature.
  - `app/` for app shell wiring such as `Application`, root activity, navigation shell, and top-level DI bootstrap.
- Inside a feature, keep Android layering local to that feature when needed:
  - `ui/` for composables, screen state, and screen-level ViewModels.
  - `domain/` only if the feature grows enough to justify feature-local use cases or policies.
  - `data/` only for feature-specific repositories or adapters.
- Inside `core/`, prefer clear capability-oriented names over generic buckets:
  - `core/transport/` for websocket, connectivity, reconnect, and session code.
  - `core/protocol/` for command payloads, DTOs, serialization, protocol constants, and message parsing.
  - `core/designsystem/` or `core/ui/` for theme and reusable visual primitives.
  - `core/di/` for dependency wiring.
  - `core/common/` only for truly shared utilities with narrow scope. Do not create a dumping ground.
- Avoid growing top-level packages like `data/`, `home/`, or `navigation/` when their contents actually belong to either a feature or a cross-cutting core capability.
- For the current app, the preferred target shape is:
  - `app/` for `BoloopApp`, `MainActivity`, and root wiring.
  - `feature/drivecontrol/` for the current operator control screen and its state holder.
  - `core/transport/` for websocket connection management and connectivity handling.
  - `core/protocol/` for navigation command types, acknowledgements, and protocol constants.
  - `core/designsystem/` for Compose theme code.
- When adding new files, place them by axis of change:
  - If a change is about operator workflow or a screen, it belongs in `feature/...`.
  - If a change is about transport, protocol, or reusable app infrastructure, it belongs in `core/...`.
  - If a file exists only to wire the app together, it belongs in `app/`.
