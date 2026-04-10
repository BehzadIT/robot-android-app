# AGENTS.md

## Purpose
This repo contains the Android operator app for the robot project.

## Instructions
- When working from the parent `ai-robot` workspace, read the shared docs root at
  `../docs`.
- At minimum, review:
  - `../docs/overview/current-architecture.md`
  - `../docs/control-and-apps/android-control-app.md`
  - `../docs/control-and-apps/operator-control-model.md`
  - `../docs/interfaces/command-protocol.md`
  - `../docs/verification/known-issues-and-open-questions.md`
- This repository is a submodule of the parent `ai-robot` integration
  workspace, but it keeps its own Git history and release cycle.
- Keep a clear distinction between current implemented behavior and intended future operator model.
- Do not assume the ESP32 is the command endpoint; verify command routing against the shared docs and current code.
- When making non-trivial decisions, state the relevant best practice, design pattern, technology choice, and architecture tradeoff.
- Before making any non-trivial code or design change, explicitly tell the user:
  - the architectural boundary being preserved or changed
  - the design pattern or app structure being used
  - the best practice being followed
  - the software engineering reason for the choice
  - the robotics/operator-control reason for the choice, especially around state ownership, latency tolerance, degraded connectivity, and preventing unsafe UI assumptions
- For trivial edits, keep this brief but still state whether the change affects UI structure, state management, transport behavior, or only local code shape.
- If no named pattern is appropriate, say that directly and explain the simpler engineering rule being applied instead.
- Prefer reliability-oriented networking patterns for mobile clients: explicit connection state, bounded reconnect/backoff, keepalive configured for the actual engine in use, and transport observability.
- Favor Android patterns that make state ownership explicit, such as unidirectional data flow, repository/adaptor boundaries, feature-local state holders, and transport isolation from UI rendering.
- When proposing architecture changes, explain why the behavior belongs in the Android operator app instead of firmware or shared protocol documentation.
- Log meaningful transport and protocol events, but classify expected reconnects as normal operational signals rather than fatal failures.
- Code documentation matters in this project. Add clear comments or docstrings for non-obvious behavior, especially around connection lifecycle, retry/backoff policy, protocol semantics, state ownership, and failure handling.
- Avoid commenting obvious UI or Kotlin mechanics; document the parts that encode system behavior or architectural assumptions.
- If app behavior or protocol changes, update the shared docs alongside the code.
- If the change updates a known-good integrated robot state, update the parent
  repository's submodule pointer after committing this repo.

## Communication Contract
- For meaningful tasks, give the user a short design note before editing or implementing:
  - architecture: what layer or feature owns the behavior
  - pattern: UDF, repository, adapter, state holder, feature-first organization, or another concrete choice
  - best practice: the maintainability, reliability, or UX rule being followed
  - reasoning: why that choice improves operator clarity, fault tolerance, and separation of concerns in the robot system
- After implementation, summarize whether the result kept UI concerns, transport concerns, and protocol concerns properly separated, and note any remaining tradeoffs or validation gaps.

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
