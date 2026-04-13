# Android Code Documentation Guide

## Purpose
Document the Android operator app so transport behavior, state ownership, and
operator-facing assumptions stay clear.

Audience:
- Android engineers changing UI state or transport behavior;
- integrators tracing app behavior against Pico protocol and safety semantics;
- future maintainers separating current behavior from future operator UX.

## Where Documentation Belongs
- Source docs: public Kotlin contracts and non-obvious state/transport logic.
- Markdown docs: implemented operator behavior, app-side protocol semantics,
  reconnect expectations, and future UI direction.
- Parent shared docs: system-wide command semantics and subsystem ownership.

## What To Document
- public transport interfaces and protocol-bearing types;
- reconnect/backoff behavior;
- stop/ack handoff behavior;
- state-holder responsibilities;
- assumptions about what the Pico, not Android, owns.

Use KDoc for public contracts and inline comments for state-machine edges or
behavior that would otherwise be misread.

## What Not To Document
- trivial Kotlin or coroutine mechanics;
- temporary UI structure as if it were durable architecture; and
- comments that imply Android owns motor control, final stop/fault authority,
  or drivetrain timing.

Avoid repeating the same protocol explanation across many files.

## Kotlin Style
- Use KDoc on public interfaces, classes, and functions whose behavior matters
  outside one local call site.
- Document purpose, inputs/outputs, sequencing expectations, failure behavior,
  and externally visible side effects.
- Use inline comments only for:
  - why a state transition exists
  - why retry/backoff is shaped a certain way
  - why stop acknowledgment is required
  - why Android intentionally defers behavior to the Pico

## Robotics Rules
- Document operator intent separately from drivetrain internals.
  `throttle_percent` and `turn_percent` are operator intent; Pico owns the
  final control meaning.
- Document degraded behavior explicitly:
  - reconnect never auto-resumes motion
  - disconnect requires a fresh drive command later
  - stop acknowledgment matters because robot-side stop completion is not
    owned by the app
- If protocol payloads, reconnect behavior, stop/ack sequencing, or operator
  semantics change, update Markdown docs in the same change.

## Repo Examples
- [NavigationWebSocketApi.kt](../app/src/main/java/com/bizi/roboapp/core/transport/NavigationWebSocketApi.kt)
  is the right place for KDoc on websocket lifecycle, retry policy, command
  sequencing, and send guarantees.
- [HomeViewModel.kt](../app/src/main/java/com/bizi/roboapp/feature/drivecontrol/ui/HomeViewModel.kt)
  is the right place to explain stop-pending state, forward/reverse stop
  handoff, reconnect behavior, and app-vs-Pico ownership.

## Review Checklist
- Public transport/protocol interfaces have KDoc when relied on externally.
- Comments explain reconnect, ack, or ownership semantics, not trivial Kotlin.
- Operator-intent language stays separate from motor-control language.
- Current behavior is not confused with future planned UX.
- Protocol or transport changes update Markdown docs too.

## References
- Kotlin KDoc:
  https://kotlinlang.org/docs/kotlin-doc.html
