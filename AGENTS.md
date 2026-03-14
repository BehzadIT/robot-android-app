# AGENTS.md

## Purpose
This repo contains the Android operator app for the robot project.

## Instructions
- Before changing code here, read the shared docs root at `/home/behzad/Projects/robot-project-docs`.
- At minimum, review:
  - `/home/behzad/Projects/robot-project-docs/overview/current-architecture.md`
  - `/home/behzad/Projects/robot-project-docs/control-and-apps/android-control-app.md`
  - `/home/behzad/Projects/robot-project-docs/control-and-apps/operator-control-model.md`
  - `/home/behzad/Projects/robot-project-docs/interfaces/command-protocol.md`
  - `/home/behzad/Projects/robot-project-docs/verification/known-issues-and-open-questions.md`
- Keep a clear distinction between current implemented behavior and intended future operator model.
- Do not assume the ESP32 is the command endpoint; verify command routing against the shared docs and current code.
- When making non-trivial decisions, state the relevant best practice, design pattern, technology choice, and architecture tradeoff.
- Prefer reliability-oriented networking patterns for mobile clients: explicit connection state, bounded reconnect/backoff, keepalive configured for the actual engine in use, and transport observability.
- Log meaningful transport and protocol events, but classify expected reconnects as normal operational signals rather than fatal failures.
- Code documentation matters in this project. Add clear comments or docstrings for non-obvious behavior, especially around connection lifecycle, retry/backoff policy, protocol semantics, state ownership, and failure handling.
- Avoid commenting obvious UI or Kotlin mechanics; document the parts that encode system behavior or architectural assumptions.
- If app behavior or protocol changes, update the shared docs alongside the code.
